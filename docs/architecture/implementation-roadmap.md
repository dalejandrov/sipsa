# Implementation Roadmap — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13  
**Last reconciled against `main`:** 2026-07-19 (see
[technical backlog](../backlog/technical-backlog.md) for full per-story evidence)

This document defines the phased implementation plan for all technical backlog items.
Each phase is designed to be self-contained: the system remains consistent and deployable
at the end of every phase.

---

## Prerequisites

Before starting any phase:

- Branch: all work starts from `main` (the Spring Boot 4 / Java 25 migration is merged).
- `./mvnw clean verify` must pass before starting and after completing each story.
- No story is marked complete unless its acceptance criteria are verified.
- Read the [Contributing Guide](../../CONTRIBUTING.md) before opening a PR.

---

## ► Next Step: start Phase 2

**Phase 1 is complete** as of 2026-07-19 (see the
[technical backlog](../backlog/technical-backlog.md) for full per-story evidence):
- The Spring Boot 4 / Java 25 migration is merged to `main`.
- `./mvnw clean verify` passes (264 tests as of 2026-07-19, 0 failures, 0 skips) and
  runs in CI on every PR and push to `main` (TECH-120, merged via PR #16).
- Security: TECH-001/TECH-002 merged via PR #17 (2026-07-15, ADR-002 Accepted) and
  e2e-validated against the mock OIDC issuer.
- TECH-071 (`batch-size` defaults) — done 2026-07-16.
- TECH-030 (named `@Async` executor for audit logging) — resolved by TECH-136
  (2026-07-19), which was scoped to C-05 plus this exact finding, confirmed
  independently during a CI-flake investigation.
- TECH-020 (`@RequestMapping` leading slash), TECH-031 (health-indicator thresholds
  externalized), TECH-050 (placeholder comments removed), TECH-051
  (`toAuditEventResponse` rename), TECH-052 (`getRun()` → `Optional`), and TECH-070
  (`SoapProperties` Bean Validation) — all done 2026-07-19, merged directly to `main`
  one story per branch (`--no-ff`, no squash).

All Phase 1 acceptance criteria below are met. **Phase 2 (Contract and Correctness) is
now unblocked.**

Note: parts of Phase 3 (TECH-040 via TECH-110, and TECH-111) were completed ahead of
sequence as independently approved tracks. This does not change the phase order for the
remaining stories.

Also outside the original phase sequence: a substantial `SipsaParcial` data-integrity
and performance track (TECH-113/114/117/118/119/124/133/134/135/136, plus config
centralization stories TECH-135/136 closing C-04/C-05) emerged from TECH-011's final
review and from operating the ingestion pipeline, and is **complete** as of 2026-07-19.
See Phase 5 below and the backlog for full detail — these are not part of the Phase 1–6
count and do not change what remains open in Phase 1.

---

## Parallel Track — Package Boundary Refactoring (ADR-007)

Not part of the phase sequence below. [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md)
was accepted in a narrow, scoped form (F1, F2, F4, F5 — not F3) after a structural
diagnosis found a small set of verifiable, low-risk package-boundary issues. This track ran
independently of Phase 1–6 and did not block or get blocked by them.

| Story | Title | Status |
|---|---|---|
| TECH-090 | Move internal ingestion commands to `application/command` | **Done** (`refactor/internal-models-and-api-filter`) |
| TECH-091 | Move `TimezoneFilter` to `api/filter` | **Done** (`refactor/internal-models-and-api-filter`) |
| TECH-095 | Remove domain→infrastructure Javadoc reference in `SoapGateway` | **Done** (`refactor/internal-models-and-api-filter`) |
| TECH-093 | Add ArchUnit package-boundary rules | Pending — dependencies (TECH-090/091/095) now merged; story itself not started |
| TECH-094 | SPIKE: Evaluate relocating CXF-generated SOAP sources | Pending |
| TECH-092 | Separate generated SOAP sources from manual code | **Blocked** on TECH-094 |

`./mvnw clean verify` passed after TECH-090/091/095 (7 tests, 0 failures). No REST route,
JSON body, HTTP status, DB schema, or SOAP integration behavior changed. See ADR-007 for
full evidence and the explicit list of package moves this track does **not** authorize
(general DTO/mapper/exception relocation, JPA/domain model separation, `SipsaParcial`
deduplication changes, or general service refactoring).

---

## Parallel Track — CI and AWS Infrastructure

Also outside the phase sequence (see the backlog for full stories):

| Story | Title | Status |
|---|---|---|
| TECH-120 | Continuous integration pipeline (GitHub Actions) | **Done** (2026-07-14, merged via PR #16; first run green — `FlywayMigrationsTest` executed with `tests=4`, `skipped=0`) |
| TECH-137 | Terraform bootstrap and GitHub OIDC validation | **Done** (2026-07-21, branch `infra/terraform-bootstrap` — corrected same day: S3-native locking, Terraform 1.15.7/AWS provider 6.55.0, Trivy, OIDC contract, REST API decision; no AWS resource created) |
| TECH-138 | Provision production VPC foundation | **Done** (2026-07-21, branch `infra/production-vpc-foundation` — `modules/network`, 16/16 `terraform test` green; no AWS resource created) |
| TECH-139 | Define production RDS PostgreSQL foundation | **Done** (2026-07-21, branch `infra/production-rds-foundation` — `modules/database`, 20/20 `terraform test` green; no AWS resource created) |
| TECH-140 | Define production ECR and ECS task foundation | **Done** (2026-07-21, branch `infra/production-ecs-task-foundation` — `modules/ecr` + `modules/ecs-task`, 26/26 `terraform test` green; no AWS resource created, no image published) |
| TECH-141 | Define internal ALB and ECS service foundation | **Done** (2026-07-21, branch `infra/internal-alb-ecs-service` — `modules/ecs-service`, 17/17 `terraform test` green; no AWS resource created) |
| TECH-130 | Cognito resource server, scopes and app clients | **Done** (2026-07-21, branch `infra/cognito-authentication-foundation` — `modules/cognito`, 23/23 `terraform test` green; no AWS resource created) — Terraform foundation and ECS configuration wiring complete (TECH-142, 2026-07-22), no AWS resources applied |
| TECH-142 | Wire Cognito configuration into the ECS task | **Done** (2026-07-22, branch `infra/wire-cognito-ecs-configuration` — issuer/allowlist wired root-only, 108/108 `terraform test` tree-wide, 338 Java tests green; no AWS resource created) |
| TECH-131 | API Gateway: API keys, usage plans, throttling, access logs | **Done** (2026-07-22, branch `infra/api-gateway-private-integration` — `modules/api-gateway`, 21/21 `terraform test` green, 129/129 tree-wide; no AWS resource created) — Terraform foundation complete, VPC Link architecture corrected to an NLB-chain (REST API VPC Links are NLB-only) |
| TECH-132 | Private networking: ECS, VPC Link, internal ALB | **In progress** — VPC, RDS, ECS task, internal service, and API Gateway/VPC Link foundations done (TECH-138, TECH-139, TECH-140, TECH-141, TECH-131); Cognito ECS wiring done (TECH-142); real AWS provisioning still pending |

---

## Phase 1 — Foundation Cleanup [**Done**, 2026-07-19]

**Objective:** Fix bugs, enforce minimum security, and remove low-risk debt.
No behavioral changes to the public API. No new dependencies (except Spring Security).

**Can start:** Immediately after migration branch is merged to `main`.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-001 | Protect `/api/internal/**` with authentication — **Done** (2026-07-15, PR #17, ADR-002) | Security | `fix/internal-endpoint-security` |
| TECH-002 | Restrict Actuator `loggers` endpoint — **Done** (2026-07-15, PR #17) | Security | `fix/internal-endpoint-security` |
| TECH-020 | Fix `@RequestMapping` without leading `/` — **Done** (2026-07-19) | Bug | `fix/request-mapping-leading-slash` |
| TECH-030 | Named executor in `@Async` for audit logging — **Resolved by TECH-136** (2026-07-19) | Bug | `fix/async-executor-audit` (superseded — see TECH-136) |
| TECH-031 | Externalize `SipsaHealthIndicator` thresholds — **Done** (2026-07-19) | Config | `refactor/externalize-health-thresholds` |
| TECH-050 | Remove placeholder comments from 4 handlers — **Done** (2026-07-19) | QA | `refactor/remove-existing-code-comments` |
| TECH-051 | Rename `toAuditEventRequest` → `toAuditEventResponse` — **Done** (2026-07-19) | QA | `refactor/rename-audit-mapper-response` |
| TECH-052 | `IngestionControlService.getRun()` → `Optional` — **Done** (2026-07-19) | QA | `refactor/optional-ingestion-run` |
| TECH-070 | Bean Validation constraints on `SoapProperties` — **Done** (2026-07-19) | Config | `refactor/validate-soap-properties` |
| TECH-071 | Align `batch-size` defaults — **Done** (2026-07-16) | Config | `fix/unify-ingestion-batch-size-config` |

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes. ✅
- `POST /api/internal/ingestion/run` returns `401` without credentials. ✅
- `GET /api/sipsa/ciudad` returns `200` without credentials. ✅
- `GET /actuator/health` returns `200` without credentials. ✅
- Zero `// ...existing code...` comments in `src/main/`. ✅ (TECH-050)
- No breaking changes to the public API contract. ✅

**Criterion to start Phase 2:** Phase 1 merge to `main` is complete and green. ✅ **Met
2026-07-19** — Phase 2 is now unblocked.

---

## Phase 2 — Contract and Correctness

**Objective:** Fix HTTP semantics, improve error responses, and make the scheduler non-blocking.

| Story | Title | Type | Branch | Status |
|---|---|---|---|---|
| TECH-021 | `SipsaParseException` → HTTP 502 | Correctiva | `fix/parse-exception-bad-gateway` (the originally-listed `fix/error-http-semantics` name was not reused) | **Done** (2026-07-19) |
| TECH-022 | Introduce `SipsaNotFoundException` → HTTP 404 | Correctiva | `fix/notfound-exception-404` | **Done** (2026-07-19) |
| TECH-023 | Add `requestId` and `instance` to error responses | Observability | `feat/error-response-context` (the originally-listed `feat/error-correlation-id` name was not reused) | **Done** (2026-07-19) |
| TECH-053 | Make scheduler dispatch async | Correctiva | `fix/scheduler-async-execution` | Pending |
| TECH-054 | Add pagination to `GET /api/internal/ingestion/runs` | Correctiva | `fix/runs-endpoint-pagination` | Pending |

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes.
- `GET /api/internal/ingestion/runs/99999` returns `404`. ✅ (TECH-022)
- A mock test confirms `SipsaParseException` produces HTTP `502`. ✅ (TECH-021)
- Scheduler thread `runDailyWindow()` returns in under 200ms. (TECH-053, pending)
- `GET /api/internal/ingestion/runs` accepts `page` and `size` parameters. (TECH-054, pending)

**Notes:**
- TECH-021 and TECH-022 changed HTTP status codes (400→502, 422→404 respectively for
  their specific cases). Any existing client code or monitoring alerts that depended on
  the prior codes for these paths need updating.
- TECH-023 added fields to `ErrorResponse` but did not remove any existing fields
  (backward compatible).
- TECH-043 (Phase 3, full `GlobalExceptionHandler` contract coverage) is also **Done**
  (2026-07-20) — see Phase 3 below. It depended on TECH-021/022/023 landing first so the
  502/404/`requestId`/`instance` cases were part of the coverage.

**Criterion to start Phase 3:** Phase 2 merge to `main` is complete and green. **Strict
phase gating was not followed here** — TECH-043 (Phase 3) was completed and merged
ahead of TECH-053/TECH-054 (Phase 2), by explicit prioritization: TECH-021/022/023 and
TECH-043 are all part of the same HTTP error contract closure and were done together as
one thread of work. TECH-053 and TECH-054 remain the only pending Phase 2 items and can
proceed independently whenever picked up.

---

## Phase 3 — Testing

**Objective:** Establish a minimum test suite for critical business logic.
These tests must exist before Phase 4 (observability) and Phase 5 (data integrity) because
those phases modify production code that needs a safety net.

| Story | Title | Type | Branch | Status |
|---|---|---|---|---|
| TECH-040 | Unit tests for `WindowPolicy` | Testing | `test/scheduled-ingestion-jobs` (bundled into TECH-110) | **Done** (2026-07-13) |
| TECH-110 | Validate scheduled ingestion jobs and add scheduling tests | Testing | `test/scheduled-ingestion-jobs` | **Done** (2026-07-13) |
| TECH-111 | Correct monthly `WindowPolicy` method binding, grace days, and stable window keys | Correctiva | `fix/window-policy-monthly-rules` | **Done** (2026-07-14, merged via PR #15) |
| TECH-041 | Unit tests for `SpecificationBuilder` | Testing | `test/specification-builder` | Pending |
| TECH-042 | Unit tests for `IngestionJob` | Testing | `test/ingestion-job` | Pending |
| TECH-043 | Tests for `GlobalExceptionHandler` | Testing | `test/global-exception-handler-contract` (the originally-listed `test/exception-handler` name was not reused) | **Done** (2026-07-20) |

**Progress (2026-07-20):** TECH-040 was completed via TECH-110; TECH-111 was produced by
that validation and implemented on 2026-07-14 (merged via PR #15). TECH-043 is done —
`GlobalExceptionHandlerContractTest` covers all 15 `@ExceptionHandler` cases (311 tests
total on `main`). TECH-041/042 have not started — the phase remains open on those two.

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes with ≥ 30 unit tests. ✅ (311 tests as of 2026-07-20 — but
  the phase does not exit until the per-component criteria below are also met.)
- `WindowPolicy`: ≥ 8 test cases covering daily/monthly, force=true, timezone. ✅ (25 delivered)
- `SpecificationBuilder`: ≥ 7 test cases covering all filter combinations. (TECH-041, pending)
- `IngestionJob`: ≥ 7 test cases covering all execution paths. (TECH-042, pending)
- `GlobalExceptionHandler`: one test per exception handler. ✅ (TECH-043 — 15 cases,
  every `@ExceptionHandler` method, via real MVC dispatch)
- No tests depend on a running database or network connection. ✅ (TECH-043's tests use
  `@WebMvcTest`, no database)

**Criterion to start Phase 4:** Phase 3 merge to `main` is complete; ≥ 30 tests pass.

---

## Phase 4 — Observability and Performance

**Objective:** Instrument the system for production diagnosis and fix the N+1 query issue.

| Story | Title | Type | Branch | Status |
|---|---|---|---|---|
| TECH-032 | Add Micrometer metrics for ingestion | Observability | `feat/ingestion-micrometer-metrics` (the originally-listed `feat/ingestion-metrics` name was not reused) | **Done** (2026-07-20, merged to `main`) |
| TECH-060 | Fix N+1 in `upsertFallbackBatch` | Performance | `perf/remove-mayoristas-fallback-n-plus-one` (the originally-listed `fix/batch-upsert-n-plus-one` name was not reused) | Implemented and pushed, not yet merged to `main` |

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes.
- `GET /actuator/metrics` includes `sipsa.ingestion.duration` and `sipsa.ingestion.records`. ✅ (TECH-032)
- Metrics have a `method` tag with the SOAP method name. ✅ (TECH-032)
- `upsertFallbackBatch()` executes exactly 1 SELECT query per batch regardless of batch size. (TECH-060, implemented on its branch, not yet merged)

**Criterion to start Phase 5:** Phase 4 merge to `main` is complete. TECH-010 (SPIKE) is resolved.

---

## Phase 5 — Data Integrity

**Objective:** Resolve the `SipsaParcial` deduplication issue.

**Status (2026-07-16): essentially complete.** TECH-010 and TECH-011 are **Done**
(branch `fix/sipsa-parcial-data-integrity`, validated with three real DANE ingestions on
Docker Compose); ADR-001 is `Accepted`. TECH-012's local half is complete; its only
remaining item is confirming with the data owner whether an external historical database
exists (if so, TECH-115 activates).

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-010 | SPIKE: Define natural deduplication key for Parcial — **Done** (2026-07-16) | SPIKE | `fix/sipsa-parcial-data-integrity` |
| TECH-012 | SPIKE: Verify `sipsa_parcial` growth — **local half done**; external half conditional | SPIKE | (read-only script + runbook) |
| TECH-011 | Implement correct deduplication for Parcial — **Done** (2026-07-16) | Correctiva | `fix/sipsa-parcial-data-integrity` |

**Acceptance criteria for phase exit:**
- Two consecutive runs of `promediosSipsaParcial` (same window key) produce zero new records on the second run.
- `computeKeyHash()` returns the same value for the same business inputs.
- `UpsertMetrics.skipped > 0` on the second run.
- `./mvnw clean verify` passes.
- If production data contained duplicates: migration plan documented and applied.

**Follow-on track (outside the original phase count, complete as of 2026-07-19):**
TECH-011's final review and subsequent operation of the ingestion pipeline surfaced a
cluster of related stories, all **Done** and tracked in the backlog rather than as new
roadmap phases: TECH-113 (`artiId`/`muniId` filter fix), TECH-114 (strict `enmaFecha`
parsing, folded into TECH-011), TECH-117 (concurrent-insertion safety via
`ON CONFLICT DO NOTHING`), TECH-118/TECH-134 (decimal precision alignment across all
five SIPSA price models), TECH-119 (redundant index removal), TECH-124 (article-filter
covering index), TECH-133 (monthly window config centralization), and TECH-135/TECH-136
(rejection-threshold and async-executor config centralization, closing C-04/C-05). None
of these required a schema rollback; the only new migrations were V2 (TECH-011), V3
(TECH-119) and V4 (TECH-124) — V1 was never modified.

---

## Phase 6 — Documentation

**Objective:** Write ADRs and evaluate integration test strategy.
This phase can run in parallel with any of the above phases.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-044 | SPIKE: Evaluate WireMock/Testcontainers for integration tests | SPIKE | `spike/integration-test-strategy` |
| TECH-055 | SPIKE: `isMonthly()` in `IngestionHandler` contract | SPIKE | `spike/ingestion-handler-contract` |
| TECH-080 | Write ADR-002 (security) after TECH-001 — **Done** (2026-07-15, PR #17; ADR-002 Accepted, documented with the implementation) | Documentation | `fix/internal-endpoint-security` |
| TECH-081 | Write ADR-001 (deduplication) after TECH-010 | Documentation | `docs/architecture-decisions` |

---

## Roadmap Summary

```
main (post-migration)
│
├── Phase 1 — Foundation Cleanup          [Done, 2026-07-19]
│   Security (TECH-001/002, PR #17) + all 8 remaining stories done — TECH-071
│   (2026-07-16), TECH-030 (resolved by TECH-136), TECH-020/031/050/051/052/070
│   (all 2026-07-19, one story per branch, merged directly to main)
│   Duration: complete
│
├── Phase 2 — Contract and Correctness    [Unblocked — Phase 1 complete; not started]
│   Error semantics + Scheduler + Pagination
│   Duration estimate: 3–4 days
│
├── Phase 3 — Testing                     [After Phase 2; partially done ahead of sequence]
│   Unit tests for critical logic
│   TECH-040/TECH-110 done (2026-07-13); TECH-111 done (2026-07-14, PR #15);
│   TECH-041/042/043 remain
│   Duration estimate: 3–5 days
│
├── Phase 4 — Observability + Performance [Started ahead of sequence]
│   Metrics (TECH-032) — Done, 2026-07-20, merged to main.
│   N+1 fix (TECH-060) — implemented and pushed, not yet merged.
│   Duration estimate: 2–3 days
│
└── Phase 5 — Data Integrity              [Done, 2026-07-16; follow-on track done 2026-07-19]
    Parcial deduplication (TECH-010/011/012 local half) plus the TECH-113..136
    follow-on track (filters, concurrency, precision, indexing, config)
    Duration estimate: — (complete)

Phase 6 — Documentation [Parallel]
ADRs + SPIKE evaluations — TECH-080/081 done; TECH-044/055 remain
Duration estimate: ongoing
```

---

## Stories Explicitly Not in This Roadmap

The following were analyzed and rejected for this roadmap. See [Refactoring Roadmap](refactoring-roadmap.md).

- RF-01: Moving DTOs between packages (a narrow, 3-class slice was later accepted and implemented via ADR-007/TECH-090 — see the Parallel Track above; the general idea remains rejected)
- RF-02: Splitting `IngestionControlService`
- RF-03: Feature-based package reorganization
- RF-04: Moving exceptions to different layers
- RF-05: Eliminating `AuditTrailService`
- RF-06: Moving `batchUpsert` out of repositories (evaluated during TECH-011)
- RF-07: Replacing `WindowViolationException` with return value
- RF-08: Refactoring `ThreadLocal` in `TimezoneUtil`
- RF-09: Adopting RFC 9457 `ProblemDetail`
- RF-10: Adopting DDD tactical patterns

---

## Implementation Process

For every story in this roadmap, follow these steps in order:

```
1. git switch main && git pull
2. git switch -c <branch-from-story>
3. Implement the minimal change (one story per branch)
4. Write or update tests for changed logic
5. ./mvnw clean verify  ← must pass before proceeding
6. Update documentation:
     docs/backlog/technical-backlog.md  → mark story Done
     CHANGELOG.md                       → add entry to [Unreleased]
     Related ADR                        → update status if applicable
     This roadmap                       → update phase status if complete
7. git push origin <branch>
8. Open a Pull Request using the PR template
9. Reference the story ID in the PR title and description
```

Do not combine stories from different phases in a single PR unless they share the same branch
(e.g., TECH-050 and TECH-051 in `fix/cleanup-placeholder-comments`).

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for the full development guide.
