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
| TECH-020 | Fix `@RequestMapping` without leading `/` | High | 1 | **Done** (2026-07-19, branch `fix/request-mapping-leading-slash`) |
| TECH-021 | `SipsaParseException` → HTTP 502 | Medium | 2 | Done |
| TECH-022 | Introduce `SipsaNotFoundException` → HTTP 404 | Medium | 2 | Done |
| TECH-023 | Add `requestId` and `instance` to error responses | Low | 2 | Done |
| TECH-030 | Named executor in `@Async` for audit logging | Low | 1 | **Resolved by TECH-136** (2026-07-19 — `@Async("ingestionTaskExecutor")` on `logEvent`) |
| TECH-031 | Externalize `SipsaHealthIndicator` thresholds | Low | 1 | **Done** (2026-07-19, branch `refactor/externalize-health-thresholds`) |
| TECH-032 | Add Micrometer metrics for ingestion | Medium | 4 | Done |
| TECH-040 | Unit tests for `WindowPolicy` | High | 3 | **Done** (implemented by TECH-110) |
| TECH-041 | Unit tests for `SpecificationBuilder` | High | 3 | Done |
| TECH-042 | Unit tests for `IngestionJob` | High | 3 | Done |
| TECH-043 | Tests for `GlobalExceptionHandler` | Medium | 3 | Done |
| TECH-044 | SPIKE: Integration test strategy (WireMock/Testcontainers) | Low | 6 | **Resolved** (2026-08-03, [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md) — combined WireMock + Testcontainers, one IT per handler; follow-up work is TECH-150..161) |
| TECH-050 | Remove placeholder comments from handlers | Low | 1 | **Done** (2026-07-19, branch `refactor/remove-existing-code-comments`) |
| TECH-051 | Rename `toAuditEventRequest` → `toAuditEventResponse` | Low | 1 | **Done** (2026-07-19, branch `refactor/rename-audit-mapper-response`) |
| TECH-052 | `getRun()` returns `Optional<IngestionRun>` | Low | 1 | **Done** (2026-07-19, branch `refactor/optional-ingestion-run`) |
| TECH-053 | Make scheduler dispatch async | Medium | 2 | Done |
| TECH-054 | Add pagination to `GET /api/internal/ingestion/runs` | Low | 2 | Done |
| TECH-055 | SPIKE: `isMonthly()` in `IngestionHandler` contract | Low | 6 | **Done** (2026-07-20, `spike/evaluate-is-monthly-contract`) |
| TECH-056 | Consolidate monthly-method classification in `WindowPolicy` | Medium | 6 | **Done** (2026-07-20, `refactor/consolidate-monthly-method-classification`) |
| TECH-060 | Fix N+1 in `upsertFallbackBatch` | Medium | 4 | Done |
| TECH-070 | Bean Validation on `SoapProperties` | Low | 1 | **Done** (2026-07-19, branch `refactor/validate-soap-properties`) |
| TECH-071 | Align `batch-size` defaults | Low | 1 | **Done** (2026-07-16 — single typed source of truth, canonical 500) |
| TECH-080 | Write ADR-002 (security) | Low | 6 | **Done** |
| TECH-081 | Write ADR-001 (deduplication) | Low | 6 | **Done** (2026-07-16 — ADR-001 Accepted with empirical evidence) |
| TECH-090 | Move internal ingestion commands to `application/command` | Low | — | **Done** |
| TECH-091 | Move `TimezoneFilter` out of `infrastructure/config` into `api` | Low | — | **Done** |
| TECH-092 | Separate generated SOAP sources from manual code | Low | — | **Done** (2026-07-20, `refactor/relocate-generated-soap-classes`) |
| TECH-093 | Add ArchUnit package-boundary rules (Historia B) | Low | — | Done |
| TECH-094 | SPIKE: Evaluate relocating CXF-generated SOAP sources | Low | — | Done |
| TECH-095 | Remove domain→infrastructure Javadoc reference in `SoapGateway` (Historia A) | Low | — | **Done** |
| TECH-100 | Define the API's canonical date/time representation (`LocalDate` vs `OffsetDateTime`) as an explicit contract | Medium | — | **Done** (2026-07-27, `fix/timezone-calendar-dates-and-invalid-header-400`, PR #36 — see [ADR-008](../adr/ADR-008-timezone-locale-and-date-semantics.md)) |
| TECH-101 | Fix `WindowPolicy.validateMonthly` method binding and monthly `windowKey` contract | High | 3 | **Superseded by [TECH-111](#tech-111)** — same defects (F-WP-01/02/03), fixed 2026-07-14, one day before this story was formalized |
| TECH-102 | Add timezone-conversion tests (instants vs. calendar dates) across `America/Bogota`, `America/New_York`, `America/Los_Angeles`, `UTC`, including DST transitions | Medium | — | **Done** (2026-07-28, `feat/tech-102-104-105-closeout` — `TimezoneUtilTest` 13 cases for genuine-instant fields across zones/seasons/2026 US DST transitions; `SipsaIngestionMapperTest` 9 cases for the calendar-date resolver, including confirming `America/Bogota` never observes DST) |
| TECH-103 | `TimezoneFilter` responds `400 SIPSA_INVALID_TIMEZONE` on an invalid `X-Timezone` header instead of silently degrading to UTC | Medium | — | **Done** (2026-07-27, `fix/timezone-calendar-dates-and-invalid-header-400`, PR #36) |
| TECH-104 | Migrate `fecha_captura`/`fecha_mes_ini`/`fecha_ini`/`enma_fecha` from `TIMESTAMPTZ` to `DATE` (SPIKE done 2026-07-27; migration itself implemented 2026-07-28) | Low | — | **Done** (2026-07-28, `feat/tech-102-104-105-closeout` — schema migration, 5 entities, `SipsaIngestionMapper`/`ParcialIngestionHandler`, `ParcialKeyHash` v2, `SpecificationBuilder` simplified; validated against real DANE SOAP data in Docker — see write-up below) |
| TECH-105 | Evaluate the real need for i18n (`Accept-Language` + `MessageSource`) before implementing it | Low | — | **Evaluated, still deferred** (2026-07-28) — no `Accept-Language`/`MessageSource`/`Locale`-based i18n infrastructure exists anywhere in the codebase (confirmed by search, not assumed); no client requirement for a non-Spanish/non-English audience has ever been documented; error codes are already stable strings, a correct precondition if this is prioritized later. Conclusion: no action needed now; not re-evaluate until a concrete client requirement appears |
| TECH-106 | Fix `GlobalExceptionHandler`'s 3 `LocalDateTime` timestamps to an explicit-zone type | Low | — | **Done** (2026-07-27, `fix/timezone-calendar-dates-and-invalid-header-400`, PR #36) |
| TECH-110 | Validate scheduled ingestion jobs and add scheduling tests | High | 3 | **Done** |
| TECH-111 | Correct monthly `WindowPolicy` method binding, grace days, and stable window keys | High | 3 | **Done** |
| TECH-120 | Continuous integration pipeline (GitHub Actions) | High | — | **Done** |
| TECH-130 | Cognito resource server, scopes and app clients | High | — | **Done** (2026-07-21, branch `infra/cognito-authentication-foundation` — `modules/cognito`, 23/23 `terraform test` green, no AWS resource created; ECS config wiring completed 2026-07-22 by [TECH-142](#tech-142)) |
| TECH-131 | API Gateway: API keys, usage plans, throttling, access logs | High | — | **Done** (2026-07-22, branch `infra/api-gateway-private-integration` — `modules/api-gateway`, 21/21 `terraform test` green, 129/129 tree-wide, no AWS resource created) |
| TECH-132 | Private networking: ECS, VPC Link, internal ALB, gateway-bypass prevention | High | — | **In progress** — declarative infrastructure complete (TECH-138, TECH-139, TECH-140, TECH-141, TECH-131); local deployment hardening done (TECH-144, 2026-07-22); real AWS validation blocked (TECH-143, unmerged) pending SIPSA-specific credentials |
| TECH-137 | Terraform bootstrap and GitHub OIDC validation | High | — | **Done** (2026-07-21, branch `infra/terraform-bootstrap` — corrected same day: S3-native locking, Terraform 1.15.7/AWS provider 6.55.0, Trivy scanning, OIDC contract, REST API decision) |
| TECH-138 | Provision production VPC foundation | High | — | **Done** (2026-07-21, branch `infra/production-vpc-foundation` — `modules/network`, 16/16 `terraform test` green, no AWS resource created) |
| TECH-139 | Define production RDS PostgreSQL foundation | High | — | **Done** (2026-07-21, branch `infra/production-rds-foundation` — `modules/database`, 20/20 `terraform test` green, no AWS resource created) |
| TECH-140 | Define production ECR and ECS task foundation | High | — | **Done** (2026-07-21, branch `infra/production-ecs-task-foundation` — `modules/ecr` + `modules/ecs-task`, 26/26 `terraform test` green, no AWS resource created, no image published) |
| TECH-141 | Define internal ALB and ECS service foundation | High | — | **Done** (2026-07-21, branch `infra/internal-alb-ecs-service` — `modules/ecs-service`, 17/17 `terraform test` green, no AWS resource created) |
| TECH-142 | Wire Cognito configuration into the ECS task | High | — | **Done** (2026-07-22, branch `infra/wire-cognito-ecs-configuration` — issuer/allowlist wired root-only, 108/108 `terraform test` green tree-wide, 338 Java tests green, no AWS resource created) |
| TECH-143 | Validate production deployment prerequisites and Terraform plan | High | — | **Blocked / In progress** (branch `infra/production-deployment-preflight`, NOT merged — kept as evidence) — no SIPSA AWS credentials available; RDS/backend/OIDC/real-plan/cost checks blocked; local work extracted to TECH-144 |
| TECH-144 | Harden deployment configuration from local preflight evidence | High | — | **Done** (2026-07-22, branch `infra/preflight-local-hardening` — 131/131 `terraform test` tree-wide, 338 Java tests green, no AWS resource created) — Cognito human-client gate, ECS memory re-verified at 1024 MiB, grace period 480s from 6 real samples, DB credential design, no AWS access used |
| TECH-113 | Fix `artiId`/`muniId` filters of `GET /api/sipsa/parcial` | Medium | — | **Done** (2026-07-16, branch `fix/sipsa-parcial-query-filters`) |
| TECH-114 | Strict `enmaFecha` parsing with explicit rejection (H-1) | Medium | — | **Done** (2026-07-16 — implemented within TECH-011; H-1 did not occur on real data) |
| TECH-115 | Backfill/consolidation of a pre-existing external `sipsa_parcial` database | Medium | — | Conditional — only if an external historical database is confirmed to exist |
| TECH-116 | Disable `baseline-on-migrate` after per-environment Flyway history inventory | Low | — | **Done** (2026-08-03, branch `chore/disable-flyway-baseline-on-migrate`) |
| TECH-117 | Handle concurrent `SipsaParcial` duplicate insertion safely | Medium | — | **Done** (2026-07-19, branch `fix/sipsa-parcial-concurrent-dedup` — atomic `ON CONFLICT (key_hash) DO NOTHING`, collisions counted as skipped) |
| TECH-118 | Align `SipsaParcial` decimal precision (JPA 15,2 vs DDL 19,2) | Low | — | **Done** (2026-07-19, branch `fix/align-sipsa-parcial-decimal-precision` — annotation aligned to `19,2`, no migration) |
| TECH-119 | Remove redundant `idx_sipsa_parcial_key_hash` index | Low | — | **Done** (2026-07-16, branch `fix/remove-redundant-parcial-key-hash-index`, dedicated migration) |
| TECH-122 | Harden `SipsaParcial` natural-key constraints (NOT NULL / natural unique) | Low | — | Pending (contract phase; gated on TECH-012 external half) |
| TECH-124 | Optimize `SipsaParcial` article-filter queries | Low | — | **Done** (2026-07-18, branch `perf/sipsa-parcial-article-filter-index`, dedicated migration — covering index; count 18 ms → ~2 ms) |
| TECH-133 | Centralize and validate monthly ingestion window configuration | Low | — | **Done** (2026-07-17 — typed `monthlyWindowStart`, divergent `06:00` fallback removed, effective 14:00 unchanged) |
| TECH-134 | Align remaining SIPSA decimal annotations with the DDL (`Ciudad`, `Semanal`) | Low | — | **Done** (2026-07-19, branch `fix/align-remaining-sipsa-decimal-precision` — all SIPSA price models now declare `19,2`, no migration) |
| TECH-135 | Centralize ingestion rejection-threshold configuration (C-04) | Low | — | **Done** (2026-07-19, branch `refactor/centralize-ingestion-rejection-thresholds` — thresholds bind once in `IngestionProperties`, effective 0.01/5000 unchanged) |
| TECH-136 | Centralize async executor configuration and pin the audit executor (C-05) | Low | — | **Done** (2026-07-19, branch `refactor/centralize-async-executor-config` — `AsyncExecutorProperties` + `@Async("ingestionTaskExecutor")` for audit; geometry 2/10/25/60s unchanged) |
| TECH-150 | Integration-test scaffolding: Failsafe `integration-tests` profile, `*IT` convention, shared WireMock SOAP fixture support | High | 6 | **Done** (2026-08-03, branch `spike/tech-044-comprehensive-testing-strategy` — found and fixed a real WireMock/Jetty 12 dependency conflict along the way, see story) |
| TECH-151 | `CiudadIngestionHandlerIT` (WireMock + Testcontainers PG) | High | 6 | **Done** (2026-08-03, branch `test/ciudad-ingestion-handler-it`) |
| TECH-152 | `SemanaIngestionHandlerIT` (WireMock + Testcontainers PG) | Medium | 6 | **Done** (2026-08-03, branch `test/semana-ingestion-handler-it`) |
| TECH-153 | `MesIngestionHandlerIT` (WireMock + Testcontainers PG) | Medium | 6 | **Done** (2026-08-03, branch `test/mes-ingestion-handler-it`) |
| TECH-154 | `AbasIngestionHandlerIT` (WireMock + Testcontainers PG) | Medium | 6 | **Done** (2026-08-03, branch `test/abas-ingestion-handler-it`) |
| TECH-155 | `ParcialIngestionHandlerIT` (WireMock + Testcontainers PG); existing `ParcialIngestionHandlerTest` kept as-is | High | 6 | **Done** (2026-08-03, branch `test/parcial-ingestion-handler-it` — completes the 5-handler IT suite, TECH-151..155) |
| TECH-156 | `SoapStreamingClientTest`: retry/backoff/GZIP decompression unit coverage | Medium | 3 | **Done** (2026-08-03, branch `test/unit-coverage-gaps-tech156-158`) |
| TECH-157 | `SipsaReadServiceTest` + `PaginationConfigTest` | Medium | 3 | **Done** (2026-08-03, branch `test/unit-coverage-gaps-tech156-158`) |
| TECH-158 | Unit coverage for `GenericIngestionJob`/`IngestionService` dispatch (currently covered only transitively) | Low | 3 | **Done** (2026-08-03, branch `test/unit-coverage-gaps-tech156-158`) |
| TECH-159 | Introduce JaCoCo (report-only, no build-breaking `check` goal yet) | Medium | 3 | **Done** (2026-08-03, branch `test/introduce-jacoco-reporting`) |
| TECH-160 | E2E suite: golden-path (`Ciudad`) + failure-path (SOAP 500) black-box test via `RANDOM_PORT` + WireMock + Testcontainers + mock OIDC | High | 6 | **Done** (2026-08-03, branch `test/e2e-ciudad-golden-and-failure-path`) |
| TECH-161 | CI: new `integration-verify` job (`./mvnw verify -P integration-tests`), parallel to `verify` | Medium | 6 | **Done** (2026-08-03, branch `ci/integration-verify-job`) |

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
**Status:** **Done**  
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
**Status:** **Done**  
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
a dedicated expand-only migration, and suites
`ParcialKeyHashTest`, `ParcialIngestionHandlerTest`, `ParcialMigrationUpgradeTest`
plus extended `FlywayMigrationsTest`.

---

### TECH-012

**Title:** SPIKE — Verify `sipsa_parcial` growth in production  
**Type:** SPIKE  
**Priority:** High  
**Phase:** 5  
**Status:** **Partial — local half Done (2026-07-16); external half Conditional** (see below)  
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
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `fix/request-mapping-leading-slash`
**Dependencies:** None.

**Problem:**
Two controllers declare their routes without a leading slash, inconsistent with the rest of the project.

**Evidence:**
- `SipsaOpsController.java:35`: `@RequestMapping("api/internal/ingestion")`
- `IngestionAuditController.java:34`: `@RequestMapping("api/internal/audit")`

**Precision:** Spring MVC normalizes a class-level `@RequestMapping` value at startup
regardless of a leading slash, so the effective route (`/api/internal/ingestion/**`,
`/api/internal/audit/**`) was never actually broken — this was a declared-contract
inconsistency (vs. `SipsaRestController`'s `@RequestMapping("/api/sipsa")`), not a
routing failure. `SecurityConfig`'s `requestMatchers` already used the leading-slash
form and already matched correctly before this change.

**Acceptance Criteria:**
- [x] Both controllers have `@RequestMapping("/api/internal/...")` with leading `/`.
- [x] `./mvnw clean verify` passes.

**Completed:** 2026-07-19, branch `fix/request-mapping-leading-slash`. Both class-level
annotations now read `@RequestMapping("/api/internal/ingestion")` and
`@RequestMapping("/api/internal/audit")`. New test
`InternalControllerRouteMappingTest` pins the structural evidence (Spring's
`HandlerMapping` resolves each path to the expected controller and handler method,
independent of HTTP status) alongside the pre-existing `InternalEndpointSecurityTest`
(15 test cases across both endpoint groups: 401 without token, 403 with wrong scope,
2xx with correct scope — all still green, confirming security and status codes are
unchanged). Docker smoke: clean `down -v && up --build`, both endpoint groups exercised
with the local mock-OIDC token flow (no Flyway migration in this story). No DTOs,
security config, scopes, or business logic touched.

---

### TECH-021

**Title:** Map `SipsaParseException` to HTTP 502 instead of 400  
**Type:** Correctiva  
**Priority:** Medium  
**Phase:** 2  
**Status:** Done  
**Complexity:** XS  
**Branch:** `fix/parse-exception-bad-gateway`
**Dependencies:** None.

**Problem:**
`SipsaParseException` is mapped to HTTP 400 (Bad Request). This exception is thrown when
DANE's XML response cannot be parsed — the error is in the upstream response, not in the
client's request.

**Evidence (before):**
- `GlobalExceptionHandler.java:117`: `return buildErrorResponse(HttpStatus.BAD_REQUEST, "PARSE_ERROR", ...)`
- `AbstractStaxParser.java:82-85`: thrown when parsing DANE SOAP XML

**Acceptance Criteria:**
- [x] `SipsaParseException` → HTTP `502 Bad Gateway`.
- [ ] Error code: `SIPSA_UPSTREAM_PARSE_ERROR` — **deliberately not done in this story.**
      ADR-003's error-code taxonomy is still `Proposed`, not `Accepted`; this story's
      scope, per the operator's explicit instruction, is the HTTP status mapping only.
      The `code` field is unchanged (`"PARSE_ERROR"`, preserved verbatim). Follow-up
      below.
- [x] Response body does not expose DANE XML details. Pre-existing behavior, unaffected
      by this story — `AbstractStaxParser`'s messages describe the parse failure (e.g.
      "XML Stream Error: ...") without embedding the raw upstream XML payload.
- [ ] TECH-043 test covers this case — **deliberately not done in this story.** TECH-043
      is the full `GlobalExceptionHandler` coverage story (every handler method, one
      suite). This story adds a narrowly-scoped
      `GlobalExceptionHandlerParseExceptionTest` (`@WebMvcTest` + real MVC dispatch)
      covering exactly this exception's contract — status, error code, message,
      timestamp, content type — plus a regression check that
      `SipsaBusinessException` → 422 is untouched. TECH-043 will absorb or supersede it.
- [x] `./mvnw clean verify` passes (270 tests, up from 264 — 6 new).

**Completed:** `SipsaParseException` now maps to `502 Bad Gateway` instead of
`400 Bad Request` — `GlobalExceptionHandler.handleParseException` changed its status
argument from `HttpStatus.BAD_REQUEST` to `HttpStatus.BAD_GATEWAY`; no other part of the
handler or `ErrorResponse` changed. This is a **contractual (breaking) change** for any
client depending on the previous `400` status for parse failures — the error `code`
(`"PARSE_ERROR"`), `message`, `timestamp`, and body shape are all preserved verbatim, so
only the HTTP status line differs. Rationale: a parse failure here means DANE's upstream
SOAP/XML response could not be understood — the API client did nothing wrong, so `400`
(client error) was semantically incorrect; `502` (this server, acting as a gateway,
received an invalid response from an upstream server) is correct. ADR-003 explicitly
authorizes this status-code correction to proceed independent of its own `Proposed`
status. **Follow-up (separate story, not this one):** adopting the `SIPSA_UPSTREAM_PARSE_ERROR`
error-code taxonomy from ADR-003 requires that ADR to be `Accepted` first, and is
explicitly out of scope here per the operator's instruction not to touch `ErrorResponse`
in this story.

---

### TECH-022

**Title:** Introduce `SipsaNotFoundException` for not-found cases  
**Type:** Correctiva  
**Priority:** Medium  
**Phase:** 2  
**Status:** Done  
**Complexity:** S  
**Branch:** `fix/notfound-exception-404`
**Dependencies:** None. (TECH-021 was merged to `main` first, but this story doesn't
technically depend on it — the two exceptions are handled independently.)

**Problem:**
`SipsaBusinessException` is used for both business rule violations (correct → 422) and
resource not-found cases (incorrect → should be 404).

**Evidence (before):**
- `IngestionRunQueryService.java:90`: `throw new SipsaBusinessException("Ingestion run not found: " + runId)`
- `IngestionControlService.java:332` (`cancelRun`): `throw new SipsaBusinessException("Run not found: " + runId)`
- `AuditTrailService.java:57,88`: same pattern for unknown `requestId`/`runId`

**Acceptance Criteria:**
- [x] `SipsaNotFoundException extends RuntimeException` created
      (`domain/exception/SipsaNotFoundException.java`).
- [x] `GlobalExceptionHandler` maps `SipsaNotFoundException` to HTTP `404`
      (`handleNotFoundException`, error code `NOT_FOUND` — reuses the code
      `handleNotFound` already returns for routing-level 404s, since both are the same
      HTTP concept; `message` differs).
- [x] `GET /api/internal/ingestion/runs/{missingId}` returns `404`.
- [ ] `GET /api/internal/audit/request/unknown-uuid` returns `404` — **deliberately not
      done in this story.** The operator's instructions for this story scoped it to
      `IngestionRunQueryService.getRunStatus` (minimum) and a review of
      `IngestionControlService.cancelRun`; `AuditTrailService`'s two
      `SipsaBusinessException` sites (`logEvent`-adjacent lookups by `requestId` and by
      `runId`) were intentionally left untouched — same shape of bug, separate
      follow-up story, not silently forgotten.
- [x] `SipsaBusinessException` still returns `422` for rule violations — verified by
      regression tests at both the service and MVC layers (e.g. `cancelRun` on an
      existing-but-inactive run).
- [x] `./mvnw clean verify` passes (279 tests, up from 270 — 9 new).

**Completed:** `SipsaNotFoundException` (new, `domain/exception`) is now thrown, and
mapped by `GlobalExceptionHandler` to HTTP `404` (code `NOT_FOUND`), in exactly two
places where the previous `SipsaBusinessException` (422) conflated "resource doesn't
exist" with "resource exists but the operation is invalid":
- `IngestionRunQueryService.getRunStatus` — a run ID that doesn't exist at all.
- `IngestionControlService.cancelRun` — **only** its "run not found" branch (the
  `run == null` check). Its sibling check — "run exists but isn't STARTED/RUNNING" —
  **stays** `SipsaBusinessException` → 422, unchanged: that's a genuine business-rule
  violation on an existing resource, not an absent one.
`ErrorResponse`, `requestId`, and `instance` were not touched (TECH-023 scope). `code`
values already in use (`NOT_FOUND`, `BUSINESS_ERROR`, `PARSE_ERROR`) are unchanged.
New tests: `IngestionControlServiceCancelRunTest` (service-level, not-found vs.
not-active split), updated `IngestionRunQueryServiceGetRunStatusTest` (now asserts
`SipsaNotFoundException`, not `SipsaBusinessException`), and
`SipsaOpsControllerNotFoundTest` (real MVC dispatch via `@SpringBootTest` +
`@AutoConfigureMockMvc`, same pattern as `InternalControllerRouteMappingTest`: missing
run → 404, existing run → 200 unchanged, inactive-run cancel → 422 unchanged, active-run
cancel → 200 unchanged, and a regression check that a downstream `SipsaParseException`
through this same controller still returns 502 — TECH-021 untouched). No Flyway
migration. **Follow-up (separate story, not this one):** apply the same
not-found split to `AuditTrailService`'s two lookup methods.

---

### TECH-023

**Title:** Add `requestId` and `instance` to error responses  
**Type:** Observability  
**Priority:** Low  
**Phase:** 2  
**Status:** Done  
**Complexity:** S  
**Branch:** `feat/error-response-context` (the originally-listed `feat/error-correlation-id`
name was not reused)
**Dependencies:** None.

**Problem:**
Error responses lack correlation fields. Clients cannot identify which server-side log
entry corresponds to their received error.

**Evidence (before):** `GlobalExceptionHandler.java:324`: `ErrorResponse` record has no `requestId` or `instance`.

**Diagnosis — existing request-ID sources (before implementing):** no per-HTTP-request
correlation ID source existed anywhere in this repository. What did exist, and was
deliberately *not* reused because it doesn't cover every HTTP request:
- `IngestionTriggerService.triggerIngestion` / `SipsaIngestionScheduler` each call
  `UUID.randomUUID()` to correlate one *ingestion run* — a business-domain ID, generated
  only for ingestion-trigger operations, absent for e.g. a validation error on an
  unrelated endpoint.
- `IngestionJob` populates SLF4J MDC (`runId`, `requestId`, `method`, `windowKey`) —
  but only deep inside the async ingestion pipeline's own thread, never on the
  synchronous request-handling thread `GlobalExceptionHandler` runs on.
- No distributed-tracing library (no Sleuth, no Micrometer Tracing/Brave) is on the
  classpath — only `micrometer-registry-prometheus` (metrics, unrelated).
- No filter previously read or normalized an incoming correlation header;
  `TimezoneFilter` is the only existing `OncePerRequestFilter`, and it's unrelated
  (resolves `X-Timezone`, not request identity).
Per the operator's explicit instruction not to trust an arbitrary header directly in a
handler, and since no stable source existed, a new minimal filter was introduced (see
Completed below) — this *is* the "atributo de request establecido por un filtro" source,
now created rather than found.

**Acceptance Criteria:**
- [x] `ErrorResponse` includes `instance` (the request path, from `HttpServletRequest.getRequestURI()`).
- [x] `ErrorResponse` includes `requestId` (from `X-Request-Id` header if present, generated otherwise).
- [x] Existing fields (`timestamp`, `status`, `error`, `code`, `message`) are preserved.
- [x] `./mvnw clean verify` passes (289 tests, up from 279 — 10 new).

**Completed:** New `RequestIdFilter` (`api/filter/`, mirrors the existing
`TimezoneFilter` pattern) establishes exactly one correlation ID per request — honors an
incoming `X-Request-Id` header if present and non-blank, otherwise generates a UUID —
stored as a request attribute before the rest of the filter chain runs (so it survives
even when a handler throws), and echoed back on the `X-Request-Id` response header.
`GlobalExceptionHandler.buildErrorResponse` gained an `HttpServletRequest` parameter
(every `@ExceptionHandler` method now receives and forwards it — no handler was left
without the new fields), reads the correlation ID via `resolveRequestId(request)`
(defensive fallback to a fresh UUID only if the filter's attribute is somehow absent —
never overrides a value the filter already set), and sets `instance` from
`request.getRequestURI()` — path only, no host, scheme, or query string, per the
operator's explicit constraint. `ErrorResponse`, `IngestionValidationErrorResponse`, and
`ValidationErrorResponse` (the two handlers that build their DTOs directly rather than
through `buildErrorResponse`) all gained the same two fields in the same position,
additively — `timestamp`/`status`/`error`/`code`/`message` (plus each record's own extra
field) are untouched, and every prior status/code mapping (TECH-021's 502, TECH-022's
404, TECH-020's routing 404, etc.) is unchanged. New
`GlobalExceptionHandlerRequestContextTest` (`@WebMvcTest`, which picks up
`RequestIdFilter` automatically the same way it already picked up `TimezoneFilter`)
covers: `SipsaParseException` → 502 with `requestId`/`instance`; `SipsaNotFoundException`
→ 404 with `requestId`/`instance`; `SipsaBusinessException` → 422 with new fields and
prior contract preserved; a Bean Validation error with new fields and `fieldErrors`
preserved; a generic exception with new fields and no stack trace leaked; an incoming
`X-Request-Id` header echoed verbatim in both body and response header; the fallback
case generating a non-blank ID consistent between header and body (no fragile exact-UUID
assertion, presence/consistency only); and a blank incoming header not being trusted
verbatim. No Flyway migration.

---

### TECH-030

**Title:** Specify executor name in `@Async` for `IngestionAuditService.logEvent()`  
**Type:** Bug  
**Priority:** Low  
**Phase:** 1  
**Status:** **Resolved by [TECH-136](#tech-136)**  
**Complexity:** XS  
**Branch:** `fix/async-executor-audit` (superseded — implemented on `refactor/centralize-async-executor-config`)
**Dependencies:** None.

**Problem:**
`@Async` without an executor name uses `SimpleAsyncTaskExecutor` (creates a new thread per
invocation) instead of the configured `ingestionTaskExecutor` pool.

**Evidence:** `IngestionAuditService.java:67`: `@Async` (no name)

**Acceptance Criteria:**
- [x] `logEvent()` uses a named executor: either `@Async("ingestionTaskExecutor")` or a dedicated audit executor.
- [x] `./mvnw clean verify` passes.

**Completed:** 2026-07-19, as part of [TECH-136](#tech-136) (branch
`refactor/centralize-async-executor-config`). TECH-136 was scoped to C-05 (the
`AsyncConfig` `@Value` duplication) plus this exact finding, confirmed independently
during the 2026-07-19 CI-flake investigation before the two stories were connected;
`logEvent` now declares `@Async("ingestionTaskExecutor")`, verified by
`IngestionAuditExecutorResolutionTest` (insert thread carries the `ingestion-async-`
prefix; captured output contains neither `More than one TaskExecutor bean found` nor
`SimpleAsyncTaskExecutor`). No separate implementation exists under this story's own
branch — do not start `fix/async-executor-audit`.

---

### TECH-031

**Title:** Externalize `SipsaHealthIndicator` staleness thresholds  
**Type:** Config  
**Priority:** Low  
**Phase:** 1  
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `refactor/externalize-health-thresholds` (the originally-listed
`refactor/health-indicator-config` name was not reused)
**Dependencies:** None.

**Problem:**
Staleness thresholds (36h daily, 35 days monthly) are hardcoded in `SipsaHealthIndicator`.

**Evidence (re-confirmed against `main` before changing anything):**
`SipsaHealthIndicator.java:108`: `if (ageHours > 36)` for methods in `DAILY_METHODS`
(daily ingestion window methods — note `promediosSipsaSemanaMadr`, the weekly wholesale
endpoint, is in this set too: "daily" means "runs in the daily window", not "publishes
daily"); `:114`: `if (ageHours > (35 * 24))` for every other monitored method (the
monthly ones). Both compare against `ageHours = Duration.between(lastSuccess,
now).toHours()` — the actual comparison unit is **hours** in both branches; `35 * 24`
was inline day-to-hour arithmetic, not a separate day-based comparison.

**Acceptance Criteria:**
- [x] Thresholds are defined in `application.yaml` under `sipsa.health.*`.
- [x] `SipsaHealthIndicator` reads them via `@ConfigurationProperties`
      (`SipsaHealthProperties`, constructor injection — no `@Value` in the indicator
      itself).
- [x] `./mvnw clean verify` passes.

**Completed:** 2026-07-19, branch `refactor/externalize-health-thresholds`. New
`SipsaHealthProperties` (`sipsa.health.*`, env `SIPSA_HEALTH_DAILY_STALENESS_THRESHOLD`
/ `SIPSA_HEALTH_MONTHLY_STALENESS_THRESHOLD`) with validated `Duration` fields —
canonical defaults `36h`/`840h` reproduce the exact prior behavior (kept in hours, not
converted to a day count, since hours is the unit the code actually compares in); each
threshold must be positive or startup aborts naming the property (zero would report
every method `STALE` immediately after its own success — never a meaningful signal). No
existing properties class was reused (`IngestionProperties` is scoped to
`sipsa.ingestion.*`, a different domain). `Instant.now()` became `Instant.now(clock)`
with a package-private test-only `setClock` seam mirroring `WindowPolicy`'s established
pattern in this codebase (`Clock.systemUTC()` default, behaviorally identical to the
prior bare `Instant.now()`). The strict `>` comparison, per-method `STALE` detail
entries, and `UP`/`DOWN`/`UNKNOWN` outcomes are byte-identical to before — evidenced by
`SipsaHealthPropertiesTest` (7 binding/validation cases) and `SipsaHealthIndicatorTest`
(6 cases: both method groups exactly at their default threshold stay `UP`, one hour past
goes `DOWN`, a configured override changes the effective threshold, no-runs-yet stays
`UNKNOWN`). Verified in Docker: default thresholds logged (`36h`/`840h`), valid override
(`12h`/`10d` → `PT12H`/`PT240H`), invalid value (`0h`) → `APPLICATION FAILED TO START`
naming the property, defaults restored, `/actuator/health` and the `sipsa` indicator's
detail contract unaffected. No Flyway migration.

**Completed:** —

---

### TECH-032

**Title:** Add Micrometer metrics for the ingestion pipeline  
**Type:** Observability  
**Priority:** Medium  
**Phase:** 4  
**Status:** Done  
**Complexity:** M  
**Branch:** `feat/ingestion-micrometer-metrics` (the originally-listed `feat/ingestion-metrics`
name was not reused)
**Dependencies:** None.

**Problem:**
No custom metrics exist. It is not possible to alert on ingestion duration, record reject rate, or SOAP failures using Prometheus.

**Evidence (before):** `grep -rn "MeterRegistry\|@Timed\|Counter\|Timer" src/` → zero results.
`spring-boot-starter-actuator` and `micrometer-registry-prometheus` were already
dependencies — no new dependency was needed, only actual usage.

**Diagnosis (before implementing):**
- **Ingestion flow boundary:** `IngestionJob.execute(IngestionRequest)` (the abstract
  base class every job runs through — `GenericIngestionJob` is the sole concrete
  subclass) is the single central place every ingestion run passes through, regardless
  of method. Three early-return "skip" paths (window violation, already-succeeded
  duplicate, business-exception duplicate) exit *before* a run/`IngestionContext` is
  ever created — these are not timed as runs, since there's nothing to time yet.
- **Result object:** no separate `IngestionResult` type exists — `IngestionContext`
  (mutable, one per run) accumulates the final counts (`recordsSeen`, `recordsInserted`,
  `recordsUpdated`, `recordsSkipped`, `rejectCount`) already read once, in `execute`'s
  `finally` block, by `controlService.updateMetrics(...)`. Metrics reuse that same
  already-existing aggregate — never recalculated from a repository query, never
  incremented per-record.
- **SOAP call boundary:** `SoapGatewayImpl` (5 methods, one per SOAP action) always
  delegates to `SoapStreamingClient.stream(soapAction, payload)` exactly once per data
  fetch — `stream(...)` itself owns the retry loop (exponential backoff, configurable
  `sipsa.soap.max-retries`). Instrumenting `stream(...)` (not the gateway, and not
  per-HTTP-attempt) is the one point that counts a "call" the way a caller means it,
  with no risk of double-counting.
- **Existing Micrometer/Actuator state:** zero prior usage anywhere in `src/main`;
  `management.endpoints.web.exposure.include: health,info,metrics,prometheus` was
  already configured; `/actuator/**` (beyond `/actuator/health/**`) already requires
  authentication (unchanged by this story, not required to change per the operator's
  explicit instruction).
- **Defect found and fixed:** `micrometer-registry-prometheus` was declared
  `<optional>true</optional>` in `pom.xml`. Spring Boot Maven Plugin's `repackage` goal
  excludes `optional`/`provided` dependencies from the runnable fat jar **by default** —
  so the Prometheus registry compiled fine and appeared in `mvn dependency:tree`, but
  was never actually bundled into `BOOT-INF/lib`, and `/actuator/prometheus` 404'd
  despite being in the exposure list. Invisible until now because nothing had ever
  exercised that endpoint. Fixed by removing the `optional` flag (one line); verified
  the Prometheus jars are now present in the built jar and the endpoint returns `200`.

**Metrics implemented** (`infrastructure/observability/IngestionMetrics`, one dedicated
`@Component`, mirrors the existing `SipsaHealthIndicator`'s package/style):

| Metric | Type | Tags | Point |
|---|---|---|---|
| `sipsa.ingestion.duration` | Timer | `method`, `outcome`, `source` | `IngestionJob.execute`'s `finally` |
| `sipsa.ingestion.runs` | Counter | `method`, `outcome`, `source` | same |
| `sipsa.ingestion.records.seen/inserted/skipped/rejected` | DistributionSummary | `method`, `outcome` | same, one value per run from `IngestionContext`'s final counters |
| `sipsa.soap.calls` | Counter | `method`, `outcome` | `SoapStreamingClient.stream`'s `finally` |
| `sipsa.soap.failures` | Counter | `method` | same, only on failure |
| `sipsa.soap.retries` | Counter | `method` | same, once per retry attempt |
| `sipsa.soap.duration` | Timer | `method`, `outcome` | same, spans all retries/backoff |

`outcome` is `success`/`failure`/`canceled` for ingestion (mapping
`IngestionRunStatus.SUCCEEDED`/`FAILED`/`CANCELED`) and `success`/`failure` for SOAP.
`source` is the lowercased `RequestSource` (`manual`/`scheduled`/`system`). `method` is
always a value from a closed, small catalog (`IngestionService`'s ~5 registered
handlers; the 5 SOAP actions `SoapGatewayImpl` calls) — never `requestId`, `runId`, a
raw exception message, or any other unbounded value. Record counts use
`DistributionSummary`, not `Counter`, since each is a single aggregate value recorded
once per run (the preferred design per the operator's own instruction), not an
incremental per-record tally. Every public method on `IngestionMetrics` catches and logs
any registry exception rather than propagating it — instrumentation must never break an
ingestion run or a SOAP call. Negative record counts are refused (logged, not recorded),
never silently passed through.

**Deviations from the original spec above** (superseded by the operator's more detailed
instructions for this story): record-count metrics are `DistributionSummary`, not
`Counter`; `records.skipped` was added (the original 3-metric list omitted it, but the
domain already tracks it); the SOAP outcome tag is named `outcome` (not `result`) for
naming consistency with the ingestion metrics; `sipsa.soap.failures`/`sipsa.soap.retries`/
`sipsa.soap.duration` and `sipsa.ingestion.runs` were added beyond the original minimum
list, all explicitly requested in the newer instructions.

**Wiring:** `IngestionJob`, `GenericIngestionJob`, and `SoapStreamingClient` each gained
one new constructor parameter (`IngestionMetrics`) — Spring autowires it automatically;
`IngestionJobRejectThresholdTest`'s manual `GenericIngestionJob` construction was updated
with a mock.

**Acceptance Criteria:**
- [x] After running any ingestion method, `GET /actuator/metrics/sipsa.ingestion.duration`
      returns data — verified in Docker (below), including a real successful
      `promediosSipsaParcial` run (677,061 records).
- [x] All metrics have a `method` tag with the SOAP/ingestion method name.
- [x] `./mvnw clean verify` passes (327 tests, up from 311 — 16 new).

**Completed:** New `IngestionMetrics` component instruments both the ingestion-run
lifecycle and SOAP calls, as detailed above. New tests: `IngestionMetricsTest` (9 cases,
`SimpleMeterRegistry` — no Prometheus backend needed for tests — covering a successful
run, a failed run, two methods staying on separate/stable tag series, SOAP
success/failure/retry, a zero-record run, a negative value being refused, and an
explicit check that no meter ever carries a tag outside `method`/`outcome`/`source`),
`IngestionJobMetricsTest` (4 cases, mocked `IngestionMetrics`, verifying `execute()`
calls `recordRunCompleted` exactly once per run with the correct outcome — success,
failure, canceled — and never for a window-skipped run that never created a context),
and `SoapStreamingClientMetricsTest` (3 cases, a real local `com.sun.net.httpserver`
loopback server — no WireMock, which this repo doesn't have yet per TECH-044 — verifying
exactly one `recordSoapCallCompleted` per `stream()` call and exactly one
`recordSoapRetry` per retry attempt, for success, a non-retryable 400, and a
retry-exhausting 500). Verified in Docker: clean rebuild, `/actuator/health` unaffected,
metrics absent before any run (meters are created lazily on first use — expected, not a
bug), all 10 `sipsa.*` metrics present with exactly the designed tags after a run,
`/actuator/prometheus` returns the same series in Prometheus text format, no sensitive
data (no `requestId`, no raw payloads) in any tag or metric name. No status codes,
`ErrorResponse`, DTOs, security, retries, thresholds, scheduler, or database logic
changed. No Flyway migration.

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
**Status:** Done  
**Complexity:** S  
**Branch:** `test/complete-specification-builder-coverage` (the originally-listed
`test/specification-builder` name was not reused)

**Dependencies:** None.

**Audit before implementing:** grepped `src/main`/`src/test`/`docs` for `TECH-041`,
`SpecificationBuilder`, `Specification<`, `JpaSpecificationExecutor`, `CriteriaBuilder`,
`Predicate`. Result: exactly **one** `SpecificationBuilder` class
(`infrastructure/specification/SpecificationBuilder.java`), used by `SipsaReadService` for
5 entity types. **Zero test coverage found anywhere** — no dedicated test file, and no
indirect/transitive coverage either (no test exercises `SipsaReadService` or any
`*QueryRequest` filtering path). Unlike TECH-042, this was not a duplication-avoidance
audit; it confirmed a genuine, complete gap.

**Real contract (read from the production source, not assumed):** `withAttribute` is
exact-match equality only (no LIKE/partial-match/case-insensitivity — those simply don't
exist in this class); `withDateOrRange` has a 3-way precedence (exact date > start/end
range > no filter) with a fixed business timezone; `build()` AND-combines every added
filter (no OR support anywhere); no field-name allowlist inside the class itself, but
every one of `SipsaReadService`'s 5 call sites passes a hardcoded literal attribute name,
never client input — no concrete injection/traversal risk exists in real usage today (see
full write-up in [Testing Strategy](../architecture/testing-strategy.md)). Several topics
from the original story's suggested checklist do not apply to this class as implemented
and were **not** tested, per the instruction not to invent behavior it doesn't have:
`%`/`_` escaping, case sensitivity, joins, OR composition, enum/boolean type conversion
(all values arrive already-typed from the caller).

**Decision: Case B (real gaps — actually a full gap) — new tests.** Split across 2
classes: `SpecificationBuilderTest` (13 cases, mocked JPA Criteria API, no database — pure
predicate-selection logic) and `SpecificationBuilderPostgresTest` (8 cases, real
PostgreSQL via Testcontainers, against `SipsaMayoristasSemanalRepository` — AND
composition, real `TIMESTAMPTZ` timezone-boundary semantics, and filter+pagination
interaction, none of which a mock can honestly prove). A boundary run of the exact-date
Postgres test **found and pinned down a real, previously-undocumented implementation
detail**: `withDateOrRange`'s exact-date filter uses `cb.between`, which is inclusive on
*both* ends, so the exact next-day-midnight instant is itself matched — the class's own
Javadoc ("full day range") doesn't spell this out. Documented as observed behavior, not
treated as a defect (negligible in practice — real ingested timestamps essentially never
land on exact midnight) and not "fixed," matching the instruction to document rather than
expand scope absent a concrete demonstrated risk.

**Acceptance Criteria:**
- [x] ≥ 7 test cases as defined in [Testing Strategy](../architecture/testing-strategy.md)
      — all 8 originally-planned cases covered, plus 13 more (factory validation,
      precedence, real DB/timezone/pagination semantics) — 21 new cases total.
- [x] `./mvnw clean verify` passes (410 tests, up from 389 — 21 net new).

**Completed:** `SpecificationBuilderTest` (13 cases): `builder()` factory
null/blank/valid-timezone; `withAttribute` null-skip and equal-predicate construction
(mocked `cb.equal`, args verified); `withDateOrRange`'s 3-way precedence including exact
date beating an accompanying range; boundary `Instant` values captured and asserted for
`between`/`greaterThanOrEqualTo`/`lessThan`, not just "some call happened"; `build()`'s
empty→conjunction and single-filter→unwrapped-passthrough behavior. No SQL-string
assertions, no Hibernate internals, no incidental predicate ordering — only the class's
own observable Criteria API calls. `SpecificationBuilderPostgresTest` (8 cases, real
PostgreSQL): unfiltered `build()` matches every row; an equality filter matches only the
right rows; a real America/Bogota `TIMESTAMPTZ` calendar-day boundary for the exact-date
filter (one second before midnight included, one second after the *next* midnight
excluded) plus the separate inclusive-upper-bound edge case described above; start-only
range inclusive at the boundary; end-only range exclusive at the boundary; two filters
combined via real AND composition (not mocked); a filter combined with pagination across
multiple pages produces no duplicates, no omissions, and never surfaces a non-matching
row. No production code changed — test-only story, confirmed by diff (only 2 new test
files). No endpoints, HTTP contract, TECH-054 pagination, scheduler, metrics, audit,
ingestion repositories, TECH-060, SOAP, general security, database schema, or AWS
infrastructure change. No Flyway migration.

---

### TECH-042

**Title:** Unit tests for `IngestionJob`  
**Type:** Testing  
**Priority:** High  
**Phase:** 3  
**Status:** Done  
**Complexity:** M  
**Branch:** `test/complete-ingestion-job-coverage` (the originally-listed `test/ingestion-job`
name was not reused)

**Dependencies:** TECH-040 was not a prerequisite in practice — TECH-032 and TECH-053
(both already merged) had already established the `ScriptedIngestionJob`-subclass-with-
mocked-collaborators pattern this story reused directly.

**Audit before implementing (mandatory — do not duplicate TECH-032/TECH-053 coverage):**
grepped `src/test`/`src/main`/`docs` for `TECH-042`, `IngestionJobTest`,
`IngestionJobMetricsTest`, `IngestionJobRejectThresholdTest`, `GenericIngestionJob`, `class
IngestionJob`, then read every matching test file's actual assertions (not just names) and
the real `IngestionJob.execute()` contract. Result: 3 of the 9 target cases in [Testing
Strategy](../architecture/testing-strategy.md) were already covered —
`windowViolation_runSkipped` and the outcome-metric proxies for the canceled/failed paths,
by `IngestionJobMetricsTest` (TECH-032); `thresholdExceeded_runMarkedFailed`, thoroughly, by
`IngestionJobRejectThresholdTest` (7 direct `validateThresholds` cases, TECH-135) — but only
in isolation, not through a full `execute()` call. Six cases had **zero** test evidence
anywhere in the suite: both duplicate-run cases (skip without force, proceed with force),
rejected-record persistence via `logReject`, DB-level `updateMetrics` persistence in
`finally`, and the MDC lifecycle (zero `MDC` references existed anywhere in `src/test`
before this story). See the full audit matrix (which test covers which original case) in
the `IngestionJobTest` section of [Testing Strategy](../architecture/testing-strategy.md).

**Decision: Case B (real gaps) — new tests, not docs-only.** New `IngestionJobContractTest`
(15 cases) targets exactly the 6 confirmed gaps, plus explicit status/audit-argument
assertions for the RUNNING/SUCCEEDED/FAILED transitions (previously only provable through
the metrics-outcome proxy, not the actual `updateStatus`/`logEvent` calls) and the MDC
lifecycle (populated with the 5 documented keys during `runIngestion`, cleared after both
outcomes, proven not to leak between two sequential executions). No existing test file was
rewritten or consolidated; `IngestionJobMetricsTest`, `IngestionJobRejectThresholdTest`, and
`ScheduledIngestionDispatcherTest` are all untouched.

**Acceptance Criteria:**
- [x] ≥ 7 test cases as defined in [Testing Strategy](../architecture/testing-strategy.md)
      — all 9 original target cases now have direct or explicit coverage (up from 3), plus
      MDC lifecycle beyond the original list; 15 new cases in `IngestionJobContractTest`.
- [x] All dependencies are mocked (no database, no SOAP) — `WindowPolicy`,
      `IngestionControlService`, `IngestionAuditService`, `IngestionMetrics` all mocked;
      no `@SpringBootTest`.
- [x] `./mvnw clean verify` passes (389 tests, up from 374 — 15 net new).

**Completed:** New `IngestionJobContractTest.java`
(`src/test/java/.../application/ingestion/core/`), reusing the `ScriptedIngestionJob`
subclass-with-mocked-collaborators pattern from `IngestionJobMetricsTest` unchanged. 15
cases: duplicate-run skip (not forced) and override (forced); rejected-record persistence
(one `logReject` call per record with exact `rawData`/`reason`/`isParseError` arguments,
plus a zero-rejects case); `updateMetrics` called once in `finally` with the exact final
counts, on both success and failure; MDC populated with all 5 documented keys during
`runIngestion`, cleared after success, cleared after failure, and proven not to leak a
prior run's `runId` into the next execution on the same thread; explicit
`updateStatus`/audit-event assertions (not just the outcome-metric proxy) for the RUNNING,
SUCCEEDED, FAILED (including the `SipsaExternalException` → `httpStatus`/`soapFaultCode`
extraction path, and the null-fields case for a non-external exception), and CANCELED
transitions — the CANCELED case explicitly asserts `updateStatus` is never called with
`SUCCEEDED` or `FAILED`, proving the pre-set status is never overwritten. No production
code changed — this is a test-only story. No scheduler, dispatcher, executor,
`CallerRunsPolicy`, metric names/tags, `IngestionMetrics`, production audit logic,
business/threshold logic, repository, or API change — confirmed by diff (only the one new
test file). No Flyway migration.

---

### TECH-043

**Title:** Tests for `GlobalExceptionHandler`  
**Type:** Testing  
**Priority:** Medium  
**Phase:** 3  
**Status:** Done  
**Complexity:** S  
**Branch:** `test/global-exception-handler-contract` (the originally-listed
`test/exception-handler` name was not reused)

**Dependencies:** TECH-021 and TECH-022 were done first so that 502 and 404 cases are
included. TECH-023 also landed first, so `requestId`/`instance` are asserted on every
case rather than the original 5-field shape.

**Handler inventory (`GlobalExceptionHandler.java`, as of `main` before this story):**

| Exception | HTTP | `code` | Prior test | Gap closed |
| --- | ---: | --- | --- | --- |
| `SipsaValidationException` | 400 | `VALIDATION_ERROR` | none (unit-level only, via `ParcialQueryRequestTest`) | MVC coverage added |
| `SipsaBusinessException` | 422 | `BUSINESS_ERROR` | TECH-021/022/023 regression checks | full field coverage added |
| `SipsaNotFoundException` | 404 | `NOT_FOUND` | TECH-022, TECH-023 | full field coverage added |
| `SipsaIngestionException` | 500 | `INGESTION_ERROR` | none (unit-level only, via `IngestionJobRejectThresholdTest`) | MVC coverage added |
| `SipsaParseException` | 502 | `PARSE_ERROR` | TECH-021, TECH-023 | full field coverage added |
| `SipsaExternalException` | 502 | `EXTERNAL_ERROR` | none | MVC coverage added |
| `SipsaConfigurationException` | 500 | `CONFIGURATION_ERROR` | none (unit-level only, via `WindowPolicyTest`/`SipsaJwtValidatorsTest`) | MVC coverage added |
| `Exception` (catch-all) | 500 | `INTERNAL_ERROR` | TECH-023 | re-verified + explicit no-stack-trace/no-original-message assertions |
| `SipsaIngestionValidationException` | 400 | `INGESTION_VALIDATION_ERROR` | none | MVC coverage added, `availableMethods` asserted |
| `MethodArgumentNotValidException` | 400 | `VALIDATION_ERROR` | TECH-023 | re-verified, `fieldErrors` asserted |
| `ConstraintViolationException` | 400 | `VALIDATION_ERROR` | none | MVC coverage added (new `@Validated` path-variable fixture endpoint) |
| `MethodArgumentTypeMismatchException` | 400 | `TYPE_MISMATCH` | none | MVC coverage added |
| `HttpMessageNotReadableException` | 400 | `INVALID_FORMAT` | none | MVC coverage added (malformed JSON on the existing `@RequestBody` fixture endpoint) |
| `MissingServletRequestParameterException` | 400 | `MISSING_PARAMETER` | none | MVC coverage added |
| `NoHandlerFoundException` / `NoResourceFoundException` | 404 | `NOT_FOUND` | none | `NoResourceFoundException` covered (unmapped route); `NoHandlerFoundException` specifically is presently unreachable — see note below |

**Not covered (out of `GlobalExceptionHandler`'s scope, confirmed no handler exists):**
`HttpRequestMethodNotSupportedException` (405) and `HttpMediaTypeNotSupportedException`
(415) have no `@ExceptionHandler` in this class — Spring Boot's default error machinery
handles them, so there is nothing of this class's contract to test.

**Note (documentation-only, not a defect):** the `handleNotFound` Javadoc states it
"Requires `spring.mvc.throw-exception-if-no-handler-found=true`," but neither
`application.yaml` nor any profile sets that property. In this Spring Boot version, an
unmapped route reaches `NoResourceFoundException` on its own (verified in the new
tests), so the practical case — unmapped route → 404 — is fully covered regardless;
`NoHandlerFoundException` itself is just never thrown given current configuration. No
code change made; behavior is already correct.

**Acceptance Criteria:**
- [x] Every `@ExceptionHandler` covered via real MVC dispatch (`@WebMvcTest`), not one
      test per handler in isolation — 15 cases in `GlobalExceptionHandlerContractTest`.
- [x] Each test verifies HTTP status, `Content-Type`, `code`, `message`, `requestId`,
      `instance`, a present `timestamp`, and (for the two handlers with extra fields)
      `fieldErrors`/`availableMethods`; the generic-exception and two 500 cases also
      assert no stack trace or original exception text leaks into the body.
- [x] `./mvnw clean verify` passes (311 tests, up from 296 — 15 new).

**Fixtures reused:** `RequestContextThrowingTestController` (introduced in TECH-023) was
extended with 8 new endpoints (`validation-error`, `ingestion-error`, `external-error`,
`configuration-error`, `ingestion-validation-error`, `type-mismatch/{id}`,
`missing-param`, `constraint-violation/{value}`) rather than creating a second,
near-duplicate fixture controller — it's now shared across TECH-023's and TECH-043's
tests. `ParseExceptionThrowingTestController` (TECH-021) was left as-is, unchanged.

**Defects found:** none. Every handler behaved exactly as documented; no production code
was changed in this story.

**Completed:** New `GlobalExceptionHandlerContractTest` (15 cases) closes the coverage
gap the table above documents — this is the first test in the repository to exercise
every `@ExceptionHandler` method in `GlobalExceptionHandler` through real MVC dispatch
with the full field contract (including TECH-023's `requestId`/`instance`) asserted on
each. No status code, error code, message, `ErrorResponse` shape, or `RequestIdFilter`
behavior was changed — this story is test-and-documentation only. No Flyway migration.

---

### TECH-044

**Title:** SPIKE — Evaluate WireMock and Testcontainers for integration testing  
**Type:** SPIKE  
**Priority:** Low  
**Phase:** 6  
**Status:** **Resolved**  
**Complexity:** M  
**Branch:** `spike/tech-044-comprehensive-testing-strategy`
**Dependencies:** None.

**Objective:** Determine the integration test tooling: WireMock 3.x vs `wiremock-spring-boot:4.x`, H2 vs Testcontainers. Produce a proof-of-concept test for `CiudadIngestionHandler`.

**Partial resolution (2026-07-14):** The H2-vs-Testcontainers half is settled by
[ADR-009](../adr/ADR-009-database-migration-strategy.md): Testcontainers with real
PostgreSQL is adopted and proven by `FlywayMigrationsTest` (dependencies already in
`pom.xml`, managed by the Spring Boot BOM). The remaining scope of this SPIKE is the
WireMock half (SOAP mocking strategy and the `CiudadIngestionHandler` proof of concept).

**Full resolution (2026-08-03):** [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md)
closes the WireMock half: combined WireMock (SOAP) + Testcontainers (PostgreSQL), one
integration test per handler, via a new Maven `integration-tests` Failsafe profile —
not the H2 fallback `testing-strategy.md` originally suggested (rejected: H2 cannot
exercise the real `ON CONFLICT ... DO NOTHING` upsert or `TIMESTAMPTZ`/`DATE` semantics
the persistence layer depends on). The proof-of-concept for `CiudadIngestionHandler`
itself, and the rest of the follow-on work this decision unblocks, is tracked as
TECH-150 through TECH-161, not as part of this SPIKE.

**Acceptance Criteria:**
- [x] Decision documented in [Testing Strategy](../architecture/testing-strategy.md) and [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md).
- [ ] One working proof-of-concept integration test for `promediosSipsaCiudad` — tracked as [TECH-151](#tech-151), not part of this SPIKE's closure.

**Completed:** 2026-08-03, branch `spike/tech-044-comprehensive-testing-strategy` (decision only — see TECH-150..161 for implementation).

---

### TECH-050

**Title:** Remove `// ...existing code...` placeholder comments from handlers  
**Type:** QA  
**Priority:** Low  
**Phase:** 1  
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `refactor/remove-existing-code-comments` (superseded from the originally
listed `fix/cleanup-placeholder-comments`, which also bundled TECH-051; the two were
split into separate branches — this story stayed single-purpose)

**Evidence (re-confirmed against `main` before removal):**
- `CiudadIngestionHandler.java:114`
- `SemanaIngestionHandler.java:102`
- `AbasIngestionHandler.java:122`
- `MesIngestionHandler.java:108`

All four identical in form and position: the first line of a `catch (Exception e)`
block, immediately before a `log.warn(...)` that already documents the
partial-progress-save behavior. `ParcialIngestionHandler` had no such marker (already
cleaned during TECH-011) — confirming exactly 4, not 5.

**Dependencies:** None.

**Acceptance Criteria:**
- [x] Zero occurrences of `// ...existing code...` in `src/main/`.
- [x] The surrounding catch blocks are reviewed for completeness — each comment
      documented nothing (no decision, no generated-code marker, no tooling
      requirement); the four `catch` blocks themselves are otherwise correct and
      untouched.
- [x] `./mvnw clean verify` passes.

**Completed:** 2026-07-19, branch `refactor/remove-existing-code-comments`. Pure
comment deletion — one line removed per file, zero logic/signature/import/test
changes (`git diff --word-diff` shows exactly the four `// ...existing code...`
lines removed). No Flyway migration.

---

### TECH-051

**Title:** Rename `toAuditEventRequest` to `toAuditEventResponse` in `IngestionAuditMapper`  
**Type:** QA  
**Priority:** Low  
**Phase:** 1  
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `refactor/rename-audit-mapper-response` (kept separate from TECH-050 per
explicit scope instructions, rather than the originally-listed shared
`fix/cleanup-placeholder-comments` branch)

**Evidence (re-confirmed against `main` before renaming):**
`IngestionAuditMapper.java:36`: `AuditTrailResponse.AuditEventResponse
toAuditEventRequest(IngestionAudit entity)` — a MapStruct interface method whose input
is the `IngestionAudit` entity and whose declared return type is
`AuditTrailResponse.AuditEventResponse` (a record: `auditId`, `runId`, `requestSource`,
`eventType`, `message`, `occurredAt`) — confirmed **not** equivalent to, and never
returning, `AuditEventRequest` (an unrelated class in `application/command`, used only
for logging audit events, never for reading them back). 4 internal callers, all method
references in `AuditTrailService` (lines 65, 92, 108, 134) — no reflection, no
string-based lookup, no external consumer (the interface lives in `api/mapper`, is
Spring-managed via `@Mapper(componentModel = "spring")`, and MapStruct resolves its
generated implementation by type signature, not by method name, so the rename does not
affect code generation).

**Acceptance Criteria:**
- [x] Method renamed to `toAuditEventResponse`.
- [x] All call sites updated (4 in `AuditTrailService`; class-level Javadoc corrected
      too — it said "IngestionAudit → AuditEventRequest", meant `AuditEventResponse`).
- [x] `./mvnw clean verify` passes.

**Completed:** 2026-07-19, branch `refactor/rename-audit-mapper-response`. No
deprecated alias kept (internal mapper, no external consumer). Mapping behavior
unchanged and now covered by `IngestionAuditMapperTest` (previously zero coverage —
`AuditTrailService`, the mapper's only consumer, is always mocked in existing tests),
written and first run against the pre-rename method name to pin the contract before
renaming: all fields map correctly, `requestSource` converts to its enum name
(null-safely), `occurredAt` converts to `OffsetDateTime` via `TimezoneUtil`. No Flyway
migration.

---

### TECH-052

**Title:** `IngestionControlService.getRun()` returns `Optional<IngestionRun>`  
**Type:** QA  
**Priority:** Low  
**Phase:** 1  
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `refactor/optional-ingestion-run` (kept its own branch; the originally-listed
`refactor/optional-return-types` name was not reused since this story stayed
single-purpose)

**Evidence (re-confirmed against `main` before changing anything):**
`IngestionControlService.java:262`: `getRun(long)` called
`runRepository.findById(runId).orElse(null)` — `findById` (`JpaRepository`) already
returns `Optional<IngestionRun>`, so the method discarded that `Optional` only to force
its single caller, `IngestionRunQueryService.getRunStatus` (line 86), to null-check it
right back. Two more `findById(runId).orElse(null)` sites exist in the same class
(`isRunCanceled` and `cancelRun`) but were confirmed **out of scope**: both return
`boolean`/`void`, not `IngestionRun`, so neither is part of `getRun()`'s contract nor
covered by this story's acceptance criteria — left untouched.

**Dependencies:** None.

**Acceptance Criteria:**
- [x] `getRun()` returns `Optional<IngestionRun>` — propagating the repository's own
      `Optional` directly (no `Optional.ofNullable`/`Optional.of` needed).
- [x] The one real caller updated: `IngestionRunQueryService.getRunStatus` now uses
      `.map(mapper::toDetailDto).orElseThrow(() -> {...})` — no unguarded `.get()`, no
      `.orElse(null)` reintroducing the nullable contract.
- [x] `./mvnw clean verify` passes.

**Completed:** 2026-07-19, branch `refactor/optional-ingestion-run`. Internal contract
change only: HTTP behavior identical (`SipsaBusinessException` → 422 on a missing run,
same message, same `log.warn`; TECH-022's HTTP 404 story is separate and untouched). No
`ErrorResponse`, endpoint, DTO, security, transaction, or repository query changed.
Previously zero test coverage of either service (both are always mocked in existing
controller/security tests) — `IngestionControlServiceGetRunTest` (present/absent) and
`IngestionRunQueryServiceGetRunStatusTest` (mapped response / exception with
`verifyNoInteractions(mapper)` on absence) now cover the explicit contract. No Flyway
migration.

---

### TECH-053

**Title:** Make scheduler dispatch ingestion jobs asynchronously  
**Type:** Correctiva  
**Priority:** Medium  
**Phase:** 2  
**Status:** Done  
**Complexity:** S  
**Branch:** `feat/async-scheduled-ingestion` (the originally-listed
`fix/scheduler-async-execution` name was not reused)

**Problem:**
`SipsaIngestionScheduler.runSafely()` calls `ingestionJob.execute()` synchronously, blocking
one of the 5 scheduler threads for the full duration of the ingestion (potentially hours for Parcial).

**Evidence (before):** `SipsaIngestionScheduler.java:130`: `ingestionJob.execute(request)`

**Diagnosis (before implementing):**

| Scheduler method | Cron/window | Request source | Job called | Thread (before) | Dispatch (after) |
|---|---|---|---|---|---|
| `runDailyWindow()` | `0 20 14 * * *` (14:20 COT) | SCHEDULED | Ciudad, Parcial, Semana — 3 separate `IngestionRequest`s, sequential | scheduler thread, blocked for all 3 | `dispatcher.dispatchDailyWindow()` — one async call, still sequential inside it |
| `runMonthlyMes()` | `0 30 14 8 * *` (day 8) | SCHEDULED | MesMadr — 1 request | scheduler thread, blocked | `dispatcher.dispatchMonthlyMes()` — one async call |
| `runMonthlyAbas()` | `0 30 14 10 * *` (day 10) | SCHEDULED | AbasMes — 1 request | scheduler thread, blocked | `dispatcher.dispatchMonthlyAbas()` — one async call |

- **Overlap prevention:** already exists, unrelated to threading — the real
  `uq_ingestion_runs_window UNIQUE (method_name, window_key)` constraint (V1),
  translated by `IngestionControlService.createRun` into `SipsaBusinessException`,
  which `IngestionJob.execute` already treats as a controlled skip (audited, not
  thrown). Confirmed unchanged by this story; regression-tested (see below).
- **Executor:** the existing `ingestionTaskExecutor` (`AsyncConfig`, pool 2/10/25/60s,
  `CallerRunsPolicy`) — already used by manual-trigger ingestion
  (`AsyncIngestionService.executeAsync`) and audit logging
  (`IngestionAuditService.logEvent`). No new executor created.
- **Saturation:** `CallerRunsPolicy` means a saturated pool runs the rejected task on
  the *calling* thread — here, the scheduler thread, which would then block. This is
  accepted, existing backpressure (governs the other two `ingestionTaskExecutor`
  consumers identically), not eliminated by this story — documented precisely, not
  overclaimed (see `ScheduledIngestionDispatcher`'s Javadoc and ADR-005's Resolution).
- **Error registration:** unchanged — `runSafely`'s existing try/catch/`log.error`,
  now living in `ScheduledIngestionDispatcher` instead of `SipsaIngestionScheduler`.
- **Metrics/audit:** both already flow through `IngestionJob.execute`'s existing
  paths (`IngestionMetrics.recordRunCompleted`, `IngestionAuditService.logEvent`),
  called exactly once per `execute()` invocation — dispatching asynchronously
  doesn't change how many times `execute()` is called, only which thread calls it.
- **Shutdown:** `sipsa.scheduling.await-termination-seconds` (scheduler pool) and the
  async executor's own graceful shutdown are both pre-existing and untouched.

**Deviation from the original "Objective" and ADR-005's literal Option A sketch
(documented, not silent):** neither said to reuse `asyncIngestionService.executeAsync(request)`
called once per method. Doing so would dispatch the daily window's three methods as
three *independent* async calls, letting them race on the pool (core size 2) —
silently breaking the sequential, resource-contention-avoiding execution this same
story's own evidence describes as intentional current behavior. Instead: a new
`ScheduledIngestionDispatcher` dispatches **per window**, not per method —
`dispatchDailyWindow()` runs all three sequentially inside one `@Async` call, on one
worker thread. `AsyncIngestionService` (manual-trigger path) is untouched — different
concern, different caller, not reused here. Full reasoning recorded in
[ADR-005](../adr/ADR-005-scheduler-execution-model.md)'s Resolution section, now
**Accepted**.

**Acceptance Criteria:**
- [x] `runDailyWindow()` returns without waiting for ingestion to finish (not a literal
      "< 200ms" stopwatch assertion — proven structurally in
      `ScheduledIngestionAsyncDispatchTest` via a blocked-latch job that hasn't
      finished by the time the dispatch call returns; verified live in Docker, where
      the scheduler's log line and the async thread's first log line are ~100ms apart
      and the scheduler was never observed blocked).
- [x] Ingestion jobs run in the `ingestionTaskExecutor` pool — verified by real thread
      name (`ingestion-async-*`) in both the Spring-context test and Docker.
- [x] Logs include `requestSource=SCHEDULED` — unchanged, confirmed in Docker
      (`"requestSource":"SCHEDULED"` on all three runs).
- [x] `./mvnw clean verify` passes (334 tests, up from 327 — 7 net new).

**Completed:** `SipsaIngestionScheduler` no longer depends on `GenericIngestionJob` at
all — each `@Scheduled` method logs and calls exactly one method on the new
`ScheduledIngestionDispatcher` (a separate bean, avoiding `@Async` self-invocation),
which does the actual dispatch. Request construction (`IngestionRequest.scheduled`,
UUID `requestId`), `RequestSource.SCHEDULED`, per-method try/catch/`log.error`
failure containment, and the exact Ciudad → Parcial → Semana sequential order are all
unchanged — only relocated from `SipsaIngestionScheduler` to
`ScheduledIngestionDispatcher`. Tests: `SipsaIngestionSchedulerTest` (3 cases, rewritten —
verifies the scheduler only delegates, never touches `GenericIngestionJob`),
`ScheduledIngestionDispatcherTest` (9 cases — the old scheduler test's 8 cases moved
here verbatim (order, flags/UUID, monthly correctness, failure containment), plus one
new case proving the existing overlap-protection path survives unmodified, using a
real `GenericIngestionJob` with mocked collaborators), `ScheduledIngestionAsyncDispatchTest`
(3 cases, real `@SpringBootTest` context — the only way to actually exercise the
`@Async` proxy and real `ingestionTaskExecutor`: dispatch-returns-before-completion via
a controlled latch, thread name assertion, multiple dispatches produce one execution
each with no drops/duplicates, and the daily window's three methods confirmed to run
sequentially on a single thread even when dispatched asynchronously). Verified in
Docker with a real cron-fired trigger (daily window's start/end bounds and the cron
itself were overridden via env vars for this one verification run only — neither
`application.yaml` nor `docker-compose.yml` changed — so a genuine window-validated
run could occur without waiting for the real 14:20 COT trigger): scheduler thread
logged the trigger and returned; all three methods then ran, in order, on a single
`ingestion-async-1` thread; three runs created (`requestSource: SCHEDULED`, no
duplicates); audit events (`INGESTION_STARTED`/`RUNNING`/`FAILED`/`METRICS_UPDATED`)
present exactly once per run; `sipsa.ingestion.runs` metric shows `COUNT: 3.0`
(`source=scheduled`, no duplication); `/actuator/health` unaffected throughout. No
cron expression, window logic, HTTP contract, pagination, repository, deduplication,
metric semantics, audit event shape, database, or Flyway change.

---

### TECH-054

**Title:** Add pagination to `GET /api/internal/ingestion/runs`  
**Type:** Correctiva  
**Priority:** Low  
**Phase:** 2  
**Status:** Done  
**Complexity:** S  
**Branch:** `feat/paginate-ingestion-runs` (the originally-listed
`fix/runs-endpoint-pagination` name was not reused)

**Evidence (before):** `IngestionRunQueryService.java:68`: `controlService.findAllRuns()`
without `Pageable` — `SipsaOpsController.getAllRuns()` returned a bare
`List<IngestionRunDetailResponse>` with every row in `ingestion_runs`.

**Dependencies:** None.

**Diagnosis (before implementing):**

| Layer | Current method | Current return | Problem | Proposed contract |
|---|---|---|---|---|
| `SipsaOpsController.getAllRuns()` | `GET /api/internal/ingestion/runs`, no params | `ResponseEntity<List<IngestionRunDetailResponse>>` | bare array, unbounded | `ResponseEntity<ApiResponse<IngestionRunDetailResponse>>` |
| `IngestionRunQueryService.getAllRuns()` | no args | `List<IngestionRunDetailResponse>` (`.stream().map(...).toList()`) | maps the full list, no pagination | `getAllRuns(IngestionRunQueryRequest)` → `Page<IngestionRunDetailResponse>` via `Page.map(mapper::toDetailDto)` |
| `IngestionControlService.findAllRuns()` | no args | `List<IngestionRun>` (`runRepository.findAll()`) | unbounded `findAll()` | deleted (no callers left); `findAllRuns(Pageable)` — **already existed, unused** — is now the sole overload |
| `IngestionRunRepository` | `JpaRepository<IngestionRun, Long>` | n/a | — | `findAll(Pageable)` inherited for free, no custom `@Query` needed |

- **Existing paginated wrapper standard, confirmed before designing anything:**
  `ApiResponse<T>` (`{count, next, prev, pages, results}`, `next`/`prev` omitted when
  null) + `PaginationUtils.toApiResponse(Page<T>)`, already used by `GET /api/sipsa/*`
  and `GET /api/internal/audit/all`. Reused as-is — no second pagination format
  invented, and the generic `{content, page, size, totalElements, totalPages, first,
  last}` shape considered up front was deliberately abandoned once this convention was
  found.
- **Existing `page`/`size` DTO convention, confirmed before designing validation:**
  every `*QueryRequest` record (`CiudadQueryRequest`, `AuditQueryRequest`) clamps
  out-of-range `page`/`size` in its compact constructor rather than rejecting them.
  `IngestionRunQueryRequest` follows the identical pattern (`page < 1` → 1, `size < 1`
  → 20, `size > 100` → 100) instead of introducing a new, inconsistent validation
  behavior for just this one endpoint. A genuinely malformed (non-numeric) value is
  unaffected by clamping and still produces the pre-existing 400 `VALIDATION_ERROR`
  contract (`requestId`, `instance`, `fieldErrors`, no stack trace) — verified with a
  dedicated MVC test.
- **Order:** no existing order was documented for this endpoint (`findAll()` returned
  whatever the database happened to return). Chose `startTime DESC, runId DESC` —
  most-recent-first, with `runId` as a deterministic tie-breaker for equal
  `startTime` values, so no page can duplicate or omit a run. Built via
  `PaginationConfig.buildPageable(page, size, null)` for its existing
  defaulting/validation behavior, then wrapped in a fresh two-column `Sort` —
  `buildPageable`'s `sort` parameter only supports a single field, and `PaginationConfig`
  itself (shared by other endpoints) was left untouched.
- **Consumers:** grepped `README.md` and `CONTRIBUTING.md` — the only two references
  to this endpoint outside the code. `README.md` documented the bare-array shape (now
  updated); `CONTRIBUTING.md`'s `curl` example doesn't assert a response shape. No
  evidence of a consumer requiring the old array preserved, so no dual endpoint and no
  `unpaged=true` parameter were added.

**Acceptance Criteria:**
- [x] `GET /api/internal/ingestion/runs` accepts `page` and `size` query parameters
      (default `page=1`, `size=20`, maximum `size=100`).
- [x] Default: returns the most recent runs first (`startTime DESC, runId DESC`), page
      1 of size 20, when no parameters are provided — not "the most recent 50 runs"
      literally, since that was the pre-pagination assumption the diagnosis
      superseded once the repo's real `page`/`size`/default-20 convention was found;
      any of the most recent runs are reachable by paging.
- [x] `./mvnw clean verify` passes (362 tests, up from 334 — 28 net new).

**Completed:** Contract change for consumers: the response body changed from a bare
JSON array to the existing `ApiResponse<IngestionRunDetailResponse>` envelope —
consumers reading a raw array must switch to reading `.results`. Layers modified:
`SipsaOpsController` (now builds `ApiResponse` via `PaginationUtils.toApiResponse`),
new `IngestionRunQueryRequest` record (query-bound, clamps `page`/`size`),
`IngestionRunQueryService.getAllRuns(IngestionRunQueryRequest)` (builds the fixed-sort
`Pageable`, delegates, maps `Page<IngestionRun>` → `Page<IngestionRunDetailResponse>`),
`IngestionControlService` (deleted the dead unpaged `findAllRuns()`; the paginated
`findAllRuns(Pageable)` overload already existed and needed no change). The repository
needed zero new code: `IngestionRunRepository.findAll(Pageable)` is Spring Data JPA's
built-in paginated finder — confirmed via a real PostgreSQL query-count assertion (see
below) that it issues exactly one `SELECT ... ORDER BY ... LIMIT ? OFFSET ?` and one
`SELECT COUNT(...)`, never one query per row. No `findAll()` + in-memory `subList`
anywhere in the new code. Tests: `IngestionRunQueryServiceGetAllRunsTest` (11 cases —
empty/first/intermediate/last page, the fixed two-column stable order, entity→DTO
mapping, `totalElements`/`totalPages` propagation, page/size forwarded correctly,
confirms the no-arg `findAllRuns()` overload no longer exists on
`IngestionControlService`), `SipsaOpsControllerRunsPaginationTest` (11 cases — no-param
defaults forwarded as page=1/size=20, explicit page/size, real content, empty page,
clamped negative-page/zero-size/over-max-size (all still 200, clamped not rejected),
401 without a token, 403 with the wrong scope, the malformed-`size` 400 error
contract, and the `ApiResponse` envelope shape), `IngestionRunPaginationTest` (6 cases,
real PostgreSQL via Testcontainers, no H2 — only the requested page size is fetched
regardless of table size, order is stable and repeatable, total count is correct, the
union of every page across a full pagination walk equals the full inserted set with no
duplicates, equal-`startTime` rows still tie-break deterministically by `runId`, and a
Hibernate-statistics assertion proves a paginated fetch issues exactly 2 SQL statements
whether the table has 5 or 40 rows). Verified in Docker: clean startup; 25 runs seeded directly via SQL (a pure read endpoint needs no real DANE
SOAP call to verify); `page=1&size=10`, `page=2`, and `page=3` (partial last page)
each returned correct, non-overlapping, `startTime DESC`-ordered slices with correct
`next`/`prev` links and `count=25`, `pages=3`; the no-params call defaulted to
`size=20` (`pages=2`); 401 without a token; 403 with `sipsa/audit.read`;
`/actuator/health` and `/actuator/metrics` unaffected. `IngestionRunQueryServiceGetRunStatusTest`
and `InternalEndpointSecurityTest` updated only for the new constructor
signature/return type (`PaginationConfig` dependency; `Page.empty()` stub for
Mockito's default answer, which doesn't cover `Page`) — no behavior change in either.
No scheduler, TECH-053, metrics, audit, run-execution, cancellation, TECH-060, SIPSA
data repository, SOAP, general security, or database schema change.

---

### TECH-055

**Title:** SPIKE — `isMonthly()` method in `IngestionHandler` contract  
**Type:** SPIKE  
**Priority:** Low  
**Phase:** 6  
**Status:** Done
**Complexity:** S  
**Branch:** `spike/evaluate-is-monthly-contract` (the originally-listed
`spike/ingestion-handler-contract` name was not reused)
**Dependencies:** None (SPIKE — investigation only).

**Problem (before):** `WindowPolicy.isMonthlyMethod()` uses string matching (`contains("mesmadr")`, `contains("abas")`).
New monthly handlers with different naming patterns will silently use daily scheduling.

**Objective:** Decide whether to add `boolean isMonthly()` to `IngestionHandler` interface or document the naming convention. See [ADR-006](../adr/ADR-006-ingestion-handler-contract.md).

**Correcting the premise (found during the audit, before answering the objective):**
`isMonthly()` does not exist anywhere in the codebase today — not on `IngestionHandler`,
not on any implementation. The real current mechanism is
`WindowPolicy.resolveMonthlyRule(String)`, which has evolved (TECH-111) into something
**richer than a boolean**: it returns `Optional<MonthlyRule>`, a 3-field record
(principal day, grace day, window-key suffix) — a bare boolean was already insufficient
for `WindowPolicy`'s own internal use before this SPIKE started.

**A second, independent classification site was found, not identified by ADR-006's
original problem statement:** `SipsaHealthIndicator.DAILY_METHODS` — a hardcoded `Set` of
exact method names, used to pick daily-vs-monthly staleness thresholds, with **zero
shared source of truth** with `WindowPolicy`. This is the actual, concrete drift risk
found by this SPIKE, not the hypothetical one ADR-006 originally described.

**Decision: Recommended — Option D ("move the decision out of the handler," i.e., keep
it out of `IngestionHandler` and consolidate the two existing independent
implementations into one).** A literal `boolean isMonthly()` on `IngestionHandler`
(ADR-006's original Option A) would be insufficient (loses day/key information for the
monthly case) and would not fix the actual duplication found (`SipsaHealthIndicator`
never touches `IngestionHandler`). Full comparison of all 5 options (A–E), the boolean-
blindness analysis (concluding `WindowPolicy`'s own daily/monthly split is *not*
boolean-blind — `false` is a single coherent category, confirmed by reading the two
validation branches directly), the reverted working prototype, and the weighted decision
matrix are in the
[full SPIKE report](../architecture/spikes/TECH-055-is-monthly-contract.md).
[ADR-006](../adr/ADR-006-ingestion-handler-contract.md) updated from `Proposed` to
`Accepted` (Option D), reversing its original tentative Option A recommendation.

**Follow-up (separate story, not implemented here):** add `public boolean
isMonthlyMethod(String)` to `WindowPolicy` (delegating to the existing private rule
resolution, no behavior change); inject `WindowPolicy` into `SipsaHealthIndicator` and
remove its independent `DAILY_METHODS` set; add one contract test asserting the two call
sites cannot silently disagree. `IngestionHandler` and its 5 implementations are
untouched by this follow-up.

**Acceptance Criteria:**
- [x] Inventory of all 5 `IngestionHandler` implementations and every consumer of the
      daily/monthly classification.
- [x] Explicit recommendation among A/B/C/D/E, not "it depends": **D**.
- [x] A controlled experiment verified the recommendation, then was fully reverted
      (`git status --short` clean before the final commit — confirmed).
- [x] No source code changed as part of this SPIKE's final commit.
- [x] ADR-006 updated to `Accepted`.

**Completed:** Full report at
[docs/architecture/spikes/TECH-055-is-monthly-contract.md](../architecture/spikes/TECH-055-is-monthly-contract.md) —
inventory (5 handlers, their real method names, and what actually consumes the
classification), the corrected premise (`isMonthly()` doesn't exist; the real mechanism
is richer than a boolean), the second independent classification site found
(`SipsaHealthIndicator.DAILY_METHODS`), the boolean-blindness analysis (not blind for
`WindowPolicy`'s own decision; would become blind if Option A were implemented
literally), all 5 options compared against the requested weighted criteria, a reverted
working prototype (`WindowPolicy.isMonthlyMethod(String)` + `SipsaHealthIndicator`
refactored to consume it — all 6 existing `SipsaHealthIndicatorTest` cases passed
unmodified, proving zero behavior change, then fully reverted via `git checkout --`), the
existing-test coverage matrix (one real gap identified: no test today asserts
`WindowPolicy` and `SipsaHealthIndicator` can't drift), and the scoped follow-up story
definition. `IngestionHandler` and all 5 handler implementations are completely
untouched — this story changed no production code. No scheduler, window, metrics,
audit, repository, API, security, SOAP, database, or AWS infrastructure change.

---

### TECH-056

**Title:** Consolidate monthly-method classification in `WindowPolicy`
**Type:** Refactor
**Priority:** Medium
**Phase:** 6
**Status:** Done
**Complexity:** S
**Branch:** `refactor/consolidate-monthly-method-classification`
**Dependencies:** [TECH-055](#tech-055) (SPIKE, Done) — this story implements exactly the
follow-up TECH-055 scoped, no more.

**Problem (found by TECH-055, not assumed here):** `WindowPolicy.resolveMonthlyRule`
(private, substring-matched, richer than a boolean) and
`SipsaHealthIndicator.DAILY_METHODS` (a hardcoded `Set` of exact method names) were two
completely independent daily/monthly classification implementations, with **zero shared
source of truth**. If a 6th handler were ever added and one site updated but not the
other, the two would silently disagree — `WindowPolicy` would validate its window
correctly while `SipsaHealthIndicator` applied the wrong staleness threshold, or vice
versa.

**Diagnosis:**

| Method | Classification in `WindowPolicy` (before and now) | Classification in `HealthIndicator` (before) | Expected (and achieved) result |
|---|---|---|---|
| `promediosSipsaCiudad` | no monthly | in `DAILY_METHODS` → daily threshold | not monthly → daily |
| `promediosSipsaParcial` | no monthly | in `DAILY_METHODS` → daily threshold | not monthly → daily |
| `promediosSipsaSemanaMadr` | no monthly (weekly *data*, daily *scheduling cadence*) | in `DAILY_METHODS` → daily threshold | not monthly → daily |
| `promediosSipsaMesMadr` | monthly (`MES_MADR_RULE`, day 8/9, key `M8`) | NOT in `DAILY_METHODS` → monthly threshold | monthly → monthly |
| `promedioAbasSipsaMesMadr` | monthly (`ABAS_RULE`, day 10/11, key `M10`) | NOT in `DAILY_METHODS` → monthly threshold | monthly → monthly |

Methods are identified by exact `String` name (no alias, no enum, no wrapper type) — the
same convention every other part of this codebase uses (`IngestionHandler.getMethodName()`
returns `String`; no `IngestionMethod`/`MethodName`/`SipsaMethod` type exists anywhere,
confirmed by a fresh grep before deciding the signature — introducing one now would widen
this story's scope for no behavioral benefit). `SipsaHealthIndicator` only ever needed a
binary "is this monthly" answer, never `WindowPolicy`'s richer per-rule payload
(principal day, grace day, key suffix) — so the new query returns `boolean`, not
`Optional<MonthlyRule>`, and `MonthlyRule` itself stays a private implementation detail of
`WindowPolicy`, never exposed.

**Design:** `WindowPolicy` gains exactly one new public method:

```java
public boolean isMonthlyMethod(String methodName) {
    return resolveMonthlyRule(methodName).isPresent();
}
```

`SipsaHealthIndicator` is constructor-injected with `WindowPolicy` and calls
`windowPolicy.isMonthlyMethod(method)` in place of `DAILY_METHODS.contains(method)` —
`DAILY_METHODS` is deleted entirely, not deprecated or left dead.

**Unrecognized method — explicit behavior decision (documented, not silent):** an
ingestion method name `WindowPolicy` does not recognize at all now gets the **daily**
threshold (36h), matching `WindowPolicy`'s own "unrecognized → not monthly" convention
(`resolveMonthlyRule` returns `Optional.empty()`, the same outcome an unrecognized method
already gets from `validateAndGetKey` today — it falls through to `validateDaily`). This
is a narrow change from `SipsaHealthIndicator`'s *previous*, **untested**, independent
fallback (unrecognized → monthly/840h, the `else` branch of its own now-deleted ternary).
Per the explicit instruction to match `WindowPolicy`'s current contract for this query,
and since (a) no existing test locked in the old fallback as an intentional contract and
(b) all 5 real, registered methods classify identically before and after — this is
treated as a deliberate, narrow, fully-tested refinement, not a hidden behavior change.

> **Intentional functional change for unregistered methods. Does not affect the five
> currently supported methods. The new behavior matches `WindowPolicy`'s explicit
> convention.**

**Acceptance Criteria:**
- [x] `WindowPolicy.isMonthlyMethod(String)` is public, delegates to the existing rule
      table, no change to `validateAndGetKey`'s own behavior.
- [x] `SipsaHealthIndicator.DAILY_METHODS` is removed; classification is sourced from
      `WindowPolicy` exclusively — confirmed structurally (a reflection-based test
      asserts zero `Collection`-typed fields remain on the class) and behaviorally (a
      mocked-`WindowPolicy` test proves the same method/age flips DOWN↔UP purely from
      `WindowPolicy`'s answer, not from comparing two lists).
- [x] All 5 real methods keep their exact prior thresholds — verified by unit tests and a
      real Docker smoke test (see below).
- [x] `IngestionHandler`'s interface and all 5 implementations are untouched.
- [x] ArchUnit (TECH-093) needed no new exclusion — none of its 3 rules restrict
      `infrastructure → application` (the new `SipsaHealthIndicator → WindowPolicy`
      dependency direction), confirmed by re-running `PackageBoundaryArchitectureTest`
      (3/3 green, unmodified).
- [x] `./mvnw clean verify` passes (441 tests, up from 430 — 11 net new).

**Completed:** `WindowPolicy.isMonthlyMethod(String)` added (6 lines, delegates to the
existing private `resolveMonthlyRule`). `SipsaHealthIndicator` constructor-injected with
`WindowPolicy`; `DAILY_METHODS` field deleted; its one call site updated. Stale Javadoc
in `SipsaHealthProperties` (2 references to `SipsaHealthIndicator.DAILY_METHODS`) and
`SipsaHealthIndicator`'s own class-level Javadoc updated to describe the new,
`WindowPolicy`-sourced classification instead of a hardcoded list. Tests:
`WindowPolicyTest.IsMonthlyMethodClassification` (7 cases — all 5 real methods
individually, an unrecognized method returns `false` without throwing, and an explicit
cross-check that `isMonthlyMethod` agrees with `validateAndGetKey`'s own classification
for two real methods). `SipsaHealthIndicatorTest`: its 6 pre-existing cases updated to
mock the now-injected `WindowPolicy` (behaviorally unchanged — same thresholds, same
verdicts) plus 4 new cases — a genuine-dependency proof (identical method name and age,
`WindowPolicy`'s mocked answer alone flips the health verdict DOWN↔UP), the
unrecognized-method case (daily threshold applies, explicitly asserted), a structural
regression test (reflection over `getDeclaredFields()` asserting zero
`java.util.Collection`-assignable field exists on the class — fails on *any* future
hardcoded collection reintroduced, not a name-specific grep), and a constructor-dependency
check (`WindowPolicy` is a declared constructor parameter type). Verified in Docker: clean
startup, no wiring/context errors; two rows seeded directly via SQL
(`promediosSipsaCiudad` daily, `promediosSipsaMesMadr` monthly) aged to 40 hours —
`/actuator/health`'s `sipsa` component correctly flagged only the daily method `STALE`
(exceeds the 36h threshold) while the monthly method stayed fresh (well under 840h),
proving the consolidated classification is applied correctly, differently, per method, in
the real running application; `/actuator/metrics` and the scheduler startup log
unaffected. No scheduler, window-validation, cron, metrics, audit, endpoint, HTTP
contract, SOAP, security, or persistence change. No Flyway migration; no remote
database access.

---

### TECH-060

**Title:** Fix N+1 query in `SipsaMayoristasSemanalRepository.upsertFallbackBatch()`  
**Type:** Performance  
**Priority:** Medium  
**Phase:** 4  
**Status:** Done  
**Complexity:** M  
**Branch:** `perf/remove-mayoristas-fallback-n-plus-one` (the originally-listed
`fix/batch-upsert-n-plus-one` name was not reused)

**Dependencies:** None.

**Problem (before):**
`upsertFallbackBatch()` called `findByBusinessKeys(artiId, fuenId, fechaIni)` individually
for each (deduplicated) record in the batch, producing N database queries for N records.

**Evidence (before):** `SipsaMayoristasSemanalRepository.java:148` (pre-TECH-060):
`findByBusinessKeys()` called inside a `for` loop over the deduplicated batch.

**Diagnosis (before implementing):**

| Step | Current query | Times per batch | Semantics | Replacement |
|---|---|---:|---|---|
| Intra-batch dedup | none (in-memory `LinkedHashMap`, string-concat key) | 0 | last occurrence wins, dropped duplicates uncounted | unchanged, keyed by a `record BusinessKey(artiId, fuenId, fechaIni)` instead of string concatenation |
| Existence check | `findByBusinessKeys(artiId, fuenId, fechaIni)` (JPQL) | 1 per unique item (N) | `WHERE a=:a AND b=:b AND c=:c` — a `null` param never matches (always "not found") | removed entirely |
| Insert | `saveAll(toInsert)` + `flush()` (JPA) | 1 (batched by Hibernate) | plain insert of the non-existing subset | replaced together with the existence check by one `INSERT … ON CONFLICT (arti_id, fuen_id, fecha_ini) DO NOTHING` JDBC batch |

- **Fallback trigger:** `SemanaIngestionHandler.flushNoTmp()` — records from
  `promediosSipsaSemanaMadr` whose `tmpMayoSemId` is null (in practice, per the SOAP
  contract, this is the common case — the real Docker verification run below shows
  100% of records went through this exact path).
- **Real batch size:** `ingestionProperties.getBatchSize()` (`INGESTION_BATCH_SIZE`,
  default 500, TECH-071) — the handler already rejects (never queues) any record
  missing `artiId`/`fuenId`/`fechaIni` before it reaches the repository, so in
  production every candidate has a complete key; the repository's own `null`-key
  handling is still real, tested, general-purpose behavior (a public method, not
  gated by the one current caller).
- **Natural key:** `(artiId, fuenId, fechaIni)`, backed by
  `CONSTRAINT ux_semana_fallback UNIQUE (arti_id, fuen_id, fecha_ini)` (V1) — none of the
  three columns is `NOT NULL` at the database level (the JPA `@Column(nullable = false)`
  annotations are aspirational/unenforced, a pre-existing, unrelated mismatch — `ddl-auto
  =validate` does not reject it, confirmed unchanged, out of scope).
- **Queries executed per record (before):** 1 SELECT (`findByBusinessKeys`), unless it was
  an intra-batch duplicate of an already-seen key (0).
- **Inserts:** via `saveAll`, one JPA `INSERT` per new row (Hibernate-batched, already
  bounded — untouched by this story).
- **Updates:** none — existing rows are always skipped, never updated (unchanged).
- **Skips:** existing rows (`findByBusinessKeys` returns a row) or, for the batch-internal
  case, discarded silently during dedup (see below).
- **Intra-batch duplicates:** collapsed to the *last* occurrence via a
  `LinkedHashMap`-style dedup **before** the existence check — collapsed entries are not
  counted as `inserted` or `skipped` at all (`inserted + skipped` equals the number of
  *unique* keys in the batch, not `items.size()`). Confirmed as existing, deliberate
  behavior — preserved exactly, not "fixed" to match `SipsaParcialRepository.batchUpsert`'s
  own (different) convention of counting intra-batch duplicates as `skipped`.
- **Duplicates already in the database:** skipped (never updated), via the existence
  check.
- **Concurrent collisions:** unprotected before this story beyond the `ux_semana_fallback`
  constraint itself — a lost race between the SELECT and `saveAll` surfaced as an
  uncaught `DataIntegrityViolationException` that discarded the whole batch (the same
  gap `SipsaParcialRepository.batchUpsert` had before TECH-117).
- **Transaction:** `@Transactional` on the default method, unchanged.
- **Counts returned:** `UpsertMetrics(inserted, skipped)`, semantics preserved exactly
  (see intra-batch note above).
- **Existing tests:** none dedicated to `upsertFallbackBatch()` before this story (only an
  incidental reference in `SipsaDecimalPrecisionAlignmentTest`, unrelated).
- **Indexes/constraints:** `ux_semana_fallback UNIQUE (arti_id, fuen_id, fecha_ini)` and
  `ux_semana_tmp UNIQUE (tmp_mayo_sem_id)` (both V1) — the former is exactly the
  conflict target `ON CONFLICT` needs; the latter backs the separate, untouched
  `upsertTmpBatch` path.

**Deviation from the original Objective (documented, not silent):** the Objective said
"similar to `SipsaCiudadRepository.batchUpsert()`" — a bulk `SELECT … WHERE CONCAT(...) IN
:keys` existence-check query, still O(1) round trips but still followed by a plain
`saveAll` (still exposed to the same lost-race exception `SipsaParcialRepository
.batchUpsert` had before TECH-117). Since `ux_semana_fallback` already exists as a real,
compatible unique constraint, this story instead mirrors **`SipsaParcialRepository
.batchUpsert()`** (TECH-117): a single `INSERT … ON CONFLICT (arti_id, fuen_id, fecha_ini)
DO NOTHING` JDBC batch, via a new `SipsaMayoristasSemanalBatchInsertRepository` fragment
(same shape as `SipsaParcialBatchInsertRepository`). This removes the existence-check
query entirely (0 SELECTs, not 1) and closes the concurrency gap atomically instead of
merely "reducing" it — a strictly stronger fix than the literal suggestion, using a
technique already established and tested in this exact codebase.

**Objective:** Refactor to bulk-fetch all existing records in one query (as achieved:
folded into a single atomic insert, zero separate existence-check queries).

**Acceptance Criteria:**
- [x] `upsertFallbackBatch()` executes **0 SELECTs** and **1 INSERT (JDBC batch)** for any
      batch size — stronger than the literal "1 SELECT and 1 INSERT", since the existence
      check and the insert are the same statement.
- [x] Behavior (skip existing, insert new) is unchanged — verified by 12 dedicated tests
      (functional, structural, concurrency) plus a real 229,369-record Docker ingestion
      run followed by an identical re-run that inserted 0 and skipped all 229,369 with no
      duplicate rows.
- [x] `./mvnw clean verify` passes (374 tests, up from 362 — 12 net new).

**Follow-up (documented, not modified in this branch):** the identical N+1 pattern exists
in `SipsaMayoristasMensualRepository.upsertFallbackBatch()` and
`SipsaAbastecimientosMensualRepository.upsertFallbackBatch()` (both call their own
per-item `findByBusinessKeys` in a loop, same shape as the code this story replaced).
Out of scope here per explicit instruction; a future story should apply the identical
`ON CONFLICT` technique to both, contingent on each table having a compatible unique
constraint on its own business key (not verified here — out of scope).

**Completed:** New `SipsaMayoristasSemanalBatchInsertRepository` fragment interface +
`SipsaMayoristasSemanalBatchInsertRepositoryImpl` (raw `JdbcTemplate.batchUpdate`,
`INSERT … ON CONFLICT (arti_id, fuen_id, fecha_ini) DO NOTHING`, mirroring
`SipsaParcialBatchInsertRepositoryImpl` exactly — same parameter-limit reasoning,
`reWriteBatchedInserts` left disabled so the driver still returns exact per-row update
counts). `SipsaMayoristasSemanalRepository.upsertFallbackBatch()` rewritten: unchanged
in-memory dedup (now keyed by a `BusinessKey(artiId, fuenId, fechaIni)` record instead of
string concatenation — same collapsing behavior, `null`-safe by construction), then one
call to `insertIgnoringConflicts` in place of the per-item `findByBusinessKeys` loop and
the separate `saveAll`/`flush`. The now-dead `findByBusinessKeys` query method (no
remaining callers) was deleted. `upsertTmpBatch`/`findByTmpId` (the *other* upsert
strategy, tmpId-based) are completely untouched. Tests (all in
`SipsaMayoristasSemanalFallbackUpsertTest`, real PostgreSQL via Testcontainers — `ON
CONFLICT` and the constraint are PostgreSQL-specific, no H2): empty batch; one new
record; several new records; all-existing batch; mixed new/existing; intra-batch
duplicates (asserts the *silently-uncounted* semantics, not Parcial's counted-as-skipped
convention); `null` business-key components (always insert, both across two separate
calls and collapsed correctly within one batch); skip-never-updates (an existing row's
stored price and `fecha_sincronizacion` are provably untouched by a "skip"); rollback
(no row survives a rolled-back transaction); a structural test asserting **zero**
Hibernate-tracked query executions at batch sizes 1, 10, and 100 (the old
`findByBusinessKeys` path was Hibernate/JPQL, so this is a direct, concrete refutation of
the N+1 — the new path never touches Hibernate for this operation at all); two
concurrency tests — a real two-transaction race (loser reports `skipped`, never throws,
exactly one row survives) and a direct proof that `ux_semana_fallback` is real and backs
the conflict target. Verified in Docker: clean startup; a real
`promediosSipsaSemanaMadr` ingestion run (229,369 records seen, 229,369 inserted, 0
rejected, 0 SQL errors, `sipsa.ingestion.runs` metric incremented,
`/actuator/health` UP); an immediate identical re-run (`force=true`, reusing the same
window per existing `IngestionControlService` "restart" behavior — unrelated to this
story) inserted 0 and correctly reported 0 rows changed, with the stored row count
unchanged at 229,369 and zero duplicate `(arti_id, fuen_id, fecha_ini)` combinations —
direct, real-data proof the atomic skip-existing path works correctly at production
scale. No scheduler, TECH-053, TECH-054, API, pagination, metrics, audit, SOAP, security,
threshold, `SipsaParcial` deduplication, or AWS infrastructure change.

---

### TECH-070

**Title:** Add Bean Validation to `SoapProperties`  
**Type:** Config  
**Priority:** Low  
**Phase:** 1  
**Status:** **Done**  
**Complexity:** XS  
**Branch:** `refactor/validate-soap-properties` (the originally-listed
`refactor/config-validation` name was not reused)

**Evidence (re-confirmed against `main` before changing anything):** `SoapProperties.java`
had no `@Validated`, no field-level constraints on any of its 9 fields. Only 4 were
checked at all — imperatively, inside `SipsaSoapClientConfig`'s `@Bean` factory method,
i.e. *after* the property had already bound: `endpoint` non-null, `connectTimeoutMs`/
`readTimeoutMs >= 1`, `loggingLimitBytes`/`maxChildElements >= 0`. `maxRetries` and
`retryBackoffMs` were never validated anywhere — a negative retry count silently
degrades the retry loop into a no-op, and a negative backoff multiplies out to a
negative sleep duration that throws `IllegalArgumentException` inside
`SoapStreamingClient`'s retry loop. `namespace` was never validated either, despite
`SoapGatewayImpl` using it verbatim to build every SOAP operation's `QName`.

**Dependencies:** None.

**Acceptance Criteria (re-scoped from the original story text — see Follow-up below):**
- [x] `SoapProperties` is annotated with `@Validated`.
- [x] Critical fields have constraints matching the client's real requirements:
      `@NotBlank` on `endpoint`/`namespace`, `@Positive` (strictly `> 0`, matching the
      prior manual check) on `connectTimeoutMs`/`readTimeoutMs`, `@Min(0)` on
      `maxRetries`/`retryBackoffMs`/`loggingLimitBytes`/`maxChildElements` (`0` is a
      real, documented value for the last two — "unlimited" and "log nothing"
      respectively — not "disabled", so `@Positive` would have been wrong).
- [x] Bean Validation is now the primary, early check: it runs at property-binding
      time, before any other bean (including `SipsaSoapClientConfig`) is constructed.
      The four checks inside `SipsaSoapClientConfig.validateConfiguration()` are
      redundant legacy code — unreachable in practice, since `@Validated` always fails
      first — kept in place rather than touching the SOAP client configuration class,
      which was outside this session's explicit scope.
- [x] Application fails at startup with a clear error naming the property when
      configuration is invalid (verified for all 9 fields, unit-level and in Docker).
- [x] `./mvnw clean verify` passes.

**Follow-up (separate story, not this one):** delete
`SipsaSoapClientConfig.validateConfiguration()` now that it is dead code superseded by
`@Validated`. Small (XS), no functional change expected — left out here to avoid
touching SOAP client configuration code in a story scoped to `SoapProperties` alone.

**Completed:** 2026-07-19, branch `refactor/validate-soap-properties`. No cross-field
rules added (no real relationship between these fields in the code); no URL-format
regex on `endpoint` (`@NotBlank` is what the client actually requires — a fragile
format validator risks rejecting valid non-standard endpoints, e.g. the
`http://localhost:9999/mock` test fixture); `endpoint` stays unconditionally required
(no real "SOAP disabled" flag exists in this repository to gate it on). 16 new binding
tests (`SoapPropertiesTest`) — previously zero coverage. `./mvnw test` run before any
change confirmed every existing test fixture (`src/test/resources/application.yaml`)
already had valid values for all 9 fields; no fixture needed correcting. Found and
fixed along the way: `docker-compose.yml` passed through **zero** `SOAP_*` variables,
so shell overrides silently had no effect in Docker — added passthrough for the six
properties with an established env var name (`namespace` stays a fixed WSDL-contract
literal; `logging-limit-bytes` has no established env var and stays out of scope).
Verified in Docker: default startup, valid overrides (env vars confirmed present
inside the container), and three invalid-value cases (`connect-timeout-ms=0` →
`must be > 0`, `max-retries=-1` → `must be >= 0`, `read-timeout-ms=not-a-number` →
binding failure naming the property) each produced `APPLICATION FAILED TO START`;
defaults restored after. A genuinely blank `SOAP_ENDPOINT` could not be exercised
through Docker Compose specifically — its `${VAR:-default}` substitution treats an
empty override as unset and falls back to the default, the same behavior every other
env var in this file already has — so the blank-string constraint is proven at the
Spring-binding unit level (`SoapPropertiesTest.blankEndpointFails`) instead. Presented
as early configuration validation only — no functional change to the SOAP client
itself. No Flyway migration.

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
**Status:** **Done**  
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
**Status:** Done
**Complexity:** S
**Branch:** `refactor/relocate-generated-soap-classes` (the originally-listed
`refactor/soap-generated-package` name was not reused)

**Scope correction from TECH-094's SPIKE (mandatory, verified by running the actual
change — see [the SPIKE report](../architecture/spikes/TECH-094-generated-soap-relocation.md)
§5):** `SoapGatewayImpl.java`'s wildcard import (`soap.client.*`) also supplies
`SoapStreamingClient`, which stays in the *old* package (it's hand-written, not
generated). Retargeting the wildcard alone breaks compilation. Add a 4th line beyond the
original 3:
`import com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient;`
The SPIKE also corrected the generated-file count from 24 (ADR-007's original,
unverified figure) to a freshly-measured **22** — informational, no scope impact.

**Problem:**
`cxf-codegen-plugin` generates 22 JAXB classes (corrected by TECH-094's SPIKE from the
originally-cited 24, which was never re-verified before being repeated) into
`com.dalejandrov.sipsa.infrastructure.soap.client` — the same package as the hand-written
`SoapStreamingClient.java`. Generated and manual code are not distinguishable by package.

**Evidence:** ADR-007 §F3. `pom.xml:307` (`-p com.dalejandrov.sipsa.infrastructure.soap.client`);
`find target/generated-sources -name "*.java"` → 22 files in that package (TECH-094,
2026-07-20).

**Scope:**
- `pom.xml`: change the `wsdl2java` `-p` argument to `com.dalejandrov.sipsa.infrastructure.soap.generated`.
- `src/main/java/com/dalejandrov/sipsa/infrastructure/soap/gateway/SoapGatewayImpl.java:5`: update the wildcard import **and add the new explicit `SoapStreamingClient` import** (see the scope correction above).
- `src/main/java/com/dalejandrov/sipsa/infrastructure/soap/config/SipsaSoapClientConfig.java:4-5`: update the two explicit imports.

**Dependencies:** [TECH-094](#tech-094) (SPIKE) — **complete**, recommendation: proceed.

**Risk:** Low — **verified by TECH-094's SPIKE**, not merely assumed. JAXB
`@XmlType`/`@XmlSchema` bindings were confirmed independent of the Java package (bound to
the WSDL/XSD's XML target namespace instead); a full retarget-and-verify experiment
produced a diff-clean regeneration and a green `./mvnw clean verify` (415/415 tests,
SOAP marshalling included) once the scope correction above was applied.

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
- [x] Generated classes are emitted under `infrastructure/soap/generated`.
- [x] `SoapGatewayImpl` and `SipsaSoapClientConfig` compile against the new package.
- [x] `./mvnw clean verify` passes (430 tests, up from 415 — 15 net new).
- [x] Diff of generated output (excluding the package declaration) is empty — re-verified
      on this branch, not just carried over from TECH-094's SPIKE.

**Completed:** `pom.xml`'s `wsdl2java` `-p` argument changed to
`com.dalejandrov.sipsa.infrastructure.soap.generated` — the single source of truth for
the generated package; no generated file's package declaration was hand-edited.
`SoapGatewayImpl.java`: the single wildcard import replaced with 6 explicit imports for
the generated request types it actually constructs (`PromedioAbasSipsaMesMadr`,
`PromediosSipsaCiudad`, `PromediosSipsaMesMadr`, `PromediosSipsaParcial`,
`PromediosSipsaSemanaMadr`, `ConsultarInsumosSipsaMesMadr`) plus one explicit import for
`SoapStreamingClient` from the unmoved manual package — the scope correction TECH-094's
SPIKE found. `SipsaSoapClientConfig.java`: its 2 explicit imports
(`SrvSipsaUpraBeanService`, `SrvSipsaUpraService`) retargeted the same way. Re-verified
independently of TECH-094 (not merely inherited): `grep`-confirmed only these 2 manual
files import generated classes; `SoapStreamingClient`, `IngestionMetrics`,
`SoapProperties` Javadoc `@see` references to `SoapStreamingClient` needed no change
since that class did not move. `PackageBoundaryArchitectureTest`'s (TECH-093) own
descriptive Javadoc comment (which named the old package) was updated for accuracy; the
3 ArchUnit rules themselves needed zero changes and no new exclusion — confirmed by
re-running the suite, all green. New `SoapGeneratedPackageRelocationTest` (15 cases,
3 nested groups): **package-membership/drift detector** (7 cases) — all 22 generated
classes reflectively loadable from the new package, a parameterized check that 7
representative generated class names are no longer loadable from the old package,
`SoapStreamingClient` confirmed still in the manual package; **JAXB round-trip** (2
cases) — marshalling a generated response preserves the DANE target namespace and
produces well-formed XML, an unmarshal round-trip preserves both the exact generated
type and its field values; **request construction** (3 cases) — `SoapGatewayImpl`
against a mocked `SoapStreamingClient` builds well-formed SOAP payloads carrying the
correct namespace and root element for two different operations, and confirms the
response `InputStream` is returned unchanged (no re-wrapping). Reproducibility
re-verified on this branch: `git status --short` before and after a clean
`generate-sources` run shows zero change to tracked files (target/ is gitignored,
confirmed unaffected either way); the only cosmetic drift already documented by TECH-094
(a generation-timestamp Javadoc line in 2 of 22 files) persists unchanged in the new
package — not a new problem introduced by the move. Verified in Docker: clean startup,
`SipsaSoapClientConfig` logs "SOAP client successfully configured" with no
`ClassNotFoundException` or JAXB context error anywhere in the startup log,
`/actuator/health` reports `UP`. No SOAP timeout/retry/metrics behavior, ingestion logic,
scheduler, security, database, or AWS infrastructure change.

---

### TECH-093

**Title:** Add ArchUnit package-boundary rules (Historia B)
**Type:** Testing
**Priority:** Low
**Phase:** —
**Status:** Done
**Complexity:** S
**Branch:** `test/enforce-package-boundaries` (the originally-listed
`test/architecture-boundaries` name was not reused)

**Problem (before):** No ArchUnit (or equivalent) test existed to prevent the boundaries
fixed by TECH-090, TECH-091, and TECH-095 from regressing.

**Evidence:** ADR-007 §F5.

**Audit before implementing:** grepped `src/main`/`src/test`/`docs`/`pom.xml` for
`TECH-093`, `ArchUnit`, `architecture test`, `package boundary`, `layeredArchitecture`.
Found ADR-007 and this exact backlog entry already specify the 3 rules precisely (no
architecture had to be invented). Confirmed via a fresh grep of the real current
dependency graph, not assumed from the ADR's 2026-07-13 snapshot:

| Cross-layer edge | Files (2026-07-20, post TECH-090/091/095) | Status |
|---|---|---|
| `application → api` | 5: `IngestionTriggerService`, `IngestionRunQueryService`, `SipsaReadService`, `AuditTrailService`, `IngestionAuditService` — importing `api.dto.request/response`, `api.mapper`, `api.util.TimezoneUtil` | Accepted by ADR-007 (deliberate pattern, not a regression) |
| `infrastructure → api` | 0 | Fixed by TECH-091, confirmed still 0 |
| `domain → infrastructure` | 0 | Fixed by TECH-095, confirmed still 0 |
| `api.controller → infrastructure.persistence.repository` | 0 | Already true, no fix needed — rule only guards regression |

No ArchUnit dependency was present (`grep archunit pom.xml` → no results before this
story).

| Proposed rule | Architectural evidence | Current violations | Decision |
|---|---|---|---|
| `application` does not depend on `api`, except the 5 ADR-007-accepted services | Table above; ADR-007 §Decision explicitly excludes this rule from a blanket form | 0 (with the 5 named exclusions) | Implemented |
| `domain` does not depend on `infrastructure` | ADR-007 §F4/TECH-095 | 0 | Implemented |
| `api.controller..` does not depend on `infrastructure.persistence.repository..` | ADR-007 §F5 | 0 | Implemented |

**Scope:**
- Added `com.tngtech.archunit:archunit-junit5:1.4.2` (test scope) to `pom.xml` — bytecode
  import, not reflection, so it works against Java 25 class files regardless of ArchUnit's
  documented supported-JDK list; verified empirically (all tests green), not assumed.
- One test class, `PackageBoundaryArchitectureTest`, asserting exactly the 3 rules above —
  no more.

**Rule 1's exact form (why, per this story's own explicit scope limit):** a literal
"`application` must never depend on `api`" rule would fail on day one against an
ADR-007-accepted decision. Implemented as `noClasses().that().resideInAPackage
("..application..").and().areNotAssignableTo(<the 5 accepted classes>).should()
.dependOnClassesThat().resideInAPackage("..api..")` — the explicitly-authorized "exclude
those specific classes" strategy from this story's own scope text (the alternative,
"scope to `api.dto.request..IngestionRequest/CreateRunRequest/AuditEventRequest`," would
be vacuous today since TECH-090 already moved those 3 classes out of `api.dto.request`
entirely — it could never catch a real regression). This form protects the *actual*
regression this rule exists to catch: any application class *other than* the 5 named ones
newly reaching into `api` (controller, filter, dto, or mapper) is flagged; the 5 named
exceptions remain exactly as ADR-007 accepted them.

**Generated SOAP code (ADR-007 §F3, TECH-094):** no exclusion was needed for any of the 3
rules. Rule 3 doesn't touch `infrastructure` internally at all, and `domain`'s dependency
on `infrastructure` is confirmed zero *of any kind* — nothing about
`infrastructure.soap.client` (the CXF-generated package) interacts with these rules. No
SOAP class, generated or hand-written, was moved, and TECH-094/TECH-092 remain untouched
and unblocked by this story either way.

**Regression-detection fixture (proves the rule pattern itself can catch a violation,
without adding an invalid production class):** `archunitregression.ArchRulePatternRegressionTest`
(2 cases) — a `FakeDomainClass`/`FakeInfraClass` pair living entirely outside
`com.dalejandrov.sipsa` (so `PackageBoundaryArchitectureTest`'s own
`com.dalejandrov.sipsa`-scoped scan never sees them) proves the exact same `noClasses()
...should().dependOnClassesThat()...` pattern both (a) flags a genuine violation and (b)
does not false-positive against an innocent class in scope.

**Dependencies:** TECH-090, TECH-091 (merged, confirmed via `main` at `bac0fc9`+) and
TECH-095 (merged) — all landed first, so these rules assert the *post*-move state.

**Risk:** Low — realized as documented; zero surprises.

**Acceptance Criteria:**
- [x] ArchUnit test class exists with the 3 rules above, all green.
- [x] `./mvnw clean verify` passes (415 tests, up from 410 — 5 net new: 3 rules + 2
      regression-fixture proof cases).
- [x] No rule beyond the 3 listed was added.

**Completed:** `PackageBoundaryArchitectureTest` (3 `@ArchTest` rules, `@AnalyzeClasses
(packages = "com.dalejandrov.sipsa", importOptions = ImportOption.DoNotIncludeTests.class)`
— production sources only, no test class including itself is ever in scope) plus
`archunitregression.ArchRulePatternRegressionTest` (2 cases, isolated fixture, proves
detection capability). No production code changed: only `pom.xml` (one test-scope
dependency + one version property) and new test files. No endpoints, HTTP contract,
scheduler, metrics, persistence, TECH-054 pagination, security, database, Flyway, or AWS
infrastructure change. No Flyway migration.

---

### TECH-094

**Title:** SPIKE — Evaluate relocating CXF-generated SOAP sources
**Type:** SPIKE
**Priority:** Low
**Phase:** —
**Status:** Done
**Complexity:** XS
**Branch:** `spike/evaluate-generated-soap-relocation` (the originally-listed
`spike/soap-generated-package` name was not reused)
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
- [x] Report answering all 9 points above, added to this story's **Completed** section or
      linked as a separate note.
- [x] Explicit recommendation: proceed with TECH-092 as scoped, re-scope it, or reject it.
- [x] No source code changed as part of this SPIKE.

**Completed:** Full report at
[docs/architecture/spikes/TECH-094-generated-soap-relocation.md](../architecture/spikes/TECH-094-generated-soap-relocation.md).
**Recommendation: proceed with TECH-092**, with one scope correction. Summary of the 9
points: (1) `cxf-codegen-plugin` 4.2.2; (2) `SrvSipsaUpraBeanService.wsdl` + local
`jax-ws-catalog.xml`, no remote fetch; (3) current target package
`infrastructure.soap.client`; (4) confirmed not version-controlled (`.gitignore:11`); (5)
deterministic modulo a cosmetic generation-timestamp comment in 2 of 22 files, verified
by running codegen twice and diffing; (6) **file count corrected from the previously-cited
24 to a freshly-measured 22** — a real discrepancy this SPIKE caught rather than
re-assumed, with no change to the risk conclusion; expected diff **verified, not
estimated**, at 1 `pom.xml` line + 4 import lines (not 3 — see next point); (7) import
impact on both files confirmed, and a **real gap found**: `SoapGatewayImpl`'s wildcard
import was also the only import path for the hand-written `SoapStreamingClient` (which
stays in the old package) — retargeting the wildcard alone breaks the build; a 4th line
(one explicit `SoapStreamingClient` import) is required and was not in the original
3-line scope; (8) confirmed via a full `./mvnw clean verify` on the actual retargeted
package — `BUILD SUCCESS`, 415/415 tests green, including real-PostgreSQL Testcontainers
suites and SOAP marshalling tests; (9) yes, worth it — a 5-line diff closing a real
architectural gap (ADR-007 §F3), evidence-based rather than a "low, but non-zero"
judgment call. The relocation (package retarget + 4 import lines) was implemented,
verified end-to-end, and **fully reverted** (`git checkout --`) before this SPIKE's
final commit — `git status --short` confirmed clean afterward. No production code
change is part of this story.

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

### TECH-104

**Title:** SPIKE — evaluate cost/impact of migrating the 4 calendar-date columns from
`TIMESTAMPTZ` to `DATE`
**Type:** SPIKE (investigation only, per [ADR-008](../adr/ADR-008-timezone-locale-and-date-semantics.md) — "do not implement without the SPIKE confirming the scope")
**Priority:** Low
**Phase:** —
**Status:** **Done** — investigation completed 2026-07-27. **The migration itself was
deliberately not implemented** by this story; see recommendation below.
**Complexity:** XS (investigation) / M (the migration it evaluates, if a future story
picks it up)

**Origin:** [ADR-008](../adr/ADR-008-timezone-locale-and-date-semantics.md) item 3 /
Finding F1, and section I of the
[timezone/locale strategy review](../architecture/timezone-locale-date-strategy-review.md).
PR #36 (`fix/timezone-calendar-dates-and-invalid-header-400`) already retyped the *API
response* layer for these 4 fields to `LocalDate` (TECH-100/TECH-103/TECH-106); this SPIKE
evaluates whether the underlying entity/DB column should follow.

**Fields in scope:** `sipsa_ciudad.fecha_captura`, `sipsa_parcial.enma_fecha`,
`sipsa_mayoristas_semanal.fecha_ini`, `sipsa_mayoristas_mensual.fecha_mes_ini`,
`sipsa_abastecimientos_mensual.fecha_mes_ini` — 5 columns across 5 tables (4 distinct
logical fields; `fecha_mes_ini` exists in 2 tables), all currently `TIMESTAMPTZ` (V1).

**Findings — exact blast radius, enumerated by reading the code, not estimated:**

1. **Migration:** one new Flyway script, `ALTER COLUMN ... TYPE DATE USING (col AT TIME
   ZONE 'America/Bogota')::date` for the 5 columns. This is exactly the same expression
   `TimezoneUtil.toBusinessLocalDate` already performs in Java today — the migration
   would just move that computation from "every API read" to "once, at migration time".
   PostgreSQL rewrites the 5 single-column indexes (`idx_sipsa_ciudad_fecha`,
   `idx_sipsa_parcial_fecha`, `idx_sipsa_semana_fecha`, `idx_sipsa_mes_fecha`,
   `idx_sipsa_abas_fecha`) and the 2 composite indexes that include `enma_fecha`
   (TECH-011's natural-key index, TECH-124's `idx_sipsa_parcial_article_date` covering
   index) automatically as part of the
   `ALTER COLUMN TYPE` — no separate `DROP`/`CREATE INDEX` needed. The 3 `UNIQUE`
   constraints that include one of these columns (`ux_semana_fallback`,
   `ux_mes_fallback`, `ux_abas_fallback`) are unaffected in behavior: every stored value
   today is already exact Bogotá midnight, so equality semantics don't change, only the
   stored precision does.
2. **Entities (5 fields, 5 files):** `SipsaCiudad.fechaCaptura`,
   `SipsaParcial.enmaFecha`, `SipsaMayoristasSemanal.fechaIni`,
   `SipsaMayoristasMensual.fechaMesIni`, `SipsaAbastecimientosMensual.fechaMesIni`
   change from `Instant` to `LocalDate`.
3. **Ingestion mapping (`SipsaIngestionMapper`):** the `qualifiedByName =
   "millisToInstant"` mappings for these 5 fields (currently lines 61, 98, 115, 134; line
   78 already maps `enmaFecha` from a pre-parsed `LocalDate` — see
   `ParcialIngestionHandler`) become `qualifiedByName = "millisToBusinessLocalDate"` (a
   new small helper, same shape as the existing `millisToInstant`). This is where the
   Bogotá-zone extraction would move to — computed once per ingested record, not on
   every API read.
4. **API mappers (5 files, already touched by PR #36):** the `TimezoneUtil
   .toBusinessLocalDate(entity.getX())` expressions become plain field passthrough
   (`entity.getX()`) — MapStruct maps `LocalDate → LocalDate` with no custom expression
   needed at all. Net simplification, not just a wash.
5. **`SpecificationBuilder`:** `withDateOrRange`/`addDateFilter` is used **exclusively**
   for these 4 fields (`grep` confirms zero other callers) — today it always builds an
   `Instant` range via an injected timezone (`start.atStartOfDay(zone).toInstant()`).
   After migration this entire zone-conversion path is unneeded: filtering becomes a
   direct `LocalDate` `cb.between`, and the `timezone` constructor parameter
   `SpecificationBuilder.builder(String timezone)` takes today could be dropped
   entirely. This is the single biggest simplification the migration would unlock.
6. **Tests:** ~8 files reference these fields at the `Instant`-typed entity/fixture
   level and would need their fixtures changed to `LocalDate`:
   `ParcialQueryFilterIntegrationTest`, `ParcialConcurrentIngestionAppTest`,
   `ParcialIngestionHandlerTest`, `SpecificationBuilderPostgresTest`,
   `SipsaDecimalPrecisionAlignmentTest`, `ParcialDecimalPrecisionTest`,
   `SipsaMayoristasSemanalFallbackUpsertTest`, `ParcialConcurrentDedupTest` — this is the
   largest single cost item, not the schema change itself.
7. **No consumer-visible contract change:** the JSON response shape is already
   `LocalDate` since PR #36 — this migration is a pure internal-consistency change, not
   a second breaking change for API clients.

**Recommendation:** proceed, but as its **own follow-up story**, not bundled into this
SPIKE. The change is a genuine correctness/simplicity improvement (removes the
"did-the-mapper-remember-to-call-`toBusinessLocalDate`" class of risk by construction,
simplifies `SpecificationBuilder`, moves the zone computation from read-time — every API
call — to write-time — once per ingested record) and is fully enumerated above, so
TECH-104's own precondition ("do not implement without the SPIKE confirming the scope")
is satisfied. It is **not urgent**: the user-facing date-shift risk this whole
investigation started from is already closed by PR #36's response-layer fix, so the DB
column type is now purely an internal consistency concern, not a live bug. Suggested
next ID: the first free `TECH-1xx` slot at the time the follow-up story is picked up
(**not** reusing TECH-104 itself — this entry is the completed SPIKE, not the
migration).

**Risk of NOT migrating:** low and already mitigated — `TimezoneUtil
.toBusinessLocalDate` centralizes the Bogotá-zone extraction in one place, so the
residual risk is "a future mapper change forgets to call it", not "the API returns the
wrong date today".

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
**Status:** **Done** — Terraform foundation and ECS configuration wiring complete
(2026-07-22, [TECH-142](#tech-142)). No AWS resources have been applied yet.
**Complexity:** M
**Branch:** `infra/cognito-authentication-foundation`

**Origin:** [ADR-002](../adr/ADR-002-internal-endpoint-security.md) (Accepted, Option E),
layer 2, and [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) (Accepted) —
provisions the real identity provider for the Resource Server already implemented and
validated in TECH-001 (e2e against a mock OIDC issuer, 9/9 green, 2026-07-15). Scope
explicitly bounded: no API Gateway, VPC Link, WAF, Route 53, ACM, custom domain,
frontend, invented callback URLs, real distributed secrets, real AWS integration, or
change to Spring Security without a real, demonstrated defect.

**Real scope inventory** (`grep -RIn --exclude-dir=.git -e 'SCOPE_' -e
'hasAuthority' -e 'hasAnyAuthority' -e 'scope' src/main src/test docs`, not invented):

| Scope | Endpoint / operation | M2M | Human |
|---|---|---:|---:|
| `sipsa/ingestion.execute` | `POST /api/internal/ingestion/run` | Yes | Yes |
| `sipsa/ingestion.cancel` | `POST /api/internal/ingestion/cancel/{runId}` | Yes | Yes |
| `sipsa/ingestion.read` | `GET /api/internal/ingestion/**` | Yes | Yes |
| `sipsa/audit.read` | `GET /api/internal/audit/**` | Yes | Yes |

These four are the only `hasAuthority("SCOPE_sipsa/...")` authorities `SecurityConfig`
validates today — confirmed by reading the code, not assumed. No scope was created
without a real consumer/endpoint.

**Two app client contracts, never shared:**
- **M2M** (`aws_cognito_user_pool_client.m2m`): `client_credentials` grant only,
  confidential (`generate_secret = true`). Never `authorization_code`, `implicit`, or
  password grant. A parameterizable client in this story — the module already supports
  "one client per future integration" by instantiating it again with different inputs;
  no second, purely speculative consumer is invented.
- **Human** (`aws_cognito_user_pool_client.human`): `authorization_code` grant only,
  public (`generate_secret = false`). Cognito automatically requires PKCE for any public
  client on this grant — there is no separate Terraform argument to "require PKCE";
  `generate_secret = false` is what defines a public client, and Cognito's own token
  endpoint then requires the `code_challenge`/`code_verifier` exchange. No `implicit`,
  no password grant.

**User pool — secure-by-default configuration:** sign-in identifier `email`
(`username_attributes = ["email"]`, no separate "username" concept elsewhere in the
system); password policy with minimum length 12 (parameterizable) and required
uppercase/lowercase/number/symbol; `mfa_configuration = "OPTIONAL"` by default
(parameterizable) — there is not yet an operational MFA enrollment/recovery flow (no
frontend, no documented support process for a locked-out user), forcing MFA now would
create an operational dead end; documented as future hardening, not adopted
preventively. `advanced_security_mode = "AUDIT"` by default (parameterizable) —
risk-based visibility without blocking or challenging any sign-in; both `AUDIT` and
`ENFORCED` carry a real per-MAU cost that `OFF` does not, `AUDIT` is the deliberate
middle ground. `deletion_protection = "ACTIVE"` (note: it's a string,
`"ACTIVE"`/`"INACTIVE"`, not a bool — confirmed against the provider argument's real
type), consistent with this repository's RDS/ALB posture. `allow_admin_create_user_only
= true` by default — SIPSA is an internal operational tool, not a public sign-up
product. `prevent_user_existence_errors = "ENABLED"` and token revocation enabled on
both clients. Account recovery only via verified email
(`recovery_mechanism { name = "verified_email", priority = 1 }`) — the only verified
attribute of this pool.

**Hosted UI domain — deliberately optional:** `create_hosted_ui_domain` defaults to
`false`. There is no frontend or approved callback URL yet, so a Hosted UI domain would
have nowhere to redirect to. The human client with Authorization Code + PKCE is still
created when this is `false` — it simply has no reachable `/oauth2/authorize` endpoint
until a domain exists. Enabling it later is a single-variable change plus
`cognito_domain_prefix` (required, globally unique, no invented default — the same class
of uniqueness as an S3 bucket name), not a restructuring. No custom domain, no ACM
certificate, no Route 53 record — only a Cognito-managed prefix domain.

**Callback/logout URLs — no invented placeholders:** `human_callback_urls`/
`human_logout_urls` are required variables with no default. There is no real frontend,
so there is no real URL either — a real `terraform plan`/`apply` must not proceed with
invented values. Both variables reject any URL containing `localhost` or `example.com`
via `validation`, specifically so they can never be confused with approved production
values; they remain usable only within this module's offline tests, which use a
`*.invalid` hostname (RFC 2606, guaranteed never to resolve) to keep their placeholder
nature unambiguous even there.

**Token validity:** `access_token_validity_minutes = 60`, `id_token_validity_minutes =
60` (human client only — a `client_credentials` token carries no ID token),
`refresh_token_validity_days = 30` (human client only — `client_credentials` is not a
refresh-token-based grant). All three are Terraform variables, initial proposals not
measured against a real operational pattern.

**Compatibility with the application's existing JWT validation:** confirmed by reading
`SecurityConfig`/`SipsaJwtProperties`/`TokenUseValidator`/`AllowedClientIdsValidator`
(already implemented, already validated e2e against a mock OIDC issuer,
TECH-001/ADR-002) — **no defect found, no change to Spring Security made by this
story**: `iss` is validated against `SIPSA_JWT_ISSUER_URI` (this module's `issuer_url`
output, built from the user pool's `endpoint` attribute, is the value to use in a real
deployment); `exp` via Spring's standard validator; `token_use = access` via
`TokenUseValidator`; `client_id` via the optional `AllowedClientIdsValidator`; `scope`
per operation via the `hasAuthority` matchers against the `SCOPE_sipsa/...` authorities
Spring derives from the `scope` claim. `aud` is deliberately ignored (already documented
in `aws-production-readiness.md`) — a Cognito `client_credentials` token carries no
`aud`, consistent, not a gap this module needs to resolve.

**Client ID allowlist to ECS — designed, not wired yet:**
`publish_client_ids_to_ssm` (default `true`) publishes both client IDs as a CSV in a
`String`-type SSM Parameter Store parameter
(`/  <project>-<environment>/sipsa/jwt-allowed-client-ids`) — they are identifiers, not
secrets, per `AllowedClientIdsValidator`'s own Javadoc. This module does **not** wire
that parameter into `modules/ecs-task`'s task definition — doing so would modify an
already-merged module, out of this story's scope; it remains a documented follow-up for
whoever wires `SIPSA_JWT_ALLOWED_CLIENT_IDS`/`SIPSA_JWT_ISSUER_URI` into the ECS
environment.

**M2M client secret — corrected semantics (2026-07-22):** Cognito generates the secret;
the Terraform provider reads it as a computed attribute
(`aws_cognito_user_pool_client.m2m.client_secret`, `sensitive = true` in the provider's
schema, confirmed via `terraform providers schema`) during creation, so it can be copied
to the next resource. **Terraform therefore receives the sensitive value and retains it
in the remote state** — both in this module's state and in the state of
`environments/production`, which consumes it. Writing the value to Secrets Manager
afterward does not remove this: the state always stores the attribute's full value,
regardless of the provider's `sensitive` mark (that mark only suppresses the value in
`plan`/`apply` CLI/log output and in `terraform output` without `-json` — it does not
affect what the state contains). **It must not be claimed that "Terraform never knows
the client secret" or that storing it in Secrets Manager removes it from the state —
both are false for this resource** (unlike RDS's master secret in `modules/database`,
where `manage_master_user_password = true` makes RDS manage the secret internally
without Terraform ever reading it as an attribute). What Secrets Manager does provide is
a separate operational distribution path, scoped by IAM
(`secretsmanager:GetSecretValue` on the exact ARN), so daily consumption of the secret
does not require reading the state directly — never exposed as an output (only its ARN,
`m2m_client_secret_arn`). Also verified against the provider, not assumed: unlike an IAM
access key (truly unrecoverable), a Cognito app client's secret remains recoverable at
any time via `DescribeUserPoolClient`, regardless of this design. **The real control is
the state backend**: the S3 bucket in `infra/terraform/bootstrap/main.tf` has encryption
(`AES256`), full public-access block (`aws_s3_bucket_public_access_block`, all four
flags `true`), versioning, and native S3 locking (`use_lockfile = true`); the
`terraform-plan`/`terraform-apply`/`application-deploy` roles are separated by least
privilege (ADR-010), and no workflow in this repository runs `terraform output` against
this stack. This stack's state must be treated as sensitive material, encrypted and
accessible only by least-privilege infrastructure roles — that is the real protection,
not the value's absence from the state. Distribution to the consumer: not automated by
this story — whoever owns the real M2M integration receives explicit read access to that
specific ARN once that consumer exists. No automatic rotation implemented — evaluate
once a real rotated-secret distribution mechanism exists.

**Trivy exceptions:**

| Finding | Resource | Risk | Justification | Exception |
|---|---|---|---|---|
| AWS-0098 (LOW — Secret uses the default key) | `aws_secretsmanager_secret.m2m_client_secret` | Low — single-owner secret, no cross-account access, no compliance requirement demanding a CMK today | Same posture already applied to the bootstrap S3 bucket, database's CloudWatch log groups, the ecr repository, and network's Flow Logs group | `# trivy:ignore:AVD-AWS-0098`, revisit with a customer-managed KMS key if a real access boundary arises (e.g. an external team needing scoped decrypt) |

**Module:** `infra/terraform/modules/cognito/` (`main.tf`, `variables.tf`, `outputs.tf`,
`versions.tf`, `README.md`, `tests/`). No third-party public modules.
`environments/production/main.tf` consumes the module — no dependency on
`module.network`, `module.ecs_task`, or `module.ecs_service` (Cognito is not anchored to
the VPC).

**Outputs:** `user_pool_id`, `user_pool_arn`, `issuer_url`, `resource_server_identifier`,
`m2m_client_id`, `human_client_id`, `cognito_domain` (nullable), plus
`m2m_client_secret_arn` and `allowed_client_ids_parameter_name` (both non-sensitive — ARN
and parameter name, never the secret or its value).

**Tests:** `infra/terraform/modules/cognito/tests/cognito.tftest.hcl` — 21 cases, all
green, `terraform test` with a fully mocked AWS provider, zero real AWS account
contacted. Coverage: user pool created; `prevent_user_existence_errors` and token
revocation on both clients; password policy (length 12, all four requirements); email
verification; resource server with exactly the four real scopes (`toset` comparison);
M2M client with a secret and `client_credentials` only; human client with no secret and
`code` only; `implicit` flow absent on both; callback/logout URLs reflect the variable
exactly; rejection of `localhost`/`example.com` (`expect_failures`); token validity
(60/60/30); distinct client IDs; common tags on the user pool; no Hosted UI domain by
default (`cognito_domain` output `null`); domain created only on explicit request with a
prefix; rejection of a domain without a prefix (`expect_failures`); M2M secret written to
`aws_secretsmanager_secret_version`; allowlist SSM parameter published by default as
`String` (never `SecureString`) and disableable via a variable. Absence of API Gateway,
VPC Link, custom domain, ACM/Route53, and of a secret in outputs confirmed by inspecting
the module's source code, not as a runtime assertion.

**Acceptance Criteria:**
- [x] Cognito user pool with secure-by-default configuration (optional MFA, advanced
      security AUDIT, deletion protection, prevent-user-existence-errors, token
      revocation, recovery via verified email).
- [x] `sipsa` resource server with exactly the four real scopes, confirmed by grepping
      the code, not invented.
- [x] M2M client `client_credentials`-only, with a secret, never exposed as an output —
      only its ARN in Secrets Manager.
- [x] Human client `authorization_code`+PKCE-only, no secret, no `implicit`,
      callback/logout URLs parameterized with no invented placeholders.
- [x] The two clients are distinct resources, never sharing scopes-vs-grant
      configuration or a secret.
- [x] No Hosted UI domain created by default; explicitly creatable without a custom
      domain/ACM/Route53.
- [x] Client ID allowlist design toward ECS documented (SSM `String`), not wired into
      `modules/ecs-task` in this story.
- [x] JWT compatibility confirmed with no changes to Spring Security — no real defect
      found.
- [x] `terraform test` passes (21/21) against a mocked provider.
- [x] `terraform fmt -check -recursive`, `terraform validate` (all eight Terraform
      roots), TFLint, and `trivy config` are all clean (1 individually justified LOW
      exception).
- [x] `./mvnw -q -DskipTests compile` passes; zero change in `src`/`pom.xml`.
- [x] No `terraform apply`, `terraform import`, AWS CLI, real token request, real Hosted
      UI, or real Cognito user creation.
- [x] TECH-132 remains `In progress` (not `Done`); TECH-131 remains `Pending`.

**Completed:** `infra/terraform/modules/cognito/` created (6 files incl. tests) and wired
into `environments/production` (no network/ECS dependency). Verified locally via the
official Docker images `hashicorp/terraform:1.15.7`,
`terraform-linters/tflint:v0.64.0`, and `aquasec/trivy` (none installed on this machine):
`fmt -check -recursive` clean; `terraform init -backend=false && terraform validate`
clean for all eight Terraform roots (`bootstrap/`, `environments/production`,
`modules/network/`, `modules/database/`, `modules/ecr/`, `modules/ecs-task/`,
`modules/ecs-service/`, `modules/cognito/`); `terraform test` 21/21 passing for the new
module; TFLint 0 issues; `trivy config` 0 unresolved findings across the whole tree (1
new LOW exception, individually justified). `.github/workflows/infra-plan.yml` gained a
`terraform test — modules/cognito` step. `./mvnw -q -DskipTests compile` passed; zero
change in `src`/`pom.xml`. No `terraform apply`, `terraform import`, AWS CLI command,
real token request, real Hosted UI, or Cognito user creation at any point; no AWS
resource of any kind exists; no AWS credential was added.

**Documented gaps, prior to any real deployment** (none resolved by this story):
- Hosted UI domain not created — requires a real approved callback/logout URL first.
- SSM allowlist published but not wired to `modules/ecs-task` — follow-up pending.
- Real M2M secret distribution to the consumer not automated — explicit IAM access
  pending a real consumer.
- No automatic rotation of the M2M secret.
- MFA `OPTIONAL`, not enforced — pending an operational enrollment/recovery flow before
  considering `ON`.
- API Gateway and VPC Link (TECH-131) pending — also still blocked in TECH-132.
- No `terraform apply` executed at any point in this sequence of stories.

---

### TECH-131

**Title:** API Gateway — API keys, usage plans, throttling and access logs
**Type:** Infrastructure / Security
**Priority:** High
**Phase:** —
**Status:** **Done** — Terraform foundation complete. No AWS resources have been applied.
**Complexity:** M
**Branch:** `infra/api-gateway-private-integration`

**Origin:** ADR-002 (Accepted, Option E), layer 1. [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md)
Phase 4.

**Architecture correction — REST API VPC Links only accept an NLB, not an ALB
directly:** ADR-010's original diagram ("API Gateway → VPC Link → internal ALB")
over-simplified a real AWS constraint, confirmed against the provider resource's own
documentation, not assumed: `aws_api_gateway_vpc_link` (the classic REST API VPC Link,
distinct from `aws_apigatewayv2_vpc_link` for HTTP API) only accepts a Network Load
Balancer ARN in `target_arns` — never an Application Load Balancer directly, and only
one NLB per VPC Link. This story creates a small NLB (`aws_lb`,
`load_balancer_type = "network"`) exclusively for the VPC Link, and registers the
already-existing internal ALB (TECH-141) as that NLB's sole target, using the
AWS-documented "Application Load Balancer as a target of a Network Load Balancer"
pattern (`aws_lb_target_group` with `target_type = "alb"` +
`aws_lb_target_group_attachment`). Real topology:

```
Client → API Gateway REST API → VPC Link → NLB → (target_type=alb) → internal ALB → ECS Service
```

Constraints honored, all AWS-documented: exactly one ALB per `alb`-type target group;
the target group's port must exactly match the ALB listener's port (both `80`); health
checks are forwarded to the ALB and then to its own target group, using the same
`/actuator/health` path already used at the ALB→ECS layer. **No security group on the
NLB**: AWS documentation does not confirm whether an `alb`-type NLB target group
preserves the real caller's source IP, so this module does not attempt to scope ingress
at the NLB level by IP/SG. The real access-control boundary remains the ALB's own
security group (`modules/ecs-service`) — the root wires
`alb_allowed_ingress_cidr_blocks` to the private-app subnets' CIDRs (the documented
fallback TECH-141 already built for exactly this case), never
`alb_allowed_ingress_security_group_ids`, and never `0.0.0.0/0` (rejected by that
variable's validation since TECH-141).

**Separation of responsibilities (unchanged from ADR-002 §3):** Cognito (identity and
authorization) — API key (operational identification of an `/api/sipsa/**` consumer,
for metering only) — usage plan (throttling and quota, never authentication) — Spring
Security (final validation, defense in depth, of the access token and its scopes). The
Cognito authorizer at the gateway **does not replace** Spring Security's revalidation —
both layers run, independently, on every request to `/api/internal/**` (ADR-002: a
gateway bypass still reaches a backend that revalidates the token).

**Endpoint inventory** (grep against `src/main/java`, cross-checked against
`SecurityConfig`'s matchers — see the full table in `modules/api-gateway/README.md`):
`GET /api/sipsa` and `GET /api/sipsa/{proxy+}` (ciudad, mayoristas/mensual, parcial,
mayoristas/semanal, abastecimientos/mensual) — public, require an API key, general tier.
`POST /api/internal/ingestion/run` and `POST /api/internal/ingestion/cancel/{runId}` —
Cognito, `sipsa/ingestion.execute` and `sipsa/ingestion.cancel` scopes respectively,
strict ingestion tier (1/2). The remaining 8 GETs under `/api/internal/ingestion/**` and
`/api/internal/audit/**` — Cognito, exact scope per route (`sipsa/ingestion.read` or
`sipsa/audit.read`), general tier. `/actuator/health` — **never routed through the
gateway** (ADR-002 §5), confirmed structurally (no resource in this module references
`/actuator`). `/api/sipsa/**` uses a single `{proxy+}` resource (Spring already owns the
real routing); each `/api/internal/**` route has its own explicit resource because each
needs an exact, distinct `authorization_scopes` — a catch-all here would over-grant
permissions or would require assuming Cognito's AND/OR semantics for multiple scopes,
which this module does not assume.

**Throttling and quota (values approved by ADR-010):** general 10 req/s / burst 20,
applied stage-wide via `aws_api_gateway_method_settings` (`method_path = "*/*"`);
ingestion (`run`, `cancel/{runId}` only) 1 req/s / burst 2, via a per-route override;
monthly quota 100,000 requests, only on the general usage plan (`/api/sipsa/**`) —
`/api/internal/**` does not require an API key (ADR-010), so its throttling is
exclusively via `method_settings`, with no monthly-quota concept. Best-effort,
explicitly not an absolute cost barrier. **Gap not empirically verified** (no `apply` is
ever run): the exact `method_path` format for the ingestion override
(`"{resource_path}/{HTTP_METHOD}"`, no leading slash, per AWS documentation) must be
confirmed against a real deployed API before real traffic.

**API keys:** a parameterizable `aws_api_gateway_api_key` resource
(`var.api_gateway_api_key_name`, default `"sipsa-primary-consumer"`) — the same "one
client now, extensible later" precedent `modules/cognito` already established for the
M2M client. No fixed `value`: AWS generates it. The generated `value` attribute is
marked `sensitive = true` in the provider's schema (confirmed against the provider's Go
source, not assumed) — never read or exposed as an output (`outputs.tf` only exposes
`api_key_ids`). Retrieval once a real consumer exists: `aws apigateway
get-api-key --include-value`, scoped by IAM, never via `terraform output` or state
inspection.

**Access logs:** structured JSON, 30-day retention by default. Fields: API Gateway's own
`requestId` (**not** the same `requestId` as the application's `ErrorResponse`,
TECH-023 — they never coincide, since only errors originating in the app reach its own
requestId generation), `sourceIp`, `httpMethod`, `resourcePath`, `status`,
`integrationStatus`/`integrationLatency`, `authorizer.claims.sub`, `apiKeyId`. The
`Authorization` header, an API key value, or request/response bodies are never logged
(`data_trace_enabled = false` on all `method_settings`). The account-level CloudWatch
IAM role (`aws_api_gateway_account`) was created and configured — a real, operationally
well-known prerequisite for API Gateway logging to actually deliver anything, even
though it is not declared as a strict requirement in the Terraform resource's own
documentation. **This is an AWS-account-level singleton** (the resource has no
`rest_api_id` — it configures the account, not this specific API): safe to create once
in this repository's single, dedicated AWS account (ADR-010), but would need to be
imported/shared, not redeclared, if a second API Gateway stack is added to this account.

**Error responses — Gateway-origin vs. application-origin:**
`aws_api_gateway_gateway_response` covers `UNAUTHORIZED` (401), `ACCESS_DENIED` (403),
`THROTTLED` (429), and `DEFAULT_5XX`, each with a small, consistent JSON body
(`status`/`message`/`requestId` — API Gateway's own). **Deliberately not** the same
shape as the application's `GlobalExceptionHandler.ErrorResponse` — replicating it would
diverge the moment either side changes independently, and the gateway cannot populate
fields like the application's own `requestId`/`instance` for a request that never
reached the backend. Errors the application itself produces (400/404/500/502, its own
shape) pass through unmodified via the `HTTP_PROXY` integration.

**Timeouts:** confirmed by reading `SipsaOpsController` — `POST
/api/internal/ingestion/run` returns `202` synchronously and fast (TECH-053, async
trigger); `POST .../cancel/{runId}` is a synchronous, fast DB state update. No endpoint
waits on a long-running operation inline — confirmed by inspection, not assumed; no
timeout override or workaround was necessary.

**CORS — undecided, natively scoped by design:** `var.api_gateway_cors_allowed_origins`
defaults to empty (disabled) — `aws-production-readiness.md` §1.6 confirms there is no
browser-based client requirement in this repository. When exactly one origin is
configured, this module adds a static `Access-Control-Allow-Origin` header (and
`Access-Control-Allow-Credentials` if `cors_allow_credentials=true`) to the public GET
methods of `/api/sipsa` via `aws_api_gateway_method_response`/`integration_response`.
**Scoped to a single origin by variable validation**: API Gateway's native (no Lambda)
response headers only support a fixed value, not a dynamic echo of the real request's
`Origin` header across multiple allowed origins — that would require a Lambda proxy
integration, not adopted speculatively while no real origin is confirmed.
`cors_allowed_origins` never accepts `"*"` combined with `cors_allow_credentials=true` —
forbidden by the CORS spec itself.

**Trivy exceptions:**

| Finding | Resource | Risk | Justification | Exception |
|---|---|---|---|---|
| AWS-0003 (LOW — X-Ray not enabled) | `aws_api_gateway_stage.main` | Low — real per-request cost with no operational justification yet | Same posture as Container Insights in `modules/ecs-task` (the only accepted exception, justified by a real operational need) — X-Ray has no equivalent here yet | `# trivy:ignore:AVD-AWS-0003`, revisit if tracing across the 4 layers becomes a real need |
| AWS-0190 (LOW — cache not enabled) ×2 | `aws_api_gateway_method_settings.default`/`.ingestion_trigger` | Low/None — caching `GET .../runs`, `/running`, `/runs/{runId}` (live state) would be actively misleading, not just useless; the ingestion routes are POST (API Gateway does not cache non-GET methods) | Real correctness justification, not just cost | `# trivy:ignore:AVD-AWS-0190`, revisit caching for `/api/sipsa/**` only if a real traffic pattern justifying it appears |

No exception touches an unauthenticated public API, an open SG, a public IP, logs
without retention, or excessive IAM.

**Module:** `infra/terraform/modules/api-gateway/` (`main.tf`, `variables.tf`,
`outputs.tf`, `versions.tf`, `README.md`, `tests/`). No third-party public modules.
`environments/production/main.tf` consumes the module, wiring
`module.ecs_service.alb_arn`, `module.network.vpc_id`/`private_app_subnet_ids`, and
`module.cognito.user_pool_arn`/`resource_server_identifier` — no direct dependency on
those modules' internals, only on their declared outputs.

**Outputs:** `rest_api_id`, `rest_api_arn`, `execution_arn`, `invoke_url`, `stage_name`,
`vpc_link_id`, `usage_plan_id`, `api_key_ids` (IDs only), `access_log_group_name`,
`authorizer_id`. No API key value exposed.

**Tests:** `infra/terraform/modules/api-gateway/tests/api-gateway.tftest.hcl` — 21
cases, `terraform test` with a mocked provider, zero real AWS account. Coverage: REST
API created; VPC Link points at the NLB (never directly at the ALB); the NLB's target
group is `alb`-type with the given ALB as its sole target, matching port; correct
Cognito authorizer; each `/api/internal/**` route requires its exact scope, never a
shared set; `/api/internal/**` never requires an API key and always requires Cognito;
`/api/sipsa/**` always requires an API key and never requires Cognito; usage plan
matches ADR-010's general tier (throttle + quota); exactly the two ingestion routes
carry the strict tier; access logs configured without the Authorization header or an
API key value, `data_trace_enabled=false`; all 4 gateway responses exist with the
correct codes; `production` stage; the API key output exposes only the ID; common tags;
CORS disabled by default, correctly reflects a single origin plus the credentials flag,
and rejects both more than one origin and wildcard+credentials. Plus 2 new tests in
`environments/production/tests/production.tftest.hcl` (stub of `module.api_gateway` via
`override_module`, without affecting the 2 wiring tests from TECH-142 already there).
**129 Terraform tests total across the tree** (16+20+9+21+17+23+21+2).

**Explicit gaps, not resolved by this story:**
- IAM/SigV4 as an alternative authorization path for AWS-native automation: not
  implemented — the Cognito authorizer alone covers this story's contract; remains an
  open decision **D** if a real consumer needing it appears.
- `method_path` format for the ingestion throttling override not empirically verified
  against a real deployed API.
- Source-IP preservation behavior for `alb`-type NLB targets not confirmed by AWS — the
  ALB's ingress design uses the conservative CIDR fallback, already documented.
- CORS natively supports a single origin; multiple dynamic origins would require a
  Lambda integration, not built here.
- No custom domain, ACM, Route 53, or WAF — explicitly out of scope.
- No `terraform apply` executed at any point in this sequence of stories.

**Acceptance Criteria:**
- [x] API Gateway REST API (never HTTP API) with documented justification.
- [x] Private VPC Link to the internal ALB (via NLB, corrected and documented
      architecture).
- [x] Cognito authorizer connected to TECH-130's User Pool, without creating a new one.
- [x] Exact per-route scopes on `/api/internal/**`, API key required on
      `/api/sipsa/**`, never both mechanisms combined as authentication.
- [x] Usage plans, general throttling (10/20) and ingestion throttling (1/2), monthly
      quota (100k) — exact ADR-010 values.
- [x] Structured access logs, 30-day retention, no tokens/secrets/bodies.
- [x] Consistent 401/403/429/5xx responses, distinct from the application's shape,
      documented why.
- [x] `/actuator/health` never routed through the gateway.
- [x] `terraform test` 129/129 across the tree.
- [x] `terraform fmt -check -recursive`, `terraform validate` (all roots), TFLint
      (0 issues), `trivy config` (0 unresolved findings, 3 justified LOW exceptions)
      clean.
- [x] `./mvnw -q -DskipTests compile` passes; zero change in `src`/`pom.xml`.
- [x] No custom domain, ACM, Route 53, WAF, real consumers, real tokens, or real API key
      values delivered.
- [x] No `terraform apply`, `terraform import`, AWS CLI, at any point.
- [x] TECH-132 remains `In progress` (not marked Done by this story).

**Completed:** `infra/terraform/modules/api-gateway/` created (6 files incl. tests) and
wired into `environments/production`. Verified locally via the official Docker images
`hashicorp/terraform:1.15.7`, `terraform-linters/tflint:v0.64.0`,
`aquasec/trivy` (none installed on this machine): `fmt -check -recursive` clean;
`terraform init -backend=false && terraform validate` clean for all nine Terraform
roots; `terraform test` 129/129 across the tree; TFLint 0 issues; `trivy config` 0
unresolved findings (3 new LOW exceptions, each individually justified).
`.github/workflows/infra-plan.yml` gained a `terraform test — modules/api-gateway` step.
`./mvnw -q -DskipTests compile` passed; zero change in `src`/`pom.xml`. No `terraform
apply`, `terraform import`, AWS CLI command, real token request, or API key retrieval at
any point; no AWS resource of any kind exists.

---

### TECH-132

**Title:** Private networking — ECS, VPC Link, internal ALB, gateway-bypass prevention
**Type:** Infrastructure / Security
**Priority:** High
**Phase:** —
**Status:** **In progress** — declarative infrastructure complete across VPC/RDS/ECS/
ALB/Cognito/API Gateway (TECH-138, TECH-139, TECH-140, TECH-141, TECH-130, TECH-131);
local deployment hardening done (TECH-144, 2026-07-22 — Cognito human-client gate, ECS
memory/grace-period backed by real local measurements, DB credential strategy
designed); real AWS validation (RDS engine availability, backend bootstrap, OIDC trust
policy, a real plan, cost estimate) remains blocked (TECH-143, kept on its own
unmerged branch as evidence) pending SIPSA-specific AWS credentials, not available in
this environment — see
[docs/operations/aws-production-preflight.md](../operations/aws-production-preflight.md)
**Complexity:** M
**Branch:** — (infrastructure work; VPC foundation landed via
`infra/production-vpc-foundation` (TECH-138), RDS foundation via
`infra/production-rds-foundation` (TECH-139), ECR/ECS task foundation via
`infra/production-ecs-task-foundation` (TECH-140), internal ALB/ECS Service via
`infra/internal-alb-ecs-service` (TECH-141), API Gateway/VPC Link via
`infra/api-gateway-private-integration` (TECH-131), local deployment hardening via
`infra/preflight-local-hardening` (TECH-144); blocked AWS preflight kept, unmerged, on
`infra/production-deployment-preflight` (TECH-143))

**Audit (2026-07-20, `docs/production-aws-readiness-plan`, no AWS resource created, no
code changed):** full classification, gateway-bypass-prevention design, and evidence in
[aws-production-readiness.md](../architecture/aws-production-readiness.md) §2 and §7.
Confirmed by reading `SoapProperties`/`.env.example`: `SOAP_ENDPOINT` is a **public
internet** DANE endpoint, so a **NAT Gateway (or equivalent) is required, not optional**
— VPC endpoints alone cannot cover this egress path (this is evidence-based, not a
default assumption). App-side readiness confirmed **E**: `/actuator/health` is
unauthenticated with Spring Boot's liveness/readiness probe groups already enabled, the
app is fully environment-driven for `DB_HOST`/`DB_PORT`, and it logs to stdout
(CloudWatch-Logs-ready) — no code change needed for any of this. **Resolved (ADR-010,
implemented via TECH-138/TECH-139/TECH-140/TECH-141, all as Terraform code, none applied
to a real account):** ECS Fargate (over EC2); RDS for PostgreSQL (over self-managed),
Single-AZ, PostgreSQL 18; internal ALB + ECS Service, with the ECS→RDS security-group
rule in place; API Gateway REST API with a VPC Link (via an NLB chained to the internal
ALB — see [TECH-131](#tech-131) for the architecture correction this required). Still
open (**D**): VPC-internal TLS policy, whether Cloud Map service discovery is needed
(TECH-141 evaluated it and found no justification yet), and whether an IAM/SigV4
authorizer path is needed alongside Cognito (TECH-131 left this open, no real consumer
requiring it yet). Every real provisioning step itself is **C** (real AWS access) —
**Cannot be marked Done** until a real image exists, a real deployment is verified, and
the remaining D items are resolved.

**Decision/execution plan:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md)
(**Accepted**, 2026-07-21) — Phase 1 (network) and Phase 3 (compute/data) cover this story.
Approved: new dedicated VPC, 2 AZ, ECS Fargate (`desired_count=1`, CPU/memory as
Terraform variables, small starting values to be verified against real ingestion load),
one NAT Gateway initially (accepted single point of failure for egress, documented
upgrade path to one NAT per AZ), RDS for PostgreSQL Single-AZ (encrypted, 7-day backups,
`publicly_accessible=false`, deletion protection — Multi-AZ deferred as a future
upgrade), secrets via AWS Secrets Manager. VPC-internal TLS policy remains open.

**Progress (2026-07-21, [TECH-138](#tech-138), branch `infra/production-vpc-foundation`):**
the network foundation this story depends on is now implemented — VPC (`10.40.0.0/16`),
2 AZs selected deterministically, 2 public + 2 private-application + 2 private-database
subnets, route tables, a single NAT Gateway (accepted risk, documented), an S3 Gateway
VPC Endpoint, and configurable VPC Flow Logs — all in
`infra/terraform/modules/network/`, consumed by `environments/production`. **No AWS
resource has been created** (no `terraform apply` run); this is Terraform code only,
validated via `terraform test` against a mocked provider.

**Progress (2026-07-21, [TECH-139](#tech-139), branch `infra/production-rds-foundation`):**
the RDS PostgreSQL foundation this story depends on is now implemented — DB subnet group
(the two private database subnets from TECH-138), a security group with no ingress rule
yet (the future ECS security group, once it exists, is added via
`allowed_security_group_ids`), a parameter group, and the RDS instance itself
(PostgreSQL 18, Single-AZ, `db.t3.micro` proposed, gp3/20 GiB, encrypted,
`publicly_accessible=false`, 7-day backups, deletion protection, RDS-managed master
password via Secrets Manager) — all in `infra/terraform/modules/database/`, consumed by
`environments/production`. **No AWS resource has been created** (no `terraform apply`
run); validated via `terraform test` against a mocked provider.

**Progress (2026-07-21, [TECH-140](#tech-140), branch
`infra/production-ecs-task-foundation`):** the ECR repository and ECS cluster/task
definition foundation this story depends on are now implemented — an immutable-tag,
scan-on-push ECR repository with a two-rule lifecycle policy; a Fargate-only ECS cluster
(Container Insights on); a task definition (`awsvpc`, CPU/memory parameterized —
256/512 proposed, unverified against real ingestion load — `X86_64`, a single essential
container, `readonlyRootFilesystem` with a `/tmp` tmpfs mount, port 8080); a separate
execution role (ECR pull + Logs write + scoped secret read) and task role (empty
permissions — the application calls no AWS API directly); credentials resolved via the
task definition's `secrets` block from the RDS master secret, explicitly flagged as a
**temporary placeholder** pending a real minimum-privilege application database user —
all in `infra/terraform/modules/ecr/` and `infra/terraform/modules/ecs-task/`, consumed
by `environments/production`. **No ECS Service, no ALB, no image push.** **No AWS
resource has been created** (no `terraform apply` run); validated via `terraform test`
against a mocked provider.

**Progress (2026-07-21, [TECH-141](#tech-141), branch `infra/internal-alb-ecs-service`):**
the internal ALB and ECS Service this story's remaining scope depends on are now
implemented — three security-group relationships (ALB SG with no ingress by default,
populated only via `alb_allowed_ingress_security_group_ids`/TECH-131; ECS service SG
admitting only the ALB SG on port 8080, egress scoped to RDS/HTTPS/DNS; the ECS→RDS
ingress rule added directly on the RDS security group, since `modules/database` creates
it with none); an internal ALB (`internal = true`, private-application subnets, HTTP
listener only — no ACM certificate exists, and the ALB is unreachable outside the VPC
regardless); a target group (`target_type = ip`, health check
`/actuator/health`/`200`, confirmed safe unauthenticated by reading `SecurityConfig` and
`application.yaml`'s `show-details: when-authorized`); an ECS Service
(`desired_count = 1`, `FARGATE`, deployment circuit breaker with rollback,
`health_check_grace_period_seconds = 120` — unmeasured, flagged for validation) — all in
`infra/terraform/modules/ecs-service/`, consumed by `environments/production`. **No
image exists, no task ever runs, no AWS resource has been created** (no `terraform
apply` run); validated via `terraform test` against a mocked provider.

**Progress (2026-07-22, [TECH-131](#tech-131), branch
`infra/api-gateway-private-integration`):** the API Gateway/VPC Link piece — the last
remaining part of the full private-networking picture — is now implemented too. A
Network Load Balancer fronts the classic REST API VPC Link (confirmed via the
provider's own docs that `aws_api_gateway_vpc_link` only accepts an NLB target, never
an ALB directly — a correction to this story's own original, oversimplified diagram),
chained to the existing internal ALB via AWS's documented `alb`-type NLB target group.
The ALB's own security group gets its ingress from `alb_allowed_ingress_cidr_blocks`,
now populated with this stack's private-application subnet CIDRs (the fallback TECH-141
already built) rather than `alb_allowed_ingress_security_group_ids`, since the NLB
deliberately has no security group of its own — all in
`infra/terraform/modules/api-gateway/`, consumed by `environments/production`. **No
AWS resource has been created** (no `terraform apply` run); validated via `terraform
test` against a mocked provider, plus a stub of `module.api_gateway` in
`environments/production/tests/production.tftest.hcl`.

**Progress (2026-07-22, [TECH-144](#tech-144), branch
`infra/preflight-local-hardening`):** a deployment preflight was attempted
([TECH-143](#tech-143)) but found no SIPSA-specific AWS credentials in the environment —
every AWS-touching check (RDS engine availability, backend bootstrap, OIDC, a real
plan, cost estimate) remains blocked, kept on TECH-143's own unmerged branch as
evidence. What was verifiable locally, without AWS, was extracted into this story and
merged: the Cognito human app client is now gated behind `enable_human_client` (default
`false`); ECS task memory moved from an unmeasured 512 MiB to 1024 MiB, backed by real
local measurements at *both* values (89.73% memory utilization at 512 MiB; 44.89%–
55.25% at 1024 MiB across three fresh re-verification runs, no OOM); the health-check
grace period moved from an unmeasured 120s to 480s, based on six real local startup
samples (187s–385s, the 385s sample kept and explained, not discarded); an application
database credential strategy (`sipsa_migration`/`sipsa_runtime`, least-privilege,
exact `GRANT`s, never executed) and a Flyway rolling-deployment decision (a one-off
migration task before service rollout, not yet built) were both designed. **No AWS
resource has been created; no AWS credential was used.** Full detail:
[docs/operations/aws-production-preflight.md](../operations/aws-production-preflight.md).

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

### TECH-137

**Title:** Terraform bootstrap and GitHub OIDC validation
**Type:** Infrastructure
**Priority:** High
**Phase:** —
**Status:** **Done**
**Complexity:** S
**Branch:** `infra/terraform-bootstrap`
**Note on numbering:** `TECH-133` was already assigned (Centralize and validate monthly
ingestion window configuration, Done 2026-07-17) — this story deliberately does not reuse
that ID, taking the next free number instead.

**Origin:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) (**Accepted**,
2026-07-21) Phase 0 — the repository owner approved Terraform, in this repository, single
AWS account, `us-east-1`, `production`-only environment, and the rest of ADR-010's
initial topology. This story is the first implementation branch: it introduces the
Terraform project structure, remote-state bootstrap, and CI validation — no AWS resource
requiring a real `apply` is created.

**Scope:**
- Terraform directory structure: `infra/terraform/bootstrap/` (creates the S3 state
  bucket + locking mechanism) and `infra/terraform/environments/production/` (the
  production root module — no child modules yet; those are added with each real story,
  starting with TECH-132's network module).
- Provider and version constraints (`required_version`, `required_providers`, pinned AWS
  provider version — no floating versions).
- Common variables (`project_name`, `environment`, `aws_region`, `owner`, `cost_center`,
  `repository`, `managed_by`) and a common tags convention
  (`Project`/`Environment`/`Owner`/`ManagedBy`/`Repository`/`CostCenter`) every future
  resource must apply.
- Remote-state bootstrap strategy documented and implemented as a **separate bootstrap
  stack** (resolves the chicken-and-egg problem of Terraform needing a backend before one
  exists): the bootstrap stack itself uses local state (a one-time, manually-run
  exception, documented as such) to create the S3 bucket (versioned, encrypted, public
  access blocked) and locking mechanism; `environments/production` then points at that
  bucket as its real backend.
- GitHub Actions `infra-plan.yml`: `terraform fmt -check`, `terraform validate`, lint,
  security scan (`tfsec`), `terraform plan` — plan step conditioned on the OIDC role
  existing (documented as a prerequisite, not invented here); `fmt`/`validate` run without
  any AWS credentials.
- `.gitignore` rules for `*.tfstate`, `*.tfstate.backup`, `*.tfplan`, `.terraform/`, and
  any `*.auto.tfvars` that could carry secrets; `.terraform.lock.hcl` is committed
  (provider checksums, not a secret).
- No Cognito, ECS, ALB, RDS, API Gateway, VPC, or NAT Gateway resource is created by this
  story.

**Acceptance Criteria:**
- [x] `terraform fmt -check -recursive infra/terraform` passes.
- [x] `terraform init -backend=false` + `terraform validate` pass for both the bootstrap
      and `environments/production` configurations.
- [x] No AWS resource is created (`terraform apply` is never run against a real account).
- [x] `infra-plan.yml` runs `fmt`/`validate` without requiring AWS credentials.
- [x] Every future resource's tagging convention is defined and documented, even though
      no resource exists yet to apply it to.
- [x] TECH-130/131/132 remain `Pending` — this story is a prerequisite, not part of their
      own acceptance criteria.

**Completed:** Directory structure created (`infra/terraform/bootstrap/`,
`infra/terraform/environments/production/`), each with pinned `required_version` (`~>
1.9`) and `required_providers` (`hashicorp/aws ~> 5.60`) — no floating versions. Common
variables (`project_name`, `environment`, `aws_region`, `owner`, `cost_center`,
`repository`, `managed_by`) and a `local.common_tags` block
(`Project`/`Environment`/`Owner`/`ManagedBy`/`Repository`/`CostCenter`) applied via each
provider's `default_tags`. Remote-state chicken-and-egg resolved via a separate,
manually-applied, local-state `bootstrap/` stack (creates an S3 bucket — versioned,
AES256-encrypted, public access fully blocked — and a DynamoDB lock table with
server-side encryption and point-in-time recovery enabled); `environments/production`
carries a partial S3 backend block, real values supplied via `backend.hcl` (gitignored,
`.example` committed). `.gitignore` updated for `*.tfstate*`, `*.tfplan`, `.terraform/`,
`backend.hcl`, `terraform.tfvars`, and related patterns; `.terraform.lock.hcl` committed
for both stacks (provider checksums, not secrets). `.github/workflows/infra-plan.yml`
added: `terraform fmt -check`, `terraform validate` (both stacks), TFLint (`aws`
ruleset + naming-convention rule), and a `tfsec` scan run on every PR touching
`infra/terraform/`, none requiring AWS credentials; a separate `plan` job is wired for
GitHub Actions OIDC (`aws-actions/configure-aws-credentials`) but stays inert
(`if: vars.AWS_TERRAFORM_PLAN_ROLE_ARN != ''`) until that IAM role is created as a
follow-up prerequisite for TECH-130/TECH-132 — not invented here. Verified locally via
the official `hashicorp/terraform:1.9`, `terraform-linters/tflint`, and `aquasec/tfsec`
Docker images (Terraform not installed on this machine): `fmt -check -recursive` clean;
`terraform init -backend=false && terraform validate` succeeded for both `bootstrap/` and
`environments/production`; TFLint 0 issues; tfsec 0 unresolved findings (3 documented
`tfsec:ignore` exceptions — AWS-owned-key encryption and no dedicated access-logging
bucket for a single-owner, manually-run bootstrap stack, each with an inline rationale,
not blanket-suppressed). No `terraform apply` was run against AWS at any point; no
Cognito, ECS, ALB, RDS, API Gateway, VPC, or NAT Gateway resource exists. TECH-130/131/132
remain `Pending`.

**Correction (2026-07-21, same branch, before merge):** four follow-up commits brought
this story in line with current official documentation ahead of the first real `apply`:
DynamoDB state locking removed entirely in favor of Terraform's S3-native lockfile
(`use_lockfile = true` — current Terraform docs mark DynamoDB-locking as legacy);
Terraform raised to `>= 1.14.0, < 2.0.0` (pinned to `1.15.7` in CI/Docker, the floor
S3-native locking requires); AWS provider raised to `>= 6.0.0, < 7.0.0` (pinned to
`6.55.0`) — adopted before any AWS state existed, confirmed against the official v6
upgrade guide as carrying no breaking change for this repository's S3 resources; `tfsec`
replaced outright by `trivy config` (its checks are now part of Trivy upstream), with the
two surviving S3-only exceptions re-justified under Trivy's own `AVD-AWS-0089`/
`AVD-AWS-0132` IDs rather than copied mechanically; `infra-plan.yml`'s third-party actions
pinned by immutable commit SHA with a human-readable version comment on each; ADR-010
gained an explicit OIDC trust-policy contract (audience, repository-scoped `sub`, three
separate plan/apply/deploy roles, no ARN invented) and resolved API Gateway
REST-vs-HTTP-API outright (**REST API**). Re-validated: `fmt` clean, both stacks
`validate` clean under Terraform 1.15.7/AWS provider 6.55.0, TFLint 0 issues, `trivy
config` 0 unresolved findings. Merged to `main` at commit `9e4f7da`. Still no AWS
resource created, no `terraform apply` run.

---

### TECH-138

**Title:** Provision production VPC foundation
**Type:** Infrastructure
**Priority:** High
**Phase:** —
**Status:** **Done**
**Complexity:** M
**Branch:** `infra/production-vpc-foundation`

**Origin:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) (Accepted) Phase 1 —
the network substrate [TECH-132](#tech-132) depends on. Scoped narrowly to networking
only: VPC, subnets, routing, NAT, the S3 Gateway Endpoint, and VPC Flow Logs. **Does not**
implement ECS, ALB, RDS, Cognito, API Gateway, VPC Link, Secrets Manager, ECR, WAF,
Route 53, or ACM — those remain separate, later stories against this one's outputs.

**Topology:** VPC `10.40.0.0/16`, two Availability Zones selected deterministically via
`data.aws_availability_zones` (never hardcoded), three subnet tiers × 2 AZs:

| Tier | AZ A | AZ B | Route table |
|---|---|---|---|
| Public | `10.40.0.0/24` | `10.40.1.0/24` | One shared table → Internet Gateway |
| Private application | `10.40.10.0/24` | `10.40.11.0/24` | Per-AZ table → the single NAT Gateway |
| Private database | `10.40.20.0/24` | `10.40.21.0/24` | Per-AZ table, no default route at all |

All CIDRs are Terraform variables with `cidrhost`-based validation, not literals inside
any resource block.

**NAT Gateway:** exactly one, in the first public subnet — a deliberate cost/availability
trade-off (ADR-010), documented in `modules/network/README.md`: roughly half the cost of
one-per-AZ, at the cost of (a) a single point of failure for all private-application
egress and (b) cross-AZ data-transfer charges for traffic originating in the second AZ.
Private-application route tables are kept **per-AZ** specifically so the future
NAT-per-AZ migration only requires re-pointing one AZ's table, not restructuring subnets.
No NAT Instance is used.

**S3 Gateway VPC Endpoint:** associated with the two private-application route tables
only. No hourly/per-GB charge (unlike an Interface endpoint) — added outright to keep S3
traffic (and, once TECH-132 introduces ECS, ECR image-layer pulls) off the NAT Gateway.
Interface endpoints for ECR API/DKR, CloudWatch Logs, Secrets Manager, and STS are
deliberately not added yet — each carries an hourly + per-GB charge; reconsider once real
NAT traffic volume or a specific security requirement justifies it.

**VPC Flow Logs:** configurable (`enable_vpc_flow_logs`, default `true`), `REJECT`
traffic only by default (diagnostically useful signal without `ALL`'s volume), 30-day
CloudWatch Logs retention by default — never infinite. A dedicated IAM role/policy scoped
to exactly the three log actions needed, against this module's own log group ARN only.

**Security groups:** none created — ALB/ECS/RDS security groups are added alongside the
resources that actually consume their rules (TECH-132's later phases), not speculatively
here. No `0.0.0.0/0` ingress rule exists anywhere in this story.

**Module:** `infra/terraform/modules/network/` (`main.tf`, `variables.tf`, `outputs.tf`,
`versions.tf`, `README.md`) — no third-party/public module used; small and auditable by
design. `environments/production/main.tf` consumes it; `environments/production`'s own
`variables.tf`/`outputs.tf` gained pass-through variables (defaults matching the topology
above) and re-exposed outputs.

**Outputs:** `vpc_id`, `vpc_cidr`, `availability_zones`, `public_subnet_ids`,
`private_app_subnet_ids`, `private_database_subnet_ids`, `public_route_table_id`,
`private_app_route_table_ids`, `database_route_table_ids`, `internet_gateway_id`,
`nat_gateway_id`, `s3_gateway_endpoint_id`, `flow_log_group_name` (null when Flow Logs
are disabled). No sensitive value is exposed.

**Tests:** `infra/terraform/modules/network/tests/network.tftest.hcl` — native
`terraform test`, AWS provider fully mocked (`mock_provider "aws" {}`), **no real AWS
account contacted**. 16 cases, all passing: VPC DNS support/hostnames; exactly 2 AZs
selected deterministically; 2 public/2 private-app/2 private-database subnets with
correct CIDRs; exactly 1 NAT Gateway in the first public subnet; private-application
route tables route to the NAT; database route tables have no default route at all; the
public route table routes to the Internet Gateway; private subnets never
auto-assign public IPs; the S3 Gateway Endpoint has the correct type and service name;
Flow Logs are on by default with `REJECT`/30-day retention *and* can be deliberately
disabled via variable (output goes `null`); common tags are applied; two negative tests
confirm malformed input (wrong subnet count, invalid CIDR) is rejected by variable
validation. `command = apply` is used against the mocked provider (not `command = plan`)
because computed attributes like resource IDs stay unresolved under `plan` even when
mocked — `apply` here never touches real infrastructure, it only lets the mock provider
finish resolving those values.

**Acceptance Criteria:**
- [x] VPC, Internet Gateway, 2 public + 2 private-app + 2 private-database subnets, route
      tables and associations, 1 NAT Gateway + EIP, S3 Gateway VPC Endpoint, configurable
      VPC Flow Logs, and every required output all exist in Terraform code.
- [x] All CIDRs are validated Terraform variables, not hardcoded literals.
- [x] AZ selection is deterministic via `data.aws_availability_zones`, never a hardcoded
      AZ name.
- [x] Database subnets have no route to the NAT Gateway or the Internet Gateway.
- [x] No security group is created in this story.
- [x] `terraform test` passes (16/16) against a mocked provider — no AWS account
      contacted.
- [x] `terraform fmt -check -recursive`, `terraform validate` (both stacks + module),
      TFLint, and `trivy config` are all clean.
- [x] `./mvnw -q -DskipTests compile` passes; zero `src`/`pom.xml` change.
- [x] No `terraform apply` run; no AWS resource created; no AWS credential added.
- [x] TECH-132 updated to `In progress` (not `Done`) — ECS/ALB/RDS remain unimplemented.

**Completed:** `infra/terraform/modules/network/` created (5 files) and wired into
`environments/production`. Verified locally via the official `hashicorp/terraform:1.15.7`,
`terraform-linters/tflint`, and `aquasec/trivy` Docker images (none installed on this
machine): `fmt -check -recursive` clean; `terraform init -backend=false && terraform
validate` clean for `bootstrap/`, `environments/production` (now including the module),
and the module standalone; `terraform test` 16/16 passing; TFLint 0 issues; `trivy config`
0 unresolved findings across all four Terraform roots (2 pre-existing S3 exceptions from
TECH-137 plus 2 new, module-specific exceptions — AWS-owned-key CloudWatch Logs
encryption, and the public subnet tier's intentional `map_public_ip_on_launch = true`,
each justified inline). `.github/workflows/infra-plan.yml` gained a `terraform test —
modules/network` step (mocked provider, no credentials). `./mvnw -q -DskipTests compile`
passed; zero `src`/`pom.xml` change. No `terraform apply` executed against AWS at any
point; no AWS resource of any kind exists. TECH-132 updated to `In progress`.

---

### TECH-139

**Title:** Define production RDS PostgreSQL foundation
**Type:** Infrastructure
**Priority:** High
**Phase:** —
**Status:** **Done**
**Complexity:** M
**Branch:** `infra/production-rds-foundation`

**Origin:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) (Accepted) — the RDS
portion of [TECH-132](#tech-132)'s Phase 3. Scoped narrowly to the database only: DB subnet
group, security group (no ingress rule yet), parameter group, and the RDS instance
itself. **Does not** implement ECS, ALB, ECR, Cognito, API Gateway, VPC Link, Route 53,
ACM, WAF, Cognito secrets, application deployment, a real database connection, or a
Flyway migration against AWS.

**PostgreSQL version:** `postgres_engine_version` defaults to `"18"` (major version
only), chosen by inspecting the repository, not assumed — `docker-compose.yml` and all 11
Testcontainers-based integration tests use `postgres:18.0-alpine3.22`; Flyway
(`flyway-database-postgresql`, Spring Boot 4.1.0-managed) supports it; the only extension
in use (`citext`, `V1__initial_schema.sql`) is a standard RDS-available contrib
extension. **Not verified against a live AWS account** (no AWS API call made) — confirm
`aws rds describe-db-engine-versions --engine postgres` before the first real apply.
`auto_minor_version_upgrade = true` (minor releases are backward-compatible patches; no
evidence to pin one).

**Instance class and storage — proposals, not confirmed availability:**
`instance_class` defaults to `"db.t3.micro"` (burstable, **non-Graviton** — Graviton
availability/compatibility for this engine version is unconfirmed). `storage_type = gp3`
(no Provisioned IOPS), `allocated_storage = 20` GiB (the gp3 minimum),
`max_allocated_storage = 100` GiB (autoscaling ceiling, bounds worst-case cost). Both
`instance_class` and `postgres_engine_version` are explicitly flagged as **requiring
AWS-side availability validation before the first real apply** — the same honesty
standard TECH-138 already applied to ECS Fargate task sizing.

**Single-AZ:** `multi_az = false` by default (ADR-010) — a cost decision, not an
oversight. Accepted risk: no automatic failover; an AZ outage or maintenance event causes
a full outage until AWS completes recovery. Future path: `multi_az = true` is a one-line
variable change. **Must be revisited before real user traffic depends on this database.**

**Network:** the DB subnet group uses exclusively the two private database subnets from
TECH-138's network module — never public or private-application subnets.
`publicly_accessible = false` is hardcoded, not a variable. The RDS security group is
created with **no ingress rule** — no ECS security group exists yet; a future story
(TECH-132's compute phase) passes it via `allowed_security_group_ids`, which creates
exactly one `aws_security_group_rule` per ID, scoped to `source_security_group_id`, never
a CIDR block or `0.0.0.0/0`.

**Backups and maintenance — explicit UTC conversion:** the application's daily ingestion
window is `14:20`-`23:59` America/Bogota (UTC-5) = `19:20` UTC to `04:59` UTC the next
day; monthly jobs (days 8, 10) fire at `14:30` Bogota = `19:30` UTC, inside that same
window. `backup_window` defaults to `"06:00-06:30"` UTC (`01:00`-`01:30` AM Bogota) and
`maintenance_window` to `"sun:07:00-sun:08:00"` UTC (Sunday `02:00`-`03:00` AM Bogota) —
both fall inside the remaining `05:00`-`19:19` UTC safe window on every day of the month,
and don't overlap each other. `backup_retention_period = 7` (ADR-010), `deletion_protection
= true`, `skip_final_snapshot = false` with a `random_id`-suffixed final-snapshot
identifier (reproducible per instance, always unique across recreations — a static name
would collide on a second destroy).

**Credentials — RDS-managed, never a Terraform variable:**
`manage_master_user_password = true` — RDS creates and owns the master password directly
in Secrets Manager; Terraform never sees, stores, or versions the password value, only
`master_user_secret[0].secret_arn` (a reference). No `password` variable exists anywhere
in the module. Custom secret rotation is explicitly not implemented.

**Parameter group:** created only because there are real parameters to manage —
`log_connections = 1`, `log_disconnections = 1` (low-volume, useful). `rds.force_ssl` is
**deliberately not set**: the application's JDBC URL specifies no `sslmode` today, and
forcing SSL at the database before confirming the client negotiates it correctly would
risk breaking the future production connection — documented gap, not silently resolved.
PostgreSQL `timezone` left at RDS's own default (UTC) — this repository's timezone
handling already happens entirely at the application layer.

**Monitoring:** Performance Insights and Enhanced Monitoring both disabled by default
(`performance_insights_enabled = false`, `monitoring_interval = 0`) — added cost with no
established need yet; standard CloudWatch metrics remain available regardless. CloudWatch
Logs exports: `postgresql` and `upgrade` only (low-volume); log groups created ahead of
the RDS instance with explicit 30-day retention (never infinite) — RDS's own
auto-created log groups default to never expire otherwise.

**Trivy exceptions** (each reassessed for this module, not copied from another module):

| Finding | Resource | Risk | Justification | Exception |
|---|---|---|---|---|
| AVD-AWS-0017 (Log group not KMS-encrypted) | `aws_cloudwatch_log_group.postgresql` | Low — AWS-owned key instead of customer-managed | Server log (connections/disconnections), not query content or application data; same call already made for the Terraform state bucket and the network module's Flow Logs group | `# trivy:ignore:AVD-AWS-0017`, revisit if compliance requires it |
| AVD-AWS-0017 (Log group not KMS-encrypted) | `aws_cloudwatch_log_group.upgrade` | Low — same as above | Engine upgrade history only, lower sensitivity still | `# trivy:ignore:AVD-AWS-0017`, revisit if compliance requires it |
| AVD-AWS-0133 (Performance Insights not enabled) | `aws_db_instance.main` | Low — reduced query-level visibility | Deliberate ADR-010/TECH-139 decision (see Monitoring above), not an oversight | `# trivy:ignore:AVD-AWS-0133`, revisit once real traffic justifies the cost |
| AVD-AWS-0176 (IAM DB Authentication not enabled) | `aws_db_instance.main` | Medium — password-based auth only, no IAM token option | Requires application-side changes (IAM auth tokens) out of TECH-139's scope; no ECS task or real connection exists yet; current design (RDS-managed Secrets Manager password, no plaintext credential) is already a strong baseline | `# trivy:ignore:AVD-AWS-0176`, evaluate once TECH-132's ECS task exists — follow-up story, not yet created |

**Module:** `infra/terraform/modules/database/` (`main.tf`, `variables.tf`,
`outputs.tf`, `versions.tf`, `README.md`, `tests/`) — no third-party/public module used.
`environments/production/main.tf` consumes it, passing `vpc_id` and
`private_database_subnet_ids` from the network module's own outputs;
`environments/production`'s `variables.tf`/`outputs.tf` gained pass-through variables
(prefixed `db_*`, defaults matching ADR-010/TECH-139) and re-exposed outputs.

**Outputs:** `db_instance_id`, `db_instance_arn`, `db_endpoint` (`sensitive = true`,
defensive posture — not a credential, but identifies where the database is reachable),
`db_port`, `db_name`, `db_security_group_id`, `db_subnet_group_name`,
`master_secret_arn` (ARN only, not marked sensitive — reading the actual secret requires
a separate IAM permission this ARN does not grant). No master password, secret value, or
connection string with embedded credentials is ever exposed.

**Tests:** `infra/terraform/modules/database/tests/database.tftest.hcl` — native
`terraform test`, AWS provider fully mocked (`mock_provider "aws" {}`), the `random`
provider real but network-free (pure local computation). **No real AWS account
contacted.** 20 cases, all passing: DB subnet group uses exactly the two given database
subnets; `publicly_accessible = false`; `multi_az = false` by default;
`storage_encrypted = true`; `backup_retention_period = 7` by default;
`deletion_protection = true` by default; a final snapshot is required by default with an
identifier incorporating the random unique suffix; RDS manages the master password
(`manage_master_user_password = true`, secret ARN resolvable); no ingress rule exists
with the default empty `allowed_security_group_ids`, and exactly one
security-group-scoped (never CIDR) rule is created per configured ID; default port 5432;
common tags applied; `instance_class` and storage settings are overridable via variable;
Performance Insights and Enhanced Monitoring disabled by default; both CloudWatch log
groups exist with 30-day retention; three negative tests confirm malformed input (fewer
than two DB subnets, storage below the gp3 minimum, a Provisioned-IOPS storage type) is
rejected by variable validation. Two criteria from the acceptance checklist below are
verified by reading the module's own source, not a runtime assertion — see the test
file's leading comment: no `password`/`master_password` variable exists anywhere in
`variables.tf`, and `db_endpoint` is declared `sensitive = true` in `outputs.tf`.

**Acceptance Criteria:**
- [x] DB subnet group, RDS security group (no ingress), parameter group, and the RDS
      PostgreSQL instance all exist in Terraform code, consuming TECH-138's network
      module outputs exclusively for networking.
- [x] `publicly_accessible = false`, `multi_az = false`, `storage_encrypted = true` —
      confirmed both by direct code inspection and by `terraform test`.
- [x] `backup_retention_period = 7`, `deletion_protection = true`,
      `skip_final_snapshot = false` with a reproducible-but-unique final snapshot
      identifier.
- [x] `manage_master_user_password = true`; no `password` variable exists anywhere in the
      module.
- [x] Security group has zero ingress rules by default; a future consumer's security
      group ID (never a CIDR block) is the only way to add one.
- [x] Port 5432, validated 1-65535.
- [x] `instance_class`, `allocated_storage`, `max_allocated_storage`, and `storage_type`
      are all Terraform variables, not hardcoded — `storage_type` rejects Provisioned
      IOPS types.
- [x] Performance Insights and Enhanced Monitoring both disabled by default.
- [x] CloudWatch log group exports (`postgresql`, `upgrade`) have explicit, non-infinite
      retention.
- [x] `terraform test` passes (20/20) against a mocked provider — no AWS account
      contacted.
- [x] `terraform fmt -check -recursive`, `terraform validate` (all four Terraform roots),
      TFLint, and `trivy config` are all clean (4 documented, individually-justified
      exceptions — none about encryption presence, public access, or backups).
- [x] `./mvnw -q -DskipTests compile` passes; zero `src`/`pom.xml` change.
- [x] No `terraform apply`, `terraform import`, AWS CLI call, real RDS connection, or
      Flyway migration against AWS.
- [x] TECH-132 updated to `In progress` (unchanged from TECH-138 — still not `Done`);
      ECS/ALB remain unimplemented.

**Completed:** `infra/terraform/modules/database/` created (7 files including tests) and
wired into `environments/production` alongside the existing network module. Verified
locally via the official `hashicorp/terraform:1.15.7`, `terraform-linters/tflint`, and
`aquasec/trivy` Docker images (none installed on this machine): `fmt -check -recursive`
clean; `terraform init -backend=false && terraform validate` clean for all four Terraform
roots (`bootstrap/`, `environments/production`, `modules/network/`,
`modules/database/`); `terraform test` 20/20 passing for the database module (16/16
still passing for the network module, re-confirmed unaffected); TFLint 0 issues; `trivy
config` 0 unresolved findings across the whole tree (4 new module-specific exceptions,
each individually justified per the table above — not a blanket suppression).
`.github/workflows/infra-plan.yml` gained a `terraform test — modules/database` step
(mocked provider, no credentials). `./mvnw -q -DskipTests compile` passed; zero
`src`/`pom.xml` change. No `terraform apply`, `terraform import`, AWS CLI call, real RDS
connection, or Flyway migration executed against AWS at any point; no AWS resource of any
kind exists; no AWS credential was added. TECH-132 remains `In progress` (not `Done`) —
ECS and the internal ALB are still unimplemented.

---

### TECH-140

**Title:** Define production ECR and ECS task foundation
**Type:** Infrastructure
**Priority:** High
**Phase:** —
**Status:** **Done**
**Complexity:** M
**Branch:** `infra/production-ecs-task-foundation`

**Origin:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) (Accepted) — the
ECR/ECS-cluster/task-definition portion of [TECH-132](#tech-132)'s Phase 3. Scoped
narrowly: no ECS Service, ALB, target group, listener, autoscaling, API Gateway, VPC
Link, Cognito, Route 53, ACM, WAF, real deploy, real image, or real RDS connection.

**Application configuration inventory** (via
`grep -RIn -e '\${' -e '@ConfigurationProperties' -e 'SPRING_DATASOURCE' -e 'SERVER_PORT'
-e 'SPRING_PROFILES_ACTIVE' src/main/resources src/main/java Dockerfile
docker-compose.yml`):

| Variable | Sensitive | Future source | Required at startup | Default |
|---|---:|---|---:|---|
| `DB_USERNAME` | Yes | Secrets Manager (RDS-managed secret — temporary, see below) | Yes | none |
| `DB_PASSWORD` | Yes | Secrets Manager (same) | Yes | none |
| `DB_HOST` | No | `modules/database`'s `db_address` output | Yes (in AWS) | `localhost` (dev only) |
| `DB_PORT` | No | `modules/database`'s `db_port` output | No | `5432` |
| `DB_NAME` | No | `modules/database`'s `db_name` output | No | `sipsa_db` |
| `SPRING_PROFILES_ACTIVE` | No | Terraform variable (`ecs_spring_profile`) | Yes (determines the profile) | `dev` (base app), `docker` set explicitly in the task definition |
| `PORT` / `server.port` | No | Terraform variable (`ecs_container_port`) | No | `8080` — confirmed in `application.yaml` and `Dockerfile`, not assumed |
| `SIPSA_JWT_ISSUER_URI` | No (public URL) | `module.cognito.issuer_url`, wired into the task definition by [TECH-142](#tech-142) | Yes outside dev/docker (fails fast) | empty |
| `SIPSA_JWT_ALLOWED_CLIENT_IDS` | No | `module.cognito.allowed_client_ids_parameter_arn` (SSM), wired by [TECH-142](#tech-142) | No | empty |
| `SOAP_ENDPOINT` and other `SOAP_*` (timeouts, retries) | No | Already point at the real DANE endpoint | No | already-operational values |
| `INGESTION_*`, `SIPSA_ASYNC_*`, `SIPSA_HEALTH_*`, `LOG_LEVEL_*` | No | Operational settings, no new AWS source | No | already defined, unchanged |

No variable the application doesn't consume was invented — the table reflects exactly
what the grep found, not a speculative list.

**ECR:** `image_tag_mutability = IMMUTABLE`, `scan_on_push = true`, `AES256` encryption
by default (KMS evaluated, not enabled without a real requirement — Trivy exception
documented). Two-rule lifecycle: untagged images expire after 7 days
(`expire_untagged_after_days`); tagged images are capped at the latest 20
(`keep_last_tagged_images`) — a count limit, not a time limit, so the currently deployed
image is never deleted if a release takes longer than usual. No cross-region
replication.

**ECS Cluster:** Fargate only (`capacity_providers = ["FARGATE"]`), no EC2 capacity
provider, no `FARGATE_SPOT` (an interruption mid-scheduled-ingestion is a real,
unacceptable risk for this single production task). Container Insights enabled by
default (`enable_container_insights = true`) — real documented cost, judged acceptable
given these are scheduled jobs with no real-time human supervision.

**Task Definition:** `network_mode = "awsvpc"`, `requires_compatibilities = ["FARGATE"]`,
`cpu = 256` / `memory = 512` (MiB) — **proposals, not confirmed capacities**; documented
as needing validation against real heap/native-memory consumption, the largest-volume
Parcial ingestion (229k+ records), GC behavior, and OOM risk before the first real
deployment. `cpu_architecture = "X86_64"` by default — this repository's CI
(`ubuntu-latest`) builds x86_64 today, with no multi-arch pipeline, no ARM64
compatibility verification for Java 25, and no native-dependency audit; ARM64 remains
documented as a future optimization, not adopted without that evidence.

**Port:** 8080, confirmed directly from `application.yaml`
(`server.port: ${PORT:8080}`) and `Dockerfile` (`EXPOSE 8080`) — not assumed. Future
health check documented (not yet created): path `/actuator/health`, port 8080, expected
status `200`, with startup grace sufficient for Spring context initialization.

**Container security:** `readonlyRootFilesystem = true` — no evidence of disk writes
outside the JVM (confirmed: no `java.io.File`/`Files.write`/`createTempFile` in
`src/main/java`); a `tmpfs` mount on `/tmp` (128 MiB) as a safeguard for JVM/library
needs (JAXB/CXF) without risking breaking the application over an unvalidated
configuration. Non-root user already applied at the image level (`Dockerfile`'s
`USER appuser`). `privileged` isn't even supported by Fargate. No additional
`linuxParameters.capabilities`. A single container definition, `essential = true`.

**Ephemeral storage:** no `ephemeral_storage` block — Fargate's default (20 GiB) is left
as-is with no evidence more is needed; expected use: JVM temporaries, SOAP response
buffering, non-persistent logs (go to stdout via `awslogs`).

**IAM — execution role vs. task role:** the execution role (used by the ECS agent to
prepare the task) attaches only the standard managed policy
`AmazonECSTaskExecutionRolePolicy` plus an inline policy scoped to the exact ARNs of the
referenced secrets/parameters — never `Resource = "*"`, never
`SecretsManagerReadWrite`, never `AdministratorAccess`/`PowerUserAccess`. The task role
(credentials available inside the application) gets **no additional permissions by
default** — the application calls no AWS API directly today (JPA against RDS via a
database credential, not IAM authentication).

**Secrets:** the task definition references RDS's master secret
(`modules/database`'s `master_secret_arn`) for `DB_USERNAME`/`DB_PASSWORD`, resolved via
the task definition's `secrets` block (`valueFrom`), **never as a plaintext environment
variable**. This is **explicitly a temporary integration**, not the final design: this
story does not create a least-privilege application user (would require a real SQL
bootstrap, out of scope) — a real deployment must replace this credential before going
to production; using the master secret permanently would violate the least-privilege
principle.

**Trivy exceptions** (re-evaluated for these two modules, not copied mechanically):

| Finding | Resource | Risk | Justification | Exception |
|---|---|---|---|---|
| AVD-AWS-0033 (ECR repository not KMS-encrypted) | `aws_ecr_repository.app` | Low — AWS-owned key instead of customer-managed | Evaluated, not enabled without a real compliance driver; same call already made repeatedly in this codebase | `# trivy:ignore:AVD-AWS-0033`, revisit if compliance requires it |
| AVD-AWS-0017 (Log group not KMS-encrypted) | `aws_cloudwatch_log_group.app` (ecs-task) | Low — AWS-owned key instead of customer-managed | Same rationale as the RDS/network/bootstrap log groups already exempted | `# trivy:ignore:AVD-AWS-0017`, revisit if compliance requires it |

No exception touches encryption presence, public access, or backups — both are about
*which* encryption key manages already-present encryption.

**Modules:** `infra/terraform/modules/ecr/` and `infra/terraform/modules/ecs-task/`
(each with `main.tf`, `variables.tf`, `outputs.tf`, `versions.tf`, `README.md`,
`tests/`) — no third-party public modules. `environments/production/main.tf` consumes
both, chaining `module.ecr.repository_url` and `module.database.db_address`/`db_port`/
`db_name`/`master_secret_arn` into `module.ecs_task`.

**Outputs — ECR:** `repository_name`, `repository_arn`, `repository_url`. **Outputs —
ECS/task:** `ecs_cluster_id`, `ecs_cluster_arn`, `task_definition_arn`,
`task_definition_family`, `execution_role_arn`, `task_role_arn`,
`application_log_group_name`, `container_name`, `container_port`. No secret is exposed
in any output.

**Tests:** `infra/terraform/modules/ecr/tests/ecr.tftest.hcl` (9 cases) and
`infra/terraform/modules/ecs-task/tests/ecs-task.tftest.hcl` (17 cases) — 26 total, all
green, `terraform test` with a fully mocked AWS provider (`mock_provider "aws" {}`),
**zero real AWS account contacted**. Coverage: immutable tags, scan on push, encryption
by default, parameterizable tagged/untagged image lifecycle, common tags (ECR); Fargate
+ `awsvpc`, parameterizable CPU/memory, `X86_64` architecture by default, 30-day log
group, correctly configured `awslogs`, execution role and task role as distinct IAM
roles, zero administrative permissions, no `privileged` container or additional
capabilities, a single essential container definition, explicit rejection of the
`latest` tag, confirmed port 8080, credentials never in plaintext, and structural
confirmation that only the cluster and task definition exist (no
`aws_ecs_service`/`aws_lb` declared in the module) (ECS/task).

**Acceptance Criteria:**
- [x] ECR repository with immutable tags, scan on push, encryption, and a
      parameterizable lifecycle policy created in Terraform code.
- [x] ECS Fargate cluster (no EC2, no `FARGATE_SPOT`) with configurable Container
      Insights.
- [x] Fargate task definition (`awsvpc`, parameterizable CPU/memory, `X86_64`
      architecture by default) with a single essential container definition.
- [x] Container port confirmed as 8080 from the application's real code.
- [x] Execution role and task role are separate IAM roles; the execution role does not
      exceed the necessary scope (standard managed policy + scoped secrets); the task
      role has no default permissions.
- [x] Database credentials resolved via Secrets Manager (`secrets` block), never as a
      plaintext environment variable; master-secret wiring explicitly documented as
      temporary.
- [x] No `0.0.0.0/0`, no `privileged` container, no additional capabilities.
- [x] Application log group with explicit 30-day retention.
- [x] `terraform test` passes (26/26) against a mocked provider — zero real AWS account
      contacted.
- [x] `terraform fmt -check -recursive`, `terraform validate` (all five Terraform
      roots), TFLint, and `trivy config` are all clean (2 new, individually justified
      exceptions).
- [x] `./mvnw -q -DskipTests compile` passes; zero change in `src`/`pom.xml`.
- [x] No `terraform apply`, `terraform import`, AWS CLI, `docker push`, ECR login, or
      ECS deployment.
- [x] No ECS Service or ALB exists in this module — confirmed both by code inspection
      and a dedicated test.
- [x] TECH-132 updated to `In progress — VPC, RDS and ECS task foundations complete`
      (not `Done`).

**Completed:** `infra/terraform/modules/ecr/` and `infra/terraform/modules/ecs-task/`
created (6 files each incl. tests) and wired into `environments/production` alongside
the existing network and database modules; `modules/database` gained one small
additional output (`db_address`, hostname without port) to enable this wiring. Verified
locally via the official Docker images `hashicorp/terraform:1.15.7`,
`terraform-linters/tflint`, and `aquasec/trivy` (none installed on this machine):
`fmt -check -recursive` clean; `terraform init -backend=false && terraform validate`
clean for all five Terraform roots (`bootstrap/`, `environments/production`,
`modules/network/`, `modules/database/`, `modules/ecr/`, `modules/ecs-task/`);
`terraform test` 26/26 passing for the two new modules (16/16 and 20/20 for
network/database re-confirmed unaffected); TFLint 0 issues; `trivy config` 0 unresolved
findings across the tree (2 new exceptions, each individually justified).
`.github/workflows/infra-plan.yml` gained `terraform test — modules/ecr` and
`terraform test — modules/ecs-task` steps (mocked provider, no credentials).
`./mvnw -q -DskipTests compile` passed; zero change in `src`/`pom.xml`. No `terraform
apply`, `terraform import`, AWS CLI command, `docker push`, ECR login, or ECS deployment
executed at any point; no AWS resource of any kind exists; no AWS credential was added.
TECH-132 updated to `In progress — VPC, RDS and ECS task foundations complete`.

**Documented gaps, prior to any real deployment** (none resolved by this story, all
explicitly pending):
- Validate PostgreSQL 18 as RDS's real `engine_version` in `us-east-1`
  (`aws rds describe-db-engine-versions`).
- Validate real `db.t3.micro` availability for that engine/region combination.
- Create the real remote backend (bootstrap's S3 bucket) — the bootstrap is still not
  applied.
- Create the OIDC roles (`terraform-plan`, `terraform-apply`, `application-deploy`) —
  none exist yet.
- Measure the task definition's real CPU/memory against the largest-volume Parcial
  ingestion before fixing `cpu`/`memory` as confirmed values.
- Create an application-specific least-privilege database user — the task definition
  uses RDS's master secret only as temporary wiring.
- Create the ECS Service and the internal ALB — the rest of TECH-132's scope.
- Configure Cognito (TECH-130).
- Configure API Gateway (TECH-131).

---

### TECH-141

**Title:** Define internal ALB and ECS service foundation
**Type:** Infrastructure
**Priority:** High
**Phase:** —
**Status:** **Done**
**Complexity:** M
**Branch:** `infra/internal-alb-ecs-service`

**Origin:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) (Accepted) — the
remainder of [TECH-132](#tech-132)'s compute/network scope (internal ALB, ECS Service).
Scoped narrowly: no API Gateway, VPC Link, Cognito, WAF, Route 53, ACM, custom domain,
production autoscaling, real deployment, image publication, task execution, or
definitive DB user.

**Topology:**

```
Future API Gateway → Future VPC Link → Internal ALB (this module)
  → ECS Service in private-app subnets (this module)
    → RDS in private-DB subnets (TECH-139)
```

`internal = true` on the ALB — never internet-facing. Located in the same private-app
subnets as the ECS tasks (a dedicated layer was evaluated and dropped: no other workload
exists there today, so a separate layer would add subnet/route-table complexity with no
corresponding isolation benefit).

**Security groups — three relationships, each scoped:**
- **ALB SG:** no ingress rule by default — no VPC Link exists yet;
  `alb_allowed_ingress_security_group_ids` (preferred) stays empty until TECH-131;
  `alb_allowed_ingress_cidr_blocks` is a documented fallback with `0.0.0.0/0` explicitly
  rejected by variable validation. Egress scoped solely to the ECS SG on the
  application's port.
- **ECS Service SG:** ingress only from the ALB's SG, port 8080. Egress scoped:
  PostgreSQL (5432) to RDS's SG; HTTPS (443) to `0.0.0.0/0` — necessary because DANE's
  SOAP endpoint is a public internet URL (documented Trivy exception, `AVD-AWS-0104`,
  egress-only, never ingress); DNS (53 TCP/UDP) scoped to the VPC's CIDR, not
  `0.0.0.0/0`.
- **RDS SG:** this module adds the real `ECS → RDS` rule (`modules/database` creates the
  SG with no ingress rule of its own, by design) — port 5432, source exclusively the
  ECS SG, never a CIDR.

**ALB:** `enable_deletion_protection=true` by default, `drop_invalid_header_fields=true`,
`desync_mitigation_mode="defensive"`, `enable_http2=true`. Access logs disabled by
default (`enable_alb_access_logs=false`) — enabling them requires a real S3 bucket with
correct policy, encryption, and retention, which this story does not create; API
Gateway's access logs will be mandatory in TECH-131 regardless.

**Listener:** HTTP only — no domain or ACM certificate (ADR-010), HTTPS is not simulated
with a nonexistent certificate. Acceptable specifically because the ALB is internal,
reachable only within the VPC; API Gateway (TECH-131) will be the only public entry
point. Documented Trivy exception (`AVD-AWS-0054`, CRITICAL): the ALB's own SG has no
internet ingress and `internal=true` means AWS never assigns it a public DNS/IP
resolvable outside the VPC — HTTPS would encrypt traffic that never leaves a private
network boundary.

**Target group:** `target_type=ip` (required for Fargate tasks with `awsvpc`),
`protocol=HTTP`, `port=8080`. Health check: `path=/actuator/health`, `matcher=200` —
confirmed safe without authentication, not invented: `SecurityConfig` explicitly allows
it (`permitAll()`) and `application.yaml` sets
`management.endpoint.health.show-details: when-authorized`, meaning an unauthenticated
caller (the ALB) only receives the `UP`/`DOWN` status, never component details.

**ECS Service:** `launch_type=FARGATE`, `desired_count=1` (see the scheduler risk
below), `enable_execute_command=false` by default (ECS Exec requires deliberate
IAM/logging/audit before enabling). `deployment_minimum_healthy_percent=100` /
`deployment_maximum_percent=200` — with `desired_count=1`, this allows a new task to
start before the old one stops, confirmed by test. `deployment_circuit_breaker` enabled
with `rollback=true`. `health_check_grace_period_seconds=120` — a conservative,
unmeasured proposal, covering RDS connection, Flyway, Spring initialization, and
security-context loading, pending validation with a real startup.

**Flyway and rolling deployments — documented risk, not resolved here:** with
`minimum_healthy_percent=100`/`maximum_percent=200`, a rolling deployment can briefly
run two tasks simultaneously, and both would run Flyway on startup. Flyway's own
mechanism (a database-level lock on the schema history table for PostgreSQL) is the
documented mitigation for exactly this scenario — not customized by this repository, and
**not empirically verified** against a real concurrent rolling deployment by this story
(TECH-141 does not connect to RDS or run any task). Explicit criteria before a real
deployment: validate concurrent-migration behavior, measure timing, validate rollback,
and consider a separate migration job only if real evidence appears that Flyway's lock
is insufficient.

**Scheduler and multiple replicas — documented critical risk:** the ingestion scheduler
lives inside the application process, with no leader election or distributed lock.
`desired_count` must stay at 1 until leader election, an external scheduler, a
distributed lock, or a scheduler/API split exists — otherwise, multiple replicas would
trigger every scheduled ingestion job multiple times. This service is **explicitly not
ready** for multiple replicas or autoscaling — neither is implemented in this story.

**RDS credentials:** unchanged from TECH-140 — the wiring to RDS's master secret remains
a Terraform placeholder, not the final design. Explicit, unresolved gap: create a
least-privilege application user, create a dedicated secret, migrate the task definition
to that secret — requires real database connectivity, not attempted here.

**Nonexistent image:** no real deployment is possible yet — the referenced task
definition has no real, immutable-tagged image in ECR (TECH-140 created the repository,
not an image).

**Autoscaling:** not implemented. Documented criteria for when the scheduler risk is
resolved and real usage data exists: CPU, memory, ALB request count, and
ingestion-queue duration/depth — specific to this workload, since a policy based on
request count alone doesn't capture "an ingestion is taking a while." Never scale to
zero in production.

**Trivy exceptions** (re-evaluated for this module, none copied mechanically):

| Finding | Resource | Risk | Justification | Exception |
|---|---|---|---|---|
| AVD-AWS-0054 (CRITICAL — Listener without HTTPS) | `aws_lb_listener.http` | Low in practice despite the reported severity — the ALB is internal, with no internet ingress in its own SG, and no public DNS/IP resolvable outside the VPC | Simulating HTTPS without a real ACM certificate is not a valid alternative; the encryption would protect traffic that never leaves the private network boundary | `# trivy:ignore:AVD-AWS-0054`, revisit if internal TLS is decided as a real requirement, along with a certificate strategy |
| AVD-AWS-0104 (CRITICAL — Unrestricted `0.0.0.0/0` egress) | `aws_security_group_rule.ecs_service_egress_https` | Low — single-port (443) rule, egress-only, with no corresponding internet ingress in the same SG | The destination (DANE's SOAP endpoint) is a public internet URL, not an AWS resource with a fixed range — the same fact that already justified TECH-138's NAT Gateway | `# trivy:ignore:AVD-AWS-0104`, no more scoped destination is technically expressible for a public third-party endpoint |

No exception touches a real public ALB, an open-ingress SG, ECS with a public IP, logs
without retention, or excessive IAM — both are about network exposure already mitigated
by other controls (the ALB's SG has no ingress; the HTTP listener is never reachable
from outside the VPC).

**Module:** `infra/terraform/modules/ecs-service/` (`main.tf`, `variables.tf`,
`outputs.tf`, `versions.tf`, `README.md`, `tests/`) — ALB and Service in the same module
deliberately ("load-balanced internal service" is a single responsibility, used only by
this service; splitting out the ALB would add file-boundary complexity with no reuse to
justify it). No third-party public modules. `environments/production/main.tf` consumes
the module, chaining outputs from `module.network`, `module.ecs_task`, and
`module.database` — never rebuilding the task definition inside this module.

**Outputs:** `alb_arn`, `alb_dns_name` (`sensitive=true`, the same defensive posture as
`db_endpoint`), `alb_zone_id`, `alb_security_group_id`, `target_group_arn`,
`listener_arn`, `ecs_service_name`, `ecs_service_id`, `ecs_service_security_group_id`,
`ecs_desired_count`. No secret exposed.

**Tests:** `infra/terraform/modules/ecs-service/tests/ecs-service.tftest.hcl` — 17 cases,
all green, `terraform test` with a fully mocked AWS provider, zero real AWS account
contacted. Coverage: internal ALB in private-app subnets, never internet-facing; `ip`
target type; internal HTTP listener; correct health check
(`path`/`matcher`/`protocol`); Fargate ECS Service with `desired_count=1`; circuit
breaker with rollback; 120s grace period; ECS in private subnets with no public IP; ECS
ingress only from the ALB's SG (port 8080); RDS ingress only from the ECS SG (port
5432); ALB with no ingress by default, and exactly one rule created per configured SG;
rejection of `0.0.0.0/0` in the CIDR fallback by variable validation; port 8080 on the
target group/service; the task definition is reused by ARN, not rebuilt; ECS Exec
disabled by default; common tags applied. Absence of autoscaling, API Gateway, Cognito,
and VPC Link confirmed by inspecting the source code (no
`aws_appautoscaling_*`/`aws_api_gateway_*`/`aws_cognito_*` resource declared in the
module), not as a runtime assertion.

**Acceptance Criteria:**
- [x] ALB and ECS Service security groups created, with the ECS→RDS rule added onto
      RDS's existing SG.
- [x] Internal ALB (`internal=true`), in private-app subnets, never in public subnets.
- [x] Internal HTTP listener (no HTTPS simulated without a real certificate), documented
      as acceptable given the ALB is never reachable outside the VPC.
- [x] Target group `target_type=ip`, health check on Actuator's real, already-safe
      endpoint.
- [x] Fargate ECS Service, `desired_count=1`, circuit breaker with rollback,
      parameterized grace period.
- [x] No additional ECS Service, no additional ALB, no API Gateway, no Cognito, no VPC
      Link, no production autoscaling — confirmed by inspection and by tests.
- [x] Flyway rolling-deployment risk explicitly documented, not resolved.
- [x] Scheduler-with-multiple-replicas risk explicitly documented as critical.
- [x] RDS credential gap (least-privilege user) explicitly reiterated, unresolved.
- [x] `terraform test` passes (17/17) against a mocked provider.
- [x] `terraform fmt -check -recursive`, `terraform validate` (all six Terraform
      roots), TFLint, and `trivy config` are all clean (2 CRITICAL exceptions, both
      individually justified, none over a real public ALB, an unjustified open SG, a
      public IP on ECS, or excessive IAM).
- [x] `./mvnw -q -DskipTests compile` passes; zero change in `src`/`pom.xml`.
- [x] No `terraform apply`, `terraform import`, AWS CLI, `docker push`, ECR login, or
      ECS task execution.
- [x] TECH-132 updated to `In progress — VPC, RDS, ECS task and internal service
      foundations complete` (not `Done`).

**Completed:** `infra/terraform/modules/ecs-service/` created (6 files incl. tests) and
wired into `environments/production` alongside the existing modules. Verified locally
via the official Docker images `hashicorp/terraform:1.15.7`,
`terraform-linters/tflint`, and `aquasec/trivy` (none installed on this machine):
`fmt -check -recursive` clean; `terraform init -backend=false && terraform validate`
clean for all six Terraform roots (`bootstrap/`, `environments/production`,
`modules/network/`, `modules/database/`, `modules/ecr/`, `modules/ecs-task/`,
`modules/ecs-service/`); `terraform test` 17/17 passing for the new module (16/16,
20/20, 9/9, 17/17 for network/database/ecr/ecs-task re-confirmed unaffected — 79 tests
total across the tree); TFLint 0 issues; `trivy config` 0 unresolved findings across the
tree (2 new CRITICAL exceptions, each individually justified).
`.github/workflows/infra-plan.yml` gained a `terraform test — modules/ecs-service` step
(mocked provider, no credentials). `./mvnw -q -DskipTests compile` passed; zero change
in `src`/`pom.xml`. No `terraform apply`, `terraform import`, AWS CLI command,
`docker push`, ECR login, or ECS task execution at any point; no AWS resource of any
kind exists; no AWS credential was added. TECH-132 updated to
`In progress — VPC, RDS, ECS task and internal service foundations complete`.

**Documented gaps, prior to any real deployment** (none resolved by this story):
- Image not published to ECR (TECH-140 created the repository, not an image).
- Real remote backend not created (bootstrap is still not applied).
- OIDC roles (`terraform-plan`, `terraform-apply`, `application-deploy`) not created.
- PostgreSQL 18 not validated against real RDS `us-east-1`.
- `db.t3.micro` class not validated.
- Task definition CPU/memory (256/512) not measured against real load.
- Least-privilege database user pending — wiring to the master secret remains
  temporary.
- Scheduler not fit for multiple replicas — `desired_count` must stay at 1.
- API Gateway and VPC Link (TECH-131) pending.
- Cognito (TECH-130) pending.
- No `terraform apply` executed at any point in this sequence of stories.

---

### TECH-113

**Title:** Fix `artiId`/`muniId` filters of `GET /api/sipsa/parcial`
**Type:** Bug
**Priority:** Medium
**Status:** **Done**
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
**Status:** **Done**
**Origin:** ADR-009 rule 6 follow-up, formalized during the TECH-012 preparation.

**Acceptance Criteria:**
- [x] Inventory (per the runbook's annex queries) confirms every real environment has
      correct Flyway history and no baselined non-empty schemas.
- [x] `baseline-on-migrate: false` applied in a dedicated PR (never mixed with TECH-011).

**Completed:** 2026-08-03, branch `chore/disable-flyway-baseline-on-migrate` (commit `9829049`).
Closes ADR-009 rule 6; see ADR-009 §Resolution (2026-08-03, TECH-116).

---

### TECH-117

**Title:** Handle concurrent `SipsaParcial` duplicate insertion safely
**Type:** Correctiva
**Priority:** Medium
**Status:** **Done**
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
(the existing `sipsa_parcial_key_hash_key` constraint is the conflict target). 16 parameters per single-row statement in one JDBC batch — every batch size
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
**Status:** **Done**
**Branch:** `fix/align-sipsa-parcial-decimal-precision`
**Origin:** TECH-011 schema review. Hibernate `validate` does not compare precision, so
nothing fails; real data observed (676,210 rows) ranges 230.00–22,000.00 with ≤2 decimals
— both definitions are far above the real range, no truncation risk. Cosmetic drift only:
pick one source of truth (recommend annotating the entity to match the DDL, `19,2`) for
all three price columns. XSD declares plain `xs:decimal` (no bound).

**Diagnosis (2026-07-19, full pipeline):** XSD `xs:decimal` unbounded + `minOccurs=0`
→ parser `XmlParsingUtil.parseDecimal` = `new BigDecimal(String)` (exact, no `double`
anywhere) → `SipsaParcialRecord`/`SipsaParcialResponse` are `BigDecimal` → DDL
`NUMERIC(19,2)` (V1, versioned, carrying data). Source-of-truth ranking: XSD sets no
bound → the versioned DDL is the storage contract; the JPA annotation was the odd one
out. Real data (677,061 rows): 0.00–22,000.00, stored scale always 2 (the column typmod
coerces), 10,427 zeros, no negatives, no nulls. Note the observed range does NOT prove
future values stay small — that is exactly why the entity was raised to the DDL's 19,2
instead of shrinking the column to 15,2.

**Resolution:** entity annotation aligned to `precision=19, scale=2` (matching
`MayoristasMensual`/`Abastecimientos`); **no Flyway migration**.
No `@Digits` added: values come exclusively from DANE ingestion (no write API), the
parser is the validation point, and a cap could reject contract-valid `xs:decimal`
input. Scale > 2 at the DB boundary is *defined* behavior now: `NUMERIC(19,2)` rounds
half-away-from-zero on insert, pinned by `ParcialDecimalPrecisionTest`
(`123.456 → 123.46`, `-123.455 → -123.46`) with the parser preserving the raw value
exactly until that point (`XmlParsingUtilDecimalTest`). JSON contract unchanged:
Jackson serializes the stored `BigDecimal` exactly (`22000.00`, unquoted) — scale is a
data property, not monetary formatting.

**Out-of-scope findings (recorded, not fixed here):**
- Same `15,2`-vs-`NUMERIC(19,2)` drift in `SipsaCiudad` (`precioPromedio`, `enviado`)
  and `SipsaMayoristasSemanal` (`minimoKg`, `maximoKg`, `promedioKg`, `enviado`) —
  follow-up story below (TECH-134 proposal).
- No non-negativity CHECK on any price column; DANE has never sent negatives (677K
  rows), the parser passes signs through. Constraint decision belongs with TECH-122's
  contract phase if ever needed.

**Completed:** 2026-07-19. Tests: parser exactness (6 unit cases) + real-PostgreSQL
boundary matrix (observed min/max, zero, null, `9999999999999.99` = DECIMAL(15,2) edge,
`99999999999999999.99` = fits only 19,2, rounding pins, JSON exactness), with
`ddl-auto=validate` booting against the untouched schema.

---

### TECH-142

**Title:** Wire Cognito configuration into the ECS task
**Type:** Infrastructure
**Priority:** High
**Phase:** —
**Status:** **Done** — Terraform wiring, IAM, Terraform tests, and Java JWT-contract tests
complete. No AWS resources have been applied yet.
**Complexity:** S
**Branch:** `infra/wire-cognito-ecs-configuration`

**Origin:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) — connects the
resources declared by [TECH-130](#tech-130) (Cognito) with the configuration Spring
Security already consumes (TECH-001/ADR-002), closing the explicit gap TECH-130 left
documented ("this module does not wire that parameter into `modules/ecs-task`"). Scope
explicitly bounded: no API Gateway, no VPC Link (TECH-131 still not started), no change
to the least-privilege database user (TECH-140/141 gap, not touched here).

**Spring configuration inventory** (`grep -RIn --exclude-dir=.git -e 'SIPSA_JWT_' -e
'issuer-uri' -e 'allowed-client' -e 'allowedClient' -e 'token_use' -e 'client_id' -e
'@ConfigurationProperties' -e 'oauth2.resourceserver' src/main src/test
docker-compose.yml docs`, not invented):

| Spring property | Environment variable | Terraform source | Sensitive | Mandatory |
|---|---|---|---:|---:|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `SIPSA_JWT_ISSUER_URI` | `module.cognito.issuer_url` | No (JWKS is public by design) | Yes — no default outside the `dev` profile (which points at the local mock OIDC); the `docker` profile, the one ECS uses, inherits the base `application.yaml` with no default, so it fails fast if missing |
| `sipsa.security.jwt.allowed-client-ids` | `SIPSA_JWT_ALLOWED_CLIENT_IDS` | `module.cognito.allowed_client_ids_parameter_arn` (SSM, via `secrets`) | No (identifiers, not secrets — `AllowedClientIdsValidator`'s Javadoc) | No — optional CSV; empty means "accept any client from the trusted issuer" |

Both properties already existed and were already consumed exactly this way before this
story (`SipsaJwtProperties`, via `@Value`, not a class-level `@ConfigurationProperties` —
the only use of that annotation in the security tree is indirect, via other,
JWT-unrelated configuration classes). No new name was invented; the
`SIPSA_JWT_ALLOWED_CLIENT_IDS` CSV format (confirmed by reading
`SipsaJwtProperties.parseCsv` — comma-splits, trims whitespace, rejects blank entries)
matches exactly the comma-joined string `modules/cognito` was already publishing to SSM
since TECH-130 — **no format change was necessary**.

**Spring profile on ECS — audited, unchanged:** `modules/ecs-task`'s `spring_profile`
already defaulted to `"docker"` since TECH-140, with an explicit justification in the
variable itself. This story audits that decision directly against the code, not
assuming it: `application-docker.yaml` (`docker` profile) only overrides `DB_HOST`
(default `db` instead of `localhost`) and inherits `application.yaml` (the
"production-safe" base) with no security/logging/scheduler change. The mock OIDC
(`issuer-uri` defaulting to `http://localhost:9000/default`), the default database
credentials, the Actuator `loggers` endpoint, `show-details: always`, and verbose
logging live **exclusively** in `application-dev.yaml` (`dev` profile) — never in
`docker`. Verified conclusion: `docker` is already a safe profile for AWS; no new
`production`/`aws` profile was created, because no real difference exists to justify one
(explicit instruction: "Don't create a profile for aesthetics").

**Design — two generic variables in `modules/ecs-task`, without coupling the module to
Cognito:** `environment_variables` (`map(string)`, additional plain environment
variables) and `secret_parameters` (`map(string)`, additional `secrets` entries resolved
by the ECS agent from Secrets Manager/SSM at startup). Both are concatenated
(`concat(...)`) onto the module's already-existing fixed set — `modules/ecs-task` still
never knows about `modules/cognito`; the root (`environments/production/main.tf`) is the
only place that wires `module.cognito.issuer_url`/
`module.cognito.allowed_client_ids_parameter_arn` into `module.ecs_task`. The allowlist
wiring is guarded by a conditional (`!= null ? ... : {}` for `secret_parameters`,
`compact([...])` for `execution_ssm_parameter_arns`), since
`allowed_client_ids_parameter_arn` is `null` when `var.cognito_publish_client_ids_to_ssm`
is `false` (default `true`).

**IAM — no widening of the execution role's scope beyond what already existed:**
`modules/ecs-task` already exposed `execution_extra_secret_arns`/
`execution_ssm_parameter_arns` since TECH-140 (with a description that literally
anticipated "e.g. a future Cognito client secret once TECH-130 exists") — **no IAM
change to the module was necessary**, only passing
`execution_ssm_parameter_arns = compact([module.cognito.
allowed_client_ids_parameter_arn])` from the root. The resulting policy
(`aws_iam_role_policy.execution_secrets`, already existing) grants `ssm:GetParameters`
scoped exactly to Cognito's parameter ARN, never `Resource = "*"`, never `ssm:*`. No KMS
permission added — the parameter is `type = "String"`, not `SecureString`, so no CMK is
involved. The task role stays empty (`task_role_policy_arns` untouched) — `secrets`
resolution is done by the ECS agent via the execution role, not by the application at
runtime.

**Issuer derived, never rebuilt:** `environment_variables = { SIPSA_JWT_ISSUER_URI =
module.cognito.issuer_url }` in the root — no region, user pool ID, or URL is
hardcoded or manually rebuilt in a second place.

**Client ID allowlist — both clients included, semantics verified, not assumed:** the
claim `AllowedClientIdsValidator` checks is `client_id` — present in access tokens from
**both** Cognito grants (`client_credentials` and `authorization_code`), confirmed
against the token structure AWS Cognito documents (not just the Terraform provider's
documentation) and empirically verified by `CognitoJwtDecoderContractTest` (below) by
signing local tokens with `client_id` for both client types and confirming the decoder
accepts them when they're in the allowlist. Cognito's ID tokens don't carry `client_id`
in the same relevant way — but this is irrelevant regardless, because
`TokenUseValidator` already rejects any ID token (`token_use=id`) before the allowlist is
evaluated. The resource server accepts only access tokens — confirmed, not a change made
by this story. The human client **was** included in the allowlist (both IDs, already
published by TECH-130 from the start) because neither the application's authorization
policy (`SecurityConfig`'s scope-based matchers, not client-type-based) nor TECH-130's
scope table distinguishes M2M from human — both are legitimate consumers of the four
scopes.

**Java tests — 6 new, closing a real coverage gap, not redundant:**
`CognitoJwtDecoderContractTest` (new class) exercises
`SecurityConfig.jwtDecoder(SipsaJwtProperties)` end-to-end against locally signed tokens
shaped realistically like Cognito's — OIDC discovery, signature verification via JWKS,
`token_use`, and the `client_id` allowlist, all together — something neither
`SipsaJwtValidatorsTest` (builds `Jwt` objects by hand, no real signature/issuer) nor
`InternalEndpointSecurityTest` (mocks `JwtDecoder` entirely, injects authorities
directly) cover today. A local JDK `com.sun.net.httpserver.HttpServer` (loopback, no new
dependency — this repository has no functional WireMock HTTP server extension on the
classpath yet, the same pattern already documented in
`SoapStreamingClientMetricsTest`) serves the OIDC discovery document and the JWKS. The 6
requested scenarios: (1) valid M2M access token, decodes, scopes convert correctly to
`SCOPE_*` authorities; (2) valid human access token, decodes when the human client is in
the allowlist; (3) ID token (`token_use=id`) rejected even with a valid
signature/issuer/`client_id`; (4) `client_id` outside the allowlist rejected; (5) missing
scope — the decoder does not reject it (not its responsibility), but zero `SCOPE_*`
authorities are confirmed to be derived, which effectively denies access at
`SecurityConfig`'s matchers (the 403 path is already covered by
`InternalEndpointSecurityTest`); (6) wrong issuer rejected even with a signature the
configured JWKS accepts. Not assumed, confirmed by running the test: this Spring
Security version's default `JwtAuthenticationConverter` also adds a `FACTOR_BEARER`
authority (authentication-factor tracking) to every bearer token — irrelevant for this
application (no `SecurityConfig` matcher checks it, confirmed by grep), documented and
explicitly filtered in the test helper. No change to
`SecurityConfig`/`TokenUseValidator`/`AllowedClientIdsValidator`/`SipsaJwtProperties`
was necessary — no real compatibility defect was found.

**Terraform tests — 8 new (108 total across the tree):**
- `modules/ecs-task` gains 4 (17→21): confirms `environment_variables`/
  `secret_parameters` are empty by default without breaking the fixed set; a
  caller-provided environment variable is correctly added in plaintext; a
  `secret_parameters` entry is added to the `secrets` block (never to `environment`);
  and `execution_ssm_parameter_arns` grants read access exactly to the given ARN, never
  a wildcard.
- `modules/cognito` gains 1 new assertion within an existing run (stays at 23 runs): the
  new `allowed_client_ids_parameter_arn` output exposes the real ARN, and is `null` when
  `publish_client_ids_to_ssm` is `false`.
- `environments/production/tests/production.tftest.hcl` (new, this repository root's
  first test suite): 2 tests that specifically test the *wiring* between modules (not
  each module's internal correctness, already covered by its own suite) —
  `module.network`/`module.database`/`module.ecr`/`module.ecs_service`/
  `module.cognito` are stubbed with `override_module` (fixed, distinctive values),
  leaving only `module.ecs_task` real; it's confirmed that `SIPSA_JWT_ISSUER_URI` reaches
  the task definition with exactly `module.cognito.issuer_url`'s value, and that
  `SIPSA_JWT_ALLOWED_CLIENT_IDS` reaches it via the `secrets` block with
  `module.cognito.allowed_client_ids_parameter_arn`'s ARN, never as a plaintext
  environment value. `modules/ecs-task` gained a new output, `container_definitions`
  (the already-computed JSON, not sensitive — no secret lives there in plaintext, only
  `valueFrom` references), necessary because a root-level test cannot address a child
  module's internal resources, only its declared outputs.

**Acceptance Criteria:**
- [x] Complete Spring configuration inventory via grep, exact table, no invented names.
- [x] Spring profile audited (`docker`) — confirmed already safe for AWS, no new profile
      created without real evidence.
- [x] `issuer_url` wired from `module.cognito` into `module.ecs_task`, no manual
      reconstruction, no hardcoded region/pool.
- [x] Client ID allowlist wired via SSM (`secrets`, never plaintext), compatible CSV
      format with no changes, both clients included with explicit justification.
- [x] IAM: execution role reads exactly the required SSM parameter, never a wildcard; no
      KMS permission added (`String` parameter, not `SecureString`); task role
      unchanged.
- [x] `modules/ecs-task` remains reusable — no direct dependency on `modules/cognito`.
- [x] Allowlist semantics verified, not assumed: `client_id` present in both access-token
      types; ID tokens already rejected by `token_use`; resource server accepts only
      access tokens.
- [x] 6 locally signed JWT fixtures cover the 6 requested scenarios; no change to Spring
      Security was necessary.
- [x] 108 Terraform tests across the tree, all green (16+20+9+21+17+23+2).
- [x] 338 Java tests total, 0 failures, 0 errors, 0 skipped, `BUILD SUCCESS`.
- [x] `terraform fmt -check -recursive`, `terraform validate` (all roots), TFLint (0
      issues), `trivy config` (0 unresolved findings) clean.
- [x] No API Gateway, no VPC Link, in any module of this stack.
- [x] No `terraform apply`, `terraform import`, AWS CLI, real token request, or ECS
      deployment at any point.
- [x] TECH-130 remains explicitly marked: "Terraform foundation and ECS configuration
      wiring complete." TECH-132 remains `In progress`; TECH-131 remains `Pending`.

**Completed:** `infra/terraform/modules/ecs-task/{main.tf,variables.tf,outputs.tf}` (two
generic variables + one new output), `infra/terraform/modules/cognito/outputs.tf` (one
new ARN output), `infra/terraform/environments/production/main.tf` (wiring),
`infra/terraform/environments/production/tests/production.tftest.hcl` (new, 2 tests),
`.github/workflows/infra-plan.yml` (new `terraform test` step for
`environments/production`), `src/test/java/.../security/CognitoJwtDecoderContractTest.java`
(new, 6 tests), documentation for all four modules updated. Verified locally via
`hashicorp/terraform:1.15.7`, `terraform-linters/tflint:v0.64.0`, `aquasec/trivy`
(none installed on this machine): `fmt -check -recursive` clean; `terraform validate`
clean across all eight Terraform roots; 108/108 `terraform test` across the tree; TFLint
0 issues; `trivy config` 0 unresolved findings; `./mvnw clean verify` — 338 tests, 0
failures, 0 errors, 0 skipped, `BUILD SUCCESS`; `git diff --check` clean. No `terraform
apply`, `terraform import`, AWS CLI command, real token request, real Hosted UI, or ECS
deployment at any point; no AWS resource of any kind exists.

**Documented gaps, prior to any real deployment** (none resolved by this story):
- RDS master secret remains temporary wiring. A least-privilege application database
  user is required before deployment (gap inherited from TECH-140/141, explicitly not
  mixed with Cognito in this story).
- API Gateway and VPC Link (TECH-131) pending — not started.
- Cognito Hosted UI domain not created (TECH-130 gap, unchanged).
- No automatic rotation of the M2M secret (TECH-130 gap, unchanged).
- No `terraform apply` executed at any point in this sequence of stories.

---

### TECH-143

**Title:** Validate production deployment prerequisites and Terraform plan
**Type:** Infrastructure / Operations
**Priority:** High
**Phase:** —
**Status:** **Blocked / In progress** — not merged to `main`. Kept intact on its own
branch as evidence, explicitly separated from the locally verifiable work, which was
extracted to [TECH-144](#tech-144).
**Complexity:** M
**Branch:** `infra/production-deployment-preflight` (NOT merged — kept as evidence of
the blocked preflight)

**Origin:** closing out the real blockers preventing TECH-132's first `terraform apply`.

**Blocking reason, unchanged:** no SIPSA-specific AWS credential exists in this
environment. Two unrelated profiles were found (`incampo`, `trustid`), both with
permanent access keys, neither used — no AWS command was run with either one, at any
point, in either story.

**Remains blocked, unresolved:**
- PostgreSQL 18 / `db.t3.micro` availability in RDS `us-east-1`.
- Real S3 backend bootstrap plan.
- Inspection of the real subject issued by GitHub OIDC.
- Real `terraform plan` against the production account.
- Cost estimate with real figures.

**Repository owner's decision (2026-07-22):** TECH-143's original handling was confirmed
correct (no unrelated credential used, the story was not improperly marked Done), but
the branch mixed finished local work with blocked AWS results. Both scopes were
separated: **TECH-143 stays blocked, on its own branch, unmerged**; the locally
verifiable work (Cognito human-client gate, DB credential strategy, Flyway decision, ECS
capacity/grace-period evidence — the latter re-verified with an additional measurement at
1024 MiB) was extracted to **[TECH-144](#tech-144)**, which does merge.

**Not marked Done.** Resume this story (or a new one) once real, correctly scoped AWS
credentials exist for the SIPSA account. See
`docs/operations/aws-production-preflight.md` (present on both branches, with the
blocked sections — §1-4, §8 of that branch's version — unchanged) for the full detail.

---

### TECH-144

**Title:** Harden deployment configuration from local preflight evidence
**Type:** Infrastructure / Operations
**Priority:** High
**Phase:** —
**Status:** **Done** — local, verifiable hardening only, no AWS. Does not constitute an
AWS deployment preflight or a real Terraform plan (that remains in
[TECH-143](#tech-143), blocked).
**Complexity:** S
**Branch:** `infra/preflight-local-hardening`

**Origin:** extraction of TECH-143's finished, locally verifiable work, explicitly
separated from the blocked AWS results, by the repository owner's decision.

**Cognito human-client gate:** `enable_human_client` (new variable, `modules/cognito`,
default `false`) — the human client (`aws_cognito_user_pool_client.human`, now with
`count`) is not created without real callback/logout URLs. The M2M client, the resource
server, the scopes, and the user pool remain fully intact and unconditional. A
validation rejects enabling the human client with empty URLs. 25/25 tests in the module
(previously 23).

**ECS memory — exact result at 512 MiB and re-verified at 1024 MiB:**

| Config | Result |
|---|---|
| 512 MiB, 0.25 vCPU (3 samples) | 459.4 MiB used out of 512 MiB — **89.73% memory utilization, 10.27% free**, at post-startup idle, before any real ingestion load. No OOM. |
| 1024 MiB, 0.25 vCPU (3 new samples, re-verification required before merge) | Observed memory peak: 560.4 MiB (54.72%), 483.5 MiB (47.22%), 549.0 MiB (53.61%) — 44.89%-55.25% max utilization in the final readings. `ExitCode=0` on all three, `OOMKilled=false` on all three. |

ECS task memory: **512 → 1024 MiB** — a valid Fargate combination for 256 CPU (no CPU
change), backed by real evidence at both values, not just the pressure observed at
512 MiB.

**Startup times — min/median/max (6 real samples, not 3):**

| # | Config | Seconds to `/actuator/health` 200 |
|---:|---|---:|
| 1 | 512 MiB | 207 |
| 2 | 512 MiB | 214 |
| 3 | 512 MiB | 221 |
| 4 | 1024 MiB | 188 |
| 5 | 1024 MiB | 187 |
| 6 | 1024 MiB | **385** |

**Min: 187s. Median: ~210.5s. Max: 385s.**

**Grace-period justification:** sample 6 (385s) is kept, not discarded as a convenient
outlier — that run's own Spring Boot log reported ~192s to "Started SipsaApplication",
consistent with the other five samples, so the additional ~193s gap before the external
health probe (`curl` from the host) received `200` is more plausibly local Docker
Desktop network/host contention from intensive, concurrent Docker use during this
measurement session — not confirmed, nor dismissed outright either.
`health_check_grace_period_seconds`: 120 (original, unmeasured) → 300 (TECH-143 draft,
only 3 samples at 512 MiB) → **480** (TECH-144, final) — with ~95s of margin over the
worst of the **six** real samples, not just a favorable subset. Still a local
measurement; requires confirmation against real Fargate before the first real
deployment.

**Hardened measurement script:** `scripts/measure-container-startup.sh` — no
credentials, no AWS account dependency, `set -euo pipefail`, cleans up containers and
the compose stack on exit (even on error), configurable CPU/memory, per-sample timeout,
**fails with a non-zero exit code if any health check never reaches 200** (previously
only logged it), never uses a fixed `sleep` as a success criterion, documents its
prerequisites in its own header.

**DB strategy:** design of two PostgreSQL roles (`sipsa_migration`/`sipsa_runtime`) with
exact grants in
`infra/terraform/modules/database/scripts/create-application-users.sql` — confirmed: no
`SUPERUSER`/`CREATEROLE`/`CREATEDB`; separates migrations from runtime; minimal grants;
explicitly documents why it isn't idempotent on `CREATE ROLE` (and which parts of the
script are safe to re-run); does not connect to AWS; does not run Flyway. **Never
executed.**

**Flyway decision:** a single ECS migration task before service rollout, recorded with
an explicit follow-up (task definition, pipeline step, failure handling, rollout order,
rollback behavior — the latter documenting a real, unresolved asymmetry: an already-
applied Flyway migration does not automatically roll back if the application deployment
fails afterward). **Not implemented.**

**Scheduler:** `desired_count=1` is kept; 4 follow-up options documented, none decided —
an HA blocker, not a blocker for the initial single-task deployment.

**Not included in TECH-144** (remains exclusively in TECH-143, blocked): RDS
availability, RDS class, remote backend, real OIDC, AWS account, a real plan, costs,
real callback URLs, a real Cognito endpoint. No file in this story claims to have
validated any of those points.

**Trivy/TFLint:** re-run, clean, no new findings.

**Acceptance Criteria:**
- [x] Cognito human-client gate implemented, with tests (25/25).
- [x] ECS memory backed by real measurement at both values (512 and 1024 MiB), not just
      the failure at 512 MiB.
- [x] Grace period based on the 6 real samples (min/median/max reported), with
      reasonable margin over the max.
- [x] Hardened measurement script per the 9 requested criteria.
- [x] DB strategy designed, SQL script verified safe, never executed.
- [x] Flyway decision recorded with an explicit 4-point follow-up.
- [x] Scheduler documented, no architectural decision made.
- [x] 131/131 `terraform test` across the tree; TFLint 0; Trivy 0 unresolved.
- [x] `./mvnw clean verify` — 338 tests, 0 failures, `BUILD SUCCESS`.
- [x] No `terraform apply`, AWS CLI command, or credential used at any point.
- [x] No AWS validation claim included.
- [x] TECH-143 remains `Blocked / In progress`, unmerged. TECH-132 remains
      `In progress`.

**Completed:** `infra/terraform/modules/cognito/{main,variables,outputs,README}.tf`
(human-client gate), `modules/ecs-task/variables.tf` + tests (memory),
`modules/ecs-service/variables.tf` + tests (grace period),
`environments/production/{main,variables}.tf` (wiring), `modules/database/README.md`
(reference to the strategy), `modules/database/scripts/create-application-users.sql`
(new), `scripts/measure-container-startup.sh` (new, hardened),
`docs/operations/aws-production-preflight.md` (updated with the corrected evidence and
the explicit TECH-143/TECH-144 scope separation).

---

### TECH-134

**Title:** Align remaining SIPSA decimal annotations with the DDL (`Ciudad`, `Semanal`)
**Type:** Config
**Priority:** Low
**Status:** **Done**
**Branch:** `fix/align-remaining-sipsa-decimal-precision`
**Scope:** `SipsaCiudad.precioPromedio/enviado` and
`SipsaMayoristasSemanal.minimoKg/maximoKg/promedioKg/enviado` declare
`precision=15, scale=2` against `NUMERIC(19,2)` columns — the exact drift TECH-118
closed for Parcial. Same expected resolution (annotation → `19,2`, no migration), plus
a boundary test per dataset. Kept out of TECH-118 deliberately (its scope was Parcial;
these entities had no story-driven test coverage to piggyback on).

**Diagnosis (2026-07-19):** XSD declares every one of the six fields as unbounded
`xs:decimal` with `minOccurs=0` (no `xs:double`/`xs:float` anywhere in the WSDL);
parsers use `XmlParsingUtil.parseDecimal` (`new BigDecimal(String)` — exact); records,
responses and entities are `BigDecimal` end to end; the V1 DDL is `NUMERIC(19,2)` for
all six columns. Same source-of-truth ranking as TECH-118: DDL wins, annotations were
the odd ones out. Real data (fresh full DANE loads, 2026-07-19): Ciudad 373,038 rows,
`precio_promedio` 270.00–15,500.00, `enviado` **always 0.00**; Semanal 233,866 rows,
prices 182.00–280,000.00 (widest observed range in the schema), `enviado` **always
NULL**. No negatives, nothing beyond `DECIMAL(15,2)` — and, as in TECH-118, the
observed range is evidence for safety, never a license to shrink the columns.

**Resolution:** six annotations aligned to `precision=19, scale=2`. **No Flyway
migration.** Rounding semantics unchanged and shared
with TECH-118 (`NUMERIC(19,2)` coerces scale > 2 half-away-from-zero at insert; pinned
per model). No `@Digits`, no CHECK constraints, no API/DTO changes — JSON stays exact
unquoted numbers (verified per model).

**Final state of decimal declarations:**

| Model | Previous state | Final state |
| --- | --- | --- |
| SipsaParcial | 19,2 (TECH-118) | 19,2 |
| SipsaCiudad | 15,2 | **19,2** |
| SipsaMayoristasSemanal | 15,2 | **19,2** |
| SipsaMayoristasMensual | 19,2 | 19,2 |
| SipsaAbastecimientosMensual | 19,2 | 19,2 |

**Findings (documented, unchanged):** Ciudad `enviado` = 0.00 and Semanal `enviado` =
NULL on every real row — both look vestigial in the upstream feed; any pruning decision
is product-level, not TECH-134. Non-negativity CHECK remains deferred (TECH-122
contract phase).

**Completed:** 2026-07-19. Tests: `SipsaDecimalPrecisionAlignmentTest` (Testcontainers,
`ddl-auto=validate` boot, per-model boundary matrix incl. `99999999999999999.99` =
fits only `19,2`, rounding pins, JSON exactness, real-data shapes for `enviado`).

---

### TECH-136

**Title:** Centralize async executor configuration and pin the audit executor (C-05)
**Type:** Config
**Priority:** Low
**Status:** **Done**
**Branch:** `refactor/centralize-async-executor-config`
**Resolves:** [C-05](../architecture/technical-debt.md) and [TECH-030](#tech-030) — the
pre-existing Phase 1 story for this exact finding (`logEvent` needs a named executor).
Both were confirmed independently: C-05 from the original architectural review, and the
`@Async` ambiguity from the 2026-07-19 CI-flake investigation; TECH-136 closes both in
one implementation.
**Origin:** C-05 (technical debt: `AsyncConfig` re-declared `@Value` defaults for
`sipsa.ingestion.async.*`) plus the finding confirmed during the 2026-07-19 CI-flake
investigation: `IngestionAuditService.logEvent` used a bare `@Async` in a context with
TWO `TaskExecutor` beans — `ingestionTaskExecutor` and the `taskScheduler`
(`ThreadPoolTaskScheduler` implements `TaskExecutor` too) — and none named
`taskExecutor`, so Spring logged `More than one TaskExecutor bean found` and ran audit
events on ad-hoc `SimpleAsyncTaskExecutor` threads.

**Executor inventory (before):** 2 executor beans (`ingestionTaskExecutor`:
`ThreadPoolTaskExecutor` 2/10/25/60s, prefix `ingestion-async-`, CallerRunsPolicy,
`allowCoreThreadTimeOut(true)`, framework-default shutdown; `taskScheduler`:
`ThreadPoolTaskScheduler`, pool 5, prefix `scheduled-ingestion-`, waitForTasks=true,
awaitTermination=30s). 2 `@Async` consumers: `AsyncIngestionService`
(already `@Async("ingestionTaskExecutor")`) and `logEvent` (unqualified → effective
executor `SimpleAsyncTaskExecutor`). No `@Primary`, no `AsyncConfigurer`, no bean named
`taskExecutor`, no `TaskDecorator`.

**Resolution:** (1) `AsyncExecutorProperties` binds the PRE-EXISTING official prefix
`sipsa.ingestion.async.*` / `SIPSA_ASYNC_*` env vars (deliberately not renamed to keep
the operational contract) with validation: core ≥ 1, max ≥ 1, cross-field `max >= core`
(`@AssertTrue`), queue ≥ 0 (0 = direct handoff, documented), keep-alive ≥ 0 s;
`AsyncConfig` consumes it and keeps bean name, thread prefix, rejection policy and
geometry byte-identical. (2) Audit executor made explicit —
`@Async("ingestionTaskExecutor")` (Alternative A: smallest scope, no bean renames, no
`@Primary`, no `AsyncConfigurer`, no change for other consumers); with no unqualified
`@Async` left, the ambiguity warning cannot trigger. Still async, still
`REQUIRES_NEW`, still append-only.

**Evidence:** binding tests (12 cases incl. all aborts), real-bean test
(geometry/identity/overrides), deterministic latch-based saturation
(core=1/max=1/queue=1 → third task runs on the caller via CallerRunsPolicy),
executor-resolution test (audit insert thread `ingestion-async-*` captured via Logback
`ListAppender`; captured output free of the warning and of `SimpleAsyncTaskExecutor`),
Docker (defaults 2/10/25/60s; overrides 3/6/40/90s; `core=10/max=2` →
`APPLICATION FAILED TO START` naming the cross-field rule; runtime audit event with 0
warning occurrences), and 50/50 green repetitions of
`ParcialConcurrentIngestionAppTest` (2 `INGESTION_SUCCEEDED` / 0 `INGESTION_FAILED`
filtered by `tech117-a`/`tech117-b`, Awaitility retained).

**Follow-up findings (recorded, deliberately unchanged):**
- Shutdown: `ingestionTaskExecutor` keeps framework defaults
  (`waitForTasksToCompleteOnShutdown=false`, `awaitTerminationSeconds=0`) — queued or
  in-flight async audit events can be dropped on context shutdown. Changing this is a
  separate decision (contrast: the scheduler already waits 30 s).
- Context propagation: no `TaskDecorator`; MDC/tracing context does NOT cross into
  async threads. Audit correlation currently travels in the event payload
  (`requestId`), so logs correlate by parameter, not by MDC — acceptable today,
  revisit only if MDC-based tracing is adopted.

**Completed:** 2026-07-19. No migration, no tuning, no functional change to audit
semantics.

---

### TECH-135

**Title:** Centralize ingestion rejection-threshold configuration (C-04)
**Type:** Config
**Priority:** Low
**Status:** **Done**
**Branch:** `refactor/centralize-ingestion-rejection-thresholds`
**Origin:** C-04 (technical debt, found during the TECH-071/TECH-133 config
inventories): `IngestionJob` and `GenericIngestionJob` each re-declared
`@Value("${sipsa.ingestion.max-reject-rate:0.01}")` /
`@Value("${sipsa.ingestion.max-reject-count:5000}")` — values that agreed with
`application.yaml` but could silently drift on a future edit, the same double-source
antipattern removed for `batch-size` and `monthly-window-start`.

**Inventory (before):** bindings in both job classes (defaults 0.01/5000);
`application.yaml` `${MAX_REJECT_RATE:0.01}` / `${MAX_REJECT_COUNT:5000}`;
`.env.example` listed both env vars without documentation; `docker-compose.yml` did
NOT pass them through; no tests covered binding or threshold behavior.

**Resolution:** both properties bind once in `IngestionProperties` with startup
validation — `maxRejectRate` constrained to `[0..1]` (it is a **fraction** of
`recordsSeen`, 0.01 = 1%; the >1 error message spells out fraction-vs-percentage),
`maxRejectCount >= 0`; invalid or non-numeric values abort startup naming the
property; the resolved pair is logged once. Jobs inject the typed properties
(constructor), duplicated `@Value`s deleted. Compose passthrough added
(`MAX_REJECT_RATE`/`MAX_REJECT_COUNT`, same pattern as `INGESTION_BATCH_SIZE`);
`.env.example` documents range/semantics/precedence. **Effective values and semantics
unchanged**, now test-pinned: evaluated once at end of run over final totals,
OR-combined, strict `>`, rate check skipped when `seen=0` (count gate still applies),
`count=0` = zero tolerance; exceeding a gate throws `SipsaIngestionException` → run
`FAILED`.

**Completed:** 2026-07-19. Tests: 9 new binding cases in `IngestionPropertiesTest`
(defaults, property/env precedence, boundaries 0/1/0, negative and >1 and non-numeric
startup aborts) + `IngestionJobRejectThresholdTest` (7 behavior cases, both jobs
sharing the central values). Docker verified: defaults `0.01/5000`, env override
`0.05/1234`, defaults restored. No migration, no functional change; C-05
(`AsyncConfig`) deliberately untouched.

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

**Completed:** 2026-07-16, branch `fix/remove-redundant-parcial-key-hash-index`, a
dedicated migration (transactional `DROP INDEX`, no `IF EXISTS`; rationale in the
script). Verified: no code/test/script references the index name (grep); tested from an
empty base and as an upgrade with data preserved
(`ParcialKeyHashIndexMigrationTest`); `UNIQUE (key_hash)` still rejects duplicates
post-migration; hash lookups use `sipsa_parcial_key_hash_key` with identical plan cost; live
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

### TECH-124

**Title:** Optimize `SipsaParcial` article-filter queries
**Type:** Performance
**Priority:** Low
**Status:** **Done**
**Branch:** `perf/sipsa-parcial-article-filter-index`

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

**Completed:** 2026-07-18. Tested from a clean base (`FlywayMigrationsTest`) and
as an upgrade with data (`ParcialArticleQueryIndexMigrationTest`, 60K rows; live upgrade
on the real 677K-row local base in 197 ms). No API contract change (TECH-113 untouched).

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

---

### TECH-150

**Title:** Integration-test scaffolding — Failsafe profile, `*IT` convention, shared WireMock SOAP fixture support
**Type:** Infrastructure (test)
**Priority:** High
**Phase:** 6
**Status:** **Done**
**Complexity:** M
**Branch:** `spike/tech-044-comprehensive-testing-strategy` (continued directly from the
ADR-011 documentation work, not a separate branch — the two are one coherent change)
**Dependencies:** None. Unblocks TECH-151..155, TECH-160, TECH-161.
**Decision reference:** [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md)

**Objective:** Add the `maven-failsafe-plugin`-backed `integration-tests` profile
(`*IT.java` naming, bound to `verify`, separate from the Surefire unit run), and a
shared WireMock support base class that serves SOAP XML fixtures from
`src/test/resources/fixtures/soap/<HandlerName>/`. No handler test is written in this
story — it is scaffolding only, proven with one throwaway smoke `*IT` that gets deleted
once TECH-151 lands.

**Real finding, not assumed going in (2026-08-03): WireMock did not actually work on
this classpath.** ADR-011 stated WireMock was "already on the classpath, already
proven" based on its presence in `pom.xml` since the very first commit. That was wrong
— confirmed empirically with a throwaway spike test before writing any real code:
`new WireMockServer(0).start()` threw `FatalStartupException: Jetty 11 is not present
and no suitable HttpServerFactory extension was found`. Both existing tests that needed
an HTTP mock (`SoapStreamingClientMetricsTest`, TECH-032, and
`CognitoJwtDecoderContractTest`, TECH-142) had independently worked around this by using
a plain JDK `HttpServer` instead — `CognitoJwtDecoderContractTest`'s own Javadoc says so
explicitly ("this repository does not have a working WireMock HTTP-server extension on
its classpath yet"). Root cause and fix, in order:
1. The bare `org.wiremock:wiremock` core artifact ships no embedded HTTP server — added
   `org.wiremock:wiremock-jetty12` (Jetty 11's equivalent extension exists too, but this
   project already targets a current Jetty-12-based extension).
2. `org.wiremock:wiremock` itself transitively pulls a set of legacy Jetty
   9/10/11-artifact-named modules (`jetty-servlet`, `jetty-servlets`, `jetty-webapp`,
   `http2-server`/`-common`/`-hpack`) that don't exist in Jetty 12's module layout and
   conflicted with the Jetty 12 modules `wiremock-jetty12` needs
   (`NoClassDefFoundError: org/eclipse/jetty/io/WriteFlusher$Listener`) — excluded via
   `<exclusions>` on the `wiremock` dependency.
3. `wiremock-jetty12:3.13.2`'s own POM declares its `jetty-ee10-*` modules at `12.0.30`
   while Spring Boot 4.1.0 manages the rest of the Jetty 12 tree at `12.1.10` — a version
   split that threw `NoSuchMethodError: Environment.ensure(String)` at `WireMockServer`
   startup. Pinned `jetty-ee10-servlet`/`jetty-ee10-servlets`/`jetty-ee10-webapp`/
   `jetty-ee` to `12.1.10` in `dependencyManagement` to match.

All three fixes are in `pom.xml`, each with an inline comment explaining why. This is a
real, reusable fact for TECH-151..155/160: WireMock now genuinely works on this
classpath; no future story needs to re-derive this.

**Delivered:**
- `pom.xml`: the 3 fixes above, plus the `integration-tests` Maven profile
  (`maven-failsafe-plugin:3.5.4`, bound to `integration-test`/`verify`, active only under
  `-P integration-tests`).
- `com.dalejandrov.sipsa.support.soap.SoapWireMockSupport` — a JUnit 5
  `BeforeEachCallback`/`AfterEachCallback` extension: starts a fresh `WireMockServer` per
  test method on a dynamic port, exposes `endpoint()`, `stubFixture(handlerName,
  fixtureFileName)` (reads `fixtures/soap/<handlerName>/<file>` from the test classpath,
  stubs a `200` SOAP response), and `stubHttpStatus(status)` (for failure-path tests).
- `src/test/resources/fixtures/soap/CiudadIngestionHandler/two-records.xml` — the example
  fixture: a realistically-shaped `promediosSipsaCiudad` SOAP 1.2 response with 2
  `<return>` records, field names/values verified against `CiudadStaxParser`'s real
  handler map (`XmlFieldNames`) before writing it, not guessed.
- `WireMockScaffoldingSmokeIT` — the throwaway smoke test: stubs the fixture, drives it
  through the real `SoapGatewayImpl` → `SoapStreamingClient` → `CiudadStaxParser` chain
  (no Spring context, no database — deliberately the minimum needed to prove the
  wiring), asserts the 2 parsed records match the fixture; a second case stubs an HTTP
  500 and asserts it surfaces as `SipsaExternalException`, not a silent empty result.

**Acceptance Criteria:**
- [x] `./mvnw test` behavior is unchanged (confirmed: full suite, 465 tests via the
      `<testsuite tests="...">` XML reports, 0 failures/errors after `./mvnw clean test`;
      `WireMockScaffoldingSmokeIT` does not appear in any `surefire-reports` file —
      Surefire's default include patterns never match `*IT.java`).
- [x] `./mvnw verify -P integration-tests` runs `*IT` classes via Failsafe (confirmed:
      `target/failsafe-reports/...WireMockScaffoldingSmokeIT.txt` — "Tests run: 2,
      Failures: 0, Errors: 0").
- [x] A shared WireMock support base class exists and is documented (Javadoc on the class
      itself, plus this story) well enough that TECH-151..155 can each be implemented by
      copying its usage pattern.
- [x] Fixture directory convention (`fixtures/soap/<HandlerName>/`) documented with at
      least one real fixture file committed as an example.

**Completed:** 2026-08-03, branch `spike/tech-044-comprehensive-testing-strategy`.
`./mvnw clean test`: 465 tests green. `./mvnw verify -P integration-tests`: the new IT
green (2/2).

---

### TECH-151

**Title:** `CiudadIngestionHandlerIT` (WireMock + Testcontainers PostgreSQL)
**Type:** Test
**Priority:** High
**Phase:** 6
**Status:** **Done**
**Complexity:** M
**Branch:** `test/ciudad-ingestion-handler-it` (branched from
`spike/tech-044-comprehensive-testing-strategy`'s tip — TECH-150's scaffolding wasn't
on `main` yet)
**Dependencies:** TECH-150.

**Objective:** First real per-handler integration test, establishing the pattern
TECH-152..155 copy. Runs the full path — `SoapGateway` → `SoapStreamingClient` (real
HTTP call to a local WireMock instance serving a real `promediosSipsaCiudad` XML
fixture) → StAX parse → `SipsaIngestionMapper` → `batchUpsert` against a real
PostgreSQL 18 Testcontainer — and asserts: the correct rows land in `sipsa_ciudad`; a
second run against the same fixture produces `skipped > 0` (idempotency); `IngestionContext`
metrics match the fixture's record count.

**Design note found while implementing:** TECH-150's `SoapWireMockSupport` was
originally a JUnit 5 `@RegisterExtension` (starts/stops a server per test method).
That doesn't fit this test: `sipsa.soap.endpoint` must be known via
`@DynamicPropertySource` *before* the Spring context is created, which is earlier than
any `@RegisterExtension` callback runs. Refactored `SoapWireMockSupport` into a
stateless static-helper class (`stubFixture(WireMockServer, ...)`,
`stubHttpStatus(WireMockServer, ...)`, `endpointOf(WireMockServer)`); this test manages
its own class-scoped `WireMockServer` (started in the `@DynamicPropertySource` method,
stopped in `@AfterAll`, `resetAll()` per test in `@BeforeEach`). TECH-150's own
`WireMockScaffoldingSmokeIT` (explicitly documented as throwaway) is **deleted** by this
story, exactly as planned — its one purpose (prove WireMock+Failsafe wiring works at
all) is now subsumed by this test, which proves the same thing plus the real Postgres
path.

**Two more real findings, verified by running the test, not assumed:**
- `SoapGatewayImpl.getCiudadData()` wraps any transport failure in
  `SipsaIngestionException` (not the `SipsaExternalException` that
  `SoapStreamingClient.stream()` itself throws) — confirmed by an initial failing
  assertion. The SOAP-fault case asserts the real wrapped type
  (`SipsaIngestionException` with `SipsaExternalException` as cause).
- `ingestion_runs` has a real unique constraint on `(method_name, window_key)`
  (`uq_ingestion_runs_window`); reusing a literal window key across test methods (they
  share one Testcontainer/schema for the whole class) collided. Fixed by generating a
  unique `window_key` per `createRun()` call (`System.nanoTime()`-suffixed), matching
  the pattern `SpecificationBuilderPostgresTest` already uses for the same reason.

**Acceptance Criteria:**
- [x] Golden-path case (valid fixture → rows inserted) — asserts exact field values
      (regId, ciudad, codProducto, precioPromedio), not just row count.
- [x] Idempotency case (same fixture run twice → second run skips; `recordsInserted == 0`
      on the second run, row count unchanged at 2).
- [x] SOAP-fault case (WireMock returns 500 → handler surfaces the failure through the
      real transport; no rows persisted).

**Completed:** 2026-08-03, branch `test/ciudad-ingestion-handler-it`.
`./mvnw clean verify -P integration-tests`: 465 unit tests green (0 regressions),
3/3 new integration tests green (`target/failsafe-reports`).

---

### TECH-152

**Title:** `SemanaIngestionHandlerIT` (WireMock + Testcontainers PostgreSQL)
**Type:** Test
**Priority:** Medium
**Phase:** 6
**Status:** **Done**
**Complexity:** S (pattern already established by TECH-151)
**Branch:** `test/semana-ingestion-handler-it`
**Dependencies:** TECH-150, pattern from TECH-151.

**Objective:** Same shape as TECH-151, for `SemanaIngestionHandler` /
`promediosSipsaSemanaMadr`, asserting rows land in `sipsa_mayoristas_semanal` and
covering the `upsertFallbackBatch` path ([TECH-060](#tech-060)) against the real
tmpId/business-key dual lookup.

**Beyond TECH-151's shape:** Semana is the first handler with two real, distinct
persistence paths in the same execution — `upsertTmpBatch` (matched by
`tmp_mayo_sem_id`) for records with a tmp id, `upsertFallbackBatch` (TECH-060's atomic
`ON CONFLICT (arti_id, fuen_id, fecha_ini) DO NOTHING`) for records without one. The
fixture (`fixtures/soap/SemanaIngestionHandler/four-records.xml`) has 2 of each shape,
so every test case exercises both real repository methods against the real
`ux_semana_tmp`/`ux_semana_fallback` unique constraints, not just one of them.

**Acceptance Criteria:**
- [x] Golden-path, idempotency, and SOAP-fault cases, matching TECH-151's structure —
      golden-path and idempotency additionally assert both upsert paths independently
      (one record from each route checked by field values, not just aggregate counts).

**Completed:** 2026-08-03, branch `test/semana-ingestion-handler-it`.
`./mvnw clean verify -P integration-tests`: 465 unit tests green (0 regressions), 3/3
new integration tests green.

---

### TECH-153

**Title:** `MesIngestionHandlerIT` (WireMock + Testcontainers PostgreSQL)
**Type:** Test
**Priority:** Medium
**Phase:** 6
**Status:** **Done**
**Complexity:** S (pattern already established by TECH-151/152)
**Branch:** `test/mes-ingestion-handler-it`
**Dependencies:** TECH-150, pattern from TECH-151/152.

**Objective:** Same shape as TECH-151, for `MesIngestionHandler` /
`promediosSipsaMesMadr` (the day-8 monthly window), asserting rows land in
`sipsa_mayoristas_mensual`.

**Same dual-path shape as TECH-152:** `sipsa_mayoristas_mensual` has the identical
`upsertTmpBatch`/`upsertFallbackBatch` split (`ux_mes_tmp`/`ux_mes_fallback` unique
constraints, `tmp_mayo_mes_id` vs. `(arti_id, fuen_id, fecha_mes_ini)`), so the fixture
(`fixtures/soap/MesIngestionHandler/four-records.xml`) again has 2 records of each
shape.

**Acceptance Criteria:**
- [x] Golden-path, idempotency, and SOAP-fault cases, matching TECH-151's structure —
      golden-path and idempotency additionally assert both upsert paths independently.

**Completed:** 2026-08-03, branch `test/mes-ingestion-handler-it`.
`./mvnw clean verify -P integration-tests`: 465 unit tests green (0 regressions), 3/3
new integration tests green.

---

### TECH-154

**Title:** `AbasIngestionHandlerIT` (WireMock + Testcontainers PostgreSQL)
**Type:** Test
**Priority:** Medium
**Phase:** 6
**Status:** **Done**
**Complexity:** S (pattern already established by TECH-152/153)
**Branch:** `test/abas-ingestion-handler-it`
**Dependencies:** TECH-150, pattern from TECH-151/152/153.

**Objective:** Same shape as TECH-151, for `AbasIngestionHandler` /
`promedioAbasSipsaMesMadr` (the day-10 monthly window), asserting rows land in
`sipsa_abastecimientos_mensual`.

**Same dual-path shape as TECH-152/153:** `ux_abas_tmp`/`ux_abas_fallback` unique
constraints, `tmp_abas_mes_id` vs. `(arti_id, fuen_id, fecha_mes_ini)`. Fixture
(`fixtures/soap/AbasIngestionHandler/four-records.xml`) has 2 records of each shape.
One real detail worth noting: `AbasStaxParser` reads the same `<fechaMesIni>` XML tag
`MesStaxParser` uses (`XmlFieldNames.FECHA_MES_INI`), just into a differently-named
record field (`fechaMes`) - confirmed by reading `AbasStaxParser.HANDLERS` before writing
the fixture, not assumed.

**Acceptance Criteria:**
- [x] Golden-path, idempotency, and SOAP-fault cases, matching TECH-151's structure —
      golden-path and idempotency additionally assert both upsert paths independently.

**Completed:** 2026-08-03, branch `test/abas-ingestion-handler-it`.
`./mvnw clean verify -P integration-tests`: 465 unit tests green (0 regressions), 3/3
new integration tests green.

---

### TECH-155

**Title:** `ParcialIngestionHandlerIT` (WireMock + Testcontainers PostgreSQL)
**Type:** Test
**Priority:** High
**Phase:** 6
**Status:** **Done**
**Complexity:** M (highest-volume handler, real dedup path)
**Branch:** `test/parcial-ingestion-handler-it`
**Dependencies:** TECH-150, pattern from TECH-151.

**Objective:** Same shape as TECH-151, for `ParcialIngestionHandler` /
`promediosSipsaParcial`, but through the *real* transport and *real* database — the
existing `ParcialIngestionHandlerTest` (TECH-011) deliberately uses a real StAX parser
and real mapper against a Mockito-faked repository and a hand-built `InputStream`, so it
never exercises `SoapGateway`/`SoapStreamingClient` or a real `key_hash` unique
constraint. This story does not replace that test — it adds the missing real-transport/
real-DB coverage against a real Postgres unique index instead of a fake.

**Scope correction against the original plan:** the original acceptance criteria below
asked for "actual concurrent-looking inserts" against the `ON CONFLICT (key_hash) DO
NOTHING` path (TECH-117). This story's idempotency case exercises that real path
correctly (two *sequential* real executions against the real unique index — the second
inserts zero, skips both, through the real `insertIgnoringConflicts` SQL, not a mock),
but does not add concurrent/racing execution — that is already covered by
`ParcialConcurrentDedupTest`/`ParcialConcurrentIngestionAppTest` (TECH-117's own tests),
and duplicating it here would test the same concurrency behavior a second time rather
than add real per-handler-IT coverage. The criterion below is kept as originally written
but resolved against what was actually needed and built, not literally.

**Acceptance Criteria:**
- [x] Golden-path, idempotency, and SOAP-fault cases, matching TECH-151's structure.
- [x] `ParcialIngestionHandlerTest` is untouched — both tests coexist, each covering what
      the other does not.
- [x] The idempotency case exercises the real `ON CONFLICT (key_hash) DO NOTHING` path
      (TECH-117) against a real Postgres unique index, not a mock (sequential, not
      concurrent — see scope correction above).

**Completed:** 2026-08-03, branch `test/parcial-ingestion-handler-it`. Completes the
5-handler IT suite (TECH-151..155).
`./mvnw clean verify -P integration-tests`: 465 unit tests green (0 regressions), all
15 integration tests green (5 handlers × 3 cases).

**Completed:** —

---

### TECH-156

**Title:** `SoapStreamingClientTest` — retry/backoff/GZIP decompression unit coverage
**Type:** Test
**Priority:** Medium
**Phase:** 3
**Status:** **Done**
**Complexity:** S
**Branch:** `test/unit-coverage-gaps-tech156-158`
**Dependencies:** None.

**Objective:** Carried over from `testing-strategy.md`'s "Recommended" unit-test list
(never implemented). `SoapStreamingClientMetricsTest` (TECH-032) already proves the
*metrics* emitted per attempt/retry, using a real local `HttpServer`; this story adds
the missing behavioral assertions on the same fixture: exponential backoff timing
between retries, immediate failure (no retry) on 4xx vs retry on 5xx, and GZIP
decompression when the response carries `Content-Encoding: gzip`.

**Implemented as** `SoapStreamingClientBehaviorTest` (4 cases): 4xx → exactly 1 real HTTP
call, `SipsaExternalException` with the real status; 5xx exhausting all retries →
exactly `maxRetries + 1` real calls *and* a real elapsed-time assertion (not just a call
count) that the exponential backoff (`backoffMs * 2^0 + backoffMs * 2^1 + ...`) actually
happened; 5xx then success → recovers and returns the real response body; GZIP → a
real `GZIPOutputStream`-compressed response body is transparently decompressed to the
original plaintext.

**Acceptance Criteria:**
- [x] 4xx response → no retry, immediate `SipsaExternalException`.
- [x] 5xx response → retried up to `maxRetries`, with backoff timing asserted (not just
      counted).
- [x] `Content-Encoding: gzip` response is transparently decompressed before parsing.

**Completed:** 2026-08-03, branch `test/unit-coverage-gaps-tech156-158`. 4/4 tests green.

---

### TECH-157

**Title:** `SipsaReadServiceTest` + `PaginationConfigTest`
**Type:** Test
**Priority:** Medium
**Phase:** 3
**Status:** **Done**
**Complexity:** S
**Branch:** `test/unit-coverage-gaps-tech156-158`
**Dependencies:** None.

**Objective:** Carried over from `testing-strategy.md`'s "Recommended" unit-test list
(never implemented). `SipsaReadServiceTest`: pagination parameters are validated,
invalid IDs throw `SipsaValidationException`, `SpecificationBuilder` is invoked with the
correct hardcoded field names per query method. `PaginationConfigTest`: `buildPageable()`
converts 1-based API pages to 0-based Spring pages; `validatePageable()` enforces the
max page size.

**Scope note against the original description:** "`SpecificationBuilder` is invoked with
the correct hardcoded field names per query method" is **not** re-verified here — that
exact contract (which field name each `SipsaReadService` call site passes) is already
covered by `SpecificationBuilderTest`/`SpecificationBuilderPostgresTest`
(TECH-041), and re-asserting it from `SipsaReadService`'s side would need a real
`CriteriaBuilder`/`Root` (or reflection into the built `Specification`) for no added
signal. `SipsaReadServiceTest` instead covers what is actually `SipsaReadService`'s own
responsibility: 1-based→0-based pagination conversion, `validateIds` rejecting a
negative/zero filter ID *before* the repository is ever called (`verifyNoInteractions`),
and the repository result being mapped through the right mapper — for `getCiudad` fully,
and lightly (one valid-path + one validation-rejection case each) for the other 4 query
methods, since they all share the same `executeQuery` template.

**Real finding surfaced while writing `PaginationConfigTest`:** `validatePageable`'s
"page number cannot be negative" branch is unreachable through any current caller —
`buildPageable` already clamps `Math.max(0, page - 1)` before a `Pageable` is ever
built, and Spring's own `PageRequest.of` rejects a negative page argument itself before
`validatePageable` could see it. Tested anyway, directly, against a hand-stubbed
`Pageable` (`Mockito.mock`) to prove the method's own contract; documented as dead code
in production terms, not "fixed" (same treatment `SpecificationBuilderTest`, TECH-041,
gave a similar no-current-exploit-path observation).

**Acceptance Criteria:**
- [x] Both classes covered per their description above (with the one scope note),
      mocking collaborators (no DB).

**Completed:** 2026-08-03, branch `test/unit-coverage-gaps-tech156-158`.
`SipsaReadServiceTest`: 12/12 green. `PaginationConfigTest`: 14/14 green.

---

### TECH-158

**Title:** Unit coverage for `GenericIngestionJob` / `IngestionService` dispatch
**Type:** Test
**Priority:** Low
**Phase:** 3
**Status:** **Done**
**Complexity:** S
**Branch:** `test/unit-coverage-gaps-tech156-158`
**Dependencies:** None.

**Objective:** `GenericIngestionJob.runIngestion()` (delegates to `IngestionService.execute()`)
and `IngestionService`'s handler registry (`isValidMethod`, `getAvailableMethodNames`,
`execute` — including the "no handler found" `SipsaBusinessException` path) currently
have no dedicated unit test; they are covered only transitively through
`ScheduledIngestionDispatcherTest` and `ParcialConcurrentIngestionAppTest`, which use a
real `GenericIngestionJob` incidentally, not to prove this class's own contract. Add a
small, explicit, mocked-`IngestionHandler` test for both classes.

**Implemented as** `IngestionServiceTest` (9 cases — dispatch to the correct handler and
no other; unregistered method throws `SipsaBusinessException`, handler never invoked;
null/blank method name and null context both throw; `isValidMethod`/
`getAvailableMethodNames` reflect exactly the registered handlers; a null/empty handler
list registers nothing; `validateTriggerRequest`'s null/blank/unregistered/valid paths)
and `GenericIngestionJobTest` (2 cases — calls the `protected runIngestion` directly,
same-package test, rather than going through the full `IngestionJob.execute()`
orchestration, which is already covered elsewhere against `ScriptedIngestionJob`;
delegates to `IngestionService.execute` with the context's own method name unmodified;
an exception from `IngestionService.execute` propagates out unmodified — asserted
`isSameAs`, not just "some exception").

**Acceptance Criteria:**
- [x] `IngestionService.execute` dispatches to the correct handler by method name.
- [x] `IngestionService.execute` on an unregistered method name throws
      `SipsaBusinessException` (currently untested directly).
- [x] `GenericIngestionJob.runIngestion` delegates to `IngestionService.execute` with the
      context's method name, unmodified.

**Completed:** 2026-08-03, branch `test/unit-coverage-gaps-tech156-158`.
`IngestionServiceTest`: 9/9 green. `GenericIngestionJobTest`: 2/2 green.
`./mvnw clean test`: 506 unit tests green (0 regressions, up from 465 — the 41 new
tests across TECH-156/157/158). `./mvnw verify -P integration-tests`: all 15
integration tests still green.

---

### TECH-159

**Title:** Introduce JaCoCo (report-only)
**Type:** Infrastructure (test)
**Priority:** Medium
**Phase:** 6
**Status:** **Done**
**Complexity:** S
**Branch:** `test/introduce-jacoco-reporting`
**Dependencies:** None.
**Decision reference:** [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md)

**Objective:** Add the JaCoCo Maven plugin bound to `test` (unit) and, once TECH-150
lands, `verify` (integration), producing an HTML/XML report. **No `check` goal, no
build-breaking threshold** — the coverage targets already written in
`testing-strategy.md` (80/60/95/50%) remain aspirational until there is real data to
show whether they are realistic, consistent with this repo's stated preference
(see GitHub issue #7's own guidance: "should not enforce unrealistic thresholds
initially").

**Version note (found while implementing):** the search that suggested "JaCoCo 0.8.16"
as latest was wrong for what's actually published — Maven Central's real
`maven-metadata.xml` for `org.jacoco:jacoco-maven-plugin` tops out at **0.8.15**;
`0.8.16` doesn't resolve (`PluginResolutionException`). Used 0.8.15, confirmed working
against Java 25 bytecode with zero errors (0.8.13 added Java 25 class-file support).

**Implemented:** two `jacoco-maven-plugin` executions in the default build
(`prepare-agent` + `report` bound to the `test` phase) and two more inside the
`integration-tests` profile (`prepare-agent-integration` + `report-integration` bound to
`verify`) — separate exec files and separate reports
(`target/site/jacoco/`, `target/site/jacoco-it/`), no merge/aggregate goal, keeping the
report-only story simple. `prepare-agent`'s default `argLine` property is picked up by
Surefire/Failsafe automatically — this `pom.xml` has no explicit `<argLine>` in either
plugin's own configuration to conflict with it.

**Real numbers measured** (`./mvnw clean test` then `./mvnw verify -P integration-tests`,
both against the state after TECH-151..158) — see `testing-strategy.md`'s Coverage
Targets table for the full breakdown: `WindowPolicy` 100%, `IngestionJob` 96.7%,
`GlobalExceptionHandler` 100% (all already above target); `application.service` 58.7%
(just under the 60% target); the SOAP parser/mapper packages are the clearest evidence
of *why* TECH-151..155 mattered — 33.7%/38.4% from unit tests alone, jumping to
71.4%/91.1% once the per-handler integration tests are counted (in their own separate
report, not merged).

**Acceptance Criteria:**
- [x] `./mvnw clean verify` produces a JaCoCo report under `target/site/jacoco/` (and,
      under `-P integration-tests`, `target/site/jacoco-it/`).
- [x] No build fails due to coverage.
- [x] `testing-strategy.md`'s Coverage Targets table is updated with the first real
      measured numbers per layer, replacing the "not measured" note.

**Completed:** 2026-08-03, branch `test/introduce-jacoco-reporting`.

**Completed:** —

---

### TECH-160

**Title:** E2E suite — golden path (`Ciudad`) + failure path (SOAP 500)
**Type:** Test
**Priority:** High
**Phase:** 6
**Status:** **Done**
**Complexity:** M
**Branch:** `test/e2e-ciudad-golden-and-failure-path`
**Dependencies:** TECH-150.
**Decision reference:** [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md)

**Objective:** New `src/test/java/.../e2e/` package, `*E2ETest` naming, run via the same
`integration-tests` Failsafe profile. `@SpringBootTest(webEnvironment = RANDOM_PORT)`,
real HTTP client, WireMock SOAP, Testcontainers PostgreSQL, reusing the mock-OIDC-issuer
pattern for the authenticated internal endpoints. Deliberately narrow scope — see
ADR-011's rationale for why this does not grow into a second copy of the per-handler
ITs.

**Scope actually implemented, and how it differs from the original plan below (each
difference verified against real behavior, not assumed):**
- Golden path: `POST /api/internal/ingestion/run?method=promediosSipsaCiudad&force=true`
  → `202` → poll **`GET /api/internal/audit/request/{requestId}`** (not `GET
  /audit/run/{runId}` as originally planned — see below) until `METRICS_UPDATED`
  appears → asserts the exact 6-event sequence → extracts `runId` from that same
  response → `GET /api/internal/ingestion/runs/{runId}` confirms `status=SUCCEEDED` and
  `recordsInserted=2` → `GET /api/sipsa/ciudad` (public, `permitAll`, no token needed)
  returns the 2 persisted rows.
- Failure path: same trigger, WireMock returns SOAP `500` → sequence ends
  `INGESTION_FAILED`, `METRICS_UPDATED` still fires (the `finally` block in
  `IngestionJob.execute` runs regardless of outcome — confirmed by reading the method,
  not assumed) → run status `FAILED` → zero rows in `sipsa_ciudad`.
- **`force=true` is used deliberately**, not planned originally: without it, the trigger
  would go through the real wall-clock daily-window check (14:20 COT), making the test's
  pass/fail depend on what time it happens to run — `WindowPolicy`'s own window logic is
  already exhaustively covered by `WindowPolicyTest`; this suite exists to prove the
  *wiring*, not re-prove the window.
- **Polls `/audit/request/{requestId}`, not `/audit/run/{runId}`, and for a different
  reason than "which endpoint is more convenient":** `REQUEST_RECEIVED`/`REQUEST_ACCEPTED`
  are logged with `run_id = NULL` (they happen before any run row exists) - `GET
  /audit/run/{runId}` structurally cannot return them (found by reading
  `IngestionTriggerService`/`IngestionJob`, confirmed by running the test). Only
  `/audit/request/{requestId}` returns the complete correlated sequence.
- **Polls for `METRICS_UPDATED`, not `INGESTION_SUCCEEDED`/`INGESTION_FAILED`:** each
  `IngestionAuditService.logEvent` call is itself `@Async` - by the time the terminal
  outcome event is visible, the very next (and last) event may not have committed yet.
  Same race `testing-strategy.md` already documents from a prior incident
  (`ParcialConcurrentIngestionAppTest`, 2026-07-19); avoided the same way here.
- **`@BeforeEach` deletes (not resets) the `ingestion_runs` row for this method**, in
  FK-safe order (`ingestion_audit`/`ingestion_rejects` reference `run_id` with no
  `ON DELETE CASCADE`) - found while implementing: `IngestionControlService.createRun`
  *reuses and restarts* an existing row for the same `(method_name, window_key)` rather
  than inserting a new one (its own documented "restart" behavior), which would have
  made each test's audit history accumulate across runs of the class instead of starting
  clean.
- **Two real Spring Boot 4 module-split issues found and fixed** (neither is an app bug,
  both are new test-only dependencies): `TestRestTemplate` moved packages, from
  `org.springframework.boot.test.web.client` to `org.springframework.boot.resttestclient`
  (new dependency, `spring-boot-resttestclient`); that module's own
  `TestRestTemplateTestAutoConfiguration` additionally needs `RestTemplateBuilder` from
  `spring-boot-restclient` (`NoClassDefFoundError` without it - confirmed by running the
  test before adding the second dependency, not assumed upfront).
- **Surefire's own default `**/*Test.java` include pattern also matches
  `*E2ETest.java`** (it ends in "Test.java") - confirmed by running `./mvnw test` before
  adding an explicit exclude and seeing the E2E test's full Spring context try to boot
  in the plain unit run. Fixed with one `<excludes>` entry on `maven-surefire-plugin`;
  no other Surefire behavior (including JaCoCo's `argLine` property pickup) is affected.
- Failsafe's own default includes (`**/*IT.java`, `**/IT*.java`, `**/*ITCase.java`) don't
  match `*E2ETest` either - added `**/*E2ETest.java` explicitly to
  `maven-failsafe-plugin`'s `<includes>` rather than renaming the suite to fit
  Failsafe's convention (ADR-011 already chose the `*E2ETest` name).
- CI's `integration-verify` job (TECH-161) is updated in this same story to also assert
  the E2E report ran and wasn't skipped, alongside the 5 handler ITs it already checked.

**Acceptance Criteria:**
- [x] Both cases pass against a full, real Spring context (no mocked application beans
      beyond the WireMock SOAP endpoint and the mock OIDC issuer).
- [x] Test run time is bounded and documented: ~8s total for both cases locally
      (`target/failsafe-reports`), well inside Failsafe's shared 20-minute CI timeout
      alongside the 5 handler ITs.

**Completed:** 2026-08-03, branch `test/e2e-ciudad-golden-and-failure-path`.
`./mvnw clean verify -P integration-tests`: 506 unit tests green (0 regressions), all
17 integration tests green (15 per-handler + 2 E2E).

---

### TECH-161

**Title:** CI: `integration-verify` job (`./mvnw verify -P integration-tests`)
**Type:** Infrastructure (CI)
**Priority:** Medium
**Phase:** 6
**Status:** **Done**
**Complexity:** S
**Branch:** `ci/integration-verify-job`
**Dependencies:** TECH-150.
**Decision reference:** [ADR-011](../adr/ADR-011-integration-and-e2e-testing-strategy.md)

**Objective:** Add a second job to `.github/workflows/ci.yml`, running
`./mvnw verify -P integration-tests`, in **parallel** with the existing `verify` job
(not chained after it), reusing the same JDK 25/Maven-cache setup already proven for
Testcontainers in that workflow (TECH-120).

**Implemented as** the `integration-verify` job: same trigger (`pull_request`, push to
`main`), same `runs-on`/JDK 25 setup/Maven cache as `verify`, no `needs:` between the two
jobs (GitHub Actions runs jobs without a `needs:` dependency in parallel by default).
Adds one extra safety step beyond the bare `mvn verify` call, mirroring `verify`'s own
existing "assert the Flyway migration gate ran" step: every one of the 5 per-handler
ITs is `@Testcontainers(disabledWithoutDocker = true)`, the same silent-skip risk
`FlywayMigrationsTest` already guards against — so this job asserts, per handler, that
its Failsafe XML report exists and shows `tests > 0` and `skipped = 0`, failing the
build loudly instead of letting a Docker-unavailable runner silently skip the whole IT
suite. Verified locally against real report output before committing (not assumed): the
loop's `sed` parsing was run against the 5 real `target/failsafe-reports/TEST-*.xml`
files from a real `./mvnw verify -P integration-tests` run, confirming
`tests=3 skipped=0` for each. YAML syntax validated (`YAML.load_file`).

**Acceptance Criteria:**
- [x] New job runs on the same triggers as `verify`.
- [x] Confirmed to run in parallel, not sequentially after `verify` (no `needs:` field
      between them — matching the rationale in ADR-011: a slow/flaky
      Testcontainers/WireMock startup must never block the fast unit-test signal).
- [x] Failure of `integration-verify` blocks merge exactly like `verify` does today (no
      quieter failure mode introduced — same job-level pass/fail GitHub Actions
      semantics, no `continue-on-error`).

**Completed:** 2026-08-03, branch `ci/integration-verify-job`.
