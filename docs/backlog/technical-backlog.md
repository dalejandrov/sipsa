# Technical Backlog — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13  
**Source:** Architectural review (2026-07-13)

This backlog is the single source of truth for planned technical improvements.
When a story is implemented:
1. Change its **Status** to `Done`.
2. Add the merge commit or PR link in **Completed**.
3. Update the [Implementation Roadmap](../architecture/implementation-roadmap.md).
4. Update related ADRs if applicable.

---

## Quick Reference

| ID | Title | Priority | Phase | Status |
|---|---|---|---|---|
| TECH-001 | Protect `/api/internal/**` with authentication | Critical | 1 | Pending |
| TECH-002 | Restrict Actuator `loggers` endpoint | High | 1 | Pending |
| TECH-010 | SPIKE: Parcial deduplication key | High | 5 | Pending |
| TECH-011 | Implement correct deduplication for Parcial | High | 5 | Pending |
| TECH-012 | SPIKE: Verify `sipsa_parcial` growth in production | High | 5 | Pending |
| TECH-020 | Fix `@RequestMapping` without leading `/` | High | 1 | Pending |
| TECH-021 | `SipsaParseException` → HTTP 502 | Medium | 2 | Pending |
| TECH-022 | Introduce `SipsaNotFoundException` → HTTP 404 | Medium | 2 | Pending |
| TECH-023 | Add `requestId` and `instance` to error responses | Low | 2 | Pending |
| TECH-030 | Named executor in `@Async` for audit logging | Low | 1 | Pending |
| TECH-031 | Externalize `SipsaHealthIndicator` thresholds | Low | 1 | Pending |
| TECH-032 | Add Micrometer metrics for ingestion | Medium | 4 | Pending |
| TECH-040 | Unit tests for `WindowPolicy` | High | 3 | Pending |
| TECH-041 | Unit tests for `SpecificationBuilder` | High | 3 | Pending |
| TECH-042 | Unit tests for `IngestionJob` | High | 3 | Pending |
| TECH-043 | Tests for `GlobalExceptionHandler` | Medium | 3 | Pending |
| TECH-044 | SPIKE: Integration test strategy (WireMock/Testcontainers) | Low | 6 | Pending |
| TECH-050 | Remove placeholder comments from handlers | Low | 1 | Pending |
| TECH-051 | Rename `toAuditEventRequest` → `toAuditEventResponse` | Low | 1 | Pending |
| TECH-052 | `getRun()` returns `Optional<IngestionRun>` | Low | 1 | Pending |
| TECH-053 | Make scheduler dispatch async | Medium | 2 | Pending |
| TECH-054 | Add pagination to `GET /api/internal/ingestion/runs` | Low | 2 | Pending |
| TECH-055 | SPIKE: `isMonthly()` in `IngestionHandler` contract | Low | 6 | Pending |
| TECH-060 | Fix N+1 in `upsertFallbackBatch` | Medium | 4 | Pending |
| TECH-070 | Bean Validation on `SoapProperties` | Low | 1 | Pending |
| TECH-071 | Align `batch-size` defaults | Low | 1 | Pending |
| TECH-080 | Write ADR-002 (security) | Low | 6 | Pending |
| TECH-081 | Write ADR-001 (deduplication) | Low | 6 | Pending |
| TECH-090 | Move internal ingestion commands to `application/command` | Low | — | **Done** |
| TECH-091 | Move `TimezoneFilter` out of `infrastructure/config` into `api` | Low | — | **Done** |
| TECH-092 | Separate generated SOAP sources from manual code | Low | — | **Blocked** (needs TECH-094 SPIKE) |
| TECH-093 | Add ArchUnit package-boundary rules (Historia B) | Low | — | Pending (TECH-090/TECH-091 merged; still not started) |
| TECH-094 | SPIKE: Evaluate relocating CXF-generated SOAP sources | Low | — | Pending |
| TECH-095 | Remove domain→infrastructure Javadoc reference in `SoapGateway` (Historia A) | Low | — | **Done** |

---

## Stories

---

### TECH-001

**Title:** Protect `/api/internal/**` with authentication  
**Type:** Security  
**Priority:** Critical  
**Phase:** 1  
**Status:** Pending  
**Complexity:** S  
**Branch:** `fix/internal-endpoint-security`

**Problem:**
`SipsaOpsController` and `IngestionAuditController` expose sensitive operational endpoints
without any authentication. The code contains a `TODO` comment acknowledging this.

**Evidence:**
- `SipsaOpsController.java:33`: `TODO: This controller MUST be protected in production environments`
- `./mvnw dependency:tree | grep spring-security` → only `spring-security-crypto:7.1.0` present

**Objective:**
Add authentication to all endpoints under `/api/internal/**`. Keep `/api/sipsa/**` and
`/actuator/health` publicly accessible.

**Dependencies:** See [ADR-002](../adr/ADR-002-internal-endpoint-security.md) for the authentication mechanism decision.

