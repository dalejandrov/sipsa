# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Changed

- **TECH-053 — the scheduler dispatches ingestion asynchronously; scheduler threads no
  longer block for the duration of a run.** `SipsaIngestionScheduler` no longer depends
  on `GenericIngestionJob` at all — each `@Scheduled` method now logs and delegates to
  a new `ScheduledIngestionDispatcher` (`@Async("ingestionTaskExecutor")`, the same
  executor already used by manual-trigger ingestion and audit logging — no new
  executor). **Dispatch is per window, not per method:** the daily window's Ciudad →
  Parcial → Semana sequence still runs sequentially, on one worker thread, inside a
  single async call — dispatching each method as its own independent async call would
  have let them race on the pool, silently breaking the sequential,
  resource-contention-avoiding execution this exact codebase already relied on. This
  is a deliberate refinement of ADR-005's Option A (now **Accepted** — see its
  Resolution section), not the literal `asyncIngestionService.executeAsync(request)`-
  per-method sketch the ADR and original backlog entry described. Request
  construction, `RequestSource.SCHEDULED`, per-method failure containment
  (try/catch/`log.error`, one method's failure doesn't stop the others), and the
  existing overlap-prevention path (the real `uq_ingestion_runs_window` unique
  constraint, translated to a controlled skip by `IngestionControlService.createRun`)
  are all unchanged — only relocated and regression-tested. `CallerRunsPolicy`
  saturation behavior is unchanged and explicitly not oversold: under sustained
  overload the scheduler thread can still end up blocked, same as before. New tests:
  `SipsaIngestionSchedulerTest` (3 cases, rewritten to verify pure delegation),
  `ScheduledIngestionDispatcherTest` (9 cases — the previous scheduler test's logic
  moved here, plus a new overlap-protection regression), `ScheduledIngestionAsyncDispatchTest`
  (3 cases, real Spring context — dispatch-returns-before-completion via a controlled
  latch, `ingestion-async-*` thread name, and the daily window confirmed still
  sequential on one thread even when dispatched asynchronously). Verified in Docker
  with a real cron-fired trigger (daily window bounds and cron time overridden via env
  vars for this one verification run only, no committed config changed): scheduler
  returned immediately, all three methods ran in order on one `ingestion-async-1`
  thread, three runs created with no duplicates, audit events and the
  `sipsa.ingestion.runs` metric each present exactly once per run, health unaffected.
  No cron, window, HTTP, pagination, repository, deduplication, or database change. No
  Flyway migration; V1–V4 unchanged.

### Added

- **TECH-032 — Micrometer metrics for the ingestion pipeline and SOAP calls.** New
  `IngestionMetrics` component (`infrastructure/observability/`) instruments
  `IngestionJob.execute` (every ingestion run, regardless of method — the abstract base
  class every job runs through) and `SoapStreamingClient.stream` (every SOAP call,
  including its internal retry loop). No new dependency: `spring-boot-starter-actuator`
  and `micrometer-registry-prometheus` were already present, just unused.
  **Metrics:** `sipsa.ingestion.duration` (Timer), `sipsa.ingestion.runs` (Counter),
  `sipsa.ingestion.records.seen/inserted/skipped/rejected` (DistributionSummary, one
  value per run from the run's already-existing final counters — never recalculated,
  never double-recorded), `sipsa.soap.calls`/`sipsa.soap.failures`/`sipsa.soap.retries`
  (Counter), `sipsa.soap.duration` (Timer, spans all retries/backoff). Tags are strictly
  `method` (closed catalog: the ~5 registered ingestion methods / SOAP actions),
  `outcome` (`success`/`failure`/`canceled`), and `source` (lowercased
  `RequestSource`) — never `requestId`, `runId`, a raw exception message, or any other
  unbounded value. Every `IngestionMetrics` method swallows and logs registry
  exceptions rather than propagating them: instrumentation must never break an
  ingestion run or a SOAP call. **Bug found and fixed along the way:**
  `micrometer-registry-prometheus` was marked `<optional>true</optional>` in `pom.xml`,
  which Spring Boot Maven Plugin's `repackage` goal excludes from the runnable jar by
  default — the dependency compiled fine and showed in `dependency:tree`, but was never
  actually bundled, so `/actuator/prometheus` 404'd despite being in the exposure list.
  Fixed by removing the flag; verified in Docker with a real successful
  `promediosSipsaParcial` run (677,061 records) — all 10 `sipsa.*` metrics present with
  the designed tags in both `/actuator/metrics` and `/actuator/prometheus`,
  `/actuator/health` unaffected, no sensitive data in any tag. New tests:
  `IngestionMetricsTest` (9 cases, `SimpleMeterRegistry`), `IngestionJobMetricsTest` (4
  cases, verifying exactly one `recordRunCompleted` call per run with the correct
  outcome), `SoapStreamingClientMetricsTest` (3 cases, a real local loopback HTTP
  server — no WireMock, not yet in this repo per TECH-044 — verifying exactly one
  outcome record and the correct retry count per call). No status codes, `ErrorResponse`,
  DTOs, security, retries, thresholds, scheduler, or database logic changed. No Flyway
  migration; V1–V4 unchanged.

### Testing

- **TECH-043 — full `GlobalExceptionHandler` contract coverage.** New
  `GlobalExceptionHandlerContractTest` exercises all 15 `@ExceptionHandler` cases
  through real MVC dispatch, asserting HTTP status, `Content-Type`, `code`, `message`,
  `requestId`, `instance`, a present `timestamp`, no leaked stack trace, and each
  response type's extra fields (`fieldErrors`, `availableMethods`) where applicable.
  Test-and-documentation only: no status code, error code, message, `ErrorResponse`
  shape, or `RequestIdFilter` behavior changed — every handler already behaved exactly
  as documented, no defects found. `RequestContextThrowingTestController` (TECH-023's
  fixture) was extended with 8 new endpoints rather than adding a second, near-duplicate
  controller. `HttpRequestMethodNotSupportedException` (405) and
  `HttpMediaTypeNotSupportedException` (415) are confirmed out of scope — no handler for
  either exists in `GlobalExceptionHandler`. No Flyway migration; V1–V4 unchanged.

### Added

