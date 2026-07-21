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
| TECH-044 | SPIKE: Integration test strategy (WireMock/Testcontainers) | Low | 6 | Partially resolved — Testcontainers half settled by ADR-009 (`FlywayMigrationsTest`); WireMock half pending |
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
| TECH-110 | Validate scheduled ingestion jobs and add scheduling tests | High | 3 | **Done** |
| TECH-111 | Correct monthly `WindowPolicy` method binding, grace days, and stable window keys | High | 3 | **Done** |
| TECH-120 | Continuous integration pipeline (GitHub Actions) | High | — | **Done** |
| TECH-130 | Cognito resource server, scopes and app clients | High | — | Pending — decisions approved 2026-07-21 ([ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) Accepted), blocked on TECH-137 |
| TECH-131 | API Gateway: API keys, usage plans, throttling, access logs | High | — | Pending — blocked on TECH-130 + TECH-132 |
| TECH-132 | Private networking: ECS, VPC Link, internal ALB, gateway-bypass prevention | High | — | Pending — decisions approved 2026-07-21 (ADR-010 Accepted), blocked on TECH-137 |
| TECH-137 | Terraform bootstrap and GitHub OIDC validation | High | — | **Done** (2026-07-21, branch `infra/terraform-bootstrap` — scaffolding + CI only, no AWS resource created) |
| TECH-113 | Fix `artiId`/`muniId` filters of `GET /api/sipsa/parcial` | Medium | — | **Done** (2026-07-16, branch `fix/sipsa-parcial-query-filters`) |
| TECH-114 | Strict `enmaFecha` parsing with explicit rejection (H-1) | Medium | — | **Done** (2026-07-16 — implemented within TECH-011; H-1 did not occur on real data) |
| TECH-115 | Backfill/consolidation of a pre-existing external `sipsa_parcial` database | Medium | — | Conditional — only if an external historical database is confirmed to exist |
| TECH-116 | Disable `baseline-on-migrate` after per-environment Flyway history inventory | Low | — | Pending |
| TECH-117 | Handle concurrent `SipsaParcial` duplicate insertion safely | Medium | — | **Done** (2026-07-19, branch `fix/sipsa-parcial-concurrent-dedup` — atomic `ON CONFLICT (key_hash) DO NOTHING`, collisions counted as skipped) |
| TECH-118 | Align `SipsaParcial` decimal precision (JPA 15,2 vs DDL 19,2) | Low | — | **Done** (2026-07-19, branch `fix/align-sipsa-parcial-decimal-precision` — annotation aligned to `19,2`, no migration) |
| TECH-119 | Remove redundant `idx_sipsa_parcial_key_hash` index | Low | — | **Done** (2026-07-16, branch `fix/remove-redundant-parcial-key-hash-index`, migration V3) |
| TECH-122 | Harden `SipsaParcial` natural-key constraints (NOT NULL / natural unique) | Low | — | Pending (contract phase; gated on TECH-012 external half) |
| TECH-123 | Add `first_seen_at`/`last_seen_at` republication traceability | Low | — | Optional — not recommended now (write cost; see story) |
| TECH-124 | Optimize `SipsaParcial` article-filter queries | Low | — | **Done** (2026-07-18, branch `perf/sipsa-parcial-article-filter-index`, migration V4 — covering index; count 18 ms → ~2 ms) |
| TECH-125 | Define `SipsaParcial`/ingestion data retention policy | Low | — | Pending decision |
| TECH-133 | Centralize and validate monthly ingestion window configuration | Low | — | **Done** (2026-07-17 — typed `monthlyWindowStart`, divergent `06:00` fallback removed, effective 14:00 unchanged) |
| TECH-134 | Align remaining SIPSA decimal annotations with the DDL (`Ciudad`, `Semanal`) | Low | — | **Done** (2026-07-19, branch `fix/align-remaining-sipsa-decimal-precision` — all SIPSA price models now declare `19,2`, no migration) |
| TECH-135 | Centralize ingestion rejection-threshold configuration (C-04) | Low | — | **Done** (2026-07-19, branch `refactor/centralize-ingestion-rejection-thresholds` — thresholds bind once in `IngestionProperties`, effective 0.01/5000 unchanged) |
| TECH-136 | Centralize async executor configuration and pin the audit executor (C-05) | Low | — | **Done** (2026-07-19, branch `refactor/centralize-async-executor-config` — `AsyncExecutorProperties` + `@Async("ingestionTaskExecutor")` for audit; geometry 2/10/25/60s unchanged) |

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
migration `V2__add_parcial_natural_key_index.sql` (expand-only), and suites
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
with the local mock-OIDC token flow, Flyway V1→V4 unaffected (no migration in this
story). No DTOs, security config, scopes, or business logic touched.

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
migration; V1–V4 unchanged. **Follow-up (separate story, not this one):** apply the same
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
verbatim. No Flyway migration; V1–V4 unchanged; no `V5`.

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
detail contract unaffected. No Flyway migration; V1–V4 unchanged.

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
changed. No Flyway migration; V1–V4 unchanged; no `V5`.

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
infrastructure change. No Flyway migration; V1–V4 unchanged.

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
test file). No Flyway migration; V1–V4 unchanged.

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
behavior was changed — this story is test-and-documentation only. No Flyway migration;
V1–V4 unchanged; no `V5`.

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
lines removed). No Flyway migration; V1–V4 unchanged.

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
migration; V1–V4 unchanged.

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
migration; V1–V4 unchanged.

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
metric semantics, audit event shape, database, or Flyway change. No Flyway migration;
V1–V4 unchanged; no `V5`.

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
whether the table has 5 or 40 rows). Verified in Docker: clean startup, Flyway still at
v4 (no `V5`); 25 runs seeded directly via SQL (a pure read endpoint needs no real DANE
SOAP call to verify); `page=1&size=10`, `page=2`, and `page=3` (partial last page)
each returned correct, non-overlapping, `startTime DESC`-ordered slices with correct
`next`/`prev` links and `count=25`, `pages=3`; the no-params call defaulted to
`size=20` (`pages=2`); 401 without a token; 403 with `sipsa/audit.read`;
`/actuator/health` and `/actuator/metrics` unaffected. `IngestionRunQueryServiceGetRunStatusTest`
and `InternalEndpointSecurityTest` updated only for the new constructor
signature/return type (`PaginationConfig` dependency; `Page.empty()` stub for
Mockito's default answer, which doesn't cover `Page`) — no behavior change in either.
No scheduler, TECH-053, metrics, audit, run-execution, cancellation, TECH-060, SIPSA
data repository, SOAP, general security, or database schema change. No Flyway
migration; V1–V4 unchanged; no `V5`.

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
audit, repository, API, security, SOAP, database, or AWS infrastructure change. No
Flyway migration; V1–V4 unchanged.

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