**Acceptance Criteria:**
- [ ] `POST /api/internal/ingestion/run` returns `401` without credentials.
- [ ] `GET /api/internal/audit/recent` returns `401` without credentials.
- [ ] `GET /api/sipsa/ciudad` returns `200` without credentials.
- [ ] `GET /actuator/health` returns `200` without credentials (Docker healthcheck).
- [ ] `GET /actuator/loggers` is not publicly accessible.
- [ ] `./mvnw clean verify` passes.
- [ ] ADR-002 is written after this story is complete (TECH-080).

**Completed:** —

---

### TECH-002

**Title:** Restrict Actuator `loggers` endpoint  
**Type:** Security  
**Priority:** High  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `fix/internal-endpoint-security` (same as TECH-001)

**Problem:**
`management.endpoints.web.exposure.include` in `application.yaml` exposes `loggers`,
which allows changing log levels at runtime without authentication.

**Evidence:** `application.yaml:150`: `include: health,info,metrics,prometheus,loggers`

**Objective:** Remove `loggers` from public exposure or protect it with the same authentication as `/api/internal/**`.

**Dependencies:** TECH-001 (can be resolved in the same branch).

**Acceptance Criteria:**
- [ ] `GET /actuator/loggers` is not accessible without authentication.
- [ ] `GET /actuator/health` remains accessible without authentication.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-010

**Title:** SPIKE — Define natural deduplication key for `SipsaParcial`  
**Type:** SPIKE  
**Priority:** High  
**Phase:** 5  
**Status:** Pending  
**Complexity:** M  
**Branch:** `spike/parcial-deduplication`

**Problem:**
`SipsaIngestionMapper.computeKeyHash()` generates `UUID.randomUUID()` for every Parcial
record. `SipsaParcialRepository.batchUpsert()` performs no deduplication. Each daily run
of `promediosSipsaParcial` inserts all records as new entries.

**Evidence:**
- `SipsaIngestionMapper.java:164-166`: `return UUID.randomUUID().toString()`
- `SipsaParcialRepository.java:53`: `skipped always 0`
- `sipsa_parcial` schema: `key_hash VARCHAR(100) UNIQUE` — constraint never triggered

**Objective:**
Produce ADR-001 answering:
1. What is the natural business key for a Parcial record?
2. Does data change between daily runs for the same logical period?
3. What is the correct deduplication behavior: skip, update, or accumulate?

**Dependencies:** None (this is the blocker for TECH-011).

**Acceptance Criteria:**
- [ ] ADR-001 is written and approved.
- [ ] Natural key is identified with evidence from the DANE data schema.
- [ ] Behavior for duplicates is defined (skip vs update).

**Completed:** —

---

### TECH-011

**Title:** Implement correct deduplication for `SipsaParcial`  
**Type:** Correctiva  
**Priority:** High  
**Phase:** 5  
**Status:** Pending  
**Complexity:** M  
**Branch:** `fix/parcial-data-integrity`

**Problem:** Consequence of TECH-010. Current implementation accumulates duplicate records.

**Dependencies:** TECH-010 must be resolved first.

**Acceptance Criteria:**
- [ ] `computeKeyHash()` produces the same value for the same business inputs.
- [ ] Two consecutive runs of `promediosSipsaParcial` with the same data produce `skipped > 0` on the second run.
- [ ] `UpsertMetrics.inserted` matches the number of genuinely new records.
- [ ] `./mvnw clean verify` passes.
- [ ] If production data contains duplicates: a migration plan is documented and applied.

**Completed:** —

---

### TECH-012

**Title:** SPIKE — Verify `sipsa_parcial` growth in production  
**Type:** SPIKE  
**Priority:** High  
**Phase:** 5  
**Status:** Pending  
**Complexity:** XS  
**Branch:** None (SQL queries only)
**Dependencies:** None (SQL query only — no code changes).

**Problem:** If the system has run in production with random UUID key hashes, the table may already contain large volumes of duplicate data.

**Objective:** Run diagnostic queries in the production database:
```sql
SELECT COUNT(*) total, COUNT(DISTINCT key_hash) unique_keys FROM sipsa_parcial;
SELECT method_name, COUNT(*) runs
FROM ingestion_runs
WHERE method_name = 'promediosSipsaParcial' AND status = 'SUCCEEDED'
GROUP BY method_name;
```

**Acceptance Criteria:**
- [ ] Report documenting: total records, successful runs, ratio of records per run.
- [ ] Decision taken on whether data cleanup is required before TECH-011.

**Completed:** —

---

### TECH-020

**Title:** Fix `@RequestMapping` without leading `/` on two controllers  
**Type:** Bug  
**Priority:** High  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `fix/request-mapping-leading-slash`
**Dependencies:** None.

**Problem:**
Two controllers declare their routes without a leading slash, inconsistent with the rest of the project.

**Evidence:**
- `SipsaOpsController.java:36`: `@RequestMapping("api/internal/ingestion")`
- `IngestionAuditController.java:33`: `@RequestMapping("api/internal/audit")`