- **TECH-023 — HTTP error responses now include `requestId` and `instance`.**
  Additive change only: every existing field (`timestamp`, `status`, `error`, `code`,
  `message`), every existing status code, and every existing error `code` (including
  TECH-021's `502`/`PARSE_ERROR` and TECH-022's `404`/`NOT_FOUND`) is unchanged.
  `instance` is the request path (`HttpServletRequest.getRequestURI()` — no host,
  scheme, or query string). `requestId` comes from a new `RequestIdFilter`
  (`api/filter/`, mirrors the existing `TimezoneFilter` pattern): no per-HTTP-request
  correlation ID source existed anywhere in this repository before this — the only
  prior "requestId" concept was the ingestion-domain business correlation ID
  (`UUID.randomUUID()` in `IngestionTriggerService`/`SipsaIngestionScheduler`, generated
  only for ingestion-trigger operations, not every request), and MDC
  (`IngestionJob`) is populated only deep inside the async ingestion pipeline, never on
  the synchronous request-handling thread. The new filter honors an incoming
  `X-Request-Id` header if present and non-blank, otherwise generates a UUID — exactly
  once per request, before the rest of the filter chain runs, and echoes it back on the
  `X-Request-Id` response header. Every `@ExceptionHandler` in `GlobalExceptionHandler`
  now produces both new fields consistently (no handler was left without them),
  including the two that build their response DTOs directly rather than through the
  shared `buildErrorResponse` helper (`IngestionValidationErrorResponse`,
  `ValidationErrorResponse`). New `GlobalExceptionHandlerRequestContextTest` covers all
  five required exception shapes (parse/502, not-found/404, business/422,
  validation/400, generic/500 — with an explicit check that no stack trace leaks) plus
  correlation behavior: an incoming header is echoed verbatim, the fallback generates a
  non-blank ID consistent between the response header and body, and a blank incoming
  header is not trusted verbatim. No Flyway migration; V1–V4 unchanged.

### Changed

- **TECH-022 — a missing ingestion run now returns `404 Not Found`, not
  `422 Unprocessable Entity`.** New `SipsaNotFoundException` (mapped by
  `GlobalExceptionHandler` to HTTP 404, error code `NOT_FOUND`) replaces
  `SipsaBusinessException` (422) in exactly two places that were conflating "the
  resource doesn't exist" with "the resource exists but this operation on it is
  invalid": `IngestionRunQueryService.getRunStatus` (a run ID that was never created),
  and `IngestionControlService.cancelRun`'s `run == null` branch only — its sibling
  check ("run exists but isn't `STARTED`/`RUNNING`") stays `SipsaBusinessException` →
  422, since that's a genuine business-rule violation on an existing run, not an
  absent resource. `AuditTrailService`'s two analogous `SipsaBusinessException`
  sites were deliberately left untouched (separate follow-up story). `ErrorResponse`,
  `requestId`, and `instance` were not touched. New tests:
  `IngestionControlServiceCancelRunTest` (service-level), updated
  `IngestionRunQueryServiceGetRunStatusTest` (now expects `SipsaNotFoundException`,
  previously pinned `SipsaBusinessException`), and `SipsaOpsControllerNotFoundTest`
  (real MVC dispatch — missing run → 404, existing run → 200, inactive-run cancel →
  422, active-run cancel → 200, plus a regression check that a downstream
  `SipsaParseException` through the same controller still returns 502, TECH-021
  untouched). No Flyway migration; V1–V4 unchanged.

- **TECH-021 — `SipsaParseException` now maps to `502 Bad Gateway`, not
  `400 Bad Request`** — a contractual (breaking) HTTP status change for any client that
  depended on the previous `400`. This exception fires when DANE's upstream SOAP/XML
  response can't be parsed (`AbstractStaxParser`); the API caller sent nothing wrong, so
  `400` (client error) was the wrong semantics — `502` (this server, acting as a
  gateway, got an invalid response from an upstream server) is correct. Only the status
  argument in `GlobalExceptionHandler.handleParseException` changed
  (`HttpStatus.BAD_REQUEST` → `HttpStatus.BAD_GATEWAY`); the error `code`
  (`"PARSE_ERROR"`), `message`, `timestamp`, and body shape are all unchanged —
  `ErrorResponse` itself was not touched, and the new `SIPSA_UPSTREAM_PARSE_ERROR`
  code ADR-003 proposes was deliberately not adopted (that ADR is still `Proposed`, not
  `Accepted`; it explicitly authorizes this status-code fix to proceed independently).
  New focused `GlobalExceptionHandlerParseExceptionTest` (`@WebMvcTest`, real MVC
  dispatch) covers status, code, message, timestamp, and content type, plus a
  regression check that `SipsaBusinessException` → `422` is untouched. No Flyway
  migration; V1–V4 unchanged.

- **TECH-070 (C-01) — `SoapProperties` now validates all 9 fields at startup**,
  early configuration validation only — no functional change to the SOAP client.
  Previously only 4 fields were checked, imperatively, inside
  `SipsaSoapClientConfig`'s `@Bean` factory method (after the property had already
  bound); `maxRetries` and `retryBackoffMs` were never validated anywhere (a negative
  retry count silently no-ops the retry loop, a negative backoff throws
  `IllegalArgumentException` mid-retry), and neither was `namespace`, despite
  `SoapGatewayImpl` using it verbatim to build every SOAP operation's `QName`. Now
  `@Validated` + Jakarta constraints match exactly what the client already required:
  `@NotBlank` on `endpoint`/`namespace`, `@Positive` on the two timeouts, `@Min(0)` on
  `maxRetries`/`retryBackoffMs`/`loggingLimitBytes`/`maxChildElements` — the last two
  deliberately not `@Positive`, since `0` is a real, documented value for both
  ("unlimited" and "log nothing" respectively). No cross-field rules, no URL-format
  regex (`@NotBlank` is what the client actually needs; a fragile validator risks
  rejecting valid non-standard endpoints like the test suite's
  `http://localhost:9999/mock`). `endpoint` stays unconditionally required — no real
  "SOAP disabled" flag exists in this repository. Found and fixed along the way:
  `docker-compose.yml` passed through **zero** `SOAP_*` variables, so shell overrides
  silently had no effect in Docker; added passthrough for the six properties with an
  established env var name. 16 new binding tests, previously zero coverage. Verified
  in Docker: defaults, valid overrides (confirmed present inside the container), and
  three invalid-value cases each produced `APPLICATION FAILED TO START` naming the
  property. No Flyway migration; V1–V4 unchanged.

- **TECH-031 — `SipsaHealthIndicator` staleness thresholds externalized**, no effective
  value changed. The indicator hardcoded `36` (hours, for the daily-window method
  group) and `35 * 24` (also compared in hours, for every other monitored method) —
  both now live in the new validated `SipsaHealthProperties`
  (`sipsa.health.daily-staleness-threshold` / `monthly-staleness-threshold`, env
  `SIPSA_HEALTH_DAILY_STALENESS_THRESHOLD` / `SIPSA_HEALTH_MONTHLY_STALENESS_THRESHOLD`,
  canonical defaults `36h` / `840h` — kept in hours, the unit the comparison actually
  uses, not converted to a day count). Each threshold must be positive; zero or negative
  values abort startup naming the property (a zero threshold would mark every method
  `STALE` immediately after its own successful run). `SipsaHealthIndicator` now takes
  `SipsaHealthProperties` via constructor injection instead of hardcoded literals, and
  `Instant.now()` became `Instant.now(clock)` with a package-private test-only
  `setClock` seam mirroring `WindowPolicy`'s existing pattern in this codebase. The
  strict `>` comparison, per-method `STALE` detail entries, and `UP`/`DOWN`/`UNKNOWN`
  outcomes are unchanged — new tests pin both method groups at exactly their default
  threshold (stays `UP`) and one hour past it (`DOWN`), previously uncovered. Verified
  in Docker: defaults, valid override, and startup abort on an invalid value. No
  Actuator wiring, security, endpoint, or health-response-shape change; no migration;
  V1–V4 unchanged.

