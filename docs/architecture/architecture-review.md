# Architecture Review — SIPSA Integration Service

**Version:** 1.0  
**Date:** 2026-07-13  
**Branch at review time:** `chore/migrate-spring-boot-4-java-25`  
**Reviewer:** Architectural audit (automated, assisted by Claude Sonnet 4.6)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Inventory](#system-inventory)
3. [Architecture Map](#architecture-map)
4. [Transaction Boundaries](#transaction-boundaries)
5. [Accepted Findings](#accepted-findings)
6. [Discarded Recommendations](#discarded-recommendations)
7. [Pending Decisions](#pending-decisions)
8. [Known Risks](#known-risks)
9. [Accepted Risks](#accepted-risks)
10. [Domain Model Assessment](#domain-model-assessment)
11. [Evidence Methodology](#evidence-methodology)

---

## Executive Summary

**Verdict:** *Acceptable architecture with technical debt.*

The SIPSA Integration Service is a well-structured Spring Boot application that wraps a SOAP web service (DANE's SIPSA) behind a REST API. The core ingestion pipeline is well-designed. The streaming SOAP approach (native Java `HttpClient` + StAX), the Template Method + Strategy combination for ingestion handlers, and the transactional isolation of audit events are all solid engineering decisions.

**Strengths:**
- Memory-efficient streaming SOAP processing (619K+ records without OOM).
- Idempotent ingestion via window keys and unique constraints per method/window pair.
- Clean layering between REST, application, domain, and infrastructure.
- Resilient transaction boundaries: audit and metrics persist even when ingestion fails.
- MDC correlation (`runId`, `requestId`, `method`) throughout the ingestion pipeline.

**Critical gaps:**
1. Internal endpoints (`/api/internal/**`) have no authentication or authorization.
2. `SipsaParcial` data is duplicated on every ingestion run (UUID-based deduplication is non-deterministic — **unverified in production**, requires investigation).
3. No unit or integration tests beyond a single context-load test.
4. `SipsaParseException` is mapped to HTTP 400 when the XML comes from DANE (should be 502).

**Complexity assessment:** Appropriate for the problem. No significant over-engineering detected.

---

## System Inventory

### Controllers (REST API Layer)

| Class | Route prefix | Visibility |
|---|---|---|
| `SipsaRestController` | `/api/sipsa` | Public |
| `SipsaOpsController` | `/api/internal/ingestion` | Internal (unprotected) |
| `IngestionAuditController` | `/api/internal/audit` | Internal (unprotected) |

### Application Services

| Class | Responsibility |
|---|---|
| `SipsaReadService` | Paginated query with JPA Specification filtering |
| `IngestionTriggerService` | Orchestrates manual ingestion trigger + audit |
| `AsyncIngestionService` | `@Async` wrapper for the ingestion job |
| `IngestionControlService` | Run lifecycle: create, update, cancel, query |
| `IngestionRunQueryService` | Read-only run queries + DTO mapping |
| `IngestionAuditService` | Audit event persistence (async + sync) |
| `AuditTrailService` | Audit trail aggregation for HTTP responses |
| `IngestionService` | Handler registry and dispatcher |

### Ingestion Pipeline

| Class | Role |
|---|---|
| `IngestionJob` (abstract) | Template method: window → run → handler → finalize |
| `GenericIngestionJob` | Concrete implementation; delegates to `IngestionService` |
| `WindowPolicy` | Validates execution windows; generates window keys |
| `IngestionContext` | Mutable state carrier for a single run (metrics, rejects) |
| `IngestionHandler` (interface) | Strategy contract for each SOAP data source |
| `CiudadIngestionHandler` | promediosSipsaCiudad |
| `ParcialIngestionHandler` | promediosSipsaParcial |
| `SemanaIngestionHandler` | promediosSipsaSemanaMadr |
| `MesIngestionHandler` | promediosSipsaMesMadr |
| `AbasIngestionHandler` | promedioAbasSipsaMesMadr |
| `SipsaIngestionScheduler` | Cron-based trigger for all methods |

### Infrastructure — SOAP

| Class | Role |
|---|---|
| `SoapGateway` (interface) | Domain contract for SOAP access |
| `SoapGatewayImpl` | JAXB marshalling + HTTP delegation |
| `SoapStreamingClient` | Native Java `HttpClient` streaming with retry |
| `AbstractStaxParser<T>` | Template for StAX record parsing |
| `CiudadStaxParser`, etc. | Field-level XML parsing per data type |
| `SipsaIngestionMapper` | MapStruct: SOAP DTO → JPA entity |
| `SipsaSoapClientConfig` | CXF client configuration (timeout, XML limits) |

### Infrastructure — Persistence

| Repository | Entity | Deduplication strategy |
|---|---|---|
| `SipsaCiudadRepository` | `SipsaCiudad` | Bulk key lookup on `(regId, codProducto)` |
| `SipsaParcialRepository` | `SipsaParcial` | **None** (see finding F-DATA-01) |
| `SipsaMayoristasSemanalRepository` | `SipsaMayoristasSemanal` | tmpId-based or `(artiId, fuenId, fechaIni)` |
| `SipsaMayoristasMensualRepository` | `SipsaMayoristasMensual` | tmpId-based or `(artiId, fuenId, fechaMesIni)` |
| `SipsaAbastecimientosMensualRepository` | `SipsaAbastecimientosMensual` | tmpId-based or `(artiId, fuenId, fechaMesIni)` |
| `IngestionRunRepository` | `IngestionRun` | Unique on `(method_name, window_key)` |
| `IngestionAuditRepository` | `IngestionAudit` | Append-only |
| `IngestionRejectRepository` | `IngestionReject` | Append-only |

### Domain Entities

`SipsaCiudad`, `SipsaParcial`, `SipsaMayoristasSemanal`, `SipsaMayoristasMensual`, `SipsaAbastecimientosMensual`, `IngestionRun`, `IngestionAudit`, `IngestionReject`.

**Domain assessment:** This is primarily a transactional integration system, not a domain-rich application. Entities are data containers backed by SOAP source records. There are no Aggregate Roots in the DDD sense, no domain events, and no complex invariants enforced at the entity level. This is appropriate for the problem: the system's value is in reliable ingestion, not in domain modeling.

---

## Architecture Map

```
┌─────────────────────────────────────────────────────────────────┐
│  REST API Layer                                                  │
│  SipsaRestController  SipsaOpsController  IngestionAuditController
│  GlobalExceptionHandler  TimezoneFilter                         │
└──────────────────────────┬──────────────────────────────────────┘
                           │ delegates (no direct repo access)
┌──────────────────────────▼──────────────────────────────────────┐
│  Application Layer                                               │
│  SipsaReadService  IngestionTriggerService  AsyncIngestionService│
│  IngestionControlService  IngestionRunQueryService               │
│  IngestionAuditService  AuditTrailService  IngestionService      │
│  IngestionJob (abstract)  GenericIngestionJob                    │
│  WindowPolicy  IngestionContext  Handlers (5)  Scheduler         │
└──────────────────────────┬──────────────────────────────────────┘
                           │ implements gateway; accesses entities
┌──────────────────────────▼──────────────────────────────────────┐
│  Domain Layer                                                    │
│  Entities (8)  Exceptions (7)  SoapGateway (interface)          │
└──────────────────────────┬──────────────────────────────────────┘
                           │ implements
┌──────────────────────────▼──────────────────────────────────────┐
│  Infrastructure Layer                                            │
│  SoapGatewayImpl  SoapStreamingClient  AbstractStaxParser        │
│  SipsaIngestionMapper  Repositories (8)  SpecificationBuilder    │
│  PaginationConfig  AsyncConfig  SchedulingConfig  TimezoneFilter │
│  SipsaSoapClientConfig  SipsaHealthIndicator                     │
└─────────────────────────────────────────────────────────────────┘
```

**Dependency violations confirmed at review time** (compile-time, not just style):

| Source (application layer) | Imports from (api layer) | Impact |
|---|---|---|
| `IngestionJob.java` | `api.dto.request.AuditEventRequest` | App core depends on HTTP layer |
| `IngestionJob.java` | `api.dto.request.CreateRunRequest` | App core depends on HTTP layer |
| `IngestionJob.java` | `api.dto.request.IngestionRequest` | App core depends on HTTP layer |
| `IngestionControlService.java` | `api.dto.request.CreateRunRequest` | App service depends on HTTP layer |
| `IngestionTriggerService.java` | `api.dto.request.AuditEventRequest` | App service depends on HTTP layer |
| `SipsaReadService.java` | `api.dto.request.*`, `api.dto.response.*`, `api.mapper.*` | App service depends on HTTP layer |

**Resolution update (2026-07-13):** [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md)
(TECH-090, merged to `main`) moved `IngestionRequest`, `CreateRunRequest`, and
`AuditEventRequest` to `application/command`, eliminating every import above that involved
those three classes. Verified on `main`: exactly 5 `application` files still import from
`api` (`IngestionTriggerService`, `SipsaReadService`, `IngestionRunQueryService`,
`IngestionAuditService`, `AuditTrailService`) — all investigated and explicitly accepted by
ADR-007 (genuine HTTP DTOs and the deliberate read-service → response-mapper pattern). See
the package-rules table in [Project Architecture](project-architecture.md#package-organization)
for the current, authoritative state. The table above is retained as the historical
finding that motivated ADR-007.

---

## Transaction Boundaries

```
REST Controller
    │  [no transaction]
    ▼
IngestionTriggerService.triggerIngestion()
    │  [no transaction]
    │  → auditService.logEventSync()  [@Transactional REQUIRES_NEW] ← commits immediately
    │  → asyncIngestionService.executeAsync()  [@Async "ingestionTaskExecutor"]
              │  [new thread — no active transaction]
              ▼
         GenericIngestionJob.execute(request)
              │  [no transaction — intentional]
              │
              ├─ controlService.createRun()         [@Transactional REQUIRES_NEW] ← commits
              ├─ auditService.logEvent()             [@Async + REQUIRES_NEW] ← async, commits
              ├─ controlService.updateStatus(RUNNING)[@Transactional REQUIRES_NEW] ← commits
              ├─ auditService.logEvent()             [@Async + REQUIRES_NEW] ← async, commits
              │
              ├─ handler.execute(context)            [no transaction]
              │       │
              │       ├─ soapGateway.getCiudadData() [REMOTE CALL — outside any transaction ✓]
              │       │
              │       └─ repository.batchUpsert()   [@Transactional — own transaction per batch]
              │              [commits per batch — partial progress visible between batches]
              │
              ├─ controlService.isRunCanceled()      [@Transactional readOnly]
              │
              ├─ [on success] controlService.updateStatus(SUCCEEDED) [@Transactional REQUIRES_NEW]
              ├─ [on success] auditService.logEvent()                [@Async + REQUIRES_NEW]
              │
              └─ [finally]
                    controlService.updateMetrics()   [@Transactional REQUIRES_NEW] ← always commits
                    controlService.logReject() x N   [@Transactional REQUIRES_NEW] ← always commits
                    auditService.logEvent()          [@Async + REQUIRES_NEW]
```

**Key observations (facts, not inferences):**

1. **No SOAP call inside a database transaction.** The remote call in `handler.execute()` runs outside any transaction context. This is correct.

2. **Partial progress is intentional and visible.** Each `batchUpsert()` commits its own transaction. A failure after batch 3 of 10 leaves 3 batches persisted. The run is marked FAILED. A force-restart will re-ingest from scratch (no checkpointing). This is a documented design choice.

3. **`@Async` without executor name in `logEvent()`.** `IngestionAuditService.logEvent()` uses `@Async` without specifying `"ingestionTaskExecutor"`. Spring uses `SimpleAsyncTaskExecutor` by default (creates a new thread per invocation). See TECH-030.

4. **`REQUIRES_NEW` correctness.** Because all `REQUIRES_NEW` methods are called from a thread with no active transaction (the async ingestion thread), the `REQUIRES_NEW` propagation creates a new transaction each time. There is no suspended outer transaction. Self-invocation is not present (all calls cross Spring proxy boundaries). The behavior is correct.

5. **Audit event loss on JVM crash.** If the JVM terminates while async audit events are queued, those events are lost. This is a known limitation of the `@Async` approach.

---

## Accepted Findings

### Security

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-SEC-01 | No authentication on `/api/internal/**` | **Critical** | No `spring-security-web` in `dependency:tree`; `TODO` comment in `SipsaOpsController.java:33` |
| F-SEC-02 | Actuator `loggers` endpoint publicly accessible | **High** | `application.yaml:150` — `include: loggers` |

### Data Integrity

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-DATA-01 | `SipsaParcial` deduplication is non-functional | **High (unverified in prod)** | `SipsaIngestionMapper.java:164`: `UUID.randomUUID()` as key hash; `SipsaParcialRepository.java:53`: `skipped always 0`; `key_hash UNIQUE` constraint never triggered |
| F-DATA-02 | N+1 query in `upsertFallbackBatch` | **Medium** | `SipsaMayoristasSemanalRepository.java:148`: `findByBusinessKeys()` called per item in a loop |

### Error Handling

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-ERR-01 | `SipsaParseException` mapped to HTTP 400 | **Medium** | `GlobalExceptionHandler.java:117`; XML comes from DANE, not from API client |
| F-ERR-02 | "Not found" cases return HTTP 422 | **Medium** | `IngestionRunQueryService.java:90`: `SipsaBusinessException` for not-found |
| F-ERR-03 | No `requestId`/`instance` in error responses | **Low** | `GlobalExceptionHandler.java:324`: `ErrorResponse` lacks correlation fields |

### Code Quality

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-QA-01 | `@RequestMapping` without leading `/` on 2 controllers | **Bug** | `SipsaOpsController.java:36`; `IngestionAuditController.java:33` |
| F-QA-02 | `// ...existing code...` placeholders in 4 production handlers | **Low** | `CiudadIngestionHandler.java:115`; `SemanaIngestionHandler.java:103`; `AbasIngestionHandler.java:123`; `MesIngestionHandler.java:109` |
| F-QA-03 | `IngestionAuditMapper.toAuditEventRequest()` returns response type | **Low** | `IngestionAuditMapper.java:36`: method name says "Request", returns `AuditEventResponse` |
| F-QA-04 | `IngestionControlService.getRun()` returns null | **Low** | `IngestionControlService.java:261`: `orElse(null)` |
| F-QA-05 | `batch-size` default mismatch between `@Value` (2000) and `application.yaml` (500) | **Low** | `CiudadIngestionHandler.java:55` vs `application.yaml:109` |
| F-QA-06 | `AuditTrailService.queryAuditEvents()` builds its own Pageable | **Low** | `AuditTrailService.java:127`: ignores `PaginationConfig.buildPageable()` |

### Configuration

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-CFG-01 | `SoapProperties` has no Bean Validation constraints | **Low** | `SoapProperties.java:47`: no `@Validated`, `@NotBlank`, `@Min` |
| F-CFG-02 | `SipsaHealthIndicator` thresholds are hardcoded | **Low** | `SipsaHealthIndicator.java:107,112`: magic numbers 36 and 35*24 |

### Concurrency

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-CONC-01 | Scheduler blocks one of its 5 threads during full ingestione | **Medium** | `SipsaIngestionScheduler.java:130`: synchronous `ingestionJob.execute(request)` |
| F-CONC-02 | `@Async` in `IngestionAuditService.logEvent()` uses default executor | **Low** | `IngestionAuditService.java:67`: `@Async` without executor name |

### Observability

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-OBS-01 | No custom Micrometer metrics for ingestion | **Medium** | `grep -rn "MeterRegistry\|@Timed" src/` → zero results |
| F-OBS-02 | `GET /api/internal/ingestion/runs` is unbounded | **Low** | `IngestionRunQueryService.java:68`: `findAllRuns()` without Pageable |

### Testing

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-TEST-01 | Single context load test; no business logic coverage | **High** — **Resolved as stated** (2026-07-13): TECH-110/TECH-040 added 64 tests (65 total across 7 classes) covering `WindowPolicy`, the scheduler, and the cron expressions. Remaining gaps (`SpecificationBuilder`, `IngestionJob`, `GlobalExceptionHandler`, integration) stay tracked as T-02..T-05 / TECH-041/042/043/044. | `find src/test -name "*.java"` → 1 file at review time: `SipsaApplicationTests.java` |

### Resilience (accepted, no action planned)

| ID | Finding | Severity | Evidence |
|---|---|---|---|
| F-RES-01 | `resilience4j-spring-boot3:2.3.0` on classpath under Spring Boot 4 | **Informative** | `dependency:tree`; health autoconfig silently skipped by `@ConditionalOnClass` |

---

## Discarded Recommendations

The following were analyzed and explicitly rejected. They must not be implemented without a new architectural review.

| Recommendation | Reason for rejection |
|---|---|
| Move `IngestionRequest`, `CreateRunRequest`, `AuditEventRequest` from `api/dto/request/` to `application/dto/` | Functional today. Moving would require updating 15+ files. No bug is introduced by the current placement. The violation is real but the cost/benefit does not justify the change at this scale. **Superseded (2026-07-13):** [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md) re-verified this and accepted a narrower version — exactly the 3 named classes (~9 files touched, not 15+) moved to `application/command` (TECH-090, merged). The general "move all internal DTOs" idea remains rejected; see [Refactoring Roadmap RF-01](refactoring-roadmap.md#rf-01--move-internal-dtos-from-apidtorequest-to-applicationdto). |
| Split `IngestionControlService` | 340 lines with cohesive responsibilities (run lifecycle). No evidence of friction caused by its size. Splitting would increase indirection without removing complexity. |
| Replace `WindowViolationException` with a value return | The exception is caught immediately in `IngestionJob.execute()` and never propagates to an HTTP layer. The change would be invasive in the central pipeline with no functional improvement. |
| Move `SipsaExternalException`, `SipsaParseException` to `infrastructure` | These exceptions are referenced by domain-layer gateway contracts and application services. Moving them would create a dependency from domain toward infrastructure, which is worse. |
| Eliminate `AuditTrailService` | It provides a meaningful separation between raw audit storage (`IngestionAuditService`) and HTTP response assembly (`AuditTrailService`). Merging would increase `IngestionAuditService` responsibilities. |
| Reorganize packages by feature | The current layer-based package structure is appropriate for this project size (8 domain types, single bounded context). Feature-based reorganization would have high migration cost with marginal navigability improvement. |
| Refactor `ThreadLocal` in `TimezoneUtil` | Correct for the current model (Spring MVC, one thread per request). Virtual threads in Java 25 support `ThreadLocal`. The only risk is reactive contexts, which this project does not use. |
| Adopt DDD tactical patterns (aggregates, domain events) | This is a transactional integration system. The domain model is deliberately thin. Adopting full DDD would add ceremony without improving the core value proposition of the system. |
| Adopt RFC 9457 `ProblemDetail` | The current `ErrorResponse` is consistent and functional. Adopting `ProblemDetail` adds Spring-managed fields but requires changes to the response contract that clients depend on. See [ADR-003](../adr/ADR-003-error-response-model.md). |

---

## Pending Decisions

These require business or product input before any implementation.

| Decision | Blocking | Context |
|---|---|---|
| What is the natural deduplication key for `SipsaParcial`? | TECH-010, TECH-011 | `computeKeyHash()` currently generates a random UUID. See [ADR-001](../adr/ADR-001-data-deduplication.md). |
| Should `IngestionHandler` declare `isMonthly()`? | TECH-055 | `WindowPolicy` currently infers daily/monthly from method name strings. See [ADR-006](../adr/ADR-006-ingestion-handler-contract.md). |
| What authentication mechanism for internal endpoints? | TECH-001 | Options: Basic Auth with env credentials, API key header, mTLS, network-level restriction. See [ADR-002](../adr/ADR-002-internal-endpoint-security.md). |

---

## Known Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| `sipsa_parcial` growing unboundedly from duplicate inserts | High (if system ran in production with the current code) | High (DB storage, query performance) | Investigate in TECH-012 before taking action |
| Unauthorized activation of ingestion against DANE's SOAP service | High (if system is reachable over network) | Medium (DANE rate limiting, data consistency) | Implement TECH-001 |
| Single scheduler thread blocked for 60+ minutes during Parcial ingestion | Medium (depends on volume and timing) | Low (other scheduled tasks unaffected for this run; subsequent same-cron runs may be skipped) | Implement TECH-053 |
| Audit events lost on JVM crash during async processing | Low (requires bad timing) | Low (audit trail incomplete, not data loss) | Acceptable risk; document in ADR-004 |
| Docker image `eclipse-temurin:25-jre-noble` not available in registry | Low | High (deployment fails) | Verify during next Docker-available build |

---

## Accepted Risks

| Risk | Justification |
|---|---|
| `resilience4j-spring-boot3:2.3.0` on Spring Boot 4 classpath | Health indicator autoconfiguration is silently skipped by `@ConditionalOnClass`. No runtime error. Project does not use `@CircuitBreaker` annotations. Impact is zero for current usage. |
| No circuit breaker protection for DANE SOAP calls | The service has manual retry with exponential backoff in `SoapStreamingClient`. Adding Resilience4j circuit breakers requires usage decisions. Accepted until needed. |
| `REQUIRES_NEW` audit events can be lost on JVM crash | The window between async dispatch and commit is milliseconds. Acceptable for operational audit (not compliance audit). |

---

## Evidence Methodology

All findings in this document are supported by one or more of the following evidence types:

- **Source code inspection:** File path, class name, method name, and approximate line number are provided.
- **Build tool:** `./mvnw dependency:tree`, `./mvnw clean verify`, `./mvnw clean compile -Dmaven.compiler.showDeprecation=true`.
- **Bytecode inspection:** `javap -verbose` and `jar tf` on dependency JARs to verify class presence and annotation values.
- **Web search:** Used to verify Spring Boot 4, Spring Cloud 2025.1.2, and Resilience4j version compatibility. Sources cited in migration notes.

Findings labeled **Inference** are clearly marked as hypotheses that require validation before action.

---

*This document is part of the technical documentation suite. See also:*
- *[Technical Debt Registry](technical-debt.md)*
- *[Refactoring Roadmap](refactoring-roadmap.md)*
- *[Implementation Roadmap](implementation-roadmap.md)*
- *[Testing Strategy](testing-strategy.md)*
- *[Technical Backlog](../backlog/technical-backlog.md)*
