# Testing Strategy — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13

---

## Current State

| Metric | Value |
|---|---|
| Test files | 10 (`SipsaApplicationTests`, `WindowPolicyTest`, `SipsaSchedulingCronTest`, `SipsaIngestionSchedulerTest`, `SipsaSchedulingContextTest`, `SipsaSchedulingDisabledByDefaultTest`, `InternalIngestionCommandsTest`, `InternalEndpointSecurityTest`, `SipsaJwtValidatorsTest`, `FlywayMigrationsTest`) |
| Test methods | 104 (0 failures, 0 skips as of 2026-07-15) |
| Business logic coverage | `WindowPolicy`, `SipsaIngestionScheduler`, the `application/command` static factories, the ADR-002 security chain (authentication, scopes, client allowlist), and the ADR-009 Flyway migration gate (no JaCoCo configured yet — line-coverage % not measured) |
| Database dependency for tests | H2 in-memory for context tests; `FlywayMigrationsTest` provisions real PostgreSQL 18 via Testcontainers (self-skips without Docker locally; CI fails if it skips — TECH-120) |
| Intentional skips | 0 — the 2 `@Disabled` tests documenting TECH-111's desired behavior were re-enabled when TECH-111 fixed the defect (2026-07-14) |

Updated by TECH-110 (`test/scheduled-ingestion-jobs`), TECH-090
(`InternalIngestionCommandsTest`), TECH-111 (re-enabled and extended `WindowPolicyTest`,
now 34 cases), ADR-009 (`FlywayMigrationsTest`, 4 cases against real PostgreSQL 18), and
TECH-001/TECH-002 (`InternalEndpointSecurityTest`, 15 cases; `SipsaJwtValidatorsTest`,
11 cases) — see [Scheduled Ingestion Validation](scheduled-ingestion-validation.md) for
the scheduling report. Everything else in this document (`SpecificationBuilderTest`,
`IngestionJobTest`, `GlobalExceptionHandlerTest`, and the recommended/optional/integration
sections below) remains as originally planned and **not yet implemented**.

---

## Testing Pyramid

```
          ┌───────────────────────┐
          │   E2E / Contract      │  ← Not planned (system boundary)
          │   (WireMock + PG)     │
          ├───────────────────────┤
          │   Integration Tests   │  ← Phase 5+ (Testcontainers/WireMock)
          │   (Spring context)    │
          ├───────────────────────┤
          │   Unit Tests          │  ← Phase 3 (immediate priority)
          │   (pure Java)         │
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

These add value but are not blockers.

### `SipsaReadServiceTest`

Verify: pagination parameters are validated; invalid IDs throw `SipsaValidationException`;
`SpecificationBuilder` is called with the correct field names.

### `PaginationConfigTest`

Verify: `buildPageable()` converts 1-based API pages to 0-based Spring pages;
`validatePageable()` enforces max page size.

### `SoapStreamingClientTest`

Verify: retry logic on 5xx; immediate failure on 4xx; exponential backoff timing (use mock HTTP server);
GZIP decompression applied when `Content-Encoding: gzip`.

---

## Integration Tests — Phase 5+ (after TECH-044 SPIKE)

These require a decision on the tooling strategy.

### Option A: WireMock for SOAP + Testcontainers for PostgreSQL

- `CiudadIngestionHandlerIT`: runs full handler flow with WireMock returning a real SOAP XML fixture.
- Validates that records are inserted to the DB.
- Validates metrics in `IngestionContext`.
- Validates idempotency (second run produces `skipped > 0`).

### Option B: WireMock only (H2 for DB)

- Faster, no container startup.
- H2 compatibility with PostgreSQL-specific SQL may require query adjustments.
- Suitable for CI environments without Docker.

### Recommendation (pending TECH-044 SPIKE)

Start with Option B (WireMock + H2) to unblock integration testing quickly.
Migrate to Testcontainers when PostgreSQL-specific features (JSONB, `citext`, upsert)
become relevant to the test assertions.

---

## ArchUnit — Optional (Phase 4+)

ArchUnit can enforce architectural rules at compile time. Recommended rules for this project:

```java
// Rule 1: Controllers must not access repositories directly
noClasses().that().resideInAPackage("..api.controller..")
    .should().accessClassesThat().resideInAPackage("..persistence.repository..")
    .check(classes);

// Rule 2: Domain must not depend on infrastructure
noClasses().that().resideInAPackage("..domain..")
    .should().accessClassesThat().resideInAPackage("..infrastructure..")
    .check(classes);

// Rule 3: Generated SOAP classes must stay in soap.client package
classes().that().resideInAPackage("..soap.client..")
    .should().notAccessClassesThat().resideOutsideOfPackage("..soap..")
    .check(classes);
```

**Precondition:** ArchUnit is most valuable when the team has agreed on architectural rules
and the codebase is clean enough to enforce them without exceeding the exception list.
Implement after Phase 3 unit tests are stable.

---

## Coverage Targets

| Layer | Minimum coverage | Tool |
|---|---|---|
| Domain logic (`WindowPolicy`, `IngestionJob`) | 80% line coverage | JaCoCo |
| Application services | 60% line coverage | JaCoCo |
| Exception handler | 95% line coverage (all handlers tested) | JaCoCo |
| Infrastructure (parsers, mappers) | 50% line coverage | JaCoCo |
| Controllers | Tested via `@WebMvcTest` | — |

**JaCoCo configuration:** Add to `pom.xml` under `<build><plugins>` when implementing Phase 3.
Configure the `check` goal to fail the build if coverage drops below the minimum.

---

## Test Data Strategy

| Test type | Data strategy |
|---|---|
| Unit tests | Constructed in-memory; no fixtures |
| Context load test | H2 in-memory with `create-drop` (current setup) |
| SOAP integration tests | WireMock returning real XML fixtures from `src/test/resources/soap-responses/` |
| Full integration tests | Testcontainers with PostgreSQL 18 + WireMock |

**Fixture files:** Store representative SOAP XML responses (sanitized if necessary) in
`src/test/resources/fixtures/soap/` organized by method name.

---

## CI Considerations

- **Unit tests:** Run on every commit (`./mvnw test`).
- **Integration tests:** Run with a dedicated Maven profile (`./mvnw verify -P integration-tests`). Requires Docker in CI.
- **ArchUnit tests:** Compile-time; run on every commit with unit tests.
- **Coverage report:** Generated by JaCoCo after unit tests; enforced in CI.

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