- **TECH-052 — `IngestionControlService.getRun()` now returns
  `Optional<IngestionRun>`**, an explicit internal absence contract instead of a
  nullable return. `findById` already returned `Optional`; the method was discarding it
  with `.orElse(null)` only to force its single caller,
  `IngestionRunQueryService.getRunStatus`, to null-check it back. That caller now uses
  `.map(mapper::toDetailDto).orElseThrow(...)` — no unguarded `.get()`, no
  `.orElse(null)`. **Not an HTTP contract change:** a missing run still produces
  `SipsaBusinessException` → HTTP 422 with the same message (TECH-022's HTTP 404 story
  is separate and untouched). Two unrelated `findById(...).orElse(null)` sites in the
  same class (`isRunCanceled`, `cancelRun` — both return `boolean`/`void`, not
  `IngestionRun`) were reviewed and intentionally left untouched. New tests cover both
  services' present/absent behavior, previously uncovered (both are always mocked in
  existing controller/security tests). No migration; V1–V4 unchanged.

- **TECH-051 — `IngestionAuditMapper.toAuditEventRequest()` renamed to
  `toAuditEventResponse()`** to match its actual, unchanged return type
  (`AuditTrailResponse.AuditEventResponse` — never the unrelated `AuditEventRequest`
  class). Internal contract clarity only: the 4 call sites in `AuditTrailService` were
  updated (method references, no reflection), MapStruct resolves its generated
  implementation by type signature so the rename doesn't affect code generation, and no
  deprecated alias was kept (internal `api/mapper` interface, no external consumer).
  Field mapping is unchanged and now covered by a new `IngestionAuditMapperTest`
  (previously zero coverage, since the mapper's only consumer is always mocked in
  existing tests). Not a public API change; no Flyway migration; V1–V4 unchanged.

- **TECH-050 — removed residual `// ...existing code...` placeholder comments** from
  the `catch` blocks of `CiudadIngestionHandler`, `SemanaIngestionHandler`,
  `AbasIngestionHandler` and `MesIngestionHandler`. Non-functional cleanup only: each
  comment documented nothing and sat directly before a `log.warn(...)` that already
  describes the partial-progress-save behavior. Zero logic, signature, import, or test
  changes — one line removed per file. No Flyway migration; V1–V4 unchanged.

### Fixed

- **TECH-020 — internal controller route mappings normalized to a leading `/`.**
  `SipsaOpsController` and `IngestionAuditController` declared
  `@RequestMapping("api/internal/ingestion")` and `@RequestMapping("api/internal/audit")`
  without a leading slash, inconsistent with `SipsaRestController`'s
  `@RequestMapping("/api/sipsa")`. Spring MVC normalizes the class-level value at startup
  regardless of the leading slash, so the effective routes
  (`/api/internal/ingestion/**`, `/api/internal/audit/**`) were never actually broken —
  this is a declared-contract normalization, not a routing fix. No HTTP method, subroute,
  DTO, security scope, or status code changed. New `InternalControllerRouteMappingTest`
  pins the exact controller/handler method Spring resolves for each path; the
  pre-existing `InternalEndpointSecurityTest` (15 cases: 401/403/2xx across both
  endpoint groups) stays green unchanged. Verified in Docker (clean rebuild, both
  endpoint groups exercised with the local mock-OIDC flow, Flyway V1→V4 unaffected).

### Changed

- **TECH-136 (C-05) — async executor configuration centralized and audit executor made
  explicit.** `AsyncConfig` no longer re-declares `@Value` defaults for
  `sipsa.ingestion.async.*` — the pool geometry binds once in the new validated
  `AsyncExecutorProperties` (core ≥ 1, max ≥ 1 **and ≥ core** via cross-field check,
  queue ≥ 0 — `0` documented as direct handoff, keep-alive ≥ 0 s; invalid values abort
  startup naming the property; resolved geometry logged once). The operational contract
  is unchanged: same prefix, same `SIPSA_ASYNC_*` env vars (now passed through by
  `docker-compose.yml`), same bean (`ingestionTaskExecutor`), thread prefix
  (`ingestion-async-`), `CallerRunsPolicy`, core-thread timeout and canonical
  `2/10/25/60s` — no tuning. **Fixed alongside:** `IngestionAuditService.logEvent`
  carried a bare `@Async` in a context with two `TaskExecutor` beans (the scheduler
  also implements `TaskExecutor`), so Spring logged `More than one TaskExecutor bean
  found` and ran audit events on ad-hoc `SimpleAsyncTaskExecutor` threads (finding from
  the 2026-07-19 CI investigation). The executor is now explicit —
  `@Async("ingestionTaskExecutor")` — so audit events always run on the managed pool;
  still asynchronous, still `REQUIRES_NEW`, still append-only. Evidence-backed: a
  resolution test pins the `ingestion-async-*` insert thread and the absence of the
  warning and of `SimpleAsyncTaskExecutor` from captured output; a deterministic
  latch-based saturation test preserves the CallerRunsPolicy behavior; 12 binding
  cases cover defaults/overrides/boundaries/aborts; the TECH-117 concurrent ingestion
  test stayed green across 50 repetitions. Findings recorded for follow-up (not
  changed here): the executor keeps framework-default shutdown behavior
  (`waitForTasksToCompleteOnShutdown=false` — in-flight audit tasks may be dropped at
  shutdown) and no `TaskDecorator`/MDC propagation exists (audit correlation travels in
  the event payload, not the logging context).

- **TECH-135 (C-04) — ingestion rejection thresholds centralized into a single source
  of truth** (`IngestionProperties`, bound to `sipsa.ingestion.max-reject-rate` /
  `max-reject-count`). `IngestionJob` and `GenericIngestionJob` no longer carry
  duplicated `@Value` bindings with local defaults — the double-source antipattern
  already removed for `batch-size` (TECH-071) and `monthly-window-start` (TECH-133).
  **Effective values unchanged** (rate `0.01`, count `5000` — what `application.yaml`
  always made effective) **and evaluation semantics untouched, now documented and
  test-pinned:** the rate is a fraction of `recordsSeen` in `[0..1]` (0.01 = 1%, not a
  percentage); both gates are evaluated once at the end of each run over its final
  totals (never per batch); they combine by OR (strictly exceeding either fails the
  run with `SipsaIngestionException` → status `FAILED`); values exactly at a threshold
  pass (strict `>`); `seen=0` skips the rate check so only the count gate applies.
  Startup now validates both values (rate outside `[0..1]`, negative count or
  non-numeric input abort with a message naming the property) and logs the resolved
  pair once. `docker-compose.yml` passes `MAX_REJECT_RATE` / `MAX_REJECT_COUNT`
  through (verified in Docker: defaults `0.01/5000`, override `0.05/1234`) and
  `.env.example` documents range, semantics and precedence
  (env var > property > canonical default). No functional change, no migration.

### Fixed

