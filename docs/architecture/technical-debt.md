# Technical Debt Registry — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13  
**Last updated:** 2026-07-20 (reconciled against `main` — resolution evidence added for
O-03, A-03, A-04, T-04 (TECH-023/021/022/043, the HTTP error contract closure) and O-01
(TECH-032, ingestion observability); see the status note at the end of this document)

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
| T-04 | ~~No tests for `GlobalExceptionHandler`~~ **Resolved** (2026-07-20, TECH-043 — `GlobalExceptionHandlerContractTest` covers all 15 `@ExceptionHandler` cases via real MVC dispatch; status, `Content-Type`, `code`, `message`, `requestId`, `instance`, timestamp, and no-stack-trace-leak all asserted; no production code changed, no defects found) | **Medium** | Error contract regressions go undetected | S | Low | [TECH-043](../backlog/technical-backlog.md#tech-043) (Done) |
| T-05 | No integration tests for any ingestion handler | **Medium** | End-to-end SOAP → parse → persist path untested | L | Low | [TECH-044](../backlog/technical-backlog.md#tech-044) (SPIKE) |
| T-06 | ~~Single context-load test as only test suite~~ **Resolved** (2026-07-13, TECH-110 — 65 tests across 7 classes on `main`; remaining gaps are T-02..T-05) | **High** | Zero behavioral coverage | — | — | Foundation for T-01 through T-05 |

---

## Observability Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| O-01 | ~~No custom Micrometer metrics for ingestion~~ **Resolved** (2026-07-20, TECH-032 — new `IngestionMetrics` component instruments `IngestionJob.execute` and `SoapStreamingClient.stream`; `sipsa.ingestion.duration`/`runs`/`records.*` and `sipsa.soap.calls`/`failures`/`retries`/`duration`, all with bounded `method`/`outcome`/`source` tags; a latent bug found and fixed along the way — `micrometer-registry-prometheus` was marked `optional`, silently excluded from the runnable jar by Spring Boot's repackage goal) | **Medium** | Cannot alert on duration, rejects, SOAP failures | M | Low | [TECH-032](../backlog/technical-backlog.md#tech-032) (Done) |
| O-02 | ~~`SipsaHealthIndicator` thresholds hardcoded~~ **Resolved** (2026-07-19, TECH-031 — externalized to validated `SipsaHealthProperties`, canonical defaults `36h`/`840h` unchanged) | **Low** | Not configurable per environment | XS | Low | [TECH-031](../backlog/technical-backlog.md#tech-031) (Done) |
| O-03 | ~~No `requestId`/`instance` in error responses~~ **Resolved** (2026-07-19, TECH-023 — new `RequestIdFilter` establishes a correlation ID per request (validated incoming `X-Request-Id` or a generated UUID); `ErrorResponse`, `IngestionValidationErrorResponse`, and `ValidationErrorResponse` all gained `requestId` and `instance` additively) | **Low** | Difficult to correlate client errors with server logs | S | Low | [TECH-023](../backlog/technical-backlog.md#tech-023) (Done) |
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
| A-03 | ~~`SipsaParseException` mapped to HTTP 400 (should be 502)~~ **Resolved** (2026-07-19, TECH-021 — `GlobalExceptionHandler.handleParseException` now maps to `502 Bad Gateway`; error `code` (`PARSE_ERROR`), `message`, and body shape unchanged) | **Medium** | Wrong HTTP semantics for upstream XML errors | XS | Low | [TECH-021](../backlog/technical-backlog.md#tech-021) (Done) |
| A-04 | ~~"Not found" cases return HTTP 422 (should be 404)~~ **Resolved** (2026-07-19, TECH-022 — new `SipsaNotFoundException` maps to `404 Not Found`; migrated `IngestionRunQueryService.getRunStatus` and `IngestionControlService.cancelRun`'s not-found branch only — its not-active branch stays `SipsaBusinessException` → 422) | **Medium** | Wrong HTTP semantics | S | Low | [TECH-022](../backlog/technical-backlog.md#tech-022) (Done) |

---

## Configuration Debt

| ID | Item | Priority | Impact | Complexity | Risk | Backlog |
|---|---|---|---|---|---|---|
| C-01 | ~~`SoapProperties` lacks Bean Validation constraints~~ **Resolved** (2026-07-19, TECH-070 — `@Validated` + Jakarta constraints on all 9 fields matching the SOAP client's real requirements; two previously-unchecked-anywhere fields closed, `maxRetries` and `retryBackoffMs`; startup aborts naming the property; `docker-compose.yml` `SOAP_*` passthrough added, previously absent) | **Low** | Configuration errors discovered at runtime | XS | Low | [TECH-070](../backlog/technical-backlog.md#tech-070) (Done) |
| C-02 | ~~`batch-size` default mismatch: `@Value` (2000) vs `application.yaml` (500)~~ **Resolved** (2026-07-16, TECH-071 — all 5 handlers now inject the typed `IngestionProperties`; canonical default 500, startup validation 1..10,000, `INGESTION_BATCH_SIZE` override verified in Docker) | **Low** | Confusing to read; may surprise on fresh deploy without yaml | XS | Low | [TECH-071](../backlog/technical-backlog.md#tech-071) (Done) |
| C-03 | ~~`monthly-window-start` default mismatch: `WindowPolicy` `@Value` fallback (06:00) vs `application.yaml` (14:00)~~ **Resolved** (2026-07-17, TECH-133 — typed `LocalTime` in `IngestionProperties`, canonical 14:00, HH:mm validated at startup, `INGESTION_MONTHLY_WINDOW_START` override verified in Docker; effective behavior unchanged) | **Low** | Misleading fallback promised a different window on yaml-less deploys | XS | Low | [TECH-133](../backlog/technical-backlog.md#tech-133) (Done) |
| C-04 | ~~Reject-threshold binding duplicated: `IngestionJob` and `GenericIngestionJob` each re-declare `@Value` defaults for `sipsa.ingestion.max-reject-rate` / `max-reject-count`~~ **Resolved** (2026-07-19, TECH-135 — both thresholds bind once in `IngestionProperties` (rate validated as a fraction in [0..1], count ≥ 0, startup aborts on invalid values), jobs inject the typed properties, `MAX_REJECT_RATE`/`MAX_REJECT_COUNT` passthrough added to docker-compose and overrides verified in Docker; effective values 0.01/5000 and evaluation semantics unchanged) | **Low** | Same double-source antipattern fixed by TECH-071/TECH-133; a future edit can silently de-synchronize them | XS | Low | [TECH-135](../backlog/technical-backlog.md#tech-135) (Done) |
| C-05 | ~~`AsyncConfig` re-declares `@Value` defaults for `sipsa.ingestion.async.*` (2/10/25/60) that currently match yaml~~ **Resolved** (2026-07-19, TECH-136 — pool geometry binds once in `AsyncExecutorProperties` (validated incl. cross-field `max >= core`), `SIPSA_ASYNC_*` passthrough added to docker-compose and overrides/invalid-abort verified in Docker; additionally `IngestionAuditService.logEvent` now names its executor explicitly (`@Async("ingestionTaskExecutor")`), eliminating the "More than one TaskExecutor bean found" fallback to `SimpleAsyncTaskExecutor` ad-hoc threads; geometry and behavior unchanged) | **Low** | Maintainability only — no functional divergence confirmed | XS | Low | [TECH-136](../backlog/technical-backlog.md#tech-136) (Done) |

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
| Q-01 | ~~`// ...existing code...` placeholder comments in 4 production files~~ **Resolved** (2026-07-19, TECH-050 — one line removed per file, zero logic changes) | **Low** | Misleading; indicates unfinished work | XS | Low | [TECH-050](../backlog/technical-backlog.md#tech-050) (Done) |
| Q-02 | ~~`toAuditEventRequest()` returns response type (naming error)~~ **Resolved** (2026-07-19, TECH-051 — renamed to `toAuditEventResponse()`, 4 callers updated) | **Low** | Misleading; forces double-reading to understand intent | XS | Low | [TECH-051](../backlog/technical-backlog.md#tech-051) (Done) |
| Q-03 | ~~`IngestionControlService.getRun()` returns nullable `IngestionRun`~~ **Resolved** (2026-07-19, TECH-052 — returns `Optional<IngestionRun>`, propagating the repository's own `Optional`) | **Low** | Breaks Optional contract; callers do null checks | XS | Low | [TECH-052](../backlog/technical-backlog.md#tech-052) (Done) |
| Q-04 | ~~`@RequestMapping` without leading `/` on 2 controllers~~ **Resolved** (2026-07-19, TECH-020 — declared-contract normalization; effective routes were unaffected by Spring MVC's own normalization) | **Bug** | Inconsistent; potentially broken with strict proxies | XS | Low | [TECH-020](../backlog/technical-backlog.md#tech-020) (Done) |
| Q-05 | ~~`@Async` in `IngestionAuditService.logEvent()` uses default executor~~ **Resolved** (2026-07-19, TECH-136 — `@Async("ingestionTaskExecutor")`, verified free of `SimpleAsyncTaskExecutor` and the ambiguous-resolution warning; TECH-030 was the original story for this finding, closed by the same implementation) | **Low** | Unmanaged threads (SimpleAsyncTaskExecutor) instead of pool | XS | Low | [TECH-030](../backlog/technical-backlog.md#tech-030) / [TECH-136](../backlog/technical-backlog.md#tech-136) (Done) |
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

**Status note (updated 2026-07-20 against `main`):** of the 28 registered items,
**21 are resolved**: T-01, T-06 (closed by TECH-110/TECH-040); S-01, S-02 (closed by
TECH-001/TECH-002/ADR-002 on 2026-07-15, application layer merged via PR #17 and
e2e-validated against the mock OIDC issuer; the AWS gateway/network layers remain
tracked as TECH-130..132); PS-01 (closed by TECH-010/TECH-011/ADR-001 on 2026-07-16);
C-01, C-02, C-03, C-04, C-05 (closed by TECH-070/TECH-071/TECH-133/TECH-135/TECH-136,
2026-07-16 through 2026-07-19 — see [technical backlog](../backlog/technical-backlog.md)
for each); O-02 (closed by TECH-031, 2026-07-19); O-03 (closed by TECH-023, 2026-07-19);
A-03, A-04 (closed by TECH-021, TECH-022, both 2026-07-19); T-04 (closed by TECH-043,
2026-07-20); O-01 (closed by TECH-032, 2026-07-20 — merged to `main`, commit `277e00a`);
Q-01, Q-02, Q-03, Q-04, Q-05 (closed by TECH-050, TECH-051, TECH-052,
TECH-020, and TECH-136 respectively — the last one resolving the original TECH-030
finding — all 2026-07-19). **1 is partially resolved / re-scoped** (A-01 — closed for
the 3 internal commands by TECH-090; the residual imports are accepted by ADR-007).
Items are annotated in place rather than deleted, so the registry remains the full
historical record.

Of the remaining items, the following were **code-verified still open against `main`**
on 2026-07-20 (exact code citations in the corresponding backlog story): P-01/TECH-060
(`upsertFallbackBatch` still one query per item — same pattern also present in the
Mensual and Abastecimientos repositories; a fix has been implemented and pushed on
branch `perf/remove-mayoristas-fallback-n-plus-one`, not yet merged to `main` — see
TECH-060 in the technical backlog for full evidence). O-04/TECH-054, P-02/TECH-053,
A-02/TECH-055 and T-02/T-03 (TECH-041/042) were **not re-verified this cycle** — no
evidence contradicts their existing "pending" classification, but no fresh code
citation was collected for them either.

---

*Related documents:*
- *[Architecture Review](architecture-review.md) — source of all findings*
- *[Technical Backlog](../backlog/technical-backlog.md) — implementation stories*
- *[Refactoring Roadmap](refactoring-roadmap.md) — items not in active backlog*
