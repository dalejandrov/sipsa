# Implementation Roadmap — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13

This document defines the phased implementation plan for all technical backlog items.
Each phase is designed to be self-contained: the system remains consistent and deployable
at the end of every phase.

---

## Prerequisites

Before starting any phase:

- Branch: all work starts from `main` (after `chore/migrate-spring-boot-4-java-25` is merged).
- `./mvnw clean verify` must pass before starting and after completing each story.
- No story is marked complete unless its acceptance criteria are verified.
- Read the [Contributing Guide](../../CONTRIBUTING.md) before opening a PR.

---

## ► Next Step: Phase 1

**Phase 1 is the next phase to implement.** All prerequisites are met:
- `chore/migrate-spring-boot-4-java-25` is ready for review and merge.
- `./mvnw clean verify` passes (1 test, 0 failures).
- No Phase 1 stories are blocked by pending ADR decisions.

---

## Phase 1 — Foundation Cleanup

**Objective:** Fix bugs, enforce minimum security, and remove low-risk debt.
No behavioral changes to the public API. No new dependencies (except Spring Security).

**Can start:** Immediately after migration branch is merged to `main`.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-001 | Protect `/api/internal/**` with authentication | Security | `fix/internal-endpoint-security` |
| TECH-002 | Restrict Actuator `loggers` endpoint | Security | `fix/internal-endpoint-security` |
| TECH-020 | Fix `@RequestMapping` without leading `/` | Bug | `fix/request-mapping-leading-slash` |
| TECH-030 | Named executor in `@Async` for audit logging | Bug | `fix/async-executor-audit` |
| TECH-031 | Externalize `SipsaHealthIndicator` thresholds | Config | `refactor/health-indicator-config` |
| TECH-050 | Remove placeholder comments from 4 handlers | QA | `fix/cleanup-placeholder-comments` |
| TECH-051 | Rename `toAuditEventRequest` → `toAuditEventResponse` | QA | `fix/cleanup-placeholder-comments` |
| TECH-052 | `IngestionControlService.getRun()` → `Optional` | QA | `refactor/optional-return-types` |
| TECH-070 | Bean Validation constraints on `SoapProperties` | Config | `refactor/config-validation` |
| TECH-071 | Align `batch-size` defaults | Config | `refactor/config-validation` |

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes.
- `POST /api/internal/ingestion/run` returns `401` without credentials.
- `GET /api/sipsa/ciudad` returns `200` without credentials.
- `GET /actuator/health` returns `200` without credentials.
- Zero `// ...existing code...` comments in `src/main/`.
- No breaking changes to the public API contract.

**Criterion to start Phase 2:** Phase 1 merge to `main` is complete and green.

---

## Phase 2 — Contract and Correctness

**Objective:** Fix HTTP semantics, improve error responses, and make the scheduler non-blocking.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-021 | `SipsaParseException` → HTTP 502 | Correctiva | `fix/error-http-semantics` |
| TECH-022 | Introduce `SipsaNotFoundException` → HTTP 404 | Correctiva | `fix/error-http-semantics` |
| TECH-023 | Add `requestId` and `instance` to error responses | Observability | `feat/error-correlation-id` |
| TECH-053 | Make scheduler dispatch async | Correctiva | `fix/scheduler-async-execution` |
| TECH-054 | Add pagination to `GET /api/internal/ingestion/runs` | Correctiva | `fix/runs-endpoint-pagination` |

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes.
- `GET /api/internal/ingestion/runs/99999` returns `404`.
- A mock test confirms `SipsaParseException` produces HTTP `502`.
- Scheduler thread `runDailyWindow()` returns in under 200ms.
- `GET /api/internal/ingestion/runs` accepts `page` and `size` parameters.

**Notes:**
- TECH-021 and TECH-022 change HTTP status codes. Ensure any existing client code or monitoring
  alerts that depend on `422` or `400` for these paths are updated.
- TECH-023 adds fields to `ErrorResponse` but does not remove any existing fields (backward compatible).

**Criterion to start Phase 3:** Phase 2 merge to `main` is complete and green.

---

## Phase 3 — Testing

