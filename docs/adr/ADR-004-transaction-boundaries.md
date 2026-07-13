# ADR-004 — Transaction Boundaries in the Ingestion Pipeline

**Status:** Accepted  
**Date:** 2026-07-13  
**Author:** Derived from code analysis (architectural review 2026-07-13)

---

## Context

The SIPSA ingestion pipeline processes potentially millions of records from a SOAP service
and writes them to PostgreSQL. The pipeline must handle partial failures gracefully:
if processing fails at record 50,000 of 619,000, previously committed records should not
be lost, and the run should be recoverable.

---

## Problem

A single database transaction spanning the full ingestion of a SOAP response is not viable:
- Parcial responses contain 619,000+ records.
- Processing takes tens of minutes.
- A long-lived transaction holds DB locks, consumes connection pool resources, and cannot
  be rolled back incrementally.

Additionally, audit events and run status updates must persist even when the ingestion itself fails.

---

## Decision

The ingestion pipeline uses **no enclosing transaction**. Instead:

1. **Run lifecycle operations** (`createRun`, `updateStatus`, `updateMetrics`, `logError`, `logReject`) each use `@Transactional(propagation = REQUIRES_NEW)`, committing independently of the ingestion result.

2. **Batch persistence** (`repository.batchUpsert()`) uses its own transaction per batch call. If a batch fails, only that batch is rolled back. Previously committed batches are not affected.

3. **Audit events** use `@Async + @Transactional(REQUIRES_NEW)`. They run in a separate thread and commit independently. They persist even if the ingestion transaction is in an exception path.

4. **SOAP calls** are made outside any database transaction. There is no distributed transaction between the SOAP call and the database write.

### Transaction map

```
IngestionJob.execute()                [no transaction]
├─ controlService.createRun()         [REQUIRES_NEW → commits]
├─ auditService.logEvent()            [async REQUIRES_NEW → commits in separate thread]
├─ controlService.updateStatus()      [REQUIRES_NEW → commits]
│
├─ handler.execute()                  [no transaction]
│   ├─ soapGateway.getData()          [remote call — no transaction]
│   └─ repository.batchUpsert()       [own @Transactional → commits per batch]
│       ...repeated for each batch...
│
├─ controlService.isRunCanceled()     [readOnly]
├─ controlService.updateStatus()      [REQUIRES_NEW → commits]
│
└─ [finally block — always executes]
    ├─ controlService.updateMetrics() [REQUIRES_NEW → commits]
    ├─ controlService.logReject() × N [REQUIRES_NEW per call → commits]
    └─ auditService.logEvent()        [async REQUIRES_NEW → commits in separate thread]
```

---

## Rationale

- **Partial commits are intentional.** If the system processes 300,000 of 619,000 records before a network timeout, those 300,000 records are saved. A forced restart will re-ingest from scratch (no checkpointing), but the data that was already committed is not lost.
- **REQUIRES_NEW for lifecycle** ensures that run status and audit events are persisted even if the caller's transaction (if any) is rolled back. Since ingestion runs in a thread with no outer transaction, this creates a fresh transaction each time.
- **@Async for audit** allows audit logging to be non-blocking. The ingestion pipeline does not wait for audit to complete. The trade-off is that audit events can be lost if the JVM crashes between async dispatch and commit.

---

## Consequences

**Accepted:**
- Audit events are eventually consistent, not immediately consistent. A run may be marked FAILED before all audit events for that run are committed.
- Partial ingestion data is visible in the database while the run is still in RUNNING status. Queries against the data domain tables may see incomplete data.
- There is no rollback of partially committed data if the ingestion quality threshold fails. The run is marked FAILED and rejected records are logged, but inserted records remain.

**Not accepted:**
- It is explicitly prohibited to include a SOAP call inside a database transaction. Any future change that adds `@Transactional` to `IngestionJob.execute()` must be reviewed carefully — it would wrap SOAP calls in a DB transaction, which is incorrect.
- Distributed transactions (XA) between SOAP and PostgreSQL are not considered.

---

## Verification

This decision is confirmed by code analysis:
- `IngestionJob.java:103-265`: no `@Transactional` on `execute()`.
- `handler.execute()`: no `@Transactional`.
- `repository.batchUpsert()`: `@Transactional` without `REQUIRES_NEW` — creates its own transaction or joins an existing one (no outer transaction exists, so creates new).
- `soapGateway.getCiudadData()`: no `@Transactional`.
