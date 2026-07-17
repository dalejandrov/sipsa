# Technical Debt Registry — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13  
**Last updated:** 2026-07-13

This registry tracks all known technical debt items identified during the architectural review.
Each item references a backlog story for implementation planning.

---

## Security Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| S-01 | ~~No authentication on `/api/internal/**` endpoints~~ **Resolved** (2026-07-15, TECH-001/ADR-002 — OAuth 2.0 Resource Server with per-operation Cognito scopes; gateway/network layers tracked as TECH-130..132) | **Critical** | Unauthorized ingestion activation, data exposure | S | Low (Spring Security addition) | [TECH-001](../backlog/technical-backlog.md#tech-001) |
| S-02 | ~~Actuator `loggers` endpoint publicly accessible~~ **Resolved** (exposure: 2026-07-14 dev-only; authentication on all non-health actuator: 2026-07-15, TECH-002) | **High** | Runtime log level change enables data exposure | XS | Low | [TECH-002](../backlog/technical-backlog.md#tech-002) |

**Rationale:** S-01 was documented with a `TODO` comment in the original code. No Spring Security web dependency exists in the project.

---

## Testing Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| T-01 | ~~No unit tests for `WindowPolicy`~~ **Resolved** (2026-07-13, TECH-040/TECH-110 — 25 deterministic tests via injected `Clock`) | **High** | Undetected bugs in time-window logic, timezone errors | S | Low (additive) | [TECH-040](../backlog/technical-backlog.md#tech-040) |
| T-02 | No unit tests for `SpecificationBuilder` | **High** | Filter regressions go undetected | S | Low | [TECH-041](../backlog/technical-backlog.md#tech-041) |
| T-03 | No unit tests for `IngestionJob` | **High** | Central pipeline has no safety net | M | Low | [TECH-042](../backlog/technical-backlog.md#tech-042) |
| T-04 | No tests for `GlobalExceptionHandler` | **Medium** | Error contract regressions go undetected | S | Low | [TECH-043](../backlog/technical-backlog.md#tech-043) |
| T-05 | No integration tests for any ingestion handler | **Medium** | End-to-end SOAP → parse → persist path untested | L | Low | [TECH-044](../backlog/technical-backlog.md#tech-044) (SPIKE) |
| T-06 | ~~Single context-load test as only test suite~~ **Resolved** (2026-07-13, TECH-110 — 65 tests across 7 classes on `main`; remaining gaps are T-02..T-05) | **High** | Zero behavioral coverage | — | — | Foundation for T-01 through T-05 |

---

## Observability Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| O-01 | No custom Micrometer metrics for ingestion | **Medium** | Cannot alert on duration, rejects, SOAP failures | M | Low | [TECH-032](../backlog/technical-backlog.md#tech-032) |
| O-02 | `SipsaHealthIndicator` thresholds hardcoded | **Low** | Not configurable per environment | XS | Low | [TECH-031](../backlog/technical-backlog.md#tech-031) |
| O-03 | No `requestId`/`instance` in error responses | **Low** | Difficult to correlate client errors with server logs | S | Low | [TECH-023](../backlog/technical-backlog.md#tech-023) |
| O-04 | `GET /api/internal/ingestion/runs` unbounded | **Low** | Memory/performance issue at scale | S | Low | [TECH-054](../backlog/technical-backlog.md#tech-054) |

---

## Performance Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| P-01 | N+1 query in `SipsaMayoristasSemanalRepository.upsertFallbackBatch()` | **Medium** | Linear DB calls per record instead of constant | M | Medium | [TECH-060](../backlog/technical-backlog.md#tech-060) |
| P-02 | Scheduler blocks one of 5 threads during long ingestion runs | **Medium** | Scheduling throughput degraded for Parcial (619K records) | S | Medium | [TECH-053](../backlog/technical-backlog.md#tech-053) |

---

## Architecture Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog / Note |
|---|---|---|---|---|---|---|
| A-01 | Application layer imports from `api.dto.request` — **Partially resolved / re-scoped** (2026-07-13): TECH-090 (ADR-007, merged) removed the 3 internal-command imports; the 5 files still importing from `api` were investigated and **accepted** by ADR-007, so no further action is planned | **Low** | Coupling increase; changes to HTTP DTOs ripple into core | L | Medium | [TECH-090](../backlog/technical-backlog.md#tech-090) (Done). Residual guarded by TECH-093 (ArchUnit) when implemented. |
| A-02 | `WindowPolicy.isMonthlyMethod()` uses string matching | **Low** | New monthly handlers with different naming break scheduling | S | Low | [TECH-055](../backlog/technical-backlog.md#tech-055) (SPIKE) |
| A-03 | `SipsaParseException` mapped to HTTP 400 (should be 502) | **Medium** | Wrong HTTP semantics for upstream XML errors | XS | Low | [TECH-021](../backlog/technical-backlog.md#tech-021) |
| A-04 | "Not found" cases return HTTP 422 (should be 404) | **Medium** | Wrong HTTP semantics | S | Low | [TECH-022](../backlog/technical-backlog.md#tech-022) |

---

## Configuration Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| C-01 | `SoapProperties` lacks Bean Validation constraints | **Low** | Configuration errors discovered at runtime | XS | Low | [TECH-070](../backlog/technical-backlog.md#tech-070) |
| C-02 | ~~`batch-size` default mismatch: `@Value` (2000) vs `application.yaml` (500)~~ **Resolved** (2026-07-16, TECH-071 — all 5 handlers now inject the typed `IngestionProperties`; canonical default 500, startup validation 1..10,000, `INGESTION_BATCH_SIZE` override verified in Docker) | **Low** | Confusing to read; may surprise on fresh deploy without yaml | XS | Low | [TECH-071](../backlog/technical-backlog.md#tech-071) (Done) |
| C-03 | ~~`monthly-window-start` default mismatch: `WindowPolicy` `@Value` fallback (06:00) vs `application.yaml` (14:00)~~ **Resolved** (2026-07-17, TECH-133 — typed `LocalTime` in `IngestionProperties`, canonical 14:00, HH:mm validated at startup, `INGESTION_MONTHLY_WINDOW_START` override verified in Docker; effective behavior unchanged) | **Low** | Misleading fallback promised a different window on yaml-less deploys | XS | Low | [TECH-133](../backlog/technical-backlog.md#tech-133) (Done) |
| C-04 | Reject-threshold binding duplicated: `IngestionJob` and `GenericIngestionJob` each re-declare `@Value` defaults for `sipsa.ingestion.max-reject-rate` / `max-reject-count` (values currently agree with yaml) | **Low** | Same double-source antipattern fixed by TECH-071/TECH-133; a future edit can silently de-synchronize them | XS | Low | Candidate for a future typed-config story (found during TECH-071/TECH-133 inventories; not yet scheduled) |
| C-05 | `AsyncConfig` re-declares `@Value` defaults for `sipsa.ingestion.async.*` (2/10/25/60) that currently match yaml | **Low** | Maintainability only — no functional divergence confirmed | XS | Low | Candidate for a future typed-config story (not yet scheduled) |

---

## Persistence Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| PS-01 | ~~`SipsaParcial` deduplication uses random UUID — no real deduplication~~ **Resolved** (2026-07-16, TECH-010/TECH-011/ADR-001 — deterministic natural-key hash + skip-first upsert, validated with 3 real DANE ingestions: linear ×run duplication before, 0 duplicates after; legacy UUID rows deduplicate at ingestion time) | **Critical** | Potential unbounded table growth with duplicate records per run | M | High | [TECH-010](../backlog/technical-backlog.md#tech-010) (SPIKE), [TECH-011](../backlog/technical-backlog.md#tech-011) |
| PS-02 | Business logic (`batchUpsert`) in repository `default` methods | **Low** | SRP violation; logic hard to test independently from Spring Data | M | Medium | Not in active backlog. See [Refactoring Roadmap](refactoring-roadmap.md). |

---

## Code Quality Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| Q-01 | `// ...existing code...` placeholder comments in 4 production files | **Low** | Misleading; indicates unfinished work | XS | Low | [TECH-050](../backlog/technical-backlog.md#tech-050) |
| Q-02 | `toAuditEventRequest()` returns response type (naming error) | **Low** | Misleading; forces double-reading to understand intent | XS | Low | [TECH-051](../backlog/technical-backlog.md#tech-051) |
| Q-03 | `IngestionControlService.getRun()` returns nullable `IngestionRun` | **Low** | Breaks Optional contract; callers do null checks | XS | Low | [TECH-052](../backlog/technical-backlog.md#tech-052) |
| Q-04 | `@RequestMapping` without leading `/` on 2 controllers | **Bug** | Inconsistent; potentially broken with strict proxies | XS | Low | [TECH-020](../backlog/technical-backlog.md#tech-020) |
| Q-05 | `@Async` in `IngestionAuditService.logEvent()` uses default executor | **Low** | Unmanaged threads (SimpleAsyncTaskExecutor) instead of pool | XS | Low | [TECH-030](../backlog/technical-backlog.md#tech-030) |
| Q-06 | `AuditTrailService.queryAuditEvents()` builds its own `Pageable` | **Low** | Ignores `PaginationConfig`; inconsistent with all other services | XS | Low | Not in active backlog; natural companion of [TECH-054](../backlog/technical-backlog.md#tech-054) (pagination) when that story runs |

---

## Debt Summary

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Security | 1 | 1 | — | — | 2 |
| Testing | — | 3 | 2 | 1 | 6 |
| Observability | — | — | 1 | 3 | 4 |
| Performance | — | — | 2 | — | 2 |
| Architecture | — | — | 2 | 2 | 4 |
| Configuration | — | — | — | 2 | 2 |
| Persistence | 1 | — | — | 1 | 2 |
| Code Quality | — | — | — | 6 | 6 |
| **Total** | **2** | **4** | **7** | **15** | **28** |

**Status note (2026-07-15):** of the 28 registered items, **4 are resolved** (T-01, T-06 —
closed by TECH-110/TECH-040; S-01, S-02 — closed by TECH-001/TECH-002/ADR-002 on
2026-07-15, application layer merged via PR #17 and e2e-validated against the mock OIDC
issuer; the AWS gateway/network layers remain tracked as TECH-130..132)
and **1 is partially resolved / re-scoped** (A-01 — closed for
the 3 internal commands by TECH-090; the residual imports are accepted by ADR-007). Items
are annotated in place rather than deleted, so the registry remains the full historical
record. All other items remain pending.

---

*Related documents:*
- *[Architecture Review](architecture-review.md) — source of all findings*
- *[Technical Backlog](../backlog/technical-backlog.md) — implementation stories*
- *[Refactoring Roadmap](refactoring-roadmap.md) — items not in active backlog*