**Acceptance Criteria:**
- [ ] Both controllers have `@RequestMapping("/api/internal/...")` with leading `/`.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-021

**Title:** Map `SipsaParseException` to HTTP 502 instead of 400  
**Type:** Correctiva  
**Priority:** Medium  
**Phase:** 2  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `fix/error-http-semantics`
**Dependencies:** None.

**Problem:**
`SipsaParseException` is mapped to HTTP 400 (Bad Request). This exception is thrown when
DANE's XML response cannot be parsed — the error is in the upstream response, not in the
client's request.

**Evidence:**
- `GlobalExceptionHandler.java:117`: `return buildErrorResponse(HttpStatus.BAD_REQUEST, "PARSE_ERROR", ...)`
- `AbstractStaxParser.java:82-85`: thrown when parsing DANE SOAP XML

**Acceptance Criteria:**
- [ ] `SipsaParseException` → HTTP `502 Bad Gateway`.
- [ ] Error code: `SIPSA_UPSTREAM_PARSE_ERROR`.
- [ ] Response body does not expose DANE XML details.
- [ ] TECH-043 test covers this case.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-022

**Title:** Introduce `SipsaNotFoundException` for not-found cases  
**Type:** Correctiva  
**Priority:** Medium  
**Phase:** 2  
**Status:** Pending  
**Complexity:** S  
**Branch:** `fix/error-http-semantics` (same as TECH-021)
**Dependencies:** None. Can be implemented in the same branch as TECH-021.

**Problem:**
`SipsaBusinessException` is used for both business rule violations (correct → 422) and
resource not-found cases (incorrect → should be 404).

**Evidence:**
- `IngestionRunQueryService.java:90`: `throw new SipsaBusinessException("Ingestion run not found: " + runId)`
- `AuditTrailService.java:57`: same pattern for unknown `requestId`

**Acceptance Criteria:**
- [ ] `SipsaNotFoundException extends RuntimeException` created.
- [ ] `GlobalExceptionHandler` maps `SipsaNotFoundException` to HTTP `404`.
- [ ] `GET /api/internal/ingestion/runs/99999` returns `404`.
- [ ] `GET /api/internal/audit/request/unknown-uuid` returns `404`.
- [ ] `SipsaBusinessException` still returns `422` for rule violations.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-023

**Title:** Add `requestId` and `instance` to error responses  
**Type:** Observability  
**Priority:** Low  
**Phase:** 2  
**Status:** Pending  
**Complexity:** S  
**Branch:** `feat/error-correlation-id`
**Dependencies:** None.

**Problem:**
Error responses lack correlation fields. Clients cannot identify which server-side log
entry corresponds to their received error.

**Evidence:** `GlobalExceptionHandler.java:324`: `ErrorResponse` record has no `requestId` or `instance`.

**Acceptance Criteria:**
- [ ] `ErrorResponse` includes `instance` (the request path, from `HttpServletRequest.getRequestURI()`).
- [ ] `ErrorResponse` includes `requestId` (from `X-Request-ID` header if present, generated otherwise).
- [ ] Existing fields (`timestamp`, `status`, `error`, `code`, `message`) are preserved.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-030

**Title:** Specify executor name in `@Async` for `IngestionAuditService.logEvent()`  
**Type:** Bug  
**Priority:** Low  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `fix/async-executor-audit`
**Dependencies:** None.

**Problem:**
`@Async` without an executor name uses `SimpleAsyncTaskExecutor` (creates a new thread per
invocation) instead of the configured `ingestionTaskExecutor` pool.

**Evidence:** `IngestionAuditService.java:67`: `@Async` (no name)

**Acceptance Criteria:**
- [ ] `logEvent()` uses a named executor: either `@Async("ingestionTaskExecutor")` or a dedicated audit executor.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-031

**Title:** Externalize `SipsaHealthIndicator` staleness thresholds  
**Type:** Config  
**Priority:** Low  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `refactor/health-indicator-config`
**Dependencies:** None.

**Problem:**
Staleness thresholds (36h daily, 35 days monthly) are hardcoded in `SipsaHealthIndicator`.

**Evidence:** `SipsaHealthIndicator.java:107,112`

**Acceptance Criteria:**
- [ ] Thresholds are defined in `application.yaml` under `sipsa.health.*`.
- [ ] `SipsaHealthIndicator` reads them via `@ConfigurationProperties` or `@Value`.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-032

**Title:** Add Micrometer metrics for the ingestion pipeline  
**Type:** Observability  
**Priority:** Medium  
**Phase:** 4  
**Status:** Pending  
**Complexity:** M  
**Branch:** `feat/ingestion-metrics`
**Dependencies:** None.

**Problem:**
No custom metrics exist. It is not possible to alert on ingestion duration, record reject rate, or SOAP failures using Prometheus.

**Evidence:** `grep -rn "MeterRegistry\|@Timed\|Counter\|Timer" src/` → zero results.