| Método | Clasificación en `WindowPolicy` (antes y ahora) | Clasificación en `HealthIndicator` (antes) | Resultado esperado (y logrado) |
|---|---|---|---|
| `promediosSipsaCiudad` | no monthly | en `DAILY_METHODS` → daily threshold | not monthly → daily |
| `promediosSipsaParcial` | no monthly | en `DAILY_METHODS` → daily threshold | not monthly → daily |
| `promediosSipsaSemanaMadr` | no monthly (weekly *data*, daily *scheduling cadence*) | en `DAILY_METHODS` → daily threshold | not monthly → daily |
| `promediosSipsaMesMadr` | monthly (`MES_MADR_RULE`, day 8/9, key `M8`) | NOT en `DAILY_METHODS` → monthly threshold | monthly → monthly |
| `promedioAbasSipsaMesMadr` | monthly (`ABAS_RULE`, day 10/11, key `M10`) | NOT en `DAILY_METHODS` → monthly threshold | monthly → monthly |

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

> **Cambio funcional intencional para métodos no registrados. No afecta los cinco
> métodos actualmente soportados. El nuevo comportamiento coincide con la convención
> explícita de `WindowPolicy`.**

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
contract, SOAP, security, or persistence change. No Flyway migration; V1–V4 unchanged; no
`V5`; no remote database access.

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
the conflict target. Verified in Docker: clean startup, V1–V4 only, no `V5`; a real
`promediosSipsaSemanaMadr` ingestion run (229,369 records seen, 229,369 inserted, 0
rejected, 0 SQL errors, `sipsa.ingestion.runs` metric incremented,
`/actuator/health` UP); an immediate identical re-run (`force=true`, reusing the same
window per existing `IngestionControlService` "restart" behavior — unrelated to this
story) inserted 0 and correctly reported 0 rows changed, with the stored row count
unchanged at 229,369 and zero duplicate `(arti_id, fuen_id, fecha_ini)` combinations —
direct, real-data proof the atomic skip-existing path works correctly at production
scale. No scheduler, TECH-053, TECH-054, API, pagination, metrics, audit, SOAP, security,
threshold, `SipsaParcial` deduplication, or AWS infrastructure change. No Flyway
migration; V1–V4 unchanged; no `V5`.

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
itself. No Flyway migration; V1–V4 unchanged.

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
scheduler, security, database, or AWS infrastructure change. No Flyway migration; V1–V4
unchanged.

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
infrastructure change. No Flyway migration; V1–V4 unchanged.

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

**Audit (2026-07-20, `docs/production-aws-readiness-plan`, no AWS resource created, no
code changed):** full classification and evidence in
[aws-production-readiness.md](../architecture/aws-production-readiness.md) §2. Every
application-side item (issuer/`token_use`/`client_id`/scope validation, key rotation) is
already implemented and e2e-validated against a mock issuer — confirmed **E** (resolved).
Remaining blockers are the real user pool/resource server/app clients (**C**, needs AWS
account access) plus 3 open decisions (**D**): how many `client_credentials` app clients
and their scopes, whether a human-operator `authorization_code` flow is needed at all,
and where client secrets are stored. **Cannot be marked Done** until those are resolved.

