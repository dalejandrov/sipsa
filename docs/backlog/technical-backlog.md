# Technical Backlog — SIPSA Integration Service

**Version:** 1.1  
**Date:** 2026-07-15 (originally 2026-07-13)  
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
| TECH-001 | Protect `/api/internal/**` with authentication | Critical | 1 | **Done** (application layer; gateway/network layers: TECH-130..132) |
| TECH-002 | Restrict Actuator `loggers` endpoint | High | 1 | **Done** |
| TECH-010 | SPIKE: Parcial deduplication key | High | 5 | **Done** (2026-07-16 — key confirmed against real DANE data; ADR-001 Accepted) |
| TECH-011 | Implement correct deduplication for Parcial | High | 5 | **Done** (2026-07-16, branch `fix/sipsa-parcial-data-integrity`) |
| TECH-012 | SPIKE: Verify `sipsa_parcial` growth in production | High | 5 | **Pending external verification** — local real-data diagnosis completed 2026-07-16 (see story); the external check applies only if a historical external database is confirmed to exist |
| TECH-020 | Fix `@RequestMapping` without leading `/` | High | 1 | Pending |
| TECH-021 | `SipsaParseException` → HTTP 502 | Medium | 2 | Pending |
| TECH-022 | Introduce `SipsaNotFoundException` → HTTP 404 | Medium | 2 | Pending |
| TECH-023 | Add `requestId` and `instance` to error responses | Low | 2 | Pending |
| TECH-030 | Named executor in `@Async` for audit logging | Low | 1 | Pending |
| TECH-031 | Externalize `SipsaHealthIndicator` thresholds | Low | 1 | Pending |
| TECH-032 | Add Micrometer metrics for ingestion | Medium | 4 | Pending |
| TECH-040 | Unit tests for `WindowPolicy` | High | 3 | **Done** (implemented by TECH-110) |
| TECH-041 | Unit tests for `SpecificationBuilder` | High | 3 | Pending |
| TECH-042 | Unit tests for `IngestionJob` | High | 3 | Pending |
| TECH-043 | Tests for `GlobalExceptionHandler` | Medium | 3 | Pending |
| TECH-044 | SPIKE: Integration test strategy (WireMock/Testcontainers) | Low | 6 | Partially resolved — Testcontainers half settled by ADR-009 (`FlywayMigrationsTest`); WireMock half pending |
| TECH-050 | Remove placeholder comments from handlers | Low | 1 | Pending |
| TECH-051 | Rename `toAuditEventRequest` → `toAuditEventResponse` | Low | 1 | Pending |
| TECH-052 | `getRun()` returns `Optional<IngestionRun>` | Low | 1 | Pending |
| TECH-053 | Make scheduler dispatch async | Medium | 2 | Pending |
| TECH-054 | Add pagination to `GET /api/internal/ingestion/runs` | Low | 2 | Pending |
| TECH-055 | SPIKE: `isMonthly()` in `IngestionHandler` contract | Low | 6 | Pending |
| TECH-060 | Fix N+1 in `upsertFallbackBatch` | Medium | 4 | Pending |
| TECH-070 | Bean Validation on `SoapProperties` | Low | 1 | Pending |
| TECH-071 | Align `batch-size` defaults | Low | 1 | **Done** (2026-07-16 — single typed source of truth, canonical 500) |
| TECH-080 | Write ADR-002 (security) | Low | 6 | **Done** |
| TECH-081 | Write ADR-001 (deduplication) | Low | 6 | **Done** (2026-07-16 — ADR-001 Accepted with empirical evidence) |
| TECH-090 | Move internal ingestion commands to `application/command` | Low | — | **Done** |
| TECH-091 | Move `TimezoneFilter` out of `infrastructure/config` into `api` | Low | — | **Done** |
| TECH-092 | Separate generated SOAP sources from manual code | Low | — | **Blocked** (needs TECH-094 SPIKE) |
| TECH-093 | Add ArchUnit package-boundary rules (Historia B) | Low | — | Pending (TECH-090/TECH-091 merged; still not started) |
| TECH-094 | SPIKE: Evaluate relocating CXF-generated SOAP sources | Low | — | Pending |
| TECH-095 | Remove domain→infrastructure Javadoc reference in `SoapGateway` (Historia A) | Low | — | **Done** |
| TECH-110 | Validate scheduled ingestion jobs and add scheduling tests | High | 3 | **Done** |
| TECH-111 | Correct monthly `WindowPolicy` method binding, grace days, and stable window keys | High | 3 | **Done** |
| TECH-120 | Continuous integration pipeline (GitHub Actions) | High | — | **Done** |
| TECH-130 | Cognito resource server, scopes and app clients | High | — | Pending (infrastructure) |
| TECH-131 | API Gateway: API keys, usage plans, throttling, access logs | High | — | Pending (infrastructure) |
| TECH-132 | Private networking: ECS, VPC Link, internal ALB, gateway-bypass prevention | High | — | Pending (infrastructure) |
| TECH-113 | Fix `artiId`/`muniId` filters of `GET /api/sipsa/parcial` | Medium | — | **Done** (2026-07-16, branch `fix/sipsa-parcial-query-filters`) |
| TECH-114 | Strict `enmaFecha` parsing with explicit rejection (H-1) | Medium | — | **Done** (2026-07-16 — implemented within TECH-011; H-1 did not occur on real data) |
| TECH-115 | Backfill/consolidation of a pre-existing external `sipsa_parcial` database | Medium | — | Conditional — only if an external historical database is confirmed to exist |
| TECH-116 | Disable `baseline-on-migrate` after per-environment Flyway history inventory | Low | — | Pending |
| TECH-117 | Handle concurrent `SipsaParcial` duplicate insertion safely | Medium | — | **Done** (2026-07-19, branch `fix/sipsa-parcial-concurrent-dedup` — atomic `ON CONFLICT (key_hash) DO NOTHING`, collisions counted as skipped) |
| TECH-118 | Align `SipsaParcial` decimal precision (JPA 15,2 vs DDL 19,2) | Low | — | Pending |
| TECH-119 | Remove redundant `idx_sipsa_parcial_key_hash` index | Low | — | **Done** (2026-07-16, branch `fix/remove-redundant-parcial-key-hash-index`, migration V3) |
| TECH-122 | Harden `SipsaParcial` natural-key constraints (NOT NULL / natural unique) | Low | — | Pending (contract phase; gated on TECH-012 external half) |
| TECH-123 | Add `first_seen_at`/`last_seen_at` republication traceability | Low | — | Optional — not recommended now (write cost; see story) |
| TECH-124 | Optimize `SipsaParcial` article-filter queries | Low | — | **Done** (2026-07-18, branch `perf/sipsa-parcial-article-filter-index`, migration V4 — covering index; count 18 ms → ~2 ms) |
| TECH-125 | Define `SipsaParcial`/ingestion data retention policy | Low | — | Pending decision |
| TECH-133 | Centralize and validate monthly ingestion window configuration | Low | — | **Done** (2026-07-17 — typed `monthlyWindowStart`, divergent `06:00` fallback removed, effective 14:00 unchanged) |

---

## Stories

---

### TECH-001

