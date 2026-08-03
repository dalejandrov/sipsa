# Testing Strategy — SIPSA Integration Service

**Version:** 1.1
**Date:** 2026-07-13 (updated 2026-08-03 — see [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md))

---

## Current State

| Metric | Value |
|---|---|
| Test files (Surefire, unit) | 64 (as of 2026-08-03; grown from the 10 recorded on 2026-07-13 — see the full list under `src/test/java/`) |
| Test methods (Surefire, unit) | 459 `@Test` methods (`./mvnw clean test`: 465 executions per the XML reports — a few methods are parameterized into multiple cases; 0 failures, 0 skips) |
| Business logic coverage | Every unit-test target listed as **Done** in "Unit Tests — Mandatory" below, plus the ADR-002 security chain, the ADR-009 Flyway migration gate, and package-boundary ArchUnit rules (TECH-093). No JaCoCo configured yet — line-coverage % still not measured (tracked as [TECH-159](../backlog/technical-backlog.md#tech-159)) |
| Database dependency for tests | H2 in-memory for context/unit tests; several tests (`FlywayMigrationsTest`, `SpecificationBuilderPostgresTest`, and others) provision real PostgreSQL 18 via Testcontainers (self-skip without Docker locally; CI fails if they skip — TECH-120) |
| Integration-test scaffolding (Failsafe profile, WireMock support, fixture convention) | **Done** ([TECH-150](../backlog/technical-backlog.md#tech-150), 2026-08-03) |
| Integration tests (handler-level, real SOAP transport + real DB) | **4 of 5.** `CiudadIngestionHandlerIT` ([TECH-151](../backlog/technical-backlog.md#tech-151)), `SemanaIngestionHandlerIT` ([TECH-152](../backlog/technical-backlog.md#tech-152)), `MesIngestionHandlerIT` ([TECH-153](../backlog/technical-backlog.md#tech-153)), and `AbasIngestionHandlerIT` ([TECH-154](../backlog/technical-backlog.md#tech-154), same dual-upsert-path shape as TECH-152/153), all 2026-08-03 — golden path, idempotency, and SOAP-fault cases, through the real `SoapGatewayImpl`/`SoapStreamingClient` and a real Testcontainers PostgreSQL. Only `Parcial` remains, tracked as [TECH-155](../backlog/technical-backlog.md#tech-155); `ParcialIngestionHandlerTest` still only covers the mocked-repo/no-real-transport path (kept as-is, see ADR-011) |
| E2E tests | **None yet.** Previously "not planned"; reversed by ADR-011 (narrow scope). Tracked as [TECH-160](../backlog/technical-backlog.md#tech-160) |
| Intentional skips | 0 |

Updated by TECH-110, TECH-090, TECH-111, ADR-009, TECH-001/002 (as recorded in the
original version of this document — see git history for that detail), and since then by
every unit-test story through TECH-043 (all of "Unit Tests — Mandatory" below is now
**Done**). The **2026-08-03 update** ([ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md))
resolves [TECH-044](../backlog/technical-backlog.md#tech-044) (the integration-test
tooling SPIKE) and reverses this document's original "E2E not planned" call — see the
Integration Tests and End-to-End Tests sections below, both rewritten by that ADR. The
three "Recommended" unit tests below (`SipsaReadServiceTest`, `PaginationConfigTest`,
`SoapStreamingClientTest`) are **still not implemented** — now tracked as
[TECH-157](../backlog/technical-backlog.md#tech-157) and
[TECH-156](../backlog/technical-backlog.md#tech-156) respectively, plus a new gap found
during the ADR-011 review, `GenericIngestionJob`/`IngestionService` dispatch coverage
([TECH-158](../backlog/technical-backlog.md#tech-158)).

---

## Testing Pyramid

```
          ┌───────────────────────┐
          │   E2E                 │  ← Narrow scope: 1 golden path + 1 failure path
          │   (WireMock + PG +    │     (TECH-160). Not a second copy of the IT layer —
          │    mock OIDC)         │     see ADR-011.
          ├───────────────────────┤
          │   Integration Tests   │  ← Per-handler, WireMock + Testcontainers
          │   (Spring context)    │     (TECH-150..155, resolves TECH-044/ADR-011)
          ├───────────────────────┤
          │   Unit Tests          │  ← Mandatory tier: Done. Recommended tier: pending
          │   (pure Java)         │     (TECH-156..158)
          └───────────────────────┘
```

---

## Unit Tests — Mandatory (Phase 3)

These must exist before any further refactoring of production code.

### `WindowPolicyTest` — **Done** (TECH-040/TECH-110, `test/scheduled-ingestion-jobs`)

**Target:** `application/ingestion/core/WindowPolicy.java`

**Implemented:** `WindowPolicy` now accepts an injectable `Clock` (package-private
`setClock`, defaults to `Clock.system(zone)` — no production behavior change). 25 test
cases across 5 nested classes: DANE-documented 14:00 boundary, production 14:20 buffer,
current (method-agnostic) monthly window behavior, `windowKey` semantics, and a dedicated
`MonthlyWindowConfirmedBugDemonstration` class. At the time, 23 passed and 2 were
`@Disabled`, documenting the desired post-fix behavior of a confirmed defect
(`WindowPolicy` did not bind the allowed monthly day to the specific method — see
[scheduled-ingestion-validation.md](scheduled-ingestion-validation.md), F-WP-01/F-WP-02).
TECH-111 (2026-07-14, merged via PR #15) fixed the defect, re-enabled both tests, and
extended the suite: `WindowPolicyTest` now has 34 cases, 0 skips. Superset of the original
planned case list below, kept for historical reference:

| Original planned test case | Covered by |
|---|---|
| `dailyMethodInsideWindow_returnsKey` | `DailyWindowProductionConfig.atBufferedStart_allowed` and others |
| `dailyMethodBeforeWindow_throwsViolation` | `DailyWindowProductionConfig.beforeBufferedStart_rejected` |
| `dailyMethodAfterWindow_throwsViolation` | covered via end-of-window and next-day tests |
| `dailyMethodForceTrue_returnsKeyIgnoringWindow` | `MonthlyWindowCurrentImplementation.forceTrue_bypassesWindow_forBothMethods` (daily case implicit in production-config tests using `force=true`) |
| `monthlyMethodOnValidDay_returnsKey` | `MonthlyWindowCurrentImplementation.day8AtOrAfterStart_acceptedForBothMethods` |
| `monthlyMethodOnInvalidDay_throwsViolation` | `MonthlyWindowCurrentImplementation.day7_rejectedForBothMethods` |
| `monthlyMethodForceTrue_returnsKey` | `MonthlyWindowCurrentImplementation.forceTrue_bypassesWindow_forBothMethods` |
| `timezoneUsed_isBogotaNotUtc` | `DailyWindowProductionConfig.usesBogotaCalendarDate_notUtcOrServerDefault` |

---

### `SipsaSchedulingCronTest` — **Done** (TECH-110)

**Target:** the 3 cron expressions declared on `SipsaIngestionScheduler`, via
`org.springframework.scheduling.support.CronExpression` — no system clock dependency.

18 test cases: daily cron (before/at/after 14:20, every day 7–11, December→January
rollover, Feb 29 2028 leap day), both monthly crons (day before/at/after trigger, all 3
non-trigger days roll to the following month, December→January rollover), a no-overlap
check between the two monthly crons, and a reflection-based check that all 3 `@Scheduled`
methods declare `zone = "${sipsa.timezone:America/Bogota}"` rather than relying on the
JVM/container default.

---

### `SipsaIngestionSchedulerTest` — **Done** (TECH-110)

**Target:** `application/ingestion/scheduler/SipsaIngestionScheduler.java`, with
`GenericIngestionJob` mocked (Mockito) — no Spring context, no real SOAP/DB call.

8 test cases: `runDailyWindow()` dispatches exactly Ciudad → Parcial → Semana in order,
each with `force=false`/`requestSource=SCHEDULED`/a unique UUID `requestId`, and never a
monthly method; `runMonthlyMes()`/`runMonthlyAbas()` each dispatch exactly their own
method and nothing else; a failure in one daily method does not stop the others and does
not propagate out of `runDailyWindow()`; same for a monthly job's failure.

---

### `SipsaSchedulingContextTest` / `SipsaSchedulingDisabledByDefaultTest` — **Done** (TECH-110)

**Target:** `infrastructure/config/SchedulingConfig.java`, via limited `@SpringBootTest`.

The test suite runs with `sipsa.scheduling.enabled=false` by default (new property, see
below), so no `@Scheduled` method is ever registered in ordinary tests.
`SipsaSchedulingContextTest` explicitly re-enables it
(`@SpringBootTest(properties = "sipsa.scheduling.enabled=true")`) to prove the scheduler
bean, `taskScheduler` bean, and cron/timezone properties resolve correctly when active,
while asserting no cron trigger is imminent during the test.
`SipsaSchedulingDisabledByDefaultTest` proves the negative: the `taskScheduler` bean does
not exist under the default (disabled) configuration.

**New property:** `sipsa.scheduling.enabled` (default `true`, backward-compatible) —
`SchedulingConfig` is now `@ConditionalOnProperty(prefix = "sipsa.scheduling", name =
"enabled", havingValue = "true", matchIfMissing = true)`.

---

### `SpecificationBuilderTest` — **Done** (2026-07-20, TECH-041 — split across 2 classes)

**Target:** `infrastructure/specification/SpecificationBuilder.java`  
**Branch:** `test/complete-specification-builder-coverage` (the originally-listed
`test/specification-builder` name was not reused)

**Real contract, confirmed by reading the production source before writing any test** (no
behavior was assumed or invented):
- `builder(String timezone)`: throws `SipsaConfigurationException` if `null`/blank.
- `withAttribute(String attribute, Object value)`: exact-match `cb.equal(root.get(attribute),
  value)`; skipped entirely if `value` is `null`. No LIKE/partial-match, no
  case-insensitivity, no `%`/`_` handling, no type conversion — the caller (always
  `SipsaReadService`, passing already-typed DTO fields) is responsible for the value's
  type; `SpecificationBuilder` never inspects or converts it.
- `withDateOrRange(...)`: precedence — exact date wins if present (a full-`between` range
  from that day's start to the *next* day's start, in the builder's fixed business
  timezone, never the request's `X-Timezone`); else a start/end range (`between` if both
  given, `>=` if only start, `<` if only end); all-`null` → no filter.
- `build()`: empty → `cb.conjunction()` (always-true, unfiltered); one filter → that
  filter's own predicate, unwrapped; multiple filters → AND-combined via Spring Data's
  `Specification.and()`. **No OR composition exists anywhere in this class.**
- **Field-name safety, confirmed via `SipsaReadService`:** `SpecificationBuilder` performs
  no attribute-name allowlist/validation itself — `root.get(attribute)` is called with
  whatever string is passed. Every one of its 5 real call sites (`getCiudad`,
  `getMayoristasMensual`, `getParcial`, `getMayoristasSemanal`,
  `getAbastecimientosMensual`) passes a **hardcoded string literal**, never a
  client-supplied value — clients only ever supply filter *values* through typed
  `*QueryRequest` DTO fields, never field *names*. No concrete injection/traversal risk
  exists in the actual codebase today; this is a design observation (documented here, not
  "fixed" — no test demonstrates an exploitable path in real usage, so TECH-041 does not
  expand into a security rewrite per its own explicit scope limit). No joins are possible
  either: `Path.get(String)` does not accept a dotted association path.

**Split across 2 classes** (unit tests for pure construction logic; PostgreSQL for real
DB/timezone/composition semantics — mocking `Specification.and()`'s internals would be
fragile and would prove nothing about real query behavior):

**`SpecificationBuilderTest`** (13 cases, mocked `Root`/`CriteriaBuilder`/`Path`/`Predicate`,
no database) — verifies which builder method fires with which arguments:

| Test case | Description |
|---|---|
| `builder_nullTimezone_throws` / `builder_blankTimezone_throws` / `builder_validTimezone_succeeds` | Factory validation |
| `withAttribute_null_noPredicateAdded` | Null value → `build()` returns `conjunction()`, `root` never touched |
| `withAttribute_value_equalPredicate` | Non-null value → `cb.equal(root.get(attribute), value)` |
| `withDateOrRange_exactDate_singleDayRange` | Exact date → `cb.between` with the correct start/end `Instant`s (captured and asserted, not just "some between call") |
| `withDateOrRange_exactDateTakesPrecedenceOverRange` | Exact date + start/end all given → the range args are ignored |
| `withDateOrRange_startAndEnd_range` | Both dates, no exact date → `between(start, end+1day)` |
| `withDateOrRange_onlyStart_greaterThanOrEqual` | Start only → `>=`, and `between`/`lessThan` are never called |
| `withDateOrRange_onlyEnd_lessThan` | End only → `<` at `end+1day` |
| `withDateOrRange_allNull_noPredicateAdded` | All null → no filter, `root` never touched |
| `build_empty_returnsConjunction` | No filters → `conjunction()` |
| `build_singleFilter_returnsThatPredicateDirectly` | One filter → returned unwrapped, no AND applied |

**`SpecificationBuilderPostgresTest`** (8 cases, real PostgreSQL via Testcontainers,
against `SipsaMayoristasSemanalRepository` — the same `JpaSpecificationExecutor` +
`fecha_ini TIMESTAMPTZ` column `SipsaReadService.getMayoristasSemanal` actually uses):
no-filter returns every row; an equality filter returns only matching rows; an exact-date
filter respects the real America/Bogota `TIMESTAMPTZ` calendar-day boundary (a row one
second before midnight is included, a row one second after the *next* midnight is
excluded); a start-only range's boundary is inclusive; an end-only range's boundary is
exclusive; **a verified, real implementation detail**: the exact next-day-midnight instant
itself *is* matched by the exact-date filter, because `cb.between` is inclusive on both
ends (the class's own Javadoc describes this as a "full day range," which slightly
undersells the precise boundary — documented here as observed behavior, not treated as a
defect); two filters combined select only rows matching both (real AND composition, not
mocked); and a filter combined with pagination produces no duplicate or omitted rows
across pages, with non-matching rows never appearing on any page.

---

### `IngestionJobTest` — **Done** (2026-07-20, TECH-042 — covered by 4 existing/new classes, no single `IngestionJobTest` file)

**Target:** `application/ingestion/core/IngestionJob.java` (via a minimal `ScriptedIngestionJob`
subclass — the same role `GenericIngestionJob` plays here, but without pulling in its own
SOAP/handler concerns)  
**Branch:** `test/complete-ingestion-job-coverage`

All dependencies mocked (`WindowPolicy`, `IngestionControlService`, `IngestionAuditService`,
`IngestionMetrics`) — no database, no SOAP, no Spring context, matching the original
acceptance criteria exactly. TECH-042's audit (this story) found that TECH-032 and TECH-053
had already organically covered 3 of these 9 target cases plus a related 10th behavior
(overlap-prevention skip) while adding their own unit tests; rather than duplicate that
coverage in a new monolithic `IngestionJobTest`, the 6 genuinely-uncovered cases were added
to a new, narrowly-scoped `IngestionJobContractTest`, and the table below maps every
original target case to the real class+method that covers it today:

| Test case | Description | Covered by |
|---|---|---|
| `windowViolation_runSkipped` | `WindowViolationException` → early return, no run created | `IngestionJobMetricsTest.windowSkippedRun_neverRecordsMetrics` (TECH-032) |
| `alreadySucceeded_duplicateSkipped` | `isRunComplete=true`, `force=false` → early return | `IngestionJobContractTest.duplicateRun_notForced_skippedNoRunCreated` (TECH-042) |
| `alreadySucceeded_forceTrue_runCreated` | `isRunComplete=true`, `force=true` → run created | `IngestionJobContractTest.duplicateRun_forced_runCreatedAndProceeds` (TECH-042) |
| `runCreatedSuccessfully_progressesToRunning` | Normal flow → run created → status RUNNING | `IngestionJobContractTest.runningTransition_statusAndAuditEmitted` (TECH-042) |
| `runCanceled_afterIngestion_markedCanceled` | `isRunCanceled=true` → status CANCELED, no SUCCEEDED | `IngestionJobContractTest.canceledTransition_auditEmitted_statusNotOverwritten` (TECH-042); outcome-metric proxy already in `IngestionJobMetricsTest.canceledRun_recordsOutcomeCanceledOnce` (TECH-032) |
| `ingestionFails_runMarkedFailed` | Handler throws → status FAILED, error logged | `IngestionJobContractTest.failedTransition_statusAndAuditEmitted` + `.failedTransition_nonExternalException_nullHttpAndFaultCode` (TECH-042); outcome-metric proxy already in `IngestionJobMetricsTest.failedRun_recordsOutcomeFailureOnce` (TECH-032) |
| `thresholdExceeded_runMarkedFailed` | `rejectCount > maxRejectCount` → `SipsaIngestionException` | `IngestionJobRejectThresholdTest` (7 cases, direct `validateThresholds` calls — rate/count/OR-precedence/boundary/zero-tolerance, TECH-135) |
| `rejectedRecords_persistedInFinally` | Even on failure, `logReject` called for each rejected record | `IngestionJobContractTest.rejectedRecords_persistedViaLogRejectInFinally` + `.noRejectedRecords_logRejectNeverCalled` (TECH-042) |
| `metrics_alwaysSavedInFinally` | `updateMetrics` called regardless of success or failure | `IngestionJobContractTest.updateMetricsCalledInFinally_onSuccess` + `.updateMetricsCalledInFinally_onFailure` (TECH-042) |

**Beyond the original 9 cases**, TECH-042 also added: MDC lifecycle (populated during
`runIngestion` with the correct 5 keys, cleared after both success and failure, and proven
not to leak between two sequential executions on the same thread — zero prior coverage of
`IngestionJob`'s MDC handling anywhere in the suite). The `createRun` →
`SipsaBusinessException` overlap-skip path (a run that cannot even start) is covered
transitively by `ScheduledIngestionDispatcherTest.dailyWindow_existingOverlapProtection_stillSkipsAndContinues`
(TECH-053, via a real `GenericIngestionJob`) — legitimate behavioral coverage, left as-is
rather than duplicated at the `IngestionJob` level.

---

### `GlobalExceptionHandlerContractTest` — **Done** (2026-07-20, TECH-043)

**Target:** `api/controller/GlobalExceptionHandler.java`  
**Branch:** `test/global-exception-handler-contract` (the originally-listed
`test/exception-handler` name, and the originally-planned `GlobalExceptionHandlerTest`
class name, were not reused)

Implemented with `@WebMvcTest` and a shared fixture controller
(`RequestContextThrowingTestController`) that throws each exception type — 15 cases
covering every `@ExceptionHandler` method via real MVC dispatch.

**Note — the table below was written before TECH-021/022/023 shipped, proposing a
`SIPSA_*`-prefixed error-code taxonomy from ADR-003's draft (still `Proposed`, never
`Accepted`). That taxonomy was explicitly *not* adopted: TECH-021 and TECH-022 changed
only HTTP status codes, deliberately preserving the existing `code` values, and TECH-023
added `requestId`/`instance` without touching `code` either. The table now reflects the
`code` values actually in `main`.**

| Test case | Actual HTTP | Actual `code` field |
|---|---|---|
| `SipsaValidationException` | 400 | `VALIDATION_ERROR` |
| `SipsaParseException` (TECH-021) | 502 | `PARSE_ERROR` |
| `SipsaIngestionValidationException` | 400 | `INGESTION_VALIDATION_ERROR` |
| `SipsaNotFoundException` (TECH-022) | 404 | `NOT_FOUND` |
| `SipsaBusinessException` | 422 | `BUSINESS_ERROR` |
| `SipsaExternalException` | 502 | `EXTERNAL_ERROR` |
| `SipsaIngestionException` | 500 | `INGESTION_ERROR` |
| `SipsaConfigurationException` | 500 | `CONFIGURATION_ERROR` |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` |
| `ConstraintViolationException` | 400 | `VALIDATION_ERROR` |
| `MethodArgumentTypeMismatchException` | 400 | `TYPE_MISMATCH` |
| `HttpMessageNotReadableException` | 400 | `INVALID_FORMAT` |
| `MissingServletRequestParameterException` | 400 | `MISSING_PARAMETER` |
| `NoResourceFoundException` / `NoHandlerFoundException` | 404 | `NOT_FOUND` |
| `Exception` (unknown) | 500 | `INTERNAL_ERROR` |

Every case also asserts `requestId` (present), `instance` (matches the request path),
and a present `timestamp` (TECH-023's additive fields). **Verified:** no test asserts
that a stack trace appears in the response body, and no infrastructure detail (class
names, SQL, table names) is exposed — confirmed explicitly for the two `500` cases and
the generic-exception catch-all.

---

## Unit Tests — Recommended (Phase 3+)

These add value but are not blockers. Still not implemented as of 2026-08-03 — each now
has a tracked story (added during the [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md)
review).

### `SipsaReadServiceTest` / `PaginationConfigTest` — [TECH-157](../backlog/technical-backlog.md#tech-157)

Verify: pagination parameters are validated; invalid IDs throw `SipsaValidationException`;
`SpecificationBuilder` is called with the correct field names. `buildPageable()` converts
1-based API pages to 0-based Spring pages; `validatePageable()` enforces max page size.

### `SoapStreamingClientTest` — [TECH-156](../backlog/technical-backlog.md#tech-156)

Verify: retry logic on 5xx; immediate failure on 4xx; exponential backoff timing (use mock HTTP server);
GZIP decompression applied when `Content-Encoding: gzip`. Complements
`SoapStreamingClientMetricsTest` (TECH-032), which already proves the metrics emitted
per attempt/retry against the same kind of local `HttpServer` fixture, but not this
behavioral contract.

### `GenericIngestionJob` / `IngestionService` dispatch — [TECH-158](../backlog/technical-backlog.md#tech-158)

Found during the ADR-011 review: neither class has a dedicated unit test today. Both are
covered only *transitively*, through `ScheduledIngestionDispatcherTest` and
`ParcialConcurrentIngestionAppTest`, which happen to use a real `GenericIngestionJob` for
other reasons. Verify directly: `IngestionService.execute` dispatches to the correct
handler by method name and throws `SipsaBusinessException` for an unregistered one;
`GenericIngestionJob.runIngestion` delegates to `IngestionService.execute` unmodified.

---

## Integration Tests — Resolved by ADR-011 (closes TECH-044)

**This section previously posed an open Option A/B choice pending the TECH-044 SPIKE.**
[ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md) (2026-08-03) closes
that SPIKE. Decision: **combined WireMock (SOAP) + Testcontainers (PostgreSQL), one
integration test per handler** — the original "Option A" below, not "Option B" (H2),
which this document once recommended as a faster starting point. That recommendation is
now explicitly rejected: H2 cannot exercise the real `ON CONFLICT (key_hash) DO NOTHING`
upsert ([TECH-117](../backlog/technical-backlog.md#tech-117)) or the `TIMESTAMPTZ`/`DATE`
semantics ([ADR-008](../adr/ADR-008-timezone-locale-and-date-semantics.md)) the
persistence layer actually depends on — see ADR-011's "Options considered" for the full
reasoning.

**Scope, one class per handler** (tracked as
[TECH-151 through TECH-155](../backlog/technical-backlog.md#tech-151)):

- `CiudadIngestionHandlerIT` (TECH-151), `SemanaIngestionHandlerIT` (TECH-152),
  `MesIngestionHandlerIT` (TECH-153), and `AbasIngestionHandlerIT` (TECH-154) —
  **done**, all 2026-08-03. `ParcialIngestionHandlerIT` — pending (TECH-155), same
  pattern.
- Each: runs the full handler flow with WireMock serving a real SOAP XML fixture,
  through the real `SoapGateway`/`SoapStreamingClient`, against a real PostgreSQL 18
  Testcontainer. Validates records are inserted, `IngestionContext` metrics match the
  fixture, and a second run against the same fixture is idempotent (`skipped > 0`).
- `ParcialIngestionHandlerIT` specifically also exercises the real
  `ON CONFLICT (key_hash) DO NOTHING` path against a real unique index — the existing
  `ParcialIngestionHandlerTest` (TECH-011) uses a Mockito-faked repository for this and
  is kept as-is, not replaced.

**Build mechanics** — **done** ([TECH-150](../backlog/technical-backlog.md#tech-150),
2026-08-03): a Maven `integration-tests` profile, `maven-failsafe-plugin`, `*IT.java`
naming, bound to `verify` and kept out of the default `./mvnw test` run — see [CI
Considerations](#ci-considerations) below. Getting WireMock itself to actually start
took more than declaring the dependency (it already was declared, but non-functional —
see TECH-150 for the 3 Jetty-related `pom.xml` fixes required).

---

## End-to-End Tests — New, added by ADR-011 (reverses the original "not planned" call)

The original version of this document marked E2E "Not planned (system boundary)" — a
reasonable call while AWS deployment (TECH-132/TECH-143) was still theoretical.
[ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md) reverses it: the app is
now close to real production traffic and has never had a single test proving the HTTP
trigger → async dispatch → ingestion pipeline → audit trail → query-back chain works
together in one running Spring context.

**Deliberately narrow scope** — this is not a second copy of the per-handler integration
tests above (those verify each handler's parsing/persistence contract; this suite
verifies the *wiring* between layers, once):

- **Golden path:** `POST /api/internal/ingestion/run?method=promediosSipsaCiudad` → `202
  Accepted` → poll `GET /api/internal/ingestion/runs/{id}` until `SUCCEEDED` → `GET
  /api/sipsa/ciudad` returns the persisted rows → `GET /api/internal/audit/run/{id}`
  shows the complete `REQUEST_RECEIVED → REQUEST_ACCEPTED → INGESTION_STARTED →
  INGESTION_RUNNING → INGESTION_SUCCEEDED → METRICS_UPDATED` sequence.
- **Failure path:** same trigger, WireMock returns a SOAP 500 → run ends `FAILED`, audit
  trail intact.

**Mechanics:** `@SpringBootTest(webEnvironment = RANDOM_PORT)`, a real HTTP client,
WireMock SOAP, Testcontainers PostgreSQL, reusing the mock-OIDC-issuer pattern already
proven by the security work (PR #17) to drive the real Spring context under
authentication. Lives under `src/test/java/.../e2e/`, named `*E2ETest`, run through the
same `integration-tests` Failsafe profile as the per-handler ITs — no third tool or
phase. Tracked as [TECH-160](../backlog/technical-backlog.md#tech-160).

---

## ArchUnit — **Done** (TECH-093)

Implemented ahead of this document's original "Optional (Phase 4+)" plan.
`PackageBoundaryArchitectureTest` (plus a dedicated regression-proof fixture package,
`archunitregression`) enforces package-boundary rules — see
[ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md) §F5 for the accepted
scope. No further ArchUnit work is planned by this document; new rules (e.g. "application
must not depend on the messaging framework") are only relevant if a story that needs them
is actually adopted.

---

## Coverage Targets

| Layer | Minimum coverage | Tool |
|---|---|---|
| Domain logic (`WindowPolicy`, `IngestionJob`) | 80% line coverage | JaCoCo |
| Application services | 60% line coverage | JaCoCo |
| Exception handler | 95% line coverage (all handlers tested) | JaCoCo |
| Infrastructure (parsers, mappers) | 50% line coverage | JaCoCo |
| Controllers | Tested via `@WebMvcTest` | — |

**Status (2026-08-03):** still not measured — no JaCoCo configuration exists in
`pom.xml`. [TECH-159](../backlog/technical-backlog.md#tech-159) adds JaCoCo **report-only**
first (no build-breaking `check` goal), so these targets can be validated against real
numbers before anything is enforced. The `check` goal (build fails below threshold) is
deliberately deferred until then.

---

## Test Data Strategy

| Test type | Data strategy |
|---|---|
| Unit tests | Constructed in-memory; no fixtures |
| Context load test | H2 in-memory with `create-drop` (current setup) |
| SOAP integration tests | WireMock returning real XML fixtures from `src/test/resources/fixtures/soap/<HandlerName>/` |
| Full integration tests | Testcontainers with PostgreSQL 18 + WireMock |
| E2E tests | Testcontainers with PostgreSQL 18 + WireMock (SOAP) + mock OIDC issuer |

**Fixture files:** Store representative SOAP XML responses (sanitized if necessary) in
`src/test/resources/fixtures/soap/<HandlerName>/`, one directory per handler — established
by [TECH-150](../backlog/technical-backlog.md#tech-150).

---

## CI Considerations

**Status (2026-08-03):** the `ci.yml` workflow currently has a single job (`verify`,
running `./mvnw test`-equivalent plus the Testcontainers-backed tests that happen to run
inline with it). The `integration-tests` Maven profile itself is **done**
([TECH-150](../backlog/technical-backlog.md#tech-150)) and locally verified
(`./mvnw verify -P integration-tests` runs and passes `*IT` classes via Failsafe); wiring
it into `ci.yml` as its own parallel job is not — tracked as
[TECH-161](../backlog/technical-backlog.md#tech-161).

- **Unit tests:** run on every commit (`./mvnw test`) — unchanged by this plan.
- **Integration + E2E tests:** a new dedicated Maven profile (`./mvnw verify -P
  integration-tests`), `maven-failsafe-plugin`, `*IT`/`*E2ETest` naming. Requires Docker
  in CI (already true today for the existing inline Testcontainers tests). Runs in a
  **separate CI job, in parallel with `verify`**, not chained after it — see
  [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md) for why: a slow or
  flaky Testcontainers/WireMock startup must never block the fast unit-test signal.
- **ArchUnit tests:** compile-time; already run on every commit with unit tests (TECH-093,
  no change needed).
- **Coverage report:** JaCoCo, **report-only** to start (TECH-159) — generated, not
  enforced, until real numbers exist to validate the targets above against.

### Asserting on asynchronous side effects (audit trail)

`IngestionAuditService.logEvent` is `@Async` + `REQUIRES_NEW`: audit rows commit on a
different thread, in their own transaction, **after** the job's calling code returns.
Any test that asserts on `ingestion_audit` right after a job future completes is racing
the async commit — the window measured ~1–2 ms on a fast workstation but stretched far
enough on a constrained GitHub Actions runner to fail
`ParcialConcurrentIngestionAppTest` on 2026-07-19 (1 of 2 `INGESTION_SUCCEEDED` events
visible at assertion time).

Rules derived from that incident:

- **Audit assertions must be condition-based and bounded** — Awaitility (already on the
  test classpath via `spring-boot-starter-test`), small poll interval, hard cap. Never
  a fixed `Thread.sleep`, never an unbounded loop.
- **Assert the exact contract, don't weaken it:** audit rows are append-only inserts
  (`BIGSERIAL` PK, no unique constraints), so N executions ⇒ exactly N success events.
  Filter by the execution's own `request_id` instead of counting the whole table, so
  unrelated events can never satisfy (or break) the expectation.
- **Know what is already synchronous:** `updateStatus`/`updateMetrics`
  (`REQUIRES_NEW` on the job thread) and the batch inserts commit before the job
  future completes — those assertions stay immediate; wrapping them in waits only
  hides real regressions.
- **On timeout, fail loud with state:** include a dump of the audit events and run rows
  (metadata only) in the assertion message so a CI-only recurrence is diagnosable from
  the log.
- Surefire runs the suite sequentially in a single fork (project default) — cross-test
  parallelism was ruled out as a cause; do not disable parallelism settings to "fix"
  timing issues that are internal to a test.