**Objective:** Add at minimum:
- `sipsa.ingestion.duration` (Timer, tag: `method`)
- `sipsa.ingestion.records.seen` (Counter, tag: `method`)
- `sipsa.ingestion.records.inserted` (Counter, tag: `method`)
- `sipsa.ingestion.records.rejected` (Counter, tag: `method`)
- `sipsa.soap.calls` (Counter, tags: `method`, `result=[success,error]`)

**Acceptance Criteria:**
- [ ] After running any ingestion method, `GET /actuator/metrics/sipsa.ingestion.duration` returns data.
- [ ] All metrics have a `method` tag with the SOAP method name.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-040

**Title:** Unit tests for `WindowPolicy`  
**Type:** Testing  
**Priority:** High  
**Phase:** 3  
**Status:** Pending  
**Complexity:** S  
**Branch:** `test/window-policy`
**Dependencies:** None.

**Problem:** `WindowPolicy` contains time-critical business logic with no test coverage.

**Evidence:** `WindowPolicy.java` — 199 lines, 0 tests.

**Acceptance Criteria:**
- [ ] ≥ 8 test cases as defined in [Testing Strategy](testing-strategy.md).
- [ ] Tests are deterministic (no dependency on system clock).
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-041

**Title:** Unit tests for `SpecificationBuilder`  
**Type:** Testing  
**Priority:** High  
**Phase:** 3  
**Status:** Pending  
**Complexity:** S  
**Branch:** `test/specification-builder`

**Dependencies:** None.

**Acceptance Criteria:**
- [ ] ≥ 7 test cases as defined in [Testing Strategy](testing-strategy.md).
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-042

**Title:** Unit tests for `IngestionJob`  
**Type:** Testing  
**Priority:** High  
**Phase:** 3  
**Status:** Pending  
**Complexity:** M  
**Branch:** `test/ingestion-job`

**Dependencies:** Recommended to implement TECH-040 first to establish test patterns.

**Acceptance Criteria:**
- [ ] ≥ 7 test cases as defined in [Testing Strategy](testing-strategy.md).
- [ ] All dependencies are mocked (no database, no SOAP).
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-043

**Title:** Tests for `GlobalExceptionHandler`  
**Type:** Testing  
**Priority:** Medium  
**Phase:** 3  
**Status:** Pending  
**Complexity:** S  
**Branch:** `test/exception-handler`

**Dependencies:** TECH-021 and TECH-022 must be done first so that 502 and 404 cases are included.

**Acceptance Criteria:**
- [ ] One `@WebMvcTest` test per exception handler.
- [ ] Each test verifies HTTP code and `code` field; no stack trace in response.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-044

**Title:** SPIKE — Evaluate WireMock and Testcontainers for integration testing  
**Type:** SPIKE  
**Priority:** Low  
**Phase:** 6  
**Status:** Pending  
**Complexity:** M  
**Branch:** `spike/integration-test-strategy`
**Dependencies:** None.

**Objective:** Determine the integration test tooling: WireMock 3.x vs `wiremock-spring-boot:4.x`, H2 vs Testcontainers. Produce a proof-of-concept test for `CiudadIngestionHandler`.

**Acceptance Criteria:**
- [ ] Decision documented in [Testing Strategy](testing-strategy.md).
- [ ] One working proof-of-concept integration test for `promediosSipsaCiudad`.

**Completed:** —

---

### TECH-050

**Title:** Remove `// ...existing code...` placeholder comments from handlers  
**Type:** QA  
**Priority:** Low  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `fix/cleanup-placeholder-comments`

**Evidence:**
- `CiudadIngestionHandler.java:115`
- `SemanaIngestionHandler.java:103`
- `AbasIngestionHandler.java:123`
- `MesIngestionHandler.java:109`

**Dependencies:** None.

**Acceptance Criteria:**
- [ ] Zero occurrences of `// ...existing code...` in `src/main/`.
- [ ] The surrounding catch blocks are reviewed for completeness.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-051

**Title:** Rename `toAuditEventRequest` to `toAuditEventResponse` in `IngestionAuditMapper`  
**Type:** QA  
**Priority:** Low  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `fix/cleanup-placeholder-comments` (same branch as TECH-050)

**Evidence:** `IngestionAuditMapper.java:36`: method named `toAuditEventRequest` returns `AuditEventResponse`.

**Dependencies:** None. Can be in the same branch as TECH-050.

**Acceptance Criteria:**
- [ ] Method renamed to `toAuditEventResponse`.
- [ ] All call sites updated.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-052

**Title:** `IngestionControlService.getRun()` returns `Optional<IngestionRun>`  
**Type:** QA  
**Priority:** Low  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `refactor/optional-return-types`

**Evidence:** `IngestionControlService.java:261`: `return runRepository.findById(runId).orElse(null)`

**Dependencies:** None.

**Acceptance Criteria:**
- [ ] `getRun()` returns `Optional<IngestionRun>`.
- [ ] All callers updated to use `.orElseThrow(...)` or `.orElse(null)` explicitly.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-053

