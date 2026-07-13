# ADR-000 — Current Architecture Snapshot

**Status:** Accepted (informational — not a decision record)  
**Date:** 2026-07-13  
**Stack:** Java 25, Spring Boot 4.1.0, PostgreSQL 18, Apache CXF 4.2.2

---

## Purpose

This document is not an architecture decision. It is a snapshot of the system as it exists
after the Spring Boot 4 + Java 25 migration (branch `chore/migrate-spring-boot-4-java-25`).

Its purpose is to provide immediate context for all subsequent ADRs, developers joining the
project, and AI assistants that need to understand the system without reading the full codebase.

---

## System Purpose

SIPSA Integration Service wraps DANE Colombia's SIPSA (Sistema de Información de Precios y
Abastecimiento del Sector Agropecuario) SOAP web service behind a REST API.

DANE publishes agricultural price and supply data daily and monthly through a SOAP endpoint.
This service:

1. Provides a REST API for querying the stored data with filtering, pagination, and timezone support.
2. Automatically ingests data from the SOAP service on a schedule aligned with DANE's publication times.
3. Allows manual ingestion triggers for operational use.
4. Maintains an audit trail of all ingestion activity.
5. Stores the data in a PostgreSQL database for persistent access.

The system's value is in **reliable, idempotent data ingestion** and **accessible REST querying**,
not in complex business logic.

---

## Technology Stack

| Component | Version | Role |
|---|---|---|
| Java | 25 (Temurin 25.0.3 LTS) | Runtime |
| Spring Boot | 4.1.0 | Application framework |
| Spring Framework | 7.x (managed) | Core framework |
| Spring Cloud | 2025.1.2 (Oakwood) | Circuit breaker BOM |
| Apache CXF | 4.2.2 | SOAP WSDL code generation |
| Hibernate | 7.4.1 Final (managed) | JPA / ORM |
| PostgreSQL | 18.x | Primary database |
| Flyway | managed by Boot | Schema migration |
| Jackson | 3.x (managed by Boot) | JSON serialization |
| MapStruct | 1.6.3 | Compile-time mappers |
| Micrometer + Prometheus | managed | Observability (metrics) |
| Maven | 3.9.9 (via wrapper) | Build tool |
| Docker | eclipse-temurin:25 | Containerization |

---

## Architecture Layers

The project follows a four-layer architecture by package:

```
com.dalejandrov.sipsa/
├── api/              ← HTTP layer: controllers, DTOs, mappers, utilities
├── application/      ← Business logic: services, ingestion pipeline, scheduler
├── domain/           ← Core concepts: entities, exceptions, gateway interface
└── infrastructure/   ← Technical: repositories, SOAP client, config, parsers
```

### Layer responsibilities

**`api/`** — HTTP boundary:
- REST controllers (`SipsaRestController`, `SipsaOpsController`, `IngestionAuditController`).
- Request/response DTOs (records with Bean Validation).
- MapStruct mappers for entity → response DTO conversion.
- `GlobalExceptionHandler` with structured error responses.
- `TimezoneFilter`: reads `X-Timezone` header; sets thread-local zone for response conversion.
- `PaginationUtils`: converts Spring Data `Page` to custom `ApiResponse` with navigation URLs.

**`application/`** — Orchestration and business rules:
- `SipsaReadService`: query with dynamic filters via `SpecificationBuilder`.
- `IngestionTriggerService`, `AsyncIngestionService`: trigger and dispatch ingestion.
- `IngestionJob` (abstract) + `GenericIngestionJob`: Template Method for the ingestion pipeline.
- Five `IngestionHandler` implementations: one per SOAP data source.
- `WindowPolicy`: validates execution time windows; generates idempotency keys.
- `IngestionControlService`: run lifecycle management (create, update status, cancel).
- `IngestionAuditService`: asynchronous audit event persistence.
- `SipsaIngestionScheduler`: cron-based triggers for all ingestion windows.

**`domain/`** — Core contracts:
- JPA entities: `SipsaCiudad`, `SipsaParcial`, `SipsaMayoristasSemanal`, `SipsaMayoristasMensual`, `SipsaAbastecimientosMensual`, `IngestionRun`, `IngestionAudit`, `IngestionReject`.
- Exception hierarchy: 7 exception types covering validation, business, infrastructure, and SOAP errors.
- `SoapGateway` interface: domain contract for SOAP data access, implemented in infrastructure.