**Title:** Protect `/api/internal/**` with authentication  
**Type:** Security  
**Priority:** Critical  
**Phase:** 1  
**Status:** **Done** — application security layer implemented 2026-07-15 on branch
`fix/internal-endpoint-security` (ADR-002, Option E). **This closes the application
layer:** Spring Boot is an OAuth 2.0 Resource Server validating Cognito JWTs
(issuer, signature, `token_use=access`, optional client allowlist) and enforcing
per-operation scopes on `/api/internal/**`, stateless, with JSON 401/403. The API
Gateway, Cognito provisioning, and private-network layers are deliberately separate
infrastructure stories — see [TECH-130](#tech-130), [TECH-131](#tech-131),
[TECH-132](#tech-132) — and do not reopen this one.  
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
- [x] `POST /api/internal/ingestion/run` returns `401` without credentials.
- [x] `GET /api/internal/audit/recent` returns `401` without credentials.
- [x] `GET /api/sipsa/ciudad` returns `200` without credentials.
- [x] `GET /actuator/health` returns `200` without credentials (Docker healthcheck).
- [x] `GET /actuator/loggers` is not publicly accessible.
- [x] `./mvnw clean verify` passes.
- [x] ADR-002 is written after this story is complete (TECH-080).

**Completed:** 2026-07-15, branch `fix/internal-endpoint-security`, merged via
[PR #17](https://github.com/dalejandrov/sipsa/pull/17). Verified by
`InternalEndpointSecurityTest` (15 cases) and `SipsaJwtValidatorsTest` (11 cases).
**Post-merge e2e validation (2026-07-15):** manual 9/9-green check of the full Docker
Compose stack against the mock OIDC issuer — token issuance, `token_use=access`, `scope`
claim, issuer coherence, `401`/`403`/`2xx` matrix, Actuator policy
(`/actuator/health` public; `/actuator/info`/`metrics` token-protected), default deny.
No changes to `docker/mock-oidc-config.json` or Spring Security were needed
(evidence: ADR-002 §Local development).

---

### TECH-002

**Title:** Restrict Actuator `loggers` endpoint  
**Type:** Security  
**Priority:** High  
**Phase:** 1  
**Status:** **Done** — closed in two steps: exposure removed from the base profile
(2026-07-14, `chore/config-cleanup-dev-profile` — `loggers` is dev-only since then), and
authentication added on every non-health Actuator endpoint in all profiles (2026-07-15,
`fix/internal-endpoint-security`, ADR-002). Actuator is additionally excluded from the
public API Gateway surface by design (see ADR-002 §5, TECH-131).  
**Complexity:** XS  
**Branch:** `fix/internal-endpoint-security` (same as TECH-001)

**Problem:**
`management.endpoints.web.exposure.include` in `application.yaml` exposes `loggers`,
which allows changing log levels at runtime without authentication.

**Evidence:** `application.yaml:150`: `include: health,info,metrics,prometheus,loggers`

**Objective:** Remove `loggers` from public exposure or protect it with the same authentication as `/api/internal/**`.

**Dependencies:** TECH-001 (can be resolved in the same branch).

**Acceptance Criteria:**
- [x] `GET /actuator/loggers` is not accessible without authentication.
- [x] `GET /actuator/health` remains accessible without authentication.
- [x] `./mvnw clean verify` passes.

**Completed:** 2026-07-15, branch `fix/internal-endpoint-security`, merged via
[PR #17](https://github.com/dalejandrov/sipsa/pull/17) (with the exposure half completed
2026-07-14 on `chore/config-cleanup-dev-profile`). The Actuator policy was re-verified in
the 2026-07-15 post-merge e2e validation: `/actuator/health` 200 without a token;
`/actuator/info` and `/actuator/metrics` 401 without a token, 200 with a valid one.

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
- [x] ADR-001 is written and approved.
- [x] Natural key is identified with evidence from the DANE data schema.
- [x] Behavior for duplicates is defined (skip vs update).

**Completed:** 2026-07-16, branch `fix/sipsa-parcial-data-integrity`. The key
`(muniId, fuenId, futiId, idArtiSemana, enmaFecha)` was validated empirically against a
real full DANE ingestion into a clean local PostgreSQL 18: 676,210 records → 676,210
distinct keys (zero intra-run collisions); a second identical pre-fix run duplicated all
of them (×2, prices identical — no divergent re-publications observed). Behavior: skip
(insert-only), consistent with the other four types. See ADR-001 (`Accepted`) and the
[SPIKE report](../architecture/sipsa-parcial-data-integrity-spike.md).

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
- [x] `computeKeyHash()` produces the same value for the same business inputs
      (`ParcialKeyHash`: versioned, unit-separator-delimited, UTF-8 SHA-256 hex).
- [x] Two consecutive runs of `promediosSipsaParcial` with the same data produce `skipped > 0`
      on the second run — validated against the real DANE endpoint: run 1 inserted 676,210;
      run 2 inserted 0, skipped 676,210; run 3 (after container restart) identical.
- [x] `UpsertMetrics.inserted` matches the number of genuinely new records.
- [x] `./mvnw clean verify` passes (116 tests, 0 failures, Testcontainers gates executed).
- [x] Duplicates in production: no external database is known to exist; the local diagnostic
      base was rebuilt from scratch. Cleanup of a confirmed external base is TECH-115 (conditional).

**Completed:** 2026-07-16, branch `fix/sipsa-parcial-data-integrity`. Implementation:
deterministic `ParcialKeyHash`, skip-first `batchUpsert` (intra-batch dedupe + one bulk
hash lookup + one bulk date lookup that recomputes natural-key hashes to deduplicate
legacy UUID rows without backfill — no N+1), strict `enmaFecha` parsing with explicit
rejection (no implicit zone), `skipped` propagated to `IngestionContext` and logs,
migration `V2__add_parcial_natural_key_index.sql` (expand-only), and suites
`ParcialKeyHashTest`, `ParcialIngestionHandlerTest`, `ParcialMigrationUpgradeTest`
plus extended `FlywayMigrationsTest`.

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
- [x] Report documenting: total records, successful runs, ratio of records per run —
      executed 2026-07-16 against a clean local PostgreSQL 18 loaded from the real DANE
      endpoint (no external/remote database was available or known to exist): 676,210
      records per full ingestion, duplication exactly linear per pre-fix run (×2 after two
      runs, all groups identical prices), 0 duplicates after the TECH-011 fix across three
      runs including a container restart. `enma_fecha` 100% parseable (H-1 not confirmed).
- [ ] **Remaining:** confirm with the data owner whether any external historical
      `sipsa_parcial` database exists. If none: close this story as complete with the
      local evidence. If one exists: execute the read-only script there per the
      [runbook](../diagnostics/tech-012-runbook.md) and evaluate TECH-115.

**Completed:** — (local half complete 2026-07-16; external half conditional, see above)

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
**Status:** **Done** — implemented as part of [TECH-110](#tech-110)
**Complexity:** S  
**Branch:** `test/scheduled-ingestion-jobs` (not `test/window-policy` — bundled into
TECH-110's broader scheduling validation; see TECH-110 for why)
**Dependencies:** None.

**Problem:** `WindowPolicy` contains time-critical business logic with no test coverage.

**Evidence:** `WindowPolicy.java` — 199 lines, 0 tests (before this story).

**Acceptance Criteria:**
- [x] ≥ 8 test cases as defined in [Testing Strategy](../architecture/testing-strategy.md) — 25 delivered.
- [x] Tests are deterministic (no dependency on system clock) — injected `Clock`.
- [x] `./mvnw clean verify` passes.

**Completed:** `test/scheduled-ingestion-jobs` (2026-07-13), see
[Scheduled Ingestion Validation](../architecture/scheduled-ingestion-validation.md).

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
- [ ] ≥ 7 test cases as defined in [Testing Strategy](../architecture/testing-strategy.md).
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
- [ ] ≥ 7 test cases as defined in [Testing Strategy](../architecture/testing-strategy.md).
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

**Partial resolution (2026-07-14):** The H2-vs-Testcontainers half is settled by
[ADR-009](../adr/ADR-009-database-migration-strategy.md): Testcontainers with real
PostgreSQL is adopted and proven by `FlywayMigrationsTest` (dependencies already in
`pom.xml`, managed by the Spring Boot BOM). The remaining scope of this SPIKE is the
WireMock half (SOAP mocking strategy and the `CiudadIngestionHandler` proof of concept).

**Acceptance Criteria:**
- [ ] Decision documented in [Testing Strategy](../architecture/testing-strategy.md).
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

**Evidence:** `IngestionControlService.java:263`: `return runRepository.findById(runId).orElse(null)`.
Two additional `findById(runId).orElse(null)` call sites exist in the same class
(`IngestionControlService.java:314,329`) and should be reviewed under the same criteria
("all callers updated" already covers them).

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
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `fix/unify-ingestion-batch-size-config` (implemented on its own branch;
the originally planned `refactor/config-validation` with TECH-070 was not used)
**Dependencies:** None. Can be in the same branch as TECH-070.

**Problem (historical):**
`@Value("${sipsa.ingestion.batch-size:2000}")` (default 2000) in 5 handlers conflicted with
`application.yaml: batch-size: ${INGESTION_BATCH_SIZE:500}` (default 500).

**Acceptance Criteria:**
- [x] The `@Value` default and the `application.yaml` default agree on the same value —
      exceeded: the per-handler `@Value` defaults were removed entirely. All 5 handlers
      inject the typed `IngestionProperties` (`sipsa.ingestion.batch-size`), canonical
      default **500** (the value used in the TECH-011 real-data validations), overridable
      via `INGESTION_BATCH_SIZE`. Values outside 1..10,000 (or non-numeric) abort startup
      with a clear validation message (`IngestionPropertiesTest`, 9 binding/validation
      cases; `ParcialIngestionHandlerTest` proves the configured size drives flush cadence).
- [x] `./mvnw clean verify` passes (149 tests).

**Completed:** 2026-07-16, branch `fix/unify-ingestion-batch-size-config`. Docker
verified: default startup logs `Ingestion batch size = 500`; with
`INGESTION_BATCH_SIZE=250` it logs `250` (variable now passed through by
`docker-compose.yml`). Smoke ingestion (`promediosSipsaCiudad`, force) ran clean
against the local compose stack with the default 500.

---

### TECH-080

**Title:** Write ADR-002 — Internal endpoint security decision  
**Type:** Documentation  
**Priority:** Low  
**Phase:** 6  
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `fix/internal-endpoint-security` (documented together with the implementation,
not on a separate docs branch)

**Dependencies:** TECH-001 must be implemented first.

**Acceptance Criteria:**
- [x] `docs/adr/ADR-002-internal-endpoint-security.md` is updated from `Proposed` to `Accepted`.
- [x] The chosen mechanism is documented with its rationale.

**Completed:** 2026-07-15, branch `fix/internal-endpoint-security`, merged via
[PR #17](https://github.com/dalejandrov/sipsa/pull/17). ADR-002 accepted with
the layered model (Option E: API Gateway keys + Cognito JWT scopes + Spring Resource
Server + private networking), superseding the original Option A (HTTP Basic)
recommendation after the AWS deployment target was confirmed.

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
- [x] `docs/adr/ADR-001-data-deduplication.md` is updated from `Proposed` to `Accepted`.
- [x] Natural keys for all five data types are documented (ADR-001 §Current Deduplication
      per Data Type; Parcial's key now empirically confirmed).

**Completed:** 2026-07-16, branch `fix/sipsa-parcial-data-integrity` — ADR-001 accepted
with empirical evidence from real DANE ingestions (see TECH-010/TECH-011).

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
### TECH-110

**Title:** Validate scheduled ingestion jobs and add scheduling tests
**Type:** Testing
**Priority:** High
**Phase:** 3
**Status:** **Done**
**Complexity:** M
**Branch:** `test/scheduled-ingestion-jobs`

**Relationship to TECH-040:** This story implements TECH-040's acceptance criteria
(`WindowPolicy` unit tests) in full, and extends the scope to cover the parts of the
scheduled-ingestion pipeline TECH-040 did not: cron expression validity/timing,
`SipsaIngestionScheduler` dispatch correctness, and a scheduling-aware context test. Both
stories are Done together on this branch; TECH-040 does not need a separate branch.

**Problem:**
Two independent findings motivated this validation:
1. `WindowPolicy` has zero test coverage (`docs/architecture/technical-debt.md`, item T-01;
   `testing-strategy.md` §`WindowPolicyTest`).
2. A prior investigation (see the code-review conversation preceding this story, not yet a
   filed backlog item) found that `WindowPolicy.validateMonthly()` does not bind the
   allowed day-of-month to the specific ingestion method, so `promedioAbasSipsaMesMadr`
   (documented by DANE and by `application.yaml:121`'s own comment as a day-10 method) can
   pass validation on day 8, and `promediosSipsaMesMadr` (day 8) can pass on day 10. No
   test in the codebase would have caught this.

Neither `SipsaIngestionScheduler` (cron dispatch) nor the cron expressions themselves
(`application.yaml:126-135`) have any test coverage either — a wrong cron field, a wrong
zone, or a wrong method name passed to `runSafely()` would not be caught by
`./mvnw clean verify` today.

**Evidence:**
- `WindowPolicy.java` — 199 lines, 0 tests (`find src/test -iname "*WindowPolicy*"` → none
  before this story).
- `SipsaIngestionScheduler.java` — 0 tests.
- `src/test/resources/application.yaml` configures scheduling (`sipsa.scheduling.pool-size:
  1`, permissive test cron values) but has no explicit mechanism to prevent `@Scheduled`
  methods from actually firing during `@SpringBootTest` — confirmed by reading
  `SchedulingConfig.java`, which has no `@ConditionalOnProperty` guard.
- `SchedulingConfig.java:24-26` Javadoc claims monthly jobs run "day 8, 06:00 COT" /
  "day 10, 06:00 COT", but the actual `@Scheduled` cron expressions in
  `SipsaIngestionScheduler.java:92,107` are `0 30 14 8 * *` / `0 30 14 10 * *` — 14:30, not
  06:00. Internal documentation drift, found during this story's inventory step.

**Scope:**
- Add `Clock` to `WindowPolicy` for deterministic testing (testability-only change, no
  behavior change — see the dedicated `refactor(time)` commit).
- Add `sipsa.scheduling.enabled` property (default `true`, backward-compatible) so tests
  can guarantee no real cron fires during `@SpringBootTest`.
- `WindowPolicyTest` — daily boundary (13:59:59 / 14:00:00 / 14:00:01), monthly per-method
  day validation (7/8/9/10/11 for both `promediosSipsaMesMadr` and
  `promedioAbasSipsaMesMadr`), `force=true`, `windowKey` stability/change across grace days
  and month/year rollover.
- `SipsaSchedulingCronTest` — validates the 3 production cron expressions with
  `CronExpression.next()` against fixed `America/Bogota` reference times.
- `SipsaIngestionSchedulerTest` — verifies `runDailyWindow()`/`runMonthlyMes()`/
  `runMonthlyAbas()` dispatch the correct method names, `force=false`, and that one
  method's exception does not stop the sequence.
- A limited `@SpringBootTest` verifying the scheduling context wires correctly without
  ever making a real SOAP call.
- Documentation: `docs/architecture/scheduled-ingestion-validation.md` (new),
  `testing-strategy.md`, this backlog entry, `CHANGELOG.md`.

**Explicitly out of scope (no production behavior change without a separate story):**
- Fixing the day-8/day-10 cross-acceptance in `WindowPolicy.validateMonthly()` — this story
  only **proves and documents** the gap with a failing-if-fixed-blind test strategy (see
  the validation report for how the finding is preserved without breaking the build).
- Fixing the `SchedulingConfig` Javadoc drift (06:00 vs. 14:30) — flagged, not changed,
  since the instructions for this story restrict changes to tests/fixtures/test config/docs
  plus the two explicitly allowed testability changes.
- Making the scheduler dispatch asynchronous (`ADR-005`/`TECH-053`) — unrelated, separate
  ADR still `Proposed`.
- `isMonthly()` on `IngestionHandler` (`ADR-006`/`TECH-055`) — unrelated SPIKE.

**Risks:**
- Low — all production changes are additive/backward-compatible (Clock defaults to
  `Clock.system(zone)`, scheduling-enabled property defaults to `true`). No REST contract,
  DB schema, or SOAP integration change.
- The `Clock` injection touches `WindowPolicy`'s constructor signature — any other caller
  must be checked (there is exactly one: `IngestionJob` subclasses receive `WindowPolicy`
  via Spring DI, not direct instantiation, so this is a non-issue).

**Acceptance Criteria:**
- [x] `WindowPolicy` accepts an injectable `Clock` (default `Clock.system(zone)` when not
      overridden), with identical runtime behavior in production.
- [x] `sipsa.scheduling.enabled` property added, default `true`, no behavior change when
      absent.
- [x] ≥ 8 `WindowPolicyTest` cases (TECH-040 minimum), all deterministic (no
      `LocalDateTime.now()`/`Instant.now()`/`ZonedDateTime.now()` without `Clock`) — 25
      delivered.
- [x] `SipsaSchedulingCronTest` validates all 3 production cron expressions against fixed
      `America/Bogota` reference times, including a december→january rollover and a leap
      year case.
- [x] `SipsaIngestionSchedulerTest` verifies dispatch, `force=false`, `requestSource`, and
      exception isolation, with mocked dependencies only (no real SOAP/DB call).
- [x] A `@SpringBootTest` proves the scheduling context loads without firing a real job
      (`SipsaSchedulingContextTest`), with a negative counterpart proving it is disabled by
      default (`SipsaSchedulingDisabledByDefaultTest`).
- [x] `./mvnw clean verify` passes with zero failures — 59 tests, 0 failures, 2 intentional
      skips.
- [x] `docs/architecture/scheduled-ingestion-validation.md` documents the full inventory,
      DANE contrast matrix, and classified findings.
- [x] Any confirmed-but-unfixed bug is documented as a backlog story of its own, not fixed
      silently inside this one — F-WP-01/02/03 documented, proposed as TECH-111 (not yet
      created as a standalone backlog entry — proposed in the validation report, per this
      story's testing-only scope).

**Completed:** `test/scheduled-ingestion-jobs` (2026-07-13), see
[Scheduled Ingestion Validation](../architecture/scheduled-ingestion-validation.md).

---

### TECH-111

**Title:** Correct monthly `WindowPolicy` method binding, grace days, and stable window keys
**Classification:** Corrective / Business rule / Idempotency (**not** a refactoring —
this changes observable validation and key-generation behavior, on purpose)
**Type:** Correctiva
**Priority:** High
**Phase:** 3
**Status:** **Done** — implemented 2026-07-14 on branch `fix/window-policy-monthly-rules`
(plan approved 2026-07-13)
**Complexity:** M
**Branch:** `fix/window-policy-monthly-rules`

**Depends on:** None (see §3 of the plan below — explicitly does not block on TECH-055 or
ADR-006).

**Origin:** Confirmed during [TECH-110](#tech-110)'s validation
([scheduled-ingestion-validation.md](../architecture/scheduled-ingestion-validation.md),
findings F-WP-01, F-WP-02, F-WP-03). This entry formalizes those findings into an
implementation-ready story. **The cron expressions, `America/Bogota` zone, and
`SchedulingConfig` are confirmed correct by TECH-110 and are explicitly out of scope here
— see "Contracts that must be preserved" below.**

---

#### Problem — three confirmed defects, all in `WindowPolicy.validateMonthly()`

**F-WP-01 — Monthly day not bound to the specific method.**
`WindowPolicy.java:169-180` validates both `promediosSipsaMesMadr` and
`promedioAbasSipsaMesMadr` against the same day set (`{8,9,10,11}`), because
`validateMonthly(ZonedDateTime, boolean)` never receives the method name. Confirmed live by
`WindowPolicyTest.MonthlyWindowConfirmedBugDemonstration` (2 passing tests pin the bug; 2
`@Disabled` tests specify the fix).

**F-WP-02 — Monthly `windowKey` is the raw run date, not a stable period marker.**
`WindowPolicy.java:164`, `String key = now.format(DATE_FMT)`, always `yyyy-MM-dd`. A day-8
run and a day-9 retry of the same logical period produce different keys, breaking the
`(method_name, window_key)` idempotency guarantee. Confirmed by
`WindowPolicyTest.retryOnGraceDay_producesDifferentWindowKey_forSameLogicalPeriod`.

**F-WP-03 — Grace days skip the time check.**
`WindowPolicy.java:174,178`: `(day == 8 && !time.isBefore(monthlyStart)) || day == 9` — by
operator precedence, `day == 9` alone (any time, including midnight) returns true.
Confirmed by `WindowPolicyTest.day9_anyTime_acceptedForBothMethods_evenAtMidnight`.

---

#### Where the day/grace-day rule comes from (not silently adopted)

The exact rule requested — MesMadr: principal day 8, grace day 9; AbasMes: principal day
10, grace day 11 — is **independently corroborated by three sources already in this
repository**, not invented for this story:

1. **DANE's documented schedule** (`DANE-webservice-SIPSA.pdf`, March 2020): Mayoristas
   monthly updates on day 8; Abastecimientos monthly updates on day 10.
2. **`application.yaml:121`**, the deployed configuration's own comment:
   `monthly-run-days: ${MONTHLY_RUN_DAYS:8,10}   # Day 8 (MesMadr), Day 10 (AbasMes)`.
3. **`WindowPolicy.java`'s own pre-existing inline comment** (`validateMonthly`, currently
   unenforced by the actual conditional logic): `// Day 8 06:00 -> Day 9 23:59 (for M8)` /
   `// Day 10 06:00 -> Day 11 23:59 (for M10/Abas)`.

No other grace-day scheme (e.g., a wider multi-day window, or no grace day at all) is
documented anywhere in the codebase, commit history, or the DANE PDF. If a different grace
policy is intended, it must come from an explicit decision now, not from this story
inferring one.

---

#### Test matrix (exact cases to implement; all via the existing injected `Clock` seam)

**`promediosSipsaMesMadr` (principal day 8, grace day 9):**

| Day | Time | Expected |
|---|---|---|
| 7 | any | rejected |
| 8 | before `monthlyStart` | rejected |
| 8 | exactly `monthlyStart` | allowed |
| 8 | after `monthlyStart` | allowed |
| 9 | before `monthlyStart` | **rejected** (fixes F-WP-03 — currently allowed) |
| 9 | exactly `monthlyStart` | allowed |
| 9 | after `monthlyStart` | allowed |
| 10 | any | **rejected** (fixes F-WP-01 — currently allowed) |
| 11 | any | rejected |
| any | any, `force=true` | allowed |

**`promedioAbasSipsaMesMadr` (principal day 10, grace day 11):**

| Day | Time | Expected |
|---|---|---|
| 8 | any | **rejected** (fixes F-WP-01 — currently allowed) |
| 9 | any | rejected |
| 10 | before `monthlyStart` | rejected |
| 10 | exactly `monthlyStart` | allowed |
| 10 | after `monthlyStart` | allowed |
| 11 | before `monthlyStart` | **rejected** (fixes F-WP-03 — currently allowed) |
| 11 | exactly `monthlyStart` | allowed |
| 11 | after `monthlyStart` | allowed |
| 12 | any | rejected |
| any | any, `force=true` | allowed |

**`windowKey`:**

| Scenario | Expected |
|---|---|
| MesMadr day 8 and MesMadr day 9 retry | same key |
| AbasMes day 10 and AbasMes day 11 retry | same key |
| MesMadr vs. AbasMes, same month | different keys |
| Same method, month changes | different keys |
| Same method, December → January | different keys, correct year rollover |
| `force=true`, any day of the month | key of the **correct period** (current year-month +
  the method's own marker), not the arbitrary forced-on date |

**Mandatory:** the 2 tests currently `@Disabled` in
`WindowPolicyTest.MonthlyWindowConfirmedBugDemonstration`
(`abastecimientosMensual_diaOcho_deberiaSerRechazadoTrasElFix`,
`mayoristasMensual_diaDiez_deberiaSerRechazadoTrasElFix`) must be **re-enabled** and pass.
**No test may remain `@Disabled` when this story closes.** Daily/weekly behavior
(`promediosSipsaCiudad`, `promediosSipsaParcial`, `promediosSipsaSemanaMadr`) is unaffected
and must continue passing unchanged — `validateDaily()` is not touched by this story.

---

#### `windowKey` format — recommendation, pending confirmation before implementation

**Recommended format:** `YYYY-MM-M{principalDay}` (e.g., `2026-08-M8`, `2026-08-M10`) —
derived from the current year/month plus the method's fixed marker, **independent of which
day-of-month the run actually happened on**. This is not a new invention: it is the format
already documented (but never implemented) in `WindowPolicy.java:36`'s Javadoc and
`IngestionRun.java:37`'s Javadoc, and it matches the migration's own column comment
(`V1__initial_schema.sql:25`: `-- YYYY-MM-DD | YYYY-MM | YYYY-MM-M8`).

**Conflicting documentation found (must be reconciled, comment-only fix):**
`IngestionControlService.java:72`'s Javadoc says `"2026-01-02" for daily, "2026-01" for
monthly` (no `M8`/`M10` marker) — this is the one outlier against three other sources
(`WindowPolicy`, `IngestionRun`, `IngestionRunRepository:45`) that agree on
`YYYY-MM-M8`/`YYYY-MM-M10`. Recommend correcting this one Javadoc to match, not the other
way around.

**Confirmed before recommending this format (per this story's own requirement):**
- ✅ **DB unique constraint:** `(method_name, window_key)`, unaffected by format —
  uniqueness only ever compares full-string equality, never parses the value
  (`IngestionRunRepository.findByMethodNameAndWindowKey`,
  `countByMethodNameAndWindowKeyAndStatus`).
- ✅ **Column length:** `window_key VARCHAR(50)` — `2026-08-M8` (10 chars) fits with large
  headroom. **No migration needed.**
- ✅ **Consumers of `windowKey`, all confirmed to treat it as an opaque string (no
  parsing/regex/substring extraction anywhere in the codebase — verified by
  `grep -rn windowKey`):** `IngestionContext` (log summary only), `IngestionJob` (pass-through
  + MDC + audit), `IngestionControlService`/`IngestionRunRepository` (exact-match queries
  only), `CreateRunRequest`/`AuditEventRequest` (internal DTOs, embed the string in
  human-readable audit messages, never parse it), `IngestionRunResponse` /
  `IngestionRunDetailResponse` (**public-facing** — `GET /api/internal/ingestion/runs`,
  `/runs/{runId}`, `/running` — expose `windowKey` as an opaque string field; no known
  consumer parses its structure, but this is the one surface where an *external* client
  could theoretically depend on the old format — flagged, not blocking).
- ✅ **Logs/audit:** `IngestionJob`'s SLF4J/MDC logging and `AuditEventRequest`'s
  human-readable messages both interpolate the string as-is — no format assumption.

---

#### Historical / existing `window_key` compatibility — no data migration proposed

Per this story's explicit constraint, **no existing data will be modified**. Analysis:

- **No collision risk.** New format (`YYYY-MM-M8`/`M10`, contains a literal `M`) is
  structurally distinct from the old raw-date format (`YYYY-MM-DD`, three numeric groups)
  and from daily methods' keys (different `method_name`, so the compound unique constraint
  never confuses them regardless of string shape).
- **Old rows remain as historical artifacts.** A pre-fix `SUCCEEDED` run for
  `promediosSipsaMesMadr` with `window_key = "2026-07-08"` simply stays in the table;
  nothing reads it expecting the new format.
- **One real transition-month risk (operational, not a data-integrity risk):** if a monthly
  method already ran `SUCCEEDED` **this month** under the **old** key format before the fix
  is deployed, the new code will compute a **new** key (`YYYY-MM-M8`) that does not match
  the old row, so `isRunComplete()` returns `false` and the system will allow (or the cron
  will trigger) a **second** ingestion of the same logical period in that one transition
  month. This is not a correctness catastrophe — `SipsaMayoristasMensual` and
  `SipsaAbastecimientosMensual` are upserted, not duplicated, per their existing upsert
  strategy — but it does mean one extra DANE SOAP call, one extra `IngestionRun` audit row,
  and duplicate audit-trail entries for that single month. **Mitigation (operational, not
  code):** prefer deploying this fix shortly **after** a monthly window has already
  completed for the current cycle (i.e., not on days 8–11), so the transition does not land
  inside an active monthly period. This should be called out in the PR description when
  TECH-111 is implemented, not solved in code.
- **No backfill, no `UPDATE` statement, no new migration file is proposed by this story.**
  If historical `window_key` normalization is ever wanted (e.g., for reporting
  consistency), that is explicitly a **separate, future story** — not authorized here.

---

#### Alternatives considered

**Alternative A — Explicit per-method rule map inside `WindowPolicy` (recommended).**
Conceptually:
```
method name (matched the same way isMonthlyMethod() already does)
  -> { principalDay, graceDay, windowKeySuffix }
```
`validateMonthly` receives the resolved rule (or the method name) instead of validating
generically; the time check (`!time.isBefore(monthlyStart)`) is applied explicitly and
identically to both `principalDay` and `graceDay`, removing the F-WP-03 operator-precedence
trap entirely (no implicit `&&`/`||` chaining). `windowKey` is built from
`(now.getYear(), now.getMonthValue(), rule.windowKeySuffix())`, never from `now.getDayOfMonth()`.

- **Pros:** Fixes all three defects without touching `IngestionHandler`, the SOAP layer, or
  any REST/DB contract. Fully containable inside `WindowPolicy.java` (plus Javadoc fixes in
  3 other files). Matches this story's minimal-scope mandate.
- **Cons:** The method-name-to-rule mapping is still string-based (same matching style as
  today's `isMonthlyMethod()`), not compiler-enforced — a future third monthly method with
  an unrecognized name would need an explicit new map entry (recommend: fail fast with a
  clear `SipsaConfigurationException` if a method is classified monthly but has no rule
  entry, rather than silently falling back to shared/no validation — this preserves the
  safety property this story is fixing, for any future method too).

**Alternative B — Add `publicationSchedule()`/`isMonthly()` metadata to `IngestionHandler`.**
Moves the day/grace-day/key-marker declaration onto each handler
(`MesIngestionHandler`, `AbasIngestionHandler`), read by `WindowPolicy` via the handler
registry instead of string matching.

- **Pros:** Eliminates method-name string matching entirely, for both this bug and the
  adjacent, already-tracked TECH-055/ADR-006 concern (`isMonthly()` classification).
  Compiler-enforced: every handler must declare its schedule.
- **Cons:** Changes the `IngestionHandler` contract — all 5 handlers must be touched (3
  return "not monthly", 2 declare their schedule). Requires ADR-006 to move from `Proposed`
  to `Accepted` first, per this repository's own rule (`AGENTS.md`: "implementing a story
  whose corresponding ADR is in Proposed state" requires the ADR to be accepted first).
  Broader blast radius for a story whose actual defect is fully contained inside one method
  of one class.

**Decision: Alternative A.** It fully fixes F-WP-01, F-WP-02, and F-WP-03 without expanding
scope beyond `WindowPolicy`, and does not require ADR-006 to be decided first.

---

#### Dependency on TECH-055 / ADR-006 — explicitly NOT required

**TECH-111 does not depend on TECH-055 or ADR-006.** Reasoning:
- Only 2 monthly methods exist today, both already explicitly named in
  `application.yaml`'s own comment and in `WindowPolicy`'s existing (unenforced) inline
  comment — an explicit 2-entry map inside `WindowPolicy` is sufficient and proportionate,
  not a workaround.
- TECH-055/ADR-006 addresses a **different, adjacent** problem (daily-vs-monthly
  *classification* generalizing to future handlers via string matching,
  `technical-debt.md` item A-02) — real, but not what causes F-WP-01/02/03. Fixing F-WP-01
  does not require resolving A-02 first.
- **When to revisit:** if a **third** monthly method with a genuinely different
  publication schedule is ever added, that is the trigger to reopen ADR-006 and migrate the
  per-method rule map (and `isMonthlyMethod()` itself) onto `IngestionHandler` — not before.

---

#### Open design decision to confirm before/at implementation start (not silently decided here)

`sipsa.ingestion.monthly-run-days` (default `"8,10"`) is currently parsed as one flat
`Set<Integer>` shared across both methods — the exact mechanism this story removes from the
*validation* path. Per "contracts that must be preserved" (property names must not change),
**the property name stays**, but its *role* must be decided:
- **Recommended:** repurpose it as a **startup sanity check only** (e.g., assert `{8,10}`
  is a subset of the configured set at construction time, failing fast on misconfiguration)
  while the actual per-method day binding becomes an explicit, code-level fact (matching
  the DANE-contractual nature of these dates — they are not meant to be casually
  reconfigured per environment).
- **Alternative:** leave `monthly-run-days` fully unused/vestigial and document why in its
  Javadoc.

This should be confirmed (or delegated to the implementer's judgment, explicitly) before
`fix/window-policy-monthly-rules` starts.

> **Decision (2026-07-14, confirmed by the team before implementation):** the
> **recommended** option was adopted. `monthly-run-days` keeps its name and is now a
> startup sanity check only: `WindowPolicy`'s constructor fails fast with
> `SipsaConfigurationException` if the configured set does not contain the contractual
> principal days `{8, 10}` required by the code-level per-method rules. It no longer
> participates in per-run validation.

---

#### Contracts that MUST NOT change

- Cron expressions (`sipsa.ingestion.cron.daily/monthly-mes/monthly-abas`) and their
  defaults — **confirmed correct by TECH-110, untouched by this story.**
- `America/Bogota` as the configured zone, and how it is resolved (`sipsa.timezone`).
- All REST routes, request/response JSON shapes (`windowKey`'s *type* stays `String`; only
  its *value format* for monthly methods changes going forward).
- Database schema — no migration (`VARCHAR(50)` already sufficient).
- Property names (`sipsa.ingestion.daily-window-start`, `daily-window-end`,
  `monthly-run-days`, `monthly-window-start`, `sipsa.timezone` — all retained, see the open
  decision above for `monthly-run-days`'s role).
- SOAP integration — untouched, `WindowPolicy` has no SOAP dependency.
- `force=true` semantics — still bypasses the window check entirely for both daily and
  monthly; still returns a key (now the correct period key for monthly, not the arbitrary
  forced-on date).
- Daily/weekly method behavior (`promediosSipsaCiudad`, `promediosSipsaParcial`,
  `promediosSipsaSemanaMadr`) — `validateDaily()` is not modified by this story.

---

#### Files that would be modified (implementation not started)

| File | Change |
|---|---|
| `src/main/java/.../application/ingestion/core/WindowPolicy.java` | Core fix: per-method rule resolution in `validateMonthly`, explicit time check for both principal and grace day, `windowKey` built from year/month/marker instead of the raw date |
| `src/main/java/.../domain/entity/IngestionRun.java` | Javadoc only — already correct, verify still accurate after the fix |
| `src/main/java/.../application/service/IngestionControlService.java` | Javadoc only — fix the outlier `"2026-01"` example to match `YYYY-MM-M8`/`M10` |
| `src/main/java/.../infrastructure/persistence/repository/IngestionRunRepository.java` | Javadoc only — already correct, verify still accurate |
| `src/test/java/.../application/ingestion/core/WindowPolicyTest.java` | Re-enable the 2 `@Disabled` tests; add the full test matrix above (new day-9/day-11 time-boundary cases, new day-10/day-8 per-method rejection cases, updated `windowKey` cases) |
| `docs/architecture/scheduled-ingestion-validation.md` | Update F-WP-01/02/03 status from "confirmed, not fixed" to "fixed", with a pointer to this story |
| `docs/backlog/technical-backlog.md` | Mark TECH-111 `Done`, fill in `Completed` |
| `CHANGELOG.md` | `[Unreleased]` entry under `Fixed` |

**Explicitly NOT modified:** any DTO, controller, migration, `SchedulingConfig`,
`SipsaIngestionScheduler`, or any file outside the list above.

---

#### Risks

| Risk | Assessment |
|---|---|
| Manual/forced executions previously accepted on the "wrong" day (e.g., AbasMes on day 8) will now be rejected without `force=true` | **Intentional** — this is the fix. No known legitimate workflow relies on the current cross-acceptance (confirmed in the TECH-110 investigation: no test, ADR, or comment defends it). Operationally, anyone who was relying on it must add `force=true`. |
| Historical rows with the old `YYYY-MM-DD` monthly key coexist with new `YYYY-MM-M8`/`M10` rows | No collision (see compatibility analysis above); no migration proposed. |
| Idempotency during the deploy transition month | One-time possible redundant re-ingestion of the current month's data if deployed mid-window (see analysis above); mitigated by upsert strategy + suggested deploy timing, not by code. |
| Audit-trail queries (`IngestionAuditController`, `AuditTrailService`) | No impact — they query by `requestId`/`runId`, never by `windowKey` pattern (confirmed by code inspection). |
| Collision with existing records | None possible — compound unique constraint + structurally distinct formats (see above). |
| `monthly-run-days` property's role changes from "the rule" to "a sanity check" | Property name preserved; semantic role change is a judgment call flagged above for confirmation, not silently decided. |
| Scope creep into TECH-055/ADR-006 | Explicitly avoided — see dependency analysis above. |
| Scope creep into ADR-008 (timezone/locale) | Explicitly avoided — see below. |

---

#### ADR-008 — untouched

[ADR-008](../adr/ADR-008-timezone-locale-and-date-semantics.md) remains `Proposed`. TECH-111
fixes confirmed calendar/idempotency defects in `WindowPolicy` only. It does **not** decide,
imply, or depend on any resolution of ADR-008's open questions (response timezone,
locale/i18n, canonical temporal types, JSON serialization, `X-Timezone` handling). Nothing
in this plan touches `TimezoneFilter`, `TimezoneUtil`, or any API response DTO's temporal
field types.

---

#### Planned commit sequence (for when implementation is approved)

1. `fix(window-policy): bind monthly day and grace day to the specific ingestion method`
   — the core `WindowPolicy.java` change (F-WP-01 + F-WP-03 together, since both live in
   the same conditional block).
2. `fix(window-policy): derive monthly windowKey from year, month, and method marker`
   — the `windowKey` format change (F-WP-02).
3. `test(window-policy): re-enable and extend monthly rule tests for TECH-111`
   — re-enable the 2 `@Disabled` tests, add the full new test matrix.
4. `docs(window-policy): update window key format and mark TECH-111 done`
   — Javadoc corrections (`IngestionControlService`, verify `IngestionRun`/
   `IngestionRunRepository`), `scheduled-ingestion-validation.md`, backlog, CHANGELOG.

(Commits 1–2 could be squashed into one if the reviewer prefers — both are small,
same-file, same-root-cause changes. Kept separate above because F-WP-01/03 and F-WP-02 are
independently testable and independently revertable.)

**Acceptance Criteria:**
- [x] All test matrix cases above implemented and passing.
- [x] The 2 currently-`@Disabled` tests are re-enabled and pass; zero `@Disabled` tests
      remain in `WindowPolicyTest`.
- [x] `windowKey` for monthly methods follows `YYYY-MM-M{principalDay}`, stable across a
      principal-day/grace-day retry of the same period, and correct across month/year
      rollover.
- [x] No change to cron expressions, zone, REST contracts, JSON shapes, DB schema, or
      property names.
- [x] `./mvnw clean verify` passes with zero failures and zero skips introduced by this
      story.
- [x] `docs/architecture/scheduled-ingestion-validation.md` updated to reflect F-WP-01/02/03
      as fixed.
- [x] ADR-008 left untouched, still `Proposed`.

**Completed:** 2026-07-14, branch `fix/window-policy-monthly-rules`, merged via
[PR #15](https://github.com/dalejandrov/sipsa/pull/15). Four commits:
per-method day/grace-day binding with the time gate on both days (F-WP-01 + F-WP-03,
including the `monthly-run-days` startup sanity check per the confirmed decision above),
stable `YYYY-MM-M8`/`YYYY-MM-M10` window keys (F-WP-02), re-enabled and extended tests
(including an explicit `abas`-before-`mesmadr` rule-resolution-order test, since
`promedioAbasSipsaMesMadr` contains both name fragments), and documentation updates.
`WindowPolicyTest`: 34 tests, 0 failures, 0 skips. **Operational note for deployment:** if
a monthly method already ran `SUCCEEDED` in the current month under the old raw-date key,
the new period key will not match it and one redundant (upsert-safe) re-ingestion of that
period can occur — deploy outside days 8–11 to avoid the transition landing inside an
active monthly window.

---

### TECH-120

**Title:** Continuous integration pipeline (GitHub Actions)
**Type:** Infrastructure
**Priority:** High
**Phase:** —
**Status:** **Done** — implemented 2026-07-14 on branch `ci/github-actions`
**Complexity:** S
**Branch:** `ci/github-actions`

**Origin:** Formalizes **PEND-CI-001** from the 2026-07-14 documentation/pending-work
inventory. The gap was first recorded as post-migration recommendation #3 in
[docs/migrations/spring-boot-4-java-25.md](../migrations/spring-boot-4-java-25.md) and
noted in the CHANGELOG (".github/ — no CI workflow exists yet").

**Problem:**
No CI pipeline existed. `./mvnw clean verify` and the Flyway migration gate
(ADR-009, `FlywayMigrationsTest`) ran only when a developer remembered to run them
locally; nothing prevented merging a PR with failing tests. Worse, the migration gate
self-skips without Docker, so its coverage silently depended on each developer's local
setup.

**Solution:**
`.github/workflows/ci.yml` — a single `verify` job on `ubuntu-latest`:
- Triggers on every `pull_request` and on `push` to `main`.
- Temurin JDK 25 with built-in Maven dependency cache (`actions/setup-java`), Maven
  Wrapper for the build (single source of truth for the Maven version).
- Runs `./mvnw --batch-mode --no-transfer-progress clean verify` — the same command the
  development workflow mandates locally.
- Testcontainers uses the runner's preinstalled Docker: `FlywayMigrationsTest` runs
  against a real PostgreSQL 18 container in CI, unlike Docker-less laptops.
- A dedicated guard step parses the surefire report and **fails the build if the Flyway
  migration gate was skipped** (tests=0 or skipped>0), so the self-skip behavior can
  never silently void the gate in CI.
- Cancels superseded in-flight runs of the same branch/PR (`concurrency` +
  `cancel-in-progress`).
- `permissions: contents: read` (least privilege); no secrets, no `.env`, no credentials.
- On failure, uploads `target/surefire-reports/` and `target/failsafe-reports/` as a
  `test-reports` artifact (7-day retention) for diagnosis.

**Design decision — single job, not split build/test jobs:** the full verify takes ~1–3
minutes; splitting build and tests would duplicate the Maven build or require artifact
hand-off between jobs, adding maintenance surface with no feedback-time benefit at this
scale. Revisit if the suite grows past ~10 minutes or gains independent long-running
stages (e.g., SOAP contract tests behind WireMock).

**Acceptance Criteria:**
- [x] Workflow is valid YAML and uses only stock GitHub-hosted runner features.
- [x] `./mvnw clean verify` runs on GitHub Actions via the Maven Wrapper on Java 25.
- [x] Testcontainers tests execute against the runner's Docker; a guard step fails the
      pipeline if `FlywayMigrationsTest` is skipped.
- [x] No secrets or environment files are referenced anywhere in the workflow.
- [x] A failing test fails the pipeline (Maven non-zero exit propagates to the job).
- [x] Runs on every PR and on pushes to `main`; superseded runs are cancelled.
- [x] `GITHUB_TOKEN` restricted to `contents: read`.
- [x] Documentation updated: CONTRIBUTING.md (CI gate section),
      development-workflow.md (Step 6 note), CHANGELOG.md, and the migration notes'
      post-migration recommendation #3 marked resolved.

**Completed:** 2026-07-14, branch `ci/github-actions`, merged via
[PR #16](https://github.com/dalejandrov/sipsa/pull/16). First `main` run green on
2026-07-15: `./mvnw clean verify` passed on GitHub Actions with `FlywayMigrationsTest`
executing against real PostgreSQL 18 (`tests=4`, `skipped=0` — the migration-gate guard
step confirmed the suite ran).

---

### TECH-130

**Title:** Cognito resource server, scopes and app clients
**Type:** Infrastructure / Security
**Priority:** High
**Phase:** —
**Status:** Pending
**Complexity:** M
**Branch:** — (infrastructure work; IaC location to be defined — likely a separate repository)

**Origin:** [ADR-002](../adr/ADR-002-internal-endpoint-security.md) (Accepted, Option E),
layer 2. The application side (Resource Server, TECH-001) is already implemented and
validated against a local mock OIDC issuer (e2e re-validated 2026-07-15 post-merge, 9/9
green); this story provisions the real identity provider.

**Scope:**
- Cognito user pool for the SIPSA platform (dev and prod).
- Resource server `sipsa` declaring the custom scopes:
  `sipsa/ingestion.execute`, `sipsa/ingestion.cancel`, `sipsa/ingestion.read`,
  `sipsa/audit.read`.
- One `client_credentials` app client per machine-to-machine integration, each authorized
  only for the scopes it needs. Client secrets live in the consumer's secret store, never
  in this repository.
- App client (authorization code + hosted UI) for human operators.
- Document the issuer URI per environment; the backend consumes it via
  `SIPSA_JWT_ISSUER_URI` and optionally pins clients via `SIPSA_JWT_ALLOWED_CLIENT_IDS`.

**Acceptance Criteria:**
- [ ] A token obtained via `client_credentials` from the dev pool authorizes the matching
      `/api/internal/**` operation against a deployed backend (401/403/2xx matrix passes).
- [ ] Scopes not granted to a client are rejected with `403`.
- [ ] No secret is committed to this repository.

**Completed:** —

---

### TECH-131

**Title:** API Gateway — API keys, usage plans, throttling and access logs
**Type:** Infrastructure / Security
**Priority:** High
**Phase:** —
**Status:** Pending
**Complexity:** M
**Branch:** — (infrastructure work)

**Origin:** ADR-002 (Accepted, Option E), layer 1.

**Scope:**
- API Gateway as the single public entry point for `/api/sipsa/**` (per-consumer API
  keys, usage plans — e.g. `basic` 10 rps / 100k req/month, `partner` 50 rps / 1M — 429 on
  quota, revocation, per-key consumption metrics) and for `/api/internal/**` (JWT
  authorizer against the TECH-130 pool, or IAM/SigV4 routes for AWS-native automation; no
  API key requirement on admin routes).
- Access logs with `apiKeyId`/`clientId` per request.
- **Actuator is not routed** through the gateway (ADR-002 §5).
- API keys are identification and metering only — never authentication (ADR-002).

**Acceptance Criteria:**
- [ ] `GET /api/sipsa/**` without an API key is rejected at the gateway; with a key it is
      forwarded and metered against the key's usage plan.
- [ ] `/api/internal/**` requires a valid Cognito JWT (or IAM signature) at the gateway
      *and* is re-validated by the backend.
- [ ] `/actuator/**` is not reachable through the gateway.
- [ ] Exceeding a usage plan returns `429` from the gateway.

**Completed:** —

---

### TECH-132

**Title:** Private networking — ECS, VPC Link, internal ALB, gateway-bypass prevention
**Type:** Infrastructure / Security
**Priority:** High
**Phase:** —
**Status:** Pending
**Complexity:** M
**Branch:** — (infrastructure work)

**Origin:** ADR-002 (Accepted, Option E), layer 4.

**Scope:**
- ECS service in private subnets, no public IP; internal ALB; API Gateway reaches the
  service exclusively via VPC Link. ALB security group admits only the VPC Link ENIs.
- `/actuator/health` used by the ALB target group inside the VPC.
- Decide the metrics path (Prometheus scrape with token inside the VPC, ADOT sidecar, or
  CloudWatch) — `/actuator/prometheus` requires a valid token per ADR-002 §5.
- This layer is the enforcing control for IAM-authorized routes (SigV4 cannot be
  re-validated by the application) and eliminates metering evasion on the public data API.

**Acceptance Criteria:**
- [ ] The backend has no publicly routable address; requests bypassing API Gateway do not
      reach it.
- [ ] Healthchecks and deployments remain functional through the internal path.
- [ ] The metrics collection path is decided and documented.

**Completed:** —

---

### TECH-113

**Title:** Fix `artiId`/`muniId` filters of `GET /api/sipsa/parcial`
**Type:** Bug
**Priority:** Medium
**Status:** Pending
**Complexity:** S
**Branch:** `fix/parcial-query-filters`
**Origin:** H-2/H-3 of the [SipsaParcial integrity SPIKE](../architecture/sipsa-parcial-data-integrity-spike.md).

**Problem:** `SipsaReadService.getParcial()` filters on attribute `artiId`, which does not
exist on `SipsaParcial` (→ `IllegalArgumentException`/HTTP 500 when a client uses the
parameter), and `ParcialQueryRequest.muniId` is a `@Positive Long` while the entity/DB
column is a `String` DIVIPOLA code (type mismatch; leading zeros unfilterable).

**Acceptance Criteria:**
- [x] `GET /api/sipsa/parcial?artiId=…` filters by `idArtiSemana` and returns `200` —
      contract decision documented: `idArtiSemana` is canonical, `artiId` stays as a
      validated compatibility alias (equal values collapse; conflicting values → `400`).
- [x] `muniId` filters as text, accepting DIVIPOLA codes with leading zeros (`05001` ≠
      `5001`; trimmed; blank or >50 chars → `400`; never converted to a number).
- [x] Regression tests cover both filters: `ParcialQueryRequestTest` (11 unit cases) and
      `ParcialQueryFilterIntegrationTest` (9 HTTP cases against real PostgreSQL via
      Testcontainers, fixtures with `"05001"` and `"5001"` as distinct municipalities).

**Completed:** 2026-07-16, branch `fix/sipsa-parcial-query-filters`. No schema change
(the columns were always correct); fix confined to DTO, service, documentation and
tests. Suite: 136 tests, 0 failures. Article-only filtering still seq-scans (no index
starts with `id_arti_semana`) — functionally correct, tracked as part of TECH-124.

---

### TECH-114

**Title:** Strict `enmaFecha` parsing with explicit rejection (H-1)
**Type:** Correctiva
**Priority:** Medium
**Status:** **Done** — implemented within TECH-011 (2026-07-16)
**Origin:** H-1 of the integrity SPIKE. TECH-012's local execution showed the risk does
**not** materialize with current real DANE data (0 unparseable dates in 676,210 records),
so this hardening is preventive: `ParcialIngestionHandler.parseDate` remains strict
ISO-8601-instant-only and the handler now **rejects** (audited) any record whose date
cannot be parsed — a zoneless `xs:dateTime` is never interpreted in an implicit zone.

**Completed:** 2026-07-16, within `fix/sipsa-parcial-data-integrity` (test:
`ParcialIngestionHandlerTest.zonelessDateIsRejected`).

---

### TECH-115

**Title:** Backfill/consolidation of a pre-existing external `sipsa_parcial` database
**Type:** Datos
**Priority:** Medium
**Status:** **Conditional** — activate only if an external historical database is
confirmed to exist (the remaining half of TECH-012). No such database is currently known.

**Scope if activated:** execute the read-only diagnostics remotely per the
[runbook](../diagnostics/tech-012-runbook.md); then apply its Part II transition plan
(Alternative A vs B, operational job vs Flyway per the documented criteria). The
application code already deduplicates against legacy UUID rows at ingestion time, so the
backfill is about storage/consistency of the historical rows, not about preventing new
duplicates.

**Completed:** —

---

### TECH-116

**Title:** Disable `baseline-on-migrate` after per-environment Flyway history inventory
**Type:** Config
**Priority:** Low
**Status:** Pending
**Origin:** ADR-009 rule 6 follow-up, formalized during the TECH-012 preparation.

**Acceptance Criteria:**
- [ ] Inventory (per the runbook's annex queries) confirms every real environment has
      correct Flyway history and no baselined non-empty schemas.
- [ ] `baseline-on-migrate: false` applied in a dedicated PR (never mixed with TECH-011).

**Completed:** —

---

### TECH-117

**Title:** Handle concurrent `SipsaParcial` duplicate insertion safely
**Type:** Correctiva
**Priority:** Medium
**Status:** Pending
**Origin:** TECH-011 final review (2026-07-16). Current behavior under a lookup→insert
race between two concurrent executions of the same publication: both observe absence,
both insert, the `key_hash UNIQUE` constraint rejects one — the losing batch's
`DataIntegrityViolationException` propagates, the batch transaction rolls back, and the
handler rethrows: **the losing run fails** instead of recording the row as skipped. No
data corruption is possible (the constraint holds), but the run outcome is wrong. Today's
deployment is single-instance with a single scheduler, so the race is not reachable in
practice — this is a prerequisite for any multi-instance rollout (with ShedLock or an
equivalent also to be evaluated then).

**Acceptance Criteria:**
- [x] Two concurrent executions of the same publication → one inserts, the other records
      `skipped`, neither run fails.
- [x] The collision path never throws: instead of a constraint-violation fallback, the
      insert itself is atomic — `INSERT … ON CONFLICT (key_hash) DO NOTHING` in a single
      JDBC batch (repository fragment `SipsaParcialBatchInsertRepository`/`…Impl`); the
      per-row JDBC update count (1/0) feeds `inserted`/`skipped`, so metrics stay
      coherent (`items == inserted + skipped` per batch) and the transaction never goes
      rollback-only. Alternative A (catch-after-`saveAll`) was demonstrated unusable by
      the reproduction test: the flush violation marks the transaction rollback-only and
      discards the batch's non-conflicting rows. Advisory/table locks were discarded
      (cost, lost concurrency, no need — the constraint plus `ON CONFLICT` already
      serialize per-key inside PostgreSQL).
- [x] Concurrency tests (Testcontainers, real PostgreSQL 18, deterministic interleaving
      via an uncommitted-insert hold plus `pg_stat_activity` lock observation):
      single-key race (1+1), identical batches (5+5), partial overlap ({A,B,C} vs
      {B,C,D} — D preserved), intra-batch duplicate counted once, legacy-UUID row
      deduplicated with the stored row untouched, retry all-skip, post-collision batch
      proving the transaction survives; plus two real overlapping `GenericIngestionJob`
      executions (the endpoint's code path) — audit shows 2× `INGESTION_SUCCEEDED`,
      0× `INGESTION_FAILED`, one copy per key. Note: two byte-simultaneous triggers are
      serialized earlier by `uq_ingestion_runs_window` at run creation; TECH-117 covers
      the overlapping-execution window that force-restart opens.

**Completed:** 2026-07-19, branch `fix/sipsa-parcial-concurrent-dedup`. No migration
(V1–V4 untouched; the existing `sipsa_parcial_key_hash_key` constraint is the conflict
target). 16 parameters per single-row statement in one JDBC batch — every batch size
stays far below the 32,767-per-statement driver limit, no sub-batching needed. IDs are
deliberately not returned (entities discarded after flush); conflicting rows keep the
`ingestion_run_id` of the first inserter. TECH-011 idempotence, TECH-113 filters and
TECH-124 index untouched. Multi-instance scheduling coordination (ShedLock or
equivalent) remains a separate prerequisite for horizontal scaling.

---

### TECH-118

**Title:** Align `SipsaParcial` decimal precision (JPA `precision=15,2` vs DDL `NUMERIC(19,2)`)
**Type:** Config
**Priority:** Low
**Status:** Pending
**Origin:** TECH-011 schema review. Hibernate `validate` does not compare precision, so
nothing fails; real data observed (676,210 rows) ranges 230.00–22,000.00 with ≤2 decimals
— both definitions are far above the real range, no truncation risk. Cosmetic drift only:
pick one source of truth (recommend annotating the entity to match the DDL, `19,2`) for
all three price columns. XSD declares plain `xs:decimal` (no bound).

**Completed:** —

---

### TECH-119

**Title:** Remove redundant `idx_sipsa_parcial_key_hash` index
**Type:** Performance
**Priority:** Low
**Status:** **Done** (2026-07-16)
**Origin:** TECH-011 index inventory. `key_hash` is indexed twice: the implicit unique
index of the `UNIQUE` constraint (`sipsa_parcial_key_hash_key`, 80 MB at 676K rows) and
the explicit non-unique `idx_sipsa_parcial_key_hash` (80 MB, created by V1). One of the
two is pure write/storage overhead on every insert. Dropping the explicit one requires a
new migration (never editing V1) and a check that no query names it explicitly.

**Completed:** 2026-07-16, branch `fix/remove-redundant-parcial-key-hash-index`,
migration `V3__drop_redundant_parcial_key_hash_index.sql` (transactional `DROP INDEX`,
no `IF EXISTS`; rationale in the script). Verified: no code/test/script references the
index name (grep); V1→V2→V3 from empty base and V2→V3 upgrade with data preserved
(`ParcialKeyHashIndexMigrationTest`); `UNIQUE (key_hash)` still rejects duplicates
post-V3; hash lookups use `sipsa_parcial_key_hash_key` with identical plan cost; live
upgrade on the real 676K local base in 8 ms with −80 MB of indexes (254→174 MB) and a
subsequent full idempotent re-ingestion (`inserted=0, skipped=676,210`). Suite: 138
tests, 0 failures.

---

### TECH-122

**Title:** Harden `SipsaParcial` natural-key constraints
**Type:** Datos
**Priority:** Low
**Status:** Pending — contract phase; **gated on TECH-012's external half** (no
constraint can assume UUID-era rows are gone until the external-database question closes)
**Scope when activated:** `NOT NULL` on the five key columns (real data shows 0 nulls),
`CHECK (muni_id <> '')`, optionally `CHECK` on non-negative prices (0 negatives observed;
confirm contract first), and — only after all rows carry deterministic hashes — a natural
unique constraint replacing the hash as enforcement point if the team prefers. Apply via
expand–migrate–contract with `NOT VALID` + `VALIDATE CONSTRAINT` where applicable.

**Completed:** —

---

### TECH-123

**Title:** Add `first_seen_at`/`last_seen_at` republication traceability to `SipsaParcial`
**Type:** Observabilidad
**Priority:** Low
**Status:** Optional — **not recommended now.** Skip-first currently performs zero writes
for re-published rows; maintaining `last_seen_at` would turn every full DANE republication
into ~676K UPDATEs per daily run (the exact write amplification TECH-011 just removed).
The republication signal already exists cheaply at run granularity: `ingestion_runs` +
the `skipped` metric in logs. Activate only if per-row republication evidence becomes a
real requirement; consider then whether it belongs in a side table instead.

**Completed:** —

---

### TECH-124

**Title:** Optimize `SipsaParcial` article-filter queries
**Type:** Performance
**Priority:** Low
**Status:** **Done**
**Branch:** `perf/sipsa-parcial-article-filter-index` (migration V4)

**Problem (measured, not assumed):** `GET /api/sipsa/parcial?idArtiSemana=…` (alias
`artiId` — same Specification, verified by SQL capture) had no index leading with
`id_arti_semana`. On the real DANE dataset (677,061 rows, 36 articles, ~2.8% typical
selectivity) the first page was already sub-millisecond via a backward walk of
`idx_sipsa_parcial_fecha`, but the **per-page count query** — Hibernate emits
`count(id)`, not `count(*)` — ran as a full-table Parallel Seq Scan (~17–28 ms on every
page request, flat across cardinalities), and a non-existent article seq-scanned the
whole table (~18 ms).

**Decision:** `idx_sipsa_parcial_article_date (id_arti_semana, enma_fecha DESC)
INCLUDE (id)` — the only measured shape that makes the count an Index Only Scan
(0.5–2.3 ms, `Heap Fetches: 0`; covering `id` is mandatory because of `count(id)`), while
the `enma_fecha DESC` key column matches the endpoint's default ordering. Alternatives
`(id_arti_semana)`, `(id_arti_semana, enma_fecha DESC)` without INCLUDE (count unchanged
— each article's rows are scattered across ~all heap pages) and
`(id_arti_semana, muni_id, enma_fecha DESC)` (only helps a case that is already 0.5 ms)
were measured with real temporary indexes and discarded. Cost: 26 MB, ~0.2 s creation,
~+0.55 ms per 500-row batch; all-skip reingestion unaffected. Deep-page OFFSET cost
(~23–31 ms at page 1000) is OFFSET-inherent and out of scope — keyset pagination gets a
story only if consumers systematically page past ~page 100 or volume grows ~5×.
Evidence, plans and re-evaluation thresholds:
[tech-124-article-filter-analysis.md](../diagnostics/tech-124-article-filter-analysis.md).

**Completed:** 2026-07-18. V4 tested from clean base (`FlywayMigrationsTest` V1→V4) and
as an upgrade with data (`ParcialArticleQueryIndexMigrationTest`, 60K rows; live upgrade
on the real 677K-row local base in 197 ms). No API contract change (TECH-113 untouched).

---

### TECH-125

**Title:** Define retention policy for `SipsaParcial` and ingestion metadata
**Type:** Datos
**Priority:** Low
**Status:** Pending decision
**Scope:** distinguish functional retention (`sipsa_parcial` — fully reconstructible from
DANE, which republishes its complete history on every call), audit retention
(`ingestion_audit`, `ingestion_rejects`), and operational retention (`ingestion_runs`,
logs). No automatic deletion is implemented or proposed until the team defines
requirements; growth is currently bounded by deduplication (~340 rows/day net).

**Completed:** —

---

### TECH-133

**Title:** Centralize and validate monthly ingestion window configuration  
**Type:** Config  
**Priority:** Low  
**Status:** **Done**  
**Complexity:** S  
**Branch:** `fix/unify-monthly-ingestion-window-config`
**Dependencies:** TECH-071 (extends the `IngestionProperties` class it introduced).

**Problem (historical):**
`WindowPolicy` carried `@Value("${sipsa.ingestion.monthly-window-start:06:00}")` while
`application.yaml` set `14:00`. The `06:00` fallback was never effective (the YAML key was
always present) but promised a different behavior on any deployment missing the YAML, and
the format was only validated implicitly by `LocalTime.parse` inside the policy
constructor. Already flagged as F-SC-01 context in
`docs/architecture/scheduled-ingestion-validation.md` and as an out-of-scope finding of
TECH-071.

**Functional semantics (confirmed before changing anything):**
`monthly-window-start` is an **authorization gate**, not a scheduler time: on a monthly
method's publication day (MesMadr: 8, Abas: 10) or its grace day (9/11), a run is
authorized only at or after this time of day in the `sipsa.timezone` zone
(`America/Bogota`, fixed per ADR-008). The monthly crons fire at 14:30 — after the 14:00
gate — and `force=true` bypasses the gate while preserving the stable period key.
`WindowPolicy` already used an injectable `Clock` (TECH-110/111) pinned to the configured
zone; it never consults `ZoneId.systemDefault()`.

**Canonical value decision:** `14:00` retained — it is the value `application.yaml` has
always made effective, consistent with DANE's ~14:00 COT publication and the 14:30 crons.
Local `ingestion_runs` history contains no monthly executions (only a manual Ciudad smoke),
so there was no operational evidence justifying a functional change; the never-effective
`06:00` fallback was removed. **Effective behavior change: none.** Any different gate time
remains a business decision to validate separately.

**Acceptance Criteria:**
- [x] `monthly-window-start` has a single typed source (`IngestionProperties.monthlyWindowStart`,
      `LocalTime`, canonical default 14:00 as `DEFAULT_MONTHLY_WINDOW_START`).
- [x] No divergent local `@Value` remains (`WindowPolicy` injects `IngestionProperties`).
- [x] The effective value did not change (14:00 before via YAML, 14:00 after; pinned by tests).
- [x] Format validated at startup: `24:00`, `14:99`, non-time text abort the context naming
      the property; an explicitly empty value behaves as unset under Spring's standard
      binding (canonical default applies — pinned by a dedicated test; the Compose
      passthrough `${VAR:-14:00}` additionally replaces empty env values before Spring).
- [x] Timezone explicit: reuses the canonical `sipsa.timezone` (`America/Bogota`, ADR-008);
      invalid zones fail at construction; no `ZoneId.systemDefault()` anywhere in the policy.
- [x] `WindowPolicy` keeps its injectable `Clock`; boundaries proven with `Clock.fixed`
      (10:29/10:30/10:31 on an overridden gate, wrong-day rejection, UTC-vs-Bogota same
      instant, `force=true` bypass).
- [x] Docker override works: `INGESTION_MONTHLY_WINDOW_START` passthrough added to
      `docker-compose.yml`; verified 14:00 default and 10:30 override in container logs.
- [x] Operational documentation updated (README, `.env.example`, `application.yaml` comments).

**Completed:** 2026-07-17, branch `fix/unify-monthly-ingestion-window-config`.
`./mvnw clean verify`: 168 tests green. Startup logs a single safe confirmation pair:
`Monthly ingestion window start = <HH:mm>` (IngestionProperties) and
`Monthly ingestion timezone = <zone>` (WindowPolicy).