**Title:** Make scheduler dispatch ingestion jobs asynchronously  
**Type:** Correctiva  
**Priority:** Medium  
**Phase:** 2  
**Status:** Pending  
**Complexity:** S  
**Branch:** `fix/scheduler-async-execution`

**Problem:**
`SipsaIngestionScheduler.runSafely()` calls `ingestionJob.execute()` synchronously, blocking
one of the 5 scheduler threads for the full duration of the ingestion (potentially hours for Parcial).

**Evidence:** `SipsaIngestionScheduler.java:130`: `ingestionJob.execute(request)`

**Objective:** Replace with `asyncIngestionService.executeAsync(request)` which uses `@Async("ingestionTaskExecutor")`.

**Dependencies:** See [ADR-005](../adr/ADR-005-scheduler-execution-model.md) for the decision and trade-offs.

**Acceptance Criteria:**
- [ ] `runDailyWindow()` returns in < 200ms.
- [ ] Ingestion jobs run in the `ingestionTaskExecutor` pool.
- [ ] Logs include `requestSource=SCHEDULED`.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-054

**Title:** Add pagination to `GET /api/internal/ingestion/runs`  
**Type:** Correctiva  
**Priority:** Low  
**Phase:** 2  
**Status:** Pending  
**Complexity:** S  
**Branch:** `fix/runs-endpoint-pagination`

**Evidence:** `IngestionRunQueryService.java:68`: `controlService.findAllRuns()` without Pageable.

**Dependencies:** None.

**Acceptance Criteria:**
- [ ] `GET /api/internal/ingestion/runs` accepts `page` and `size` query parameters.
- [ ] Default: returns the most recent 50 runs when no parameters are provided.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-055

**Title:** SPIKE — `isMonthly()` method in `IngestionHandler` contract  
**Type:** SPIKE  
**Priority:** Low  
**Phase:** 6  
**Status:** Pending  
**Complexity:** S  
**Branch:** `spike/ingestion-handler-contract`
**Dependencies:** None (SPIKE — investigation only).

**Problem:** `WindowPolicy.isMonthlyMethod()` uses string matching (`contains("mesmadr")`, `contains("abas")`).
New monthly handlers with different naming patterns will silently use daily scheduling.

**Objective:** Decide whether to add `boolean isMonthly()` to `IngestionHandler` interface or document the naming convention. See [ADR-006](../adr/ADR-006-ingestion-handler-contract.md).

**Completed:** —

---

### TECH-060

**Title:** Fix N+1 query in `SipsaMayoristasSemanalRepository.upsertFallbackBatch()`  
**Type:** Performance  
**Priority:** Medium  
**Phase:** 4  
**Status:** Pending  
**Complexity:** M  
**Branch:** `fix/batch-upsert-n-plus-one`
**Dependencies:** None.

**Problem:**
`upsertFallbackBatch()` calls `findByBusinessKeys(artiId, fuenId, fechaIni)` individually for
each record in the batch, producing N database queries for N records.

**Evidence:** `SipsaMayoristasSemanalRepository.java:148`: `findByBusinessKeys()` called inside a for-loop.

**Objective:** Refactor to bulk-fetch all existing records in one query (similar to `SipsaCiudadRepository.batchUpsert()`).

**Acceptance Criteria:**
- [ ] `upsertFallbackBatch()` executes 1 SELECT and 1 INSERT for any batch size.
- [ ] Behavior (skip existing, insert new) is unchanged.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-070

**Title:** Add Bean Validation to `SoapProperties`  
**Type:** Config  
**Priority:** Low  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `refactor/config-validation`

**Evidence:** `SoapProperties.java`: no `@Validated`, no field-level constraints.

**Dependencies:** None.

**Acceptance Criteria:**
- [ ] `SoapProperties` is annotated with `@Validated`.
- [ ] Critical fields (`endpoint`, `connectTimeoutMs`, `readTimeoutMs`) have `@NotBlank` / `@Min(1)`.
- [ ] The manual validation in `SipsaSoapClientConfig.validateConfiguration()` is removed or reduced.
- [ ] Application fails at startup with a clear error when configuration is invalid.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-071

**Title:** Align `batch-size` default values  
**Type:** Config  
**Priority:** Low  
**Phase:** 1  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `refactor/config-validation` (same branch as TECH-070)
**Dependencies:** None. Can be in the same branch as TECH-070.

**Problem:**
`@Value("${sipsa.ingestion.batch-size:2000}")` (default 2000) in 5 handlers conflicts with
`application.yaml: batch-size: ${INGESTION_BATCH_SIZE:500}` (default 500).

**Acceptance Criteria:**
- [ ] The `@Value` default and the `application.yaml` default agree on the same value.
- [ ] `./mvnw clean verify` passes.

**Completed:** —

---

### TECH-080

**Title:** Write ADR-002 — Internal endpoint security decision  
**Type:** Documentation  
**Priority:** Low  
**Phase:** 6  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `docs/architecture-decisions`

**Dependencies:** TECH-001 must be implemented first.

