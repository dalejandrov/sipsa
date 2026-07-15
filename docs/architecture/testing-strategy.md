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

### `SpecificationBuilderTest`

**Target:** `infrastructure/specification/SpecificationBuilder.java`  
**Branch:** `test/specification-builder`

Use an H2 in-memory repository or pure predicate inspection (no database required).

| Test case | Description |
|---|---|
| `withAttribute_null_noPredicateAdded` | Null value → `build()` returns `conjunction()` |
| `withAttribute_value_equalPredicate` | Non-null value → equal predicate applied |
| `withDateOrRange_exactDate_singleDayRange` | Exact date → full day range (startOfDay to endOfDay) |
| `withDateOrRange_startAndEnd_range` | Both dates → between predicate |
| `withDateOrRange_onlyStart_greaterThanOrEqual` | Start only → `>=` predicate |
| `withDateOrRange_onlyEnd_lessThan` | End only → `<` predicate |
| `withDateOrRange_allNull_noPredicateAdded` | All null → no filter |
| `build_empty_returnsConjunction` | No filters → always-true conjunction |

---

### `IngestionJobTest`

**Target:** `application/ingestion/core/IngestionJob.java` (via `GenericIngestionJob`)  
**Branch:** `test/ingestion-job`

Mock all dependencies: `WindowPolicy`, `IngestionControlService`, `IngestionAuditService`,
`IngestionService`.

| Test case | Description |
|---|---|
| `windowViolation_runSkipped` | `WindowViolationException` → early return, no run created |
| `alreadySucceeded_duplicateSkipped` | `isRunComplete=true`, `force=false` → early return |
| `alreadySucceeded_forceTrue_runCreated` | `isRunComplete=true`, `force=true` → run created |
| `runCreatedSuccessfully_progressesToRunning` | Normal flow → run created → status RUNNING |
| `runCanceled_afterIngestion_markedCanceled` | `isRunCanceled=true` → status CANCELED, no SUCCEEDED |
| `ingestionFails_runMarkedFailed` | Handler throws → status FAILED, error logged |
| `thresholdExceeded_runMarkedFailed` | `rejectCount > maxRejectCount` → `SipsaIngestionException` |
| `rejectedRecords_persistedInFinally` | Even on failure, `logReject` called for each rejected record |
| `metrics_alwaysSavedInFinally` | `updateMetrics` called regardless of success or failure |

---

### `GlobalExceptionHandlerTest`

**Target:** `api/controller/GlobalExceptionHandler.java`  
**Branch:** `test/exception-handler`

Use `@WebMvcTest(GlobalExceptionHandler.class)` with a minimal test controller that throws
each exception type.

| Test case | Expected HTTP | Expected `code` field |
|---|---|---|
| `SipsaValidationException` | 400 | `SIPSA_VALIDATION_ERROR` |
| `SipsaParseException` (after TECH-021) | 502 | `SIPSA_UPSTREAM_PARSE_ERROR` |
| `SipsaIngestionValidationException` | 400 | `SIPSA_INGESTION_VALIDATION_ERROR` |
| `SipsaNotFoundException` (after TECH-022) | 404 | `SIPSA_NOT_FOUND` |
| `SipsaBusinessException` | 422 | `SIPSA_BUSINESS_ERROR` |
| `SipsaExternalException` | 502 | `SIPSA_UPSTREAM_ERROR` |
| `SipsaIngestionException` | 500 | `SIPSA_INGESTION_ERROR` |
| `SipsaConfigurationException` | 500 | `SIPSA_CONFIGURATION_ERROR` |
| `MethodArgumentNotValidException` | 400 | `SIPSA_VALIDATION_ERROR` |
| `ConstraintViolationException` | 400 | `SIPSA_VALIDATION_ERROR` |
| `Exception` (unknown) | 500 | `SIPSA_INTERNAL_ERROR` |

**Important:** Each test must verify that no stack trace appears in the response body
and that infrastructure details (class names, SQL, table names) are not exposed.

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