**Objective:** Establish a minimum test suite for critical business logic.
These tests must exist before Phase 4 (observability) and Phase 5 (data integrity) because
those phases modify production code that needs a safety net.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-040 | Unit tests for `WindowPolicy` | Testing | `test/window-policy` |
| TECH-041 | Unit tests for `SpecificationBuilder` | Testing | `test/specification-builder` |
| TECH-042 | Unit tests for `IngestionJob` | Testing | `test/ingestion-job` |
| TECH-043 | Tests for `GlobalExceptionHandler` | Testing | `test/exception-handler` |

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes with ≥ 30 unit tests.
- `WindowPolicy`: ≥ 8 test cases covering daily/monthly, force=true, timezone.
- `SpecificationBuilder`: ≥ 7 test cases covering all filter combinations.
- `IngestionJob`: ≥ 7 test cases covering all execution paths.
- `GlobalExceptionHandler`: one test per exception handler.
- No tests depend on a running database or network connection.

**Criterion to start Phase 4:** Phase 3 merge to `main` is complete; ≥ 30 tests pass.

---

## Phase 4 — Observability and Performance

**Objective:** Instrument the system for production diagnosis and fix the N+1 query issue.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-032 | Add Micrometer metrics for ingestion | Observability | `feat/ingestion-metrics` |
| TECH-060 | Fix N+1 in `upsertFallbackBatch` | Performance | `fix/batch-upsert-n-plus-one` |

**Acceptance criteria for phase exit:**
- `./mvnw clean verify` passes.
- `GET /actuator/metrics` includes `sipsa.ingestion.duration` and `sipsa.ingestion.records`.
- Metrics have a `method` tag with the SOAP method name.
- `upsertFallbackBatch()` executes exactly 1 SELECT query per batch regardless of batch size.

**Criterion to start Phase 5:** Phase 4 merge to `main` is complete. TECH-010 (SPIKE) is resolved.

---

## Phase 5 — Data Integrity

**Objective:** Resolve the `SipsaParcial` deduplication issue.

**This phase is BLOCKED until:**
1. `TECH-010` (SPIKE) is complete — business decision on the natural deduplication key.
2. `TECH-012` (SPIKE) is complete — production data volume is assessed.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-010 | SPIKE: Define natural deduplication key for Parcial | SPIKE | `spike/parcial-deduplication` |
| TECH-012 | SPIKE: Verify `sipsa_parcial` growth in production | SPIKE | (SQL-only, no branch) |
| TECH-011 | Implement correct deduplication for Parcial | Correctiva | `fix/parcial-data-integrity` |

**Acceptance criteria for phase exit:**
- Two consecutive runs of `promediosSipsaParcial` (same window key) produce zero new records on the second run.
- `computeKeyHash()` returns the same value for the same business inputs.
- `UpsertMetrics.skipped > 0` on the second run.
- `./mvnw clean verify` passes.
- If production data contained duplicates: migration plan documented and applied.

---

## Phase 6 — Documentation

**Objective:** Write ADRs and evaluate integration test strategy.
This phase can run in parallel with any of the above phases.

| Story | Title | Type | Branch |
|---|---|---|---|
| TECH-044 | SPIKE: Evaluate WireMock/Testcontainers for integration tests | SPIKE | `spike/integration-test-strategy` |
| TECH-055 | SPIKE: `isMonthly()` in `IngestionHandler` contract | SPIKE | `spike/ingestion-handler-contract` |
| TECH-080 | Write ADR-002 (security) after TECH-001 | Documentation | `docs/architecture-decisions` |
| TECH-081 | Write ADR-001 (deduplication) after TECH-010 | Documentation | `docs/architecture-decisions` |

---

## Roadmap Summary

```
main (post-migration)
│
├── Phase 1 — Foundation Cleanup          [Can start now]
│   Security + Bugs + QA + Config
│   Duration estimate: 2–3 days
│
├── Phase 2 — Contract and Correctness    [After Phase 1]
│   Error semantics + Scheduler + Pagination
│   Duration estimate: 3–4 days
│
├── Phase 3 — Testing                     [After Phase 2]
│   Unit tests for critical logic
│   Duration estimate: 3–5 days
│
├── Phase 4 — Observability + Performance [After Phase 3]
│   Metrics + N+1 fix
│   Duration estimate: 2–3 days
│
└── Phase 5 — Data Integrity              [Blocked: needs SPIKE]
    Parcial deduplication
    Duration estimate: 1–2 days (after decision)

Phase 6 — Documentation [Parallel]
ADRs + SPIKE evaluations
Duration estimate: ongoing
```

---

## Stories Explicitly Not in This Roadmap

The following were analyzed and rejected for this roadmap. See [Refactoring Roadmap](refactoring-roadmap.md).

- RF-01: Moving DTOs between packages
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