- **TECH-134 — remaining SIPSA decimal annotations aligned with the DDL.**
  `SipsaCiudad.precioPromedio/enviado` and
  `SipsaMayoristasSemanal.minimoKg/maximoKg/promedioKg/enviado` declared
  `precision=15, scale=2` against `NUMERIC(19,2)` columns — the drift TECH-118 closed
  for `SipsaParcial`, resolved with the same criterion: DANE's XSD is unbounded
  `xs:decimal` (`minOccurs=0`) for every field, so the versioned V1 DDL is the storage
  truth and the annotations now mirror it. **Every SIPSA price model now declares
  `19,2`; no migration (V1–V4 unchanged), no behavior change** (`ddl-auto=validate`
  never compared precision; the pipeline is `BigDecimal` end to end with zero
  `double`/`float`). Verified against real PostgreSQL with fresh real DANE loads
  (Ciudad 373,038 rows: 270.00–15,500.00; Semanal 233,866 rows: 182.00–280,000.00 —
  the widest range in the schema, still far inside either bound): boundary matrix
  including the `19,2`-only value `99999999999999999.99` round-trips exactly, scale > 2
  keeps the TECH-118 half-away-from-zero semantics, JSON stays exact unquoted numbers.
  Real-data shapes recorded: Ciudad `enviado` is `0.00` on every observed row and
  Semanal `enviado` is always `NULL` — both fields look vestigial upstream; documented,
  not changed.

- **TECH-118 — `SipsaParcial` decimal precision aligned with PostgreSQL.** The three
  price columns (`promedioKg`, `maximoKg`, `minimoKg`) declared `precision=15, scale=2`
  in JPA while the versioned DDL has been `NUMERIC(19,2)` since V1. Source of truth:
  the DDL — DANE's XSD declares the fields as unbounded `xs:decimal` (`minOccurs=0`),
  so no external contract backs 15 digits, and real data (677,061 rows: 0.00–22,000.00,
  scale ≤ 2, no negatives, no nulls) fits either bound. The annotation now mirrors the
  DDL (`19,2`), as `MayoristasMensual`/`Abastecimientos` already did. **Declarative
  drift only — no migration (V1–V4 unchanged), no behavior change:** Hibernate's
  `ddl-auto=validate` never compared precision, the parser is exact
  (`new BigDecimal(String)`, no `double` anywhere in the pipeline), and Jackson keeps
  serializing stored values exactly (`22000.00` — JSON stays a number; scale is data,
  not monetary formatting). Newly pinned by tests: values that fit `NUMERIC(19,2)` but
  not `DECIMAL(15,2)` round-trip exactly, and scale > 2 input is coerced by the column
  with **explicit, documented half-away-from-zero rounding** (`123.456 → 123.46`) —
  previously this coercion existed but was undocumented. No `@Digits` added: values
  come exclusively from DANE ingestion (no write API) and the XSD is unbounded, so a
  bean-validation cap could reject contract-valid data. Out-of-scope findings recorded:
  the same `15,2` drift exists in `SipsaCiudad` and `SipsaMayoristasSemanal` (follow-up
  story), and price columns carry no non-negativity CHECK constraint.

- **TECH-117 — concurrent `SipsaParcial` ingestions no longer fail on duplicate keys.**
  Two executions processing the same publication could both pass the dedup lookup
  (READ COMMITTED) and both insert the same `key_hash`; the loser died on the
  unique-violation at flush, its whole batch rolled back (losing even non-conflicting
  rows) and the run ended `FAILED`. The insert step of `batchUpsert` is now an atomic
  `INSERT … ON CONFLICT (key_hash) DO NOTHING` executed as a single JDBC batch in a
  dedicated repository fragment (`SipsaParcialBatchInsertRepositoryImpl` — no SQL in
  handlers or the application layer): a lost race resolves inside PostgreSQL to a
  per-row "not inserted" outcome counted as **skipped**, the transaction stays valid,
  non-conflicting rows persist, and both runs end `SUCCEEDED` with coherent metrics
  (`seen = inserted + skipped + rejected` per execution). The dedup lookups (hash +
  legacy-UUID recompute) are preserved as the read-avoidance optimization, the
  `UNIQUE (key_hash)` constraint remains the integrity barrier, and generated IDs are
  deliberately not fetched (ingestion discards entities after each flush). No schema
  change, no new migration; `ON CONFLICT` targets only `key_hash`, never the natural
  key (TECH-122 pending). Deterministic Testcontainers races (uncommitted-insert hold +
  `pg_stat_activity` lock observation) cover single-key, identical-batch,
  partial-overlap, intra-batch-duplicate, legacy-UUID, post-collision and retry
  scenarios, plus two real overlapping `GenericIngestionJob` executions. ADR-001's
  insert-only + skip decision is unchanged — the implementation note records that the
  strategy simply became atomic.

### Added

- **TECH-124 — covering index for the `SipsaParcial` article filter** (migration
  `V4__add_parcial_article_query_index.sql`): `idx_sipsa_parcial_article_date` on
  `(id_arti_semana, enma_fecha DESC) INCLUDE (id)`. Measured on the real DANE dataset
  (677,061 rows, PostgreSQL 18): the per-page count query of
  `GET /api/sipsa/parcial?idArtiSemana=…` — Hibernate emits `count(id)`, so the index
  must cover `id` to be usable index-only — drops from a full-table Parallel Seq Scan
  (~17–28 ms on every page request) to an Index Only Scan (0.5–2.3 ms, `Heap Fetches: 0`);
  a non-existent article drops from ~18 ms to ~0.02 ms; the `enma_fecha DESC` key column
  matches the endpoint's default ordering so every article cardinality gets an ordered
  index walk. Cost: 26 MB (170 MB table), ~0.2 s creation at current volume, ~+0.55 ms
  per 500-row ingestion batch (all-skip reingestion unaffected: zero writes). Alternatives
  `(id_arti_semana)`, `(id_arti_semana, enma_fecha DESC)` without INCLUDE, and
  `(id_arti_semana, muni_id, enma_fecha DESC)` were measured and discarded — evidence and
  re-evaluation thresholds in `docs/diagnostics/tech-124-article-filter-analysis.md`.
  No API contract change: `idArtiSemana` stays canonical, `artiId` stays a validated
  alias, `Page`/count semantics untouched. Deep-page OFFSET cost (~23–31 ms at page 1000)
  is inherent to OFFSET, marginal at current volume, and explicitly out of scope.

### Changed

- **TECH-133 — monthly ingestion window configuration centralized and validated**.
  `WindowPolicy`'s never-effective `@Value` fallback of `06:00` for
  `sipsa.ingestion.monthly-window-start` is gone: the property now binds as a typed
  `LocalTime` in `IngestionProperties` (canonical default **14:00**, the value
  `application.yaml` always made effective — DANE publishes around 14:00 COT and the
  monthly crons fire at 14:30). **Effective behavior is unchanged.** The value is an
  authorization gate (earliest time of day a monthly run may execute on its
  publication/grace day in `America/Bogota`, ADR-008), not the scheduler fire time.
  Invalid formats (`24:00`, `14:99`, free text) abort startup naming the property; an
  explicitly empty value falls back to the canonical default under standard Spring
  binding. Overridable via `INGESTION_MONTHLY_WINDOW_START` (passed through by
  `docker-compose.yml`). Startup logs one safe confirmation pair: window start and
  timezone. Boundary behavior is pinned with `Clock.fixed` tests (gate−1min/gate/gate+1min,
  wrong-day rejection, UTC-vs-Bogota same-instant divergence, `force=true` bypass).

