# Refactoring Roadmap — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13

This document records all refactoring ideas considered during the architectural review,
including those **not selected for implementation**. The purpose is to preserve institutional
knowledge so that future developers can understand what was evaluated and when conditions
might justify revisiting a decision.

---

## Active Refactorings (in backlog)

These are approved and queued for implementation. See [Technical Backlog](../backlog/technical-backlog.md).

| Story | Description | Phase |
|---|---|---|
| TECH-020 | Fix `@RequestMapping` without leading `/` | Phase 1 |
| TECH-021 | `SipsaParseException` → HTTP 502 — **Done** (2026-07-19) | Phase 2 |
| TECH-022 | Introduce `SipsaNotFoundException` → HTTP 404 — **Done** (2026-07-19) | Phase 2 |
| TECH-023 | Add `requestId` and `instance` to `ErrorResponse` — **Done** (2026-07-19) | Phase 2 |
| TECH-030 | Named executor in `@Async` of `IngestionAuditService` — **Done**, resolved by [TECH-136](../backlog/technical-backlog.md#tech-136) (2026-07-19) | Phase 1 |
| TECH-050 | Remove placeholder comments from handlers | Phase 1 |
| TECH-051 | Rename `toAuditEventRequest` → `toAuditEventResponse` | Phase 1 |
| TECH-052 | `getRun()` returns `Optional<IngestionRun>` | Phase 1 |
| TECH-053 | Make scheduler dispatch async | Phase 2 |
| TECH-060 | Fix N+1 in `upsertFallbackBatch` | Phase 4 |

---

## Deferred Refactorings — Not Recommended Now

The following refactorings were analyzed in depth and explicitly **not included** in the active backlog.

---

### RF-01 — Move internal DTOs from `api/dto/request/` to `application/dto/`

**What:** Move `IngestionRequest`, `CreateRunRequest`, `AuditEventRequest` (used by application layer) out of the `api/dto/request/` package to a new `application/dto/` package.

**Why not now:**
- The violation is real but functional. No bugs result from the current placement.
- Moving would require updating imports in 15+ files across multiple layers with no behavioral change.
- Introduces regression risk during a period when testing coverage is near zero.
- The benefit (layer purity) is theoretical at this scale.

**When to reconsider:**
- When the application layer needs to be extracted as a separate module or library.
- When the team adds tests that enforce architectural constraints (ArchUnit rules — see [TECH-093](../backlog/technical-backlog.md#tech-093)).
- When the `api` package requires a breaking change that cannot be applied to the internal DTOs simultaneously.

**Risk of implementing:** Medium — many files changed, no functional improvement.
**Risk of not implementing:** Low — coupling is stable, no growth pressure.

**Status update (2026-07-13):** [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md)
partially reopened this item at class-level granularity and was **Accepted, scoped to this
narrow slice only**. Re-verification showed the "15+ files" estimate above was for the full
unstated scope of RF-01; the actual set of classes with **zero** HTTP usage is exactly the 3
named above, touching ~9 files. The broader question this RF-01 entry originally covered
(whether to also move `IngestionTriggerRequest`, `AuditQueryRequest`, or any
`*QueryRequest` class) was re-investigated and confirmed **not applicable** — those are
genuinely HTTP-bound and stay. See ADR-007 §F1 and
[TECH-090](../backlog/technical-backlog.md#tech-090) (**Done**, merged to `main`
2026-07-13). RF-01 as a general "move all internal DTOs" idea remains **not recommended**
— only the 3 named classes were approved, and that slice is now implemented.

---

### RF-02 — Divide `IngestionControlService` into smaller services

**What:** Split `IngestionControlService` (340 lines) into, for example:
- `IngestionRunService` — create, update, cancel.
- `IngestionMetricsService` — metrics and error logging.
- `IngestionRejectService` — rejected records persistence.

**Why not now:**
- All three responsibilities are tightly coupled (they operate on the same `IngestionRun` entity).
- Splitting would add indirection (3 services instead of 1) without removing complexity.
- No evidence that the current size causes practical issues (merge conflicts, comprehension difficulty).

**When to reconsider:**
- If new developers consistently report difficulty navigating the class.
- If the class grows beyond 500 lines due to new features.
- If separate testing of reject logic or metrics logic becomes a clear need.

**Risk of implementing:** Low-Medium — internal refactoring, but many call sites.
**Risk of not implementing:** Low — size is manageable.

---

### RF-03 — Reorganize packages by feature (feature-based structure)

**What:** Reorganize from layer-based (`api/`, `application/`, `domain/`, `infrastructure/`) to feature-based:
```
sipsa/ingestion/  sipsa/ciudad/  sipsa/audit/  sipsa/parcial/
```

**Why not now:**
- The project has a single bounded context with 5 closely related data types.
- Feature-based organization adds value at the scale of 5+ independent domains with diverging evolution.
- Migration cost: every package import in the project would change.
- No evidence that developers struggle to locate files in the current structure.

**When to reconsider:**
- If the system grows to include genuinely independent bounded contexts (e.g., DANE authentication, SLA tracking, external API gateway).
- If individual data types (Ciudad, Parcial) start having divergent infrastructure dependencies.

**Risk of implementing:** High — full reorganization.
**Risk of not implementing:** Very low — no current friction.

---

### RF-04 — Move exceptions between layers

**What:** Move `SipsaExternalException`, `SipsaParseException` from `domain/exception/` to `infrastructure/` or `application/`.

**Why not now:**
- `SipsaExternalException` is referenced by `SoapGateway` (domain interface) in its Javadoc and by domain-layer handlers.
- Moving it to `infrastructure` would create a dependency from `domain` toward `infrastructure` — worse than the current arrangement.
- `SipsaParseException` is thrown from `AbstractStaxParser` (infrastructure) but caught in `IngestionJob` (application) and handled in `GlobalExceptionHandler` (API). Any single destination for it would require cross-layer imports.
- The current placement in `domain` is a reasonable pragmatic choice.

**When to reconsider:**
- Never for `SipsaExternalException` (referenced by the domain gateway contract).
- For `SipsaParseException`: only if the SOAP parsing layer is extracted as a standalone module.

**Risk of implementing:** Medium — many files to update with no practical gain.
**Risk of not implementing:** Very low — works correctly.

---

### RF-05 — Eliminate `AuditTrailService` (merge into `IngestionAuditService`)

**What:** Remove `AuditTrailService` and move its DTO transformation + HTTP-facing methods into `IngestionAuditService`.

**Why not now:**
- `AuditTrailService` provides a meaningful separation: it handles HTTP response assembly (timezone conversion, pagination logic) while `IngestionAuditService` handles raw persistence.
- Merging would make `IngestionAuditService` aware of HTTP-layer DTOs, increasing its responsibilities.
- The current two-service design is intentional and proportional.

**When to reconsider:**
- If the system is simplified and HTTP-specific logic is moved elsewhere (e.g., to `AuditTrailController` directly).

---

### RF-06 — Refactor `batchUpsert` out of repository `default` methods

**What:** Move the deduplication and upsert logic from `default` methods in repository interfaces to a dedicated service class (e.g., `IngestionPersistenceService` or handler-level methods).

**Why not now:**
- The logic is tightly coupled to repository operations (needs access to `saveAll`, `flush`, `findByBusinessKeys`).
- Moving it to a `@Service` would require injecting the repositories into the service, which replicates the coupling.
- Using a `RepositoryCustom` implementation is a valid Spring Data pattern and would be the right approach if this is refactored.
- The `default` method approach works correctly for 4 of 5 repositories (the fifth, `SipsaParcial`, has a separate data integrity issue — see TECH-011).

**When to reconsider:**
- When implementing TECH-011 (Parcial deduplication), the new implementation should be evaluated for placement.
- If the team adopts ArchUnit and adds a rule prohibiting business logic in repositories.

---

### RF-07 — Replace `WindowViolationException` with a return value

**What:** Change `WindowPolicy.validateAndGetKey()` to return `Optional<String>` or a result type instead of throwing `WindowViolationException`.

**Why not now:**
- `WindowViolationException` is caught immediately by `IngestionJob.execute()` at the same call site. It does not propagate through layers.
- The change is a purely stylistic preference (exceptions as control flow is a valid criticism, but the scope is contained).
- Changing the method signature would require updating `GenericIngestionJob`, `WindowPolicy`, and tests.

**When to reconsider:**
- If `validateAndGetKey()` is called from additional contexts where exception-based control flow becomes problematic.

---

### RF-08 — Refactor `ThreadLocal` in `TimezoneUtil`

**What:** Replace the static `ThreadLocal<ZoneId>` with a request-scoped Spring bean or pass the timezone explicitly as a method parameter.

**Why not now:**
- The current implementation is correct for Spring MVC (one thread per request).
- Virtual threads in Java 25 support `ThreadLocal`. The risk is reactive contexts, which this project does not use.
- The `ThreadLocal` is initialized by `TimezoneFilter` and cleared in its `finally` block — correct lifecycle management.

**When to reconsider:**
- If the project adopts Spring WebFlux.
- If the project adopts reactive programming patterns where a single request may span multiple threads.

---

### RF-09 — Adopt RFC 9457 `ProblemDetail` for error responses

**What:** Replace the custom `ErrorResponse` record with Spring's built-in `ProblemDetail` class, which follows RFC 9457.

**Why not now:**
- The current error contract is consistent and documented. Clients (if any) already parse it.
- `ProblemDetail` requires different field names (`type`, `title`, `detail`) which would be a breaking change.
- Spring Boot 4's `ProblemDetail` requires configuration to enable; it is not the default.

**When to reconsider:**
- If the project develops an external API contract with clients that expect RFC 9457.
- If the team adds OpenAPI/Swagger documentation and wants standard error schema support.

See [ADR-003](../adr/ADR-003-error-response-model.md) for the formal decision.

---

### RF-10 — Adopt DDD tactical patterns

**What:** Introduce Aggregate Roots, Domain Events, Value Objects, and Domain Services following DDD patterns.

**Why not now:**
- SIPSA is a transactional integration system. The "domain" is essentially read/write of agricultural price data from an external source. There is no complex business logic that benefits from rich domain modeling.
- The entities are data containers that mirror the source system's structure.
- DDD tactical patterns add significant complexity (event handling, aggregate boundaries, repository patterns) that would not improve the system's reliability or maintainability.

**When to reconsider:**
- If the system evolves to include complex business rules (e.g., price anomaly detection, multi-source data fusion, custom analytics).
- If the team grows and domain ownership separation becomes necessary.

---

## Findings Under ADR-007 (2026-07-13, updated 2026-07-13)

A structural diagnosis on branch `refactor/package-structure-and-boundaries` found four
additional narrow package-boundary issues not previously documented here.
[ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md) is now **Accepted**,
but **scoped only to F1, F2, F4, F5** — F3 remains gated behind a SPIKE.

| ID | Finding | Backlog | Status | Risk |
|---|---|---|---|---|
| F1 | Internal ingestion commands (`IngestionRequest`, `CreateRunRequest`, `AuditEventRequest`) stored as HTTP `*Request` DTOs | [TECH-090](../backlog/technical-backlog.md#tech-090) | **Done** (`refactor/internal-models-and-api-filter`) | Low |
| F2 | `TimezoneFilter` (an HTTP request filter) is placed under `infrastructure/config` and is the only confirmed `infrastructure → api` import in the codebase | [TECH-091](../backlog/technical-backlog.md#tech-091) | **Done** (`refactor/internal-models-and-api-filter`) | Very low |
| F3 | CXF-generated SOAP stubs are generated into the same package as hand-written `SoapStreamingClient` | [TECH-092](../backlog/technical-backlog.md#tech-092) | **Ready** — unblocked by [TECH-094](../backlog/technical-backlog.md#tech-094) (`spike/evaluate-generated-soap-relocation`, 2026-07-20): Recommended, proceed with 1 scope correction (see [SPIKE report](spikes/TECH-094-generated-soap-relocation.md)) | Low (verified — diff-clean regeneration, green `verify` with 415/415 tests) |
| F4 | `domain/gateway/SoapGateway` imports `infrastructure.soap.gateway.SoapGatewayImpl` for a Javadoc `@see` tag only — the only confirmed `domain → infrastructure` import | [TECH-095](../backlog/technical-backlog.md#tech-095) | **Done** (`refactor/internal-models-and-api-filter`) | None |
| F5 | No ArchUnit tests exist to prevent regression on any of the above | [TECH-093](../backlog/technical-backlog.md#tech-093) | **Done** (`test/enforce-package-boundaries`) — 3 rules, all green against the post-F1/F2/F4 state | Low |

See ADR-007 for full evidence, cost, and the list of related items investigated and
explicitly **not** recommended (`SipsaReadService`/`IngestionRunQueryService`/`AuditTrailService`
using `api.mapper`/`api.dto.response`, and `TimezoneUtil`'s placement) — none of those are
authorized by this acceptance. TECH-093 (F5) encodes exactly this non-authorization: its
`application`-vs-`api` rule explicitly excludes those same three application services (plus
`IngestionTriggerService`/`IngestionAuditService`) rather than forbidding the pattern.

---

## Future Refactorings Summary

| ID | Refactoring | Decision | Revisit Condition |
|---|---|---|---|
| RF-01 | Move DTOs to `application/` | Deferred (narrow slice implemented via ADR-007 / TECH-090, merged) | ArchUnit rules or module extraction |
| RF-02 | Split `IngestionControlService` | Deferred | Class exceeds 500 lines or navigation issues |
| RF-03 | Feature-based package structure | Deferred | 5+ independent bounded contexts |
| RF-04 | Move exceptions between layers | **Not recommended** | Only if SOAP layer extracted as module |
| RF-05 | Eliminate `AuditTrailService` | **Not recommended** | Only if HTTP logic moves to controller |
| RF-06 | Move `batchUpsert` from repositories | Deferred | During TECH-011 implementation |
| RF-07 | Replace `WindowViolationException` | Deferred | Multiple call sites or async contexts |
| RF-08 | Refactor `ThreadLocal` | Deferred | WebFlux or reactive adoption |
| RF-09 | Adopt RFC 9457 `ProblemDetail` | Deferred | External API contract requirement |
| RF-10 | Adopt DDD tactical patterns | **Not recommended** | Only if domain complexity increases significantly |