**Acceptance Criteria:**
- [ ] `docs/adr/ADR-002-internal-endpoint-security.md` is updated from `Proposed` to `Accepted`.
- [ ] The chosen mechanism is documented with its rationale.

**Completed:** —

---

### TECH-081

**Title:** Write ADR-001 — Data deduplication strategy  
**Type:** Documentation  
**Priority:** Low  
**Phase:** 6  
**Status:** Pending  
**Complexity:** XS  
**Branch:** `docs/architecture-decisions`

**Dependencies:** TECH-010 (SPIKE) must be resolved first.

**Acceptance Criteria:**
- [ ] `docs/adr/ADR-001-data-deduplication.md` is updated from `Proposed` to `Accepted`.
- [ ] Natural keys for all five data types are documented.

**Completed:** —

---

### TECH-090

**Title:** Move internal ingestion commands from `api/dto/request` to `application/command`
**Type:** Refactor
**Priority:** Low
**Phase:** —
**Status:** **Done** — implemented on branch `refactor/internal-models-and-api-filter`, approved by [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md) (`Accepted`, scoped to F1)
**Complexity:** S
**Branch:** `refactor/internal-models-and-api-filter`

**Problem:**
`IngestionRequest`, `CreateRunRequest`, and `AuditEventRequest` live in `api/dto/request/`
but are never bound from an HTTP request. All instances are built via internal static
factories and consumed exclusively by `application/*` classes.

**Evidence:** ADR-007 §F1. `grep -RIn "IngestionRequest\|CreateRunRequest\|AuditEventRequest" src/main/java`
shows zero usages inside `api/controller/*`.

**Scope — exact classes:**
- `src/main/java/com/dalejandrov/sipsa/api/dto/request/IngestionRequest.java` → `application/command/`
- `src/main/java/com/dalejandrov/sipsa/api/dto/request/CreateRunRequest.java` → `application/command/`
- `src/main/java/com/dalejandrov/sipsa/api/dto/request/AuditEventRequest.java` → `application/command/`

**Consumers to update (imports only, no logic changes):**
`application/ingestion/core/IngestionJob.java`,
`application/ingestion/scheduler/SipsaIngestionScheduler.java`,
`application/service/IngestionControlService.java`,
`application/service/IngestionTriggerService.java`,
`application/service/IngestionAuditService.java`,
`application/service/AsyncIngestionService.java`.

**Dependencies:** None remaining — ADR-007 is `Accepted` (scoped to F1).

**Pre-move verification (mandatory before moving any file):** confirm by search that none
of the three classes is used as `@RequestBody`, `@ModelAttribute`, a direct controller
method parameter, a serialized external contract, or a type referenced in OpenAPI/Swagger
documentation. If any of them turns out to be a real HTTP contract, do not move it — split
a separate application-layer model out first instead.

**Risk:** Low. Package move + import updates only. This iteration's preference is:
1. move packages, 2. keep names as-is, 3. leave any rename (e.g. `IngestionRequest` →
`RunIngestionCommand`) for a separate follow-up story. Do not mix moving and renaming in
this story if the diff would grow meaningfully.

**Contracts to preserve:** No REST route, JSON body, or HTTP status changes — these classes
were never part of the public HTTP contract.

**Acceptance Criteria:**
- [x] The three classes live under `application/command/`.
- [x] `api/dto/request/` no longer contains them.
- [x] All 6 consumer files compile against the new package.
- [x] `./mvnw clean verify` passes.
- [x] `application → api` import count (full codebase `grep`, not just the
      `architecture-review.md` curated table) drops from 9 files to 5 files after this
      story and TECH-091/TECH-095: `SipsaIngestionScheduler`, `IngestionJob`,
      `IngestionControlService`, and `AsyncIngestionService` no longer import anything from
      `api`. The 5 remaining files (`IngestionTriggerService`, `SipsaReadService`,
      `IngestionRunQueryService`, `IngestionAuditService`, `AuditTrailService`) keep
      genuinely-justified imports — see ADR-007 Consequences.

**Completed:** 2026-07-13, branch `refactor/internal-models-and-api-filter`.

---

### TECH-091

**Title:** Move `TimezoneFilter` from `infrastructure/config` to `api/filter`
**Type:** Refactor
**Priority:** Low
**Phase:** —
**Status:** **Done** — implemented on branch `refactor/internal-models-and-api-filter`, approved by [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md) (`Accepted`, scoped to F2)
**Complexity:** XS
**Branch:** `refactor/internal-models-and-api-filter`

**Problem:**
`TimezoneFilter` is an HTTP request filter (`OncePerRequestFilter`) but is placed under
`infrastructure/config`. It imports `api.util.TimezoneUtil`
(`TimezoneFilter.java:3`) — the only confirmed `infrastructure → api` import in the
codebase.

**Evidence:** ADR-007 §F2. `grep -RIn "import .*\.api\." src/main/java/.../infrastructure` → 1 hit.

**Scope — exact class:**
- `src/main/java/com/dalejandrov/sipsa/infrastructure/config/TimezoneFilter.java` → `api/filter/`