- **TECH-071 — ingestion batch size unified into a single source of truth**
  (`IngestionProperties`, bound to `sipsa.ingestion.batch-size`). The divergent
  `@Value("${sipsa.ingestion.batch-size:2000}")` default that 5 ingestion handlers
  carried is gone: all handlers now inject the same typed, validated configuration.
  Canonical default is **500** — the value used by every recent real-data validation
  (676,210 records, TECH-011 evidence) — overridable via the `INGESTION_BATCH_SIZE`
  environment variable (now passed through by `docker-compose.yml`). Invalid values
  (zero, negative, non-numeric, or above the preventive maximum of 10,000 — chosen to
  keep per-batch dedup `IN` lookups far below the PostgreSQL JDBC limit of 32,767 bind
  parameters) abort startup with a clear validation message instead of failing
  mid-ingestion. The effective size is logged once at startup
  (`Ingestion batch size = …`). No functional change: with the default 500 the
  behavior is identical to what TECH-011 validated.

- **TECH-119 — redundant `SipsaParcial` index removed** (migration
  `V3__drop_redundant_parcial_key_hash_index.sql`). `V1` had created two identical
  B-tree indexes on `sipsa_parcial.key_hash`: the explicit non-unique
  `idx_sipsa_parcial_key_hash` and the backing index of the `UNIQUE (key_hash)`
  constraint. V3 drops the explicit one; **the `UNIQUE` constraint and its index are
  preserved** and now serve every hash lookup (verified by `EXPLAIN` before/after with
  identical cost, and by a full idempotent re-ingestion of 676,210 real records:
  `inserted=0, skipped=676,210`). Storage on the local real-data base: total indexes
  254 MB → 174 MB (−80 MB); every insert also stops paying double index maintenance.
  Transactional `DROP INDEX` (8 ms observed on 676K rows; rationale in the migration and
  in `docs/database/database-changelog.md`). No data changes, no API change; validated
  from empty base (V1→V2→V3) and as a V2→V3 upgrade with data via Testcontainers
  (`ParcialKeyHashIndexMigrationTest`), including a post-V3 duplicate insert rejected by
  the constraint.

### Fixed

- **TECH-113 — `GET /api/sipsa/parcial` filters corrected** (H-2/H-3 from the integrity
  SPIKE). The article filter no longer targets the nonexistent entity attribute `artiId`
  (which produced `IllegalArgumentException` → HTTP 500 whenever used): the canonical
  parameter is now `idArtiSemana`, matching the entity and the DANE contract, with
  `artiId` retained as a validated compatibility alias (same value → one condition;
  conflicting values → `400 VALIDATION_ERROR`). The municipality filter `muniId` is now
  **text** instead of `Long`, preserving DIVIPOLA leading zeros exactly (107,468 real
  rows carry them; `05001` ≠ `5001`) — trimmed, non-blank, max 50 chars (column bound),
  no numeric conversion ever. Filter errors return `400`, never `500`. Verified against
  real PostgreSQL via Testcontainers through the HTTP endpoint
  (`ParcialQueryFilterIntegrationTest`, 9 cases) plus 11 unit cases
  (`ParcialQueryRequestTest`); API documentation updated with the canonical/alias rule
  and leading-zero examples. No schema change needed — `muni_id VARCHAR(50)` was always
  correct; the bug was only in the query contract.