**`infrastructure/`** — Technical implementations:
- 8 Spring Data JPA repositories (one per entity).
- `SoapGatewayImpl`: JAXB marshalling + HTTP streaming delegation.
- `SoapStreamingClient`: native Java `HttpClient` with retry and GZIP decompression.
- `AbstractStaxParser<T>` + 5 concrete parsers: StAX-based XML streaming for memory efficiency.
- `SipsaIngestionMapper`: MapStruct mapper from SOAP DTOs to JPA entities.
- `SpecificationBuilder<T>`: fluent JPA Specification API for dynamic query construction.
- Configuration classes: `AsyncConfig`, `SchedulingConfig`, `PaginationConfig`, `SipsaSoapClientConfig`.
- `SipsaHealthIndicator`: custom Actuator health check based on data freshness.

---

## Data Flow

### REST Query Flow

```
Client → GET /api/sipsa/{type} → SipsaRestController
    → SipsaReadService (validation + SpecificationBuilder)
    → Repository.findAll(Specification, Pageable)
    → Page<Entity> → MapStruct → Page<ResponseDTO>
    → ApiResponse (with pagination URLs) → 200 OK
```

### Ingestion Flow (scheduled)

```
Cron trigger → SipsaIngestionScheduler.runSafely()
    → ingestionJob.execute(request)         [synchronous — see ADR-005]
    → WindowPolicy.validateAndGetKey()       [check window; get idempotency key]
    → IngestionControlService.createRun()   [REQUIRES_NEW transaction]
    → IngestionAuditService.logEvent()      [async, REQUIRES_NEW]
    → handler.execute(context)
        → soapGateway.getData()              [HTTP streaming, no transaction]
        → StaxParser.next()                  [StAX record iteration]
        → Validate fields, addRejectedRecord if invalid
        → SipsaIngestionMapper.toEntity()
        → repository.batchUpsert()           [own transaction per batch]
    → IngestionControlService.updateStatus(SUCCEEDED) [REQUIRES_NEW]
    → [finally] updateMetrics + logReject × N [REQUIRES_NEW each]
```

See [ADR-004](ADR-004-transaction-boundaries.md) for the transaction model.

### Manual Trigger Flow

```
POST /api/internal/ingestion/run?method=X&force=false
    → SipsaOpsController → IngestionTriggerService
    → IngestionAuditService.logEventSync()  [synchronous, REQUIRES_NEW]
    → ingestionService.validateTriggerRequest()
    → asyncIngestionService.executeAsync()  [@Async "ingestionTaskExecutor"]
    → 202 Accepted (immediately)
    [background: GenericIngestionJob.execute() in thread pool]
```

---

## SOAP Integration

The DANE SIPSA SOAP service is consumed through a two-layer approach:

1. **JAXB + CXF codegen**: Apache CXF `wsdl2java` generates Java stubs from the WSDL at build time.
   These stubs are used only for request marshalling (JAXB → XML string).

2. **Native `HttpClient` streaming**: `SoapStreamingClient` sends the XML payload via Java's built-in
   `HttpClient` (HTTP/1.1) and returns the response as an `InputStream` without loading it into memory.

3. **StAX parsing**: Each `*StaxParser` processes the stream record-by-record, allowing processing of
   619,000+ records without memory issues.

This approach bypasses JAX-WS marshalling of responses, which would load entire datasets into memory.

---

## Persistence

**Database:** PostgreSQL 18 (single instance).

**Schema management:** Flyway. Production schema in `V1__initial_schema.sql`. No auto-DDL.

**Tables:**
- 5 data tables: `sipsa_ciudad`, `sipsa_parcial`, `sipsa_mayoristas_semanal`, `sipsa_mayoristas_mensual`, `sipsa_abastecimientos_mensual`.
- 3 operational tables: `ingestion_runs`, `ingestion_audit`, `ingestion_rejects`.

**Deduplication:**
- `sipsa_ciudad`: business key `(reg_id, cod_producto)`.
- `sipsa_mayoristas_semanal`, `sipsa_mayoristas_mensual`, `sipsa_abastecimientos_mensual`: tmpId when available, or composite business key.
- `sipsa_parcial`: **currently broken** — random UUID (see [ADR-001](ADR-001-data-deduplication.md)).
- `ingestion_runs`: unique constraint on `(method_name, window_key)`.

---

## Scheduler

Three cron schedules (all in `America/Bogota` timezone):

| Schedule | Methods | Time |
|---|---|---|
| Daily | Ciudad, Parcial, Semana | 14:20 COT |
| Monthly (day 8) | MesMadr | 14:30 COT |
| Monthly (day 10) | AbasMes | 14:30 COT |

