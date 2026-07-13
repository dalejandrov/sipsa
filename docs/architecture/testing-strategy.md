# Testing Strategy — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13

---

## Current State

| Metric | Value |
|---|---|
| Test files | 1 (`SipsaApplicationTests.java`) |
| Test methods | 1 (`contextLoads()`) |
| Business logic coverage | 0% (no JaCoCo configured) |
| Database dependency for tests | H2 in-memory (added during migration) |

The existing test confirms that the Spring context loads with H2 in-memory. It provides
no behavioral coverage.

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

### `WindowPolicyTest`

**Target:** `application/ingestion/core/WindowPolicy.java`  
**Branch:** `test/window-policy`

| Test case | Description |
|---|---|
| `dailyMethodInsideWindow_returnsKey` | Current time inside window → returns `YYYY-MM-DD` key |
| `dailyMethodBeforeWindow_throwsViolation` | Time before start → `WindowViolationException` |
| `dailyMethodAfterWindow_throwsViolation` | Time after end → `WindowViolationException` |
| `dailyMethodForceTrue_returnsKeyIgnoringWindow` | `force=true` outside window → key returned without exception |
| `monthlyMethodOnValidDay_returnsKey` | Day 8 after configured time → key returned |
| `monthlyMethodOnInvalidDay_throwsViolation` | Day 7 → `WindowViolationException` |
| `monthlyMethodForceTrue_returnsKey` | `force=true` on day 7 → key returned |
| `timezoneUsed_isBogotaNotUtc` | Verify calculations use `America/Bogota`, not UTC |

**Implementation note:** Inject a fixed `Clock` or `ZonedDateTime` to avoid flaky tests that
depend on the system clock. Consider exposing `validateAndGetKey(String, boolean, ZonedDateTime)` as
a package-private overload for testability, or use `@TestConfiguration` to override the timezone.

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