- **TECH-011 — `SipsaParcial` deduplication** ([ADR-001](docs/adr/ADR-001-data-deduplication.md),
  now `Accepted`; closes debt PS-01). `computeKeyHash()` no longer generates a random
  UUID per row: `key_hash` is the deterministic SHA-256 (versioned, unit-separator
  delimited, UTF-8, lowercase hex) of the natural key
  `(muniId, fuenId, futiId, idArtiSemana, enmaFecha)` — confirmed unique against real
  DANE data by the TECH-012 diagnostic. `SipsaParcialRepository.batchUpsert()` is now a
  real skip-first upsert: intra-batch dedupe, one bulk lookup by hash, and one bulk
  lookup by survey date that recomputes natural-key hashes so **legacy UUID rows
  deduplicate too, without any backfill** (no per-record queries). The survey date is
  parsed strictly (ISO-8601 instant with explicit offset/zone) and records with
  unparseable dates are rejected with audit trail instead of persisted with a silent
  null (closes the SPIKE's H-1 risk preventively — 0 occurrences observed in real data).
  `skipped` is now propagated to `IngestionContext` and the completion log.
  **Validated end-to-end against the real DANE endpoint on Docker Compose:** pre-fix,
  two identical runs duplicated the full dataset (676,210 → 1,352,420 rows, every group
  ×2 with identical prices); post-fix, run 1 inserted 676,210, run 2 and run 3 (after a
  container restart) inserted 0 and skipped 676,210 each, table stable with 0 duplicate
  groups. New migration `V2__add_parcial_natural_key_index.sql` (expand-only support
  index; no data changes — see [docs/database/database-changelog.md](docs/database/database-changelog.md)).
  New tests: `ParcialKeyHashTest`, `ParcialIngestionHandlerTest` (idempotent
  re-ingestion, legacy-UUID dedupe, strict date rejection), `ParcialMigrationUpgradeTest`
  (V1→V2 upgrade over duplicated legacy data on real PostgreSQL), extended
  `FlywayMigrationsTest`. Suite: 116 tests. TECH-012's script fixed to use the real
  `start_time` column (was `started_at`).

### Security

- **TECH-001/TECH-002 — application security layer** ([ADR-002](docs/adr/ADR-002-internal-endpoint-security.md),
  now `Accepted` with the layered AWS model: API Gateway API keys + Cognito JWT +
  Resource Server + private networking). Spring Boot is now an OAuth 2.0 Resource Server:
  - `/api/internal/**` requires a Cognito access token with the per-operation scope —
    `sipsa/ingestion.execute` (run), `sipsa/ingestion.cancel` (cancel),
    `sipsa/ingestion.read` (run queries), `sipsa/audit.read` (audit trail). **Breaking for
    previously-unauthenticated operational scripts, by design.**
  - JWT validation: issuer/signature/expiry, `token_use=access` (Cognito ID tokens
    rejected), optional client allowlist via `SIPSA_JWT_ALLOWED_CLIENT_IDS` (fail-fast on
    malformed values). Issuer configured via `SIPSA_JWT_ISSUER_URI` (required — the app
    refuses to start without it; no secrets involved, none versioned).
  - Stateless chain: no sessions, no CSRF surface, no form login, no HTTP Basic, no
    cookies. Default deny for undeclared routes. `401`/`403` are JSON in the
    `ErrorResponse` shape with generic messages (no HTML, no stack traces, no hint of
    which validation failed).
  - `GET /api/sipsa/**` stays public in the application — per-consumer API keys, quotas
    and throttling are API Gateway's job (TECH-131). `/actuator/health` stays public for
    container healthchecks; every other Actuator endpoint now requires a valid token
    (closing TECH-002), and Actuator is excluded from the public gateway surface.
  - Local development runs without AWS: `docker compose up` now includes a mock OIDC
    service (`ghcr.io/navikt/mock-oauth2-server:5.0.2`, config in
    `docker/mock-oidc-config.json`); the `dev` profile defaults the issuer to
    `http://localhost:9000/default`. See CONTRIBUTING.md for token commands.
  - New dependencies: `spring-boot-starter-oauth2-resource-server`;
    `spring-security-test` and `spring-boot-webmvc-test` (test scope — Spring Boot 4
    moved `@AutoConfigureMockMvc` into the dedicated `spring-boot-webmvc-test` module).
  - Infrastructure layers formalized as TECH-130 (Cognito), TECH-131 (API Gateway),
    TECH-132 (private networking) — pending, tracked in the backlog.
  - Merged via PR #17 (2026-07-15). **Post-merge validation (2026-07-15):** manual
    end-to-end check of the full Docker Compose stack against the mock OIDC issuer,
    9/9 green — token issuance, `token_use=access`, `scope` claim, issuer coherence,
    `401` (no/invalid/tampered token), `403` (missing scope, both ingestion↔audit
    directions), `2xx` (correct scope), `/actuator/health` public with
    `/actuator/info`/`/actuator/metrics` token-protected, and default deny on
    undeclared routes. No changes to `docker/mock-oidc-config.json` or the Spring
    Security implementation were required. Evidence recorded in ADR-002 and TECH-001.

### Added

- **TECH-120 — CI pipeline** (`.github/workflows/ci.yml`). Every pull request and every
  push to `main` now runs `./mvnw clean verify` on GitHub Actions (Temurin JDK 25, Maven
  Wrapper, Maven dependency cache). The Testcontainers-based Flyway migration gate
  (`FlywayMigrationsTest`, ADR-009) executes against the runner's Docker, and a dedicated
  guard step fails the pipeline if that suite is skipped — the local-only Docker self-skip
  can no longer void the gate. Superseded runs of the same branch/PR are cancelled;
  `GITHUB_TOKEN` is restricted to `contents: read`; no secrets or `.env` are used; on
  failure the surefire/failsafe reports are uploaded as a `test-reports` artifact.
  Documented in `CONTRIBUTING.md` (Continuous Integration section) and
  `docs/development/development-workflow.md` (Step 6). Closes post-migration
  recommendation #3 of the Spring Boot 4 migration. Merged via PR #16 (2026-07-15);
  first `main` run green with the migration gate confirmed executed (`tests=4`,
  `skipped=0`).

- `src/main/resources/application-dev.yaml` — new `dev` profile holding everything that is
  convenient locally but must not reach production: default database credentials
  (`sipsa_user`/`sipsa_pass`), verbose per-package log levels, `format_sql`, full health
  details (`show-details: always`), and the Actuator `loggers` endpoint.
- **[ADR-009](docs/adr/ADR-009-database-migration-strategy.md)** — database migration
  strategy: Flyway confirmed as the only migration tool (Liquibase evaluated and
  rejected), with binding conventions (immutable applied migrations, strict ordering,
  fix-forward, expand–migrate–contract for destructive changes). Day-to-day workflow in
  [docs/development/database-migrations.md](docs/development/database-migrations.md).
- `FlywayMigrationsTest` — migration gate on Testcontainers: applies the full migration
  chain against a real PostgreSQL 18 container (same image as `docker-compose.yml`) and
  boots the full context with `ddl-auto: validate`, failing on broken SQL, on a missing
  Flyway auto-configuration (the 2026-07-14 regression), and on entity/schema drift.
  Skipped automatically when Docker is unavailable. New test dependencies (managed by
  the Spring Boot BOM): `spring-boot-testcontainers`, `testcontainers-postgresql`,
  `testcontainers-junit-jupiter`. Suite: 69 tests (was 65).
- Flyway hardening in `application.yaml`: `validate-on-migrate: true`,
  `clean-disabled: true`, `out-of-order: false`, and an explicit, documented baseline
  policy (`baseline-version: 1`).
- `src/main/resources/application-docker.yaml` — explicit `docker` profile so
  `docker-compose.yml`'s `SPRING_PROFILES_ACTIVE=docker` points at a real profile instead
  of silently falling back to the base configuration. Sets only container-topology facts
  (database host defaults to the `db` service); credentials still have no defaults.

### Changed

- `src/main/resources/application.yaml` is now a production-safe baseline:
  - `DB_USERNAME`/`DB_PASSWORD` no longer have hardcoded defaults — the application fails
    fast at startup if they are missing outside the `dev` profile.
  - DANE-contractual values are fixed in the file instead of being environment variables
    (property names unchanged): `sipsa.timezone`, `sipsa.soap.namespace`, ingestion
    windows (`daily-window-start/end`, `monthly-run-days`, `monthly-window-start`), cron
    expressions, and the pagination policy.
  - Actuator no longer exposes `loggers` by default (dev-only now; partially addresses
    TECH-002) and `show-details` changed from `always` to `when-authorized`.
  - Baseline logging reduced to production-safe levels; verbose levels moved to the dev
    profile.
- `.env.example` trimmed to the variables that remain configurable (credentials,
  endpoint, timeouts, tuning knobs) and repositioned as a versioned reference template
  only — no `.env` file is read at runtime; variables are provided via the shell,
  `docker-compose`, or the deployment platform. Documents that contractual values now
  live in `application.yaml`.
- `docker-compose.yml` — database name/credentials now use `${VAR:-default}`
  interpolation (overridable from the shell without any `.env` file) and are passed
  consistently to both the `db` and `app` services; the `pg_isready` healthcheck uses the
  same interpolated values.
- `README.md` — configuration section rewritten: documents the `dev`/`docker`/base
  profile split and removes the instruction to copy `.env.example` to `.env`.

### Removed

- `req.xml` (root) — manual SOAP smoke-test artifact with embedded `curl` commands, not
  referenced by any code, build, or documentation.

### Fixed

- **TECH-111 — monthly `WindowPolicy` rules corrected** (F-WP-01/02/03, confirmed by
  TECH-110's validation; merged via PR #15, 2026-07-14). `validateMonthly()` now binds each monthly method to its own
  DANE publication rule — `promediosSipsaMesMadr`: principal day 8, grace day 9;
  `promedioAbasSipsaMesMadr`: principal day 10, grace day 11 — rejecting the cross-method
  acceptance it previously allowed, and the `monthly-window-start` time gate now applies
  to grace days too (day 9/11 at midnight is no longer accepted). The monthly `windowKey`
  is now the stable per-period marker `YYYY-MM-M8`/`YYYY-MM-M10` documented all along in
  the Javadoc, so a grace-day retry reuses the principal day's key and the
  `(method_name, window_key)` idempotency guarantee holds across retries; `force=true`
  still bypasses the window but returns the correct period key instead of the raw forced-on
  date. Rule resolution checks `abas` before `mesmadr` (the Abas method name contains both
  fragments), protected by an explicit test. `sipsa.ingestion.monthly-run-days` is
  repurposed as a startup sanity check (fails fast with `SipsaConfigurationException` if
  days 8 and 10 are missing) and no longer participates in per-run validation; property
  name unchanged. No data migration: historical raw-date monthly keys coexist safely with
  the new format. The two `@Disabled` tests from TECH-110 are re-enabled; daily/weekly
  validation, cron expressions, timezone, REST contracts, and DB schema are untouched.
  **Deployment note:** deploy outside days 8–11 — if a monthly method already succeeded in
  the current month under the old key format, one redundant (upsert-safe) re-ingestion of
  that period can occur during the transition month.

- **Flyway migrations silently never ran on Spring Boot 4** — Spring Boot 4 moved the
  Flyway auto-configuration out of `spring-boot-autoconfigure` into the dedicated
  `spring-boot-flyway` module. With only `flyway-core`/`flyway-database-postgresql` on the
  classpath, the application started against an empty database and failed Hibernate schema
  validation (`missing table [ingestion_audit]`). Undetected until now because the test
  suite disables Flyway (H2 `create-drop`). Added `org.springframework.boot:spring-boot-flyway`;
  verified against a clean PostgreSQL container: all `V1` tables created, app `UP`.
- **Docker build was broken** — the build stage referenced `maven:3.9.9-eclipse-temurin-25`,
  a tag that does not exist on Docker Hub (pending risk #1 of the Spring Boot 4 migration).
  Replaced with `eclipse-temurin:25-jdk-noble`: the Maven Wrapper already pins Maven 3.9.9,
  keeping a single source of truth for the Maven version. Full
  `docker compose build && up` verified: healthcheck `UP`, `GET /api/sipsa/ciudad` → 200.
- `.dockerignore` excluded `docker-compose.yaml` but the real file is `docker-compose.yml`;
  now covers both, plus `CHANGELOG.md`, `CONTRIBUTING.md`, `AGENTS.md`, `.github/`, `.claude/`.

### Docker

- The runtime image now sets `SPRING_PROFILES_ACTIVE=docker` as a default (overridable), so
  a standalone `docker run` fails fast on missing credentials instead of silently starting
  with the `dev` profile defaults.
- Removed the obsolete `-Djava.security.egd=file:/dev/./urandom` JVM flag (legacy workaround,
  unnecessary on Java 25) and added `--no-install-recommends` to the `curl` install.

### Testing

- **TECH-110/TECH-040** — Added a full automated validation suite for scheduled ingestion:
  `WindowPolicyTest` (25 cases, deterministic via injected `Clock`), `SipsaSchedulingCronTest`
  (18 cases validating the 3 production cron expressions with `CronExpression`, no system
  clock dependency), `SipsaIngestionSchedulerTest` (8 cases verifying dispatch, `force`,
  `requestSource`, and per-job exception isolation with `GenericIngestionJob` mocked), and
  two Spring context tests (`SipsaSchedulingContextTest`,
  `SipsaSchedulingDisabledByDefaultTest`) proving the scheduling wiring both when enabled
  and disabled. Total: 59 tests (was 1), 0 failures, 2 tests intentionally `@Disabled`
  pending TECH-111. See `docs/architecture/scheduled-ingestion-validation.md`.
- Two tests in `WindowPolicyTest` were intentionally `@Disabled` at the time of TECH-110,
  documenting the desired behavior of a confirmed-but-unfixed defect in
  `WindowPolicy.validateMonthly()` (see Findings below); they were the acceptance criteria
  for TECH-111 and have since been re-enabled by it (see Fixed above).

### Added

- `WindowPolicy` now accepts an injectable `Clock` via a package-private, test-only seam
  (`setClock`, defaults to `Clock.system(zone)` — behaviorally identical to the previous
  `ZonedDateTime.now(zone)` call). No public API or functional behavior changed.
- `sipsa.scheduling.enabled` property (default `true`, backward-compatible). Set to
  `false` to prevent Spring from registering `@EnableScheduling`'s bean post-processor at
  all, guaranteeing no `@Scheduled` method fires. Used by
  `src/test/resources/application.yaml` to disable real scheduling for the test suite by
  default.

- `com.h2database:h2` test dependency. Enables `SipsaApplicationTests.contextLoads()` to
  run without a PostgreSQL instance, making `./mvnw clean verify` self-contained.
- `src/test/resources/application.yaml` — Test configuration with H2 in-memory database,
  disabled Flyway, and minimal SOAP configuration for context load tests.
- `InternalIngestionCommandsTest` — unit tests for `IngestionRequest`, `CreateRunRequest`,
  and `AuditEventRequest` static factory methods, verifying construction behavior is
  unchanged after their move to `application.command` (TECH-090).

### Findings (not fixed at the time of TECH-110; since fixed by TECH-111 — see Fixed above and `docs/architecture/scheduled-ingestion-validation.md`)

- **Confirmed bug (F-WP-01):** `WindowPolicy.validateMonthly()` does not bind the allowed
  day-of-month to the specific ingestion method — `promedioAbasSipsaMesMadr` (documented
  day 10) currently passes validation on day 8, and `promediosSipsaMesMadr` (documented
  day 8) currently passes on day 10. The cron scheduler itself is correctly separated by
  method; only `WindowPolicy`'s independent safety check is affected.
- **Confirmed bug (F-WP-02):** the monthly `windowKey` is the raw run date
  (`yyyy-MM-dd`), not the `YYYY-MM-M8`/`YYYY-MM-M10` format documented in `WindowPolicy`'s
  own Javadoc, so a retry on the grace day (e.g., day 9 after a day 8 attempt) mints a new
  key instead of reusing the one for the same logical period — breaking the
  `(method_name, window_key)` idempotency guarantee across grace-day retries.
- Both were tracked as TECH-111, formalized in `docs/backlog/technical-backlog.md` with an
  approved implementation plan and since implemented (see the TECH-111 entry under Fixed).

### Changed

- **Java 21 → Java 25 (LTS)**. Runtime updated to Eclipse Temurin 25.0.3. Maven compiler
  plugin updated from `<source>/<target>` to `<release>25</release>` to enforce API boundaries.

- **Spring Boot 3.5.9 → Spring Boot 4.1.0**. Requires Java 17+ (Java 25 used).
  Pulls in Spring Framework 7, Hibernate 7.4.1, and Jackson 3.

- **Spring Framework 6.x → Spring Framework 7.x** (managed by Spring Boot 4.1.0).
  Deprecated `HttpStatus.UNPROCESSABLE_ENTITY` replaced with `HttpStatus.UNPROCESSABLE_CONTENT`
  (RFC 9110 alignment).  
  Deprecated `@NonNull` from `org.springframework.lang` removed from `TimezoneFilter`
  (Spring 7 migrates to JSpecify internally).

- **Spring Cloud 2025.0.0 (Northfields) → Spring Cloud 2025.1.2 (Oakwood)**. Compatible with
  Spring Boot 4.1.x.

- **Apache CXF 4.1.4 → 4.2.2**. Adds Jakarta EE 11 support and Spring Boot 4 / Spring
  Framework 7 compatibility.

- **Hibernate 6.x → Hibernate 7.4.1 Final** (managed by Spring Boot 4.1.0).
  Hibernate 7 requires an actual JDBC connection to auto-detect the database dialect.
  Added `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect` to
  `application.yaml` to provide the dialect explicitly.

- **Jackson 2.x → Jackson 3.x** (managed by Spring Boot 4.1.0).
  The `jackson-annotations` module retains `com.fasterxml.jackson.annotation` package names
  in Jackson 3. No source changes were required for annotation imports.

- **Actuator health package relocated**. `org.springframework.boot.actuate.health.{Health,
  HealthIndicator}` moved to `org.springframework.boot.health.contributor` in Spring Boot 4.
  `SipsaHealthIndicator` updated accordingly.

- **Resilience4j explicit starters removed**. `resilience4j-spring-boot3` and
  `resilience4j-spring6` direct dependencies removed. Resilience4j is now sourced exclusively
  via `spring-cloud-starter-circuitbreaker-resilience4j` managed by Spring Cloud BOM.
  Note: Spring Cloud 2025.1.2 still pulls `resilience4j-spring-boot3:2.3.0` transitively;
  its health indicator auto-configurations are silently skipped by `@ConditionalOnClass` on
  Spring Boot 4 (no runtime impact).

- **Docker images updated**.
  Build stage: `maven:3.9.9-eclipse-temurin-21` → `maven:3.9.9-eclipse-temurin-25`.
  Runtime stage: `eclipse-temurin:21-jre-jammy` → `eclipse-temurin:25-jre-noble`.

- **Maven Wrapper recreated**. `.mvn/wrapper/maven-wrapper.properties` was missing from the
  repository. Recreated pointing to Maven 3.9.9. File removed from `.gitignore`.

- **Internal ingestion commands moved out of the HTTP DTO package** ([ADR-007](docs/adr/ADR-007-package-boundaries-and-internal-models.md),
  TECH-090). `IngestionRequest`, `CreateRunRequest`, and `AuditEventRequest` moved from
  `api.dto.request` to `application.command` — they were never bound from an HTTP request
  and are internal to the ingestion/audit pipeline. Import-only change in the 6 consumer
  classes; no REST route, JSON body, or HTTP status changed.

- **`TimezoneFilter` relocated to the API layer** (ADR-007, TECH-091). Moved from
  `infrastructure.config` to `api.filter` — it is an HTTP request filter, not a technical
  config class, and was the codebase's only `infrastructure → api` dependency. Package
  declaration only; behavior, filter order, headers, `ThreadLocal` lifecycle, and Spring
  bean registration are unchanged.

- **`SoapGateway` no longer references an infrastructure class** (ADR-007, TECH-095).
  Removed the `SoapGatewayImpl` import used only for a Javadoc `@see` tag — the codebase's
  only `domain → infrastructure` dependency. Javadoc-only change.

### Fixed

- `SipsaHealthIndicator` import updated from `org.springframework.boot.actuate.health` to
  `org.springframework.boot.health.contributor` after package relocation in Spring Boot 4.

- `HttpStatus.UNPROCESSABLE_ENTITY` → `HttpStatus.UNPROCESSABLE_CONTENT` in
  `GlobalExceptionHandler` (deprecated in Spring Framework 7 per RFC 9110).

- `@NonNull` annotation from `org.springframework.lang` removed from
  `TimezoneFilter.doFilterInternal()` (deprecated in Spring Framework 7).

- `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect` added to
  `application.yaml`. Without this, Hibernate 7 cannot start without a database connection
  to auto-detect the dialect (unlike Hibernate 6 which inferred it from the JDBC URL).

- `SchedulingConfig`'s Javadoc incorrectly stated the two monthly ingestion jobs run at
  "06:00 COT"; the actual `@Scheduled` cron expressions fire at 14:30 COT (06:00 was
  `WindowPolicy`'s unrelated Java-level fallback default for `monthly-window-start`, never
  what `application.yaml` or the cron itself configure). Comment-only fix, no behavior
  change. Found during TECH-110's scheduling inventory.

### Documentation

- `docs/architecture/architecture-review.md` — Full architectural review with 25 accepted
  findings, evidence methodology, transaction boundary diagram, and accepted/discarded
  recommendations.
- `docs/architecture/technical-debt.md` — 28-item debt registry classified by area and priority.
- `docs/architecture/refactoring-roadmap.md` — 10 deferred refactorings with justification
  and conditions for revisiting.
- `docs/architecture/implementation-roadmap.md` — 6-phase implementation plan.
- `docs/architecture/testing-strategy.md` — Test pyramid, mandatory test cases, tooling strategy.
  Updated to reflect the `WindowPolicyTest`/`SipsaSchedulingCronTest`/`SipsaIngestionSchedulerTest`/
  scheduling-context tests actually implemented by TECH-110.
- `docs/architecture/scheduled-ingestion-validation.md` — Full validation of the scheduled
  ingestion pipeline: job inventory, cron table, `WindowPolicy` contrast, DANE schedule
  matrix (with 2020 currency caveat), concurrency analysis, and classified findings
  (TECH-110).
- `docs/architecture/timezone-locale-date-strategy-review.md` — Temporal inventory (all
  `Instant`/`LocalDate`/`OffsetDateTime` fields across entities, DTOs, and infrastructure),
  a contrast matrix against DANE's documented SOAP method semantics (with the March-2020
  currency caveat), an evaluation of `TimezoneFilter`/`WindowPolicy`, and a comparison of
  four timezone/locale strategy alternatives. Evidence for ADR-008.
- `docs/adr/ADR-008-timezone-locale-and-date-semantics.md` — Proposed strategy for
  timezone, locale, and date-semantics handling across the API. **Status: Proposed, not
  accepted.** No code changed as part of this documentation.
- `docs/adr/ADR-000-current-architecture.md` — Architecture snapshot after migration.
- `docs/adr/ADR-001` through `ADR-006` — Architecture decision records (ADR-004 accepted;
  ADR-001, ADR-002, ADR-003, ADR-005, ADR-006 proposed).
- `docs/backlog/technical-backlog.md` — Prioritized technical stories with acceptance
  criteria (36 as of 2026-07-13; the count grows as validations produce new stories).
- `docs/migrations/spring-boot-4-java-25.md` — Migration notes, breaking changes, validation.
- `CHANGELOG.md` — This file.
- `CONTRIBUTING.md` — Developer guide for contributions.
- `.github/` — Issue templates and PR template. (The "no CI workflow exists yet" caveat
  originally recorded here was resolved by TECH-120 — see the Added section above.)