Aligned with DANE's data publication time (~14:00 COT). The 20-30 minute delay provides a buffer.

The scheduler currently executes jobs synchronously in the scheduler thread. See [ADR-005](ADR-005-scheduler-execution-model.md) for the pending decision on changing this.

---

## Audit

Every ingestion request generates a sequence of `IngestionAudit` events persisted to `ingestion_audit`:

```
REQUEST_RECEIVED → REQUEST_ACCEPTED → INGESTION_STARTED → INGESTION_RUNNING
→ INGESTION_SUCCEEDED (or INGESTION_FAILED or INGESTION_CANCELED)
→ METRICS_UPDATED
```

Window violations and duplicate skips are also recorded. Audit events are written asynchronously
using `@Async + @Transactional(REQUIRES_NEW)`, so they persist even if the ingestion fails.

Query endpoints: `GET /api/internal/audit/request/{requestId}`, `/run/{runId}`, `/recent`, `/all`.

---

## Docker

**Dockerfile:** Multi-stage build.
- Build stage: `maven:3.9.9-eclipse-temurin-25`
- Runtime stage: `eclipse-temurin:25-jre-noble`
- Runs as non-root user (`appuser`).
- JVM flags: `-XX:MaxRAMPercentage=75 -XX:+UseG1GC`.

**Docker Compose:** Starts PostgreSQL 18 and the application. PostgreSQL health check gates application startup.

---

## Observability

| Mechanism | Implementation | Coverage |
|---|---|---|
| Structured logging | Logback (SLF4J) | All layers |
| MDC correlation | `runId`, `requestId`, `method`, `windowKey` | Ingestion pipeline |
| Custom health check | `SipsaHealthIndicator` (data freshness) | `/actuator/health/sipsa` |
| Metrics endpoint | Micrometer + Prometheus | `/actuator/prometheus` |
| Custom metrics | **None yet** | — (see [TECH-032](../backlog/technical-backlog.md#tech-032)) |

---

## Why Not Microservices

The system has a single bounded context (SIPSA agricultural data), a single external dependency
(DANE SOAP service), and a single database. Splitting into microservices would add:
- Network latency for every inter-service call.
- Distributed transaction complexity (currently solved by `REQUIRES_NEW`).
- Deployment and operational overhead.

At the current scale, microservices would be over-engineering with no benefit.

**Reconsider if:** The system needs to serve multiple data sources with independent teams,
independent deployment cycles, or vastly different scaling requirements.

## Why Not Hexagonal Architecture

The project uses a layer-based structure (api, application, domain, infrastructure) that achieves
the same dependency isolation goals as hexagonal architecture for this system's complexity.

Hexagonal architecture would add:
- Port/adapter interface pairs for every integration point.
- Additional indirection (adapters calling ports calling use cases).
- No new testability benefits beyond what the `SoapGateway` interface already provides.

The domain is protected from infrastructure by the `SoapGateway` interface.
The application layer is protected from HTTP by the service layer.
These are the two critical boundaries. Hexagonal formalism for the remaining boundaries is not justified.

**Reconsider if:** The team needs to swap multiple implementation adapters for the same port (e.g.,
multiple databases, multiple SOAP services with the same contract, or the application becomes a library).

## Why Not Full DDD Tactical Patterns

SIPSA is a transactional integration system, not a domain-rich application. The domain consists
of agricultural price records that mirror an external data source. There are no:
- Complex invariants enforced between aggregates.
- Domain events that trigger side effects.
- Business rules that operate across entity boundaries.

Full DDD tactical patterns (Aggregate Roots, domain events, value objects, domain services) would add
ceremony without improving reliability or correctness for this type of system.

The current design uses:
- Entities (JPA entities with identity).
- A service layer that orchestrates the ingestion pipeline.
- A gateway interface isolating the domain from SOAP infrastructure.

This is appropriate for the problem.

**Reconsider if:** The system evolves to include price validation rules, anomaly detection, or
multi-source data fusion where cross-entity business rules become complex.

---

## Known Issues at Snapshot Date

See [Architecture Review](../architecture/architecture-review.md) for full details.

Critical:
- Internal endpoints (`/api/internal/**`) have no authentication.
- `SipsaParcial` deduplication is non-functional (random UUID key hash).

High:
- No unit tests for business logic.
- Scheduler blocks a thread during ingestion.

Full backlog: [Technical Backlog](../backlog/technical-backlog.md).
