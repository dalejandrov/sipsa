# ADR-005 — Scheduler Execution Model

**Status:** Proposed  
**Date:** 2026-07-13  
**Backlog:** [TECH-053](../backlog/technical-backlog.md#tech-053)

---

## Context

The SIPSA ingestion scheduler (`SipsaIngestionScheduler`) is a Spring `@Scheduled` component
that triggers three cron-based ingestion windows:

- Daily at 14:20 COT: Ciudad, Parcial, Semana (sequential).
- Day 8 at 14:30 COT: MesMadr.
- Day 10 at 14:30 COT: AbasMes.

The scheduling thread pool is configured with 5 threads (`sipsa.scheduling.pool-size: 5`).

---

## Problem

**Current behavior (synchronous):**

```java
// SipsaIngestionScheduler.java:124-135
private void runSafely(String methodName) {
    ingestionJob.execute(request);  // blocks until complete
}
```

`runDailyWindow()` calls `runSafely()` three times sequentially. The three ingestion jobs
(Ciudad, Parcial, Semana) run one after another in the same scheduler thread.

**Consequence:** During the daily window, one of the 5 scheduler threads is occupied for the
entire duration of three sequential ingestion runs. For Parcial (619K+ records), this can
take tens of minutes to over an hour.

**Risk level:** Low-to-medium for a single-instance deployment. The other 4 scheduler threads
remain available for other cron tasks. The main risk is if the same `@Scheduled` method is
triggered again while the previous run is still in the same thread — Spring's default behavior
is to skip the next invocation if the previous one has not completed in the same thread.

---

## Alternatives

### Option A — Dispatch to async executor (Recommended)

```java
private void runSafely(String methodName) {
    asyncIngestionService.executeAsync(request);  // returns immediately
}
```

The scheduler thread returns immediately after dispatch. The ingestion runs in the
`ingestionTaskExecutor` pool.

**Pros:** Scheduler thread never blocks for long; simpler reasoning about scheduler capacity.  
**Cons:**
1. If `ingestionTaskExecutor` queue is full (`queue-capacity: 25`), the job submission is rejected by `CallerRunsPolicy` — the scheduler thread will block after all. (Can be mitigated by increasing queue capacity or using `AbortPolicy` with explicit error handling.)
2. The scheduler cannot directly detect if the ingestion job is still running from the previous cron invocation. This is already handled by the `isRunComplete()` check in `IngestionJob.execute()` — a duplicate window will be skipped.
3. If the JVM is shut down gracefully, async jobs in the executor queue may not complete.

### Option B — Keep synchronous execution (current)

Accept that one scheduler thread is blocked during ingestion. Document as a known limitation.

**Pros:** Simple; the scheduler is aware if the job is still running; no risk of double-dispatch.  
**Cons:** Scheduler thread blocked for long periods; potential scheduling delays for other tasks if thread pool is saturated.

### Option C — Separate thread pool per ingestion method

Create a dedicated single-thread executor for the daily window, separate from the general `ingestionTaskExecutor`.

**Pros:** Complete isolation between scheduling and ingestion concerns.  
**Cons:** Additional configuration complexity; more thread pools to manage.

---

## Decision

**Not yet decided.** This ADR is `Proposed`.

Recommendation: **Option A**, with the following safeguards:
1. Increase `sipsa.ingestion.async.queue-capacity` from 25 to a value that accommodates all expected concurrent ingestion jobs (at most 5: 3 daily + 2 monthly).
2. Document that if the executor is saturated, the `CallerRunsPolicy` will cause the scheduler thread to block — the same behavior as Option B but less predictable.
3. Validate that `isRunComplete()` correctly prevents duplicate window processing when the scheduler fires again before the previous run has finished.

---

## Consequences

**If Option A is accepted:**
- `SipsaIngestionScheduler` depends on `AsyncIngestionService` instead of `GenericIngestionJob`.
- Scheduler threads are never blocked by ingestion work.
- The `ingestionTaskExecutor` pool size must be sufficient for all concurrent ingestion methods.
- Graceful shutdown (`await-termination: true`) ensures in-progress ingestions complete before JVM exit.

**If Option B is accepted:**
- No code change.
- Document the 1-thread-per-window limitation in this ADR and in operational runbooks.

---

*Update this ADR to `Accepted` after TECH-053 is implemented.*