**Dependencies:** None remaining — ADR-007 is `Accepted` (scoped to F2). Do **not** move
`TimezoneUtil` in this story — its placement is deferred (see ADR-007). Preserve exactly:
behavior, filter order, headers, the `ThreadLocal`, `finally`-block cleanup, and its
registration as a Spring bean (`@Component`) — do not change the filter's logic, only its
package.

**Risk:** Very low. `TimezoneFilter` is `@Component`-scanned by Spring Boot's default
component scan (`com.dalejandrov.sipsa` base package); no explicit registration bean
references its current package.

**Contracts to preserve:** `X-Timezone` header handling, response conversion behavior,
filter ordering — no change.

**Acceptance Criteria:**
- [x] `TimezoneFilter` lives under `api/filter/`.
- [x] `infrastructure/config/` no longer contains it.
- [x] `./mvnw clean verify` passes. Diff against the pre-move file is exactly one line
      (the `package` declaration) — behavior, filter order, headers, `ThreadLocal`,
      `finally`-block cleanup, and `@Component` registration are byte-for-byte unchanged.
- [x] `infrastructure → api` import count drops from 1 to 0.

**Completed:** 2026-07-13, branch `refactor/internal-models-and-api-filter`.

---

### TECH-092

**Title:** Separate generated SOAP sources from manual code
**Type:** Refactor
**Priority:** Low
**Phase:** —
**Status:** **Blocked** — F3 is explicitly **not** part of ADR-007's scoped acceptance.
Requires [TECH-094](#tech-094) (SPIKE) to complete first; TECH-094's findings determine
whether this story is approved as-is, re-scoped, or rejected. **Do not move any generated
code before TECH-094 reports back.**
**Complexity:** S
**Branch:** `refactor/soap-generated-package` (not created yet)

**Problem:**
`cxf-codegen-plugin` generates 24 JAXB classes into
`com.dalejandrov.sipsa.infrastructure.soap.client` — the same package as the hand-written
`SoapStreamingClient.java`. Generated and manual code are not distinguishable by package.

**Evidence:** ADR-007 §F3. `pom.xml:264` (`-p com.dalejandrov.sipsa.infrastructure.soap.client`);
`find target/generated-sources -name "*.java"` → 24 files in that package.

**Scope:**
- `pom.xml`: change the `wsdl2java` `-p` argument to `com.dalejandrov.sipsa.infrastructure.soap.generated`.
- `src/main/java/com/dalejandrov/sipsa/infrastructure/soap/gateway/SoapGatewayImpl.java:5`: update the wildcard import.
- `src/main/java/com/dalejandrov/sipsa/infrastructure/soap/config/SipsaSoapClientConfig.java:4-5`: update the two explicit imports.

**Dependencies:** [TECH-094](#tech-094) (SPIKE) must complete first.

**Risk:** Low, but must be verified, not assumed — JAXB `@XmlType`/`@XmlSchema` bindings
occasionally reference the generated package implicitly.

**Verification steps (mandatory, per this story's acceptance criteria):**
1. Confirm the WSDL and JAX-WS catalog are unchanged.
2. `./mvnw clean generate-sources` and inspect the new
   `target/generated-sources/.../infrastructure/soap/generated/` output for any unexpected
   class differences beyond the package declaration (`diff` against the current output with
   only the package line changed).
3. `./mvnw clean verify` passes end to end (SOAP marshalling included).

**Contracts to preserve:** WSDL contract, SOAP request/response marshalling, XML namespace
bindings — no change.

**Acceptance Criteria:**
- [ ] Generated classes are emitted under `infrastructure/soap/generated`.
- [ ] `SoapGatewayImpl` and `SipsaSoapClientConfig` compile against the new package.
- [ ] `./mvnw clean verify` passes.
- [ ] Diff of generated output (excluding the package declaration) is empty.

**Completed:** —

---

### TECH-093

**Title:** Add ArchUnit package-boundary rules (Historia B)
**Type:** Testing
**Priority:** Low
**Phase:** —
**Status:** Pending — **blocked until TECH-090 and TECH-091 are merged** (ADR-007 is
already `Accepted`, scoped to F5; the block is sequencing, not approval)
**Complexity:** S
**Branch:** `test/architecture-boundaries`

**Problem:** No ArchUnit (or equivalent) test exists to prevent the boundaries fixed by
TECH-090, TECH-091, and TECH-095 from regressing.

**Evidence:** ADR-007 §F5.

**Scope:**
- Add `com.tngtech.archunit:archunit-junit5` (test scope) to `pom.xml`.
- Add one ArchUnit test class asserting exactly these 3 rules (no more in this first version):
  1. `application` does not depend on `api`.
  2. `domain` does not depend on `infrastructure`.
  3. `api.controller..` does not depend on `infrastructure.persistence.repository..`.

**Explicitly out of scope for this story's rules:** any additional rule beyond the 3 above.
In particular, rule 1 (`application` must not depend on `api`) must be written narrowly
enough to pass against the classes ADR-007 explicitly keeps as-is
(`SipsaReadService`, `IngestionRunQueryService`, `AuditTrailService` using
`api.mapper`/`api.dto.response`/`api.dto.request.*QueryRequest`) — either by excluding
those specific classes or by scoping the rule to `api.dto.request..IngestionRequest`,
`CreateRunRequest`, `AuditEventRequest` (which TECH-090 removes anyway). Do not write a
rule that fails against an accepted decision.

**Dependencies:** TECH-090 and TECH-091 must be merged first so the ArchUnit rules assert
the *post*-move state rather than failing immediately. TECH-095 (Javadoc fix) must also be
merged first so rule 2 passes on day one.

**Risk:** Low.

**Acceptance Criteria:**
- [ ] ArchUnit test class exists with the 3 rules above, all green.
- [ ] `./mvnw clean verify` passes.
- [ ] No rule beyond the 3 listed was added.

**Completed:** —

---

### TECH-094

**Title:** SPIKE — Evaluate relocating CXF-generated SOAP sources
**Type:** SPIKE
**Priority:** Low
**Phase:** —
**Status:** Pending
**Complexity:** XS
**Branch:** `spike/soap-generated-package`
**Dependencies:** None.

**Problem:** ADR-007 §F3 identified that CXF-generated JAXB classes share a package with
hand-written code, but the risk assessment ("low, but non-zero") was a judgment call, not
verified evidence. TECH-092 must not proceed until this is investigated directly.

**Objective:** Investigate and report on:
1. Which plugin and version generates the classes (`cxf-codegen-plugin`, version — confirm
   against `pom.xml`).
2. From which WSDL (`SrvSipsaUpraBeanService.wsdl` — confirm path and catalog).
3. The currently configured target package (`pom.xml`'s `wsdl2java` `-p` argument).
4. Whether the generated classes are version-controlled (they should not be — confirm
   `.gitignore` covers `target/`).
5. Whether `./mvnw clean generate-sources` reproduces the classes exactly on a clean run
   (run it twice, diff the output).
6. The expected diff size if the package is retargeted to
   `infrastructure/soap/generated` (estimate file count and import-site count — already
   known to be 24 generated files + 2 manual import sites from ADR-007 §F3, but this SPIKE
   should verify that count is still accurate).
7. Import impact on `SoapGatewayImpl.java` and `SipsaSoapClientConfig.java`.
8. CXF compatibility — confirm the `-p` argument is respected consistently across the CXF
   version in use, with no known issues in that version's changelog.
9. Whether the relocation is worth the generated noise, given the actual (not assumed) diff
   size and risk found above.

**Acceptance Criteria:**
- [ ] Report answering all 9 points above, added to this story's **Completed** section or
      linked as a separate note.
- [ ] Explicit recommendation: proceed with TECH-092 as scoped, re-scope it, or reject it.
- [ ] No source code changed as part of this SPIKE.

**Completed:** —

---

### TECH-095

**Title:** Remove domain→infrastructure Javadoc reference in `SoapGateway` (Historia A)
**Type:** QA
**Priority:** Low
**Phase:** —
**Status:** **Done** — implemented on branch `refactor/internal-models-and-api-filter`, approved by [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md) (`Accepted`, scoped to F4)
**Complexity:** XS
**Branch:** `refactor/internal-models-and-api-filter` (may be implemented together with
TECH-090/TECH-091 in the same iteration; keep as its own commit)

**Problem:** `domain/gateway/SoapGateway.java:3` imports
`infrastructure.soap.gateway.SoapGatewayImpl` solely to support a `@see SoapGatewayImpl`
Javadoc tag (line 39) — the only confirmed `domain → infrastructure` import in the codebase.

**Evidence:** ADR-007 §F4. `grep -RIn "import .*\.infrastructure\." src/main/java/.../domain` → 1 hit.

**Scope — exact class:**
- `src/main/java/com/dalejandrov/sipsa/domain/gateway/SoapGateway.java`: remove the
  `SoapGatewayImpl` import; replace the `@see SoapGatewayImpl` tag with a plain-text
  mention (e.g. `{@code SoapGatewayImpl} (infrastructure layer)`).

**Dependencies:** None remaining — ADR-007 is `Accepted` (scoped to F4).

**Risk:** None. Javadoc-only change, no compiled behavior difference beyond the removed import.

**Contracts to preserve:** No behavior change. This is a precondition for TECH-093's
`domain`-must-not-depend-on-`infrastructure` ArchUnit rule to pass on day one.

**Acceptance Criteria:**
- [x] `SoapGateway.java` no longer imports anything from `infrastructure`.
- [x] Javadoc still points a reader to the implementation class (as plain text, not a
      compiled `@see` reference).
- [x] `./mvnw clean verify` passes.
- [x] `domain → infrastructure` import count drops from 1 to 0.

**Completed:** 2026-07-13, branch `refactor/internal-models-and-api-filter`.
