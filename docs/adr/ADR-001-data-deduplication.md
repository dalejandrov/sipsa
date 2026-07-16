# ADR-001 — Data Deduplication Strategy

**Status:** Accepted (2026-07-16)  
**Date:** 2026-07-13 (accepted 2026-07-16 with empirical evidence from real DANE ingestions)  
**Author:** Architectural review (2026-07-13)  
**Backlog:** [TECH-010](../backlog/technical-backlog.md#tech-010), [TECH-011](../backlog/technical-backlog.md#tech-011)

---

## Context

SIPSA Integration Service ingests data from five DANE SOAP endpoints. Each endpoint can be
called multiple times (daily, forced re-runs, scheduled triggers). The system must handle
repeated ingestion of the same logical data without creating duplicate records.

Each data type uses a different deduplication strategy — and one of them (`SipsaParcial`) is
broken.

---

## Current Deduplication per Data Type

| Type | Method | Current strategy | Status |
|---|---|---|---|
| Ciudad | `promediosSipsaCiudad` | Business key lookup `(regId, codProducto)` before insert | Works |
| Semanal | `promediosSipsaSemanaMadr` | `tmpMayoSemId` (when available) or `(artiId, fuenId, fechaIni)` | Works |
| Mensual | `promediosSipsaMesMadr` | `tmpMayoMesId` (when available) or `(artiId, fuenId, fechaMesIni)` | Works |
| Abas | `promedioAbasSipsaMesMadr` | `tmpAbasMesId` (when available) or `(artiId, fuenId, fechaMesIni)` | Works |
| Parcial | `promediosSipsaParcial` | `UUID.randomUUID()` — non-deterministic | **Broken** |

---

## Problem

`SipsaIngestionMapper.computeKeyHash()` generates a new random UUID on every call:

```java
// SipsaIngestionMapper.java:164
default String computeKeyHash(SipsaParcialRecord record, Instant fechaEncuesta) {
    return UUID.randomUUID().toString();  // different on every call
}
```

`SipsaParcialRepository.batchUpsert()` inserts all records without deduplication:

```java
// SipsaParcialRepository.java:41-57
default UpsertMetrics batchUpsert(List<SipsaParcial> items) {
    // ...sets fechaSincronizacion...
    saveAll(items);  // inserts everything, no checks
    return new UpsertMetrics(inserted, 0);  // skipped is always 0
}
```

The `sipsa_parcial` table has a `key_hash UNIQUE` constraint, but because every ingestion
assigns a new UUID, the constraint is never violated and duplicates accumulate.

---

## Questions Requiring Business Input

Before this ADR can be accepted, the following questions must be answered:

1. **What is the natural business key for a Parcial record?**
   - Candidate: `(muniId, fuenId, futiId, idArtiSemana, enmaFecha)` — combination of municipality, source, source type, article ID, and survey date.
   - Needs confirmation that this combination uniquely identifies a price observation.

2. **Does Parcial data change between daily runs for the same logical period?**
   - If DANE publishes updated prices for the same survey date in a subsequent call, should the system update existing records or retain the original?

3. **Should the system maintain historical snapshots or only the latest value per key?**
   - Current Ciudad/Semanal/Mensual strategy: **skip if exists** (immutable after first ingestion).
   - Alternative: **update if exists** (always keep the latest value).

4. **What is the volume in production?**
   - See [TECH-012](../backlog/technical-backlog.md#tech-012): diagnostic query needed to assess impact.

---

## Alternatives

### Option A — Deterministic hash from business key (Recommended pending confirmation)

```java
// Compute a stable hash from business identifiers
default String computeKeyHash(SipsaParcialRecord record, Instant fechaEncuesta) {
    String input = record.muniId() + "|" + record.fuenId() + "|"
        + record.futiId() + "|" + record.idArtiSemana() + "|"
        + (fechaEncuesta != null ? fechaEncuesta.toEpochMilli() : "null");
    return DigestUtils.sha256Hex(input);  // or any stable hash
}
```

- `batchUpsert()` becomes a real upsert: find existing by `key_hash`, skip or update.
- Consistent with the skip-first approach of other data types.

**Pros:** Deterministic; UNIQUE constraint works as intended; no duplicates after fix.  
**Cons:** Requires data cleanup if production already has duplicates.

### Option B — Composite unique constraint in schema (no key_hash)

Add a database unique constraint on `(muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha)`.
Use PostgreSQL's `INSERT ... ON CONFLICT DO NOTHING` via a custom repository query.

**Pros:** No hash computation; constraint enforced at DB level.  
**Cons:** Requires a schema migration; longer unique constraint; `ON CONFLICT` is PostgreSQL-specific (reduces portability, but this project is PostgreSQL-only).

### Option C — Accumulate all records (keep UUID approach, accept duplicates)

Decide that Parcial data is append-only historical. Each daily run represents a snapshot.
Document this as intentional behavior.

**Pros:** No code change.  
**Cons:** Table grows unboundedly; no deduplication; inconsistent with other data types; existing `UNIQUE` constraint on `key_hash` is misleading.

---

## Decision

**Option A — deterministic hash + insert-only/skip — accepted (2026-07-16)**, on empirical
evidence from controlled ingestions of the real DANE endpoint into a clean local
PostgreSQL 18 (full procedure and per-query results in the
[TECH-012 diagnostics](../diagnostics/tech-012-runbook.md) flow; summary below):

1. **The natural key is confirmed unique on real data.** One full ingestion returned
   676,210 records spanning 2020-02-01 → 2026-07-15 (1,997 survey dates — DANE re-publishes
   its complete history on every call), and produced exactly 676,210 distinct
   `(muniId, fuenId, futiId, idArtiSemana, enmaFecha)` tuples — zero intra-run collisions.
2. **Duplication was linear per run, as PS-01 predicted.** A second identical ingestion
   under the pre-fix code doubled the table (1,352,420 rows): 676,210 duplicate groups,
   every one exactly ×2, **all with identical prices** (zero divergent groups) — DANE
   returned a byte-identical dataset, so skip-first semantics lose nothing.
3. **`idArtiSemana` is stable across runs** — every re-published record matched its
   previous key. **`enmaFecha` arrives as a parseable ISO instant** (all values are local
   midnight `America/Bogota`, stored as `05:00Z`; zero unparseable dates — H-1's mechanism
   exists in code but did not fire on real data; parsing is now strict anyway).
4. Skip-first matches the semantics of the other four data types.

**Answers to the business questions:** (1) yes — confirmed empirically; (2) skip (observed
re-publications are identical; revisit only if divergent re-publications ever appear);
(3) no snapshots — canonical state only; (4) no external historical database is known to
exist — if one is ever confirmed, its cleanup is a separate story (TECH-115 proposal);
the local diagnostic base was rebuilt from scratch after the fix, so no cleanup migration
was needed.

**Scope note:** hash payload is versioned (`v1`), unit-separator delimited, UTF-8,
SHA-256 lowercase hex — see `ParcialKeyHash`. Legacy UUID rows deduplicate at ingestion
time via a natural-key candidate lookup (no backfill required); a backfill/consolidation
of any pre-existing external database remains conditional and out of scope here.

---

## Consequences

**If Option A is chosen:**
- `computeKeyHash()` becomes deterministic.
- `SipsaParcialRepository.batchUpsert()` must implement bulk key lookup.
- If production has duplicates: a one-time cleanup migration is required.
- The `key_hash UNIQUE` constraint becomes meaningful.

**If Option B is chosen:**
- A new Flyway migration `V2__parcial_natural_key.sql` is required.
- The `key_hash` column may be removed.
- Repository uses native SQL for `ON CONFLICT DO NOTHING`.

**If Option C is chosen:**
- The `UNIQUE` constraint on `key_hash` must be dropped (it creates a false expectation of uniqueness).
- The system must be documented as accumulating historical snapshots.
- Monitoring/alerting on table growth is mandatory.

---

*Update this ADR to `Accepted` after the business questions are answered and TECH-010 is resolved.*