**Decision/execution plan:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md)
(**Accepted**, 2026-07-21) resolves the IaC-tool and Cognito-ownership blockers —
Terraform, this repository, repository owner administers Cognito initially — and approves
the two-contract identity model (M2M `client_credentials` with confidential app clients;
human users via Authorization Code + PKCE, public app client, no implicit flow, no shared
app client between the two). Implementation is blocked on
[TECH-137](#tech-137) (Terraform bootstrap) landing first. **Still Pending** — no real
Cognito resource created yet; only decisions and Terraform scaffolding exist.

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

**Audit (2026-07-20, `docs/production-aws-readiness-plan`, no AWS resource created, no
code changed):** full classification and evidence in
[aws-production-readiness.md](../architecture/aws-production-readiness.md) §2. Confirmed
by reading `IngestionJob`/`ScheduledIngestionDispatcher`: the async trigger (TECH-053)
means `POST /api/internal/ingestion/run` returns `202` in milliseconds, so API Gateway's
~29s REST integration timeout is not at risk (**E**, resolved). No incompatibility found
between the Cognito authorizer, API keys, private integration, and Spring Security's own
re-validation — four independent responsibilities, not overlapping ones (§3 of the
readiness doc). Open blockers: depends on TECH-130 existing (**C**); REST vs HTTP API,
whether an IAM/SigV4 authorizer path is needed, real usage-plan tiers (the backlog's
`basic`/`partner` figures below are illustrative examples, not a decision), and CORS
(**D**, all four — CORS in particular has zero prior mention anywhere in this repo).
**Cannot be marked Done** until those are resolved.

**Decision/execution plan:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md)
(**Accepted**, 2026-07-21) — Fase 4 covers this story's gateway provisioning. Approved
throttling values: general 10 req/s / burst 20 / 100,000 req per month per consumer;
ingestion-trigger routes 1 req/s / burst 2 (replacing the illustrative `basic`/`partner`
figures below). No custom domain/ACM for the first version — API Gateway's managed
`execute-api` endpoint is used. REST-vs-HTTP-API and IAM/SigV4 necessity remain open,
deferred to this story's own implementation by design (see readiness audit §10 Q8/Q9);
CORS also remains open. **Still Pending**, blocked on TECH-130 and TECH-132.

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

**Audit (2026-07-20, `docs/production-aws-readiness-plan`, no AWS resource created, no
code changed):** full classification, gateway-bypass-prevention design, and evidence in
[aws-production-readiness.md](../architecture/aws-production-readiness.md) §2 and §7.
Confirmed by reading `SoapProperties`/`.env.example`: `SOAP_ENDPOINT` is a **public
internet** DANE endpoint, so a **NAT Gateway (or equivalent) is required, not optional**
— VPC endpoints alone cannot cover this egress path (this is evidence-based, not a
default assumption). App-side readiness confirmed **E**: `/actuator/health` is
unauthenticated with Spring Boot's liveness/readiness probe groups already enabled, the
app is fully environment-driven for `DB_HOST`/`DB_PORT`, and it logs to stdout
(CloudWatch-Logs-ready) — no code change needed for any of this. Open blockers (**D**):
ECS Fargate vs EC2, RDS vs self-managed PostgreSQL, VPC-internal TLS policy, and whether
Cloud Map service discovery is needed (likely not, for a single API-Gateway-fronted
service — not yet confirmed). Every provisioning step itself is **C** (real AWS access).
**Cannot be marked Done** until the D items are resolved.

**Decision/execution plan:** [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md)
(**Accepted**, 2026-07-21) — Fase 1 (network) and Fase 3 (compute/data) cover this story.
Approved: new dedicated VPC, 2 AZ, ECS Fargate (`desired_count=1`, CPU/memory as
Terraform variables, small starting values to be verified against real ingestion load),
one NAT Gateway initially (accepted single point of failure for egress, documented
upgrade path to one NAT per AZ), RDS for PostgreSQL Single-AZ (encrypted, 7-day backups,
`publicly_accessible=false`, deletion protection — Multi-AZ deferred as a future
upgrade), secrets via AWS Secrets Manager. VPC-internal TLS policy remains open. **Still
Pending**, blocked on [TECH-137](#tech-137) (Terraform bootstrap) landing first.

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
2026-07-21) Fase 0 — the repository owner approved Terraform, in this repository, single
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
`MayoristasMensual`/`Abastecimientos`); **no Flyway migration — V1/V2/V3/V4 unchanged**.
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
`ddl-auto=validate` booting against the untouched V1→V4 schema.

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
migration — V1/V2/V3/V4 unchanged, no V5.** Rounding semantics unchanged and shared
with TECH-118 (`NUMERIC(19,2)` coerces scale > 2 half-away-from-zero at insert; pinned
per model). No `@Digits`, no CHECK constraints, no API/DTO changes — JSON stays exact
unquoted numbers (verified per model).

**Final state of decimal declarations:**

| Modelo | Estado previo | Estado final |
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

**Completed:** 2026-07-19. No migration (V1–V4 untouched), no tuning, no functional
change to audit semantics.

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
