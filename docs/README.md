# SIPSA Documentation

Technical documentation for the SIPSA Integration Service.

---

## Architecture

| Document | Description |
|---|---|
| [Architecture Review](architecture/architecture-review.md) | Findings, accepted/discarded recommendations, known risks, domain model assessment |
| [Technical Debt Registry](architecture/technical-debt.md) | All known technical debt, classified by category and priority |
| [Refactoring Roadmap](architecture/refactoring-roadmap.md) | Active and deferred refactorings, with justifications for deferral |
| [Implementation Roadmap](architecture/implementation-roadmap.md) | Phased plan for all backlog items |
| [Testing Strategy](architecture/testing-strategy.md) | Test pyramid, mandatory/recommended/optional tests, tooling |

## Architecture Decision Records

| ADR | Title | Status |
|---|---|---|
| [ADR-001](adr/ADR-001-data-deduplication.md) | Data Deduplication Strategy | Proposed |
| [ADR-002](adr/ADR-002-internal-endpoint-security.md) | Internal Endpoint Security | Proposed |
| [ADR-003](adr/ADR-003-error-response-model.md) | Error Response Model | Proposed |
| [ADR-004](adr/ADR-004-transaction-boundaries.md) | Transaction Boundaries in Ingestion | **Accepted** |
| [ADR-005](adr/ADR-005-scheduler-execution-model.md) | Scheduler Execution Model | Proposed |
| [ADR-006](adr/ADR-006-ingestion-handler-contract.md) | Ingestion Handler Contract | Proposed |

See [ADR Index](adr/README.md) for the full list and status guide.

## Backlog

| Document | Description |
|---|---|
| [Technical Backlog](backlog/technical-backlog.md) | All 28 stories, prioritized with acceptance criteria and Git branches |

## Migrations

| Document | Description |
|---|---|
| [Spring Boot 4 + Java 25](migrations/spring-boot-4-java-25.md) | Migration notes, breaking changes, validation results |

## Diagrams

| Diagram | Description |
|---|---|
| [ER Diagram](diagrams/er-diagram.puml) | Database schema |
| [Class Diagram](diagrams/class-diagram.puml) | System architecture by layer |
| [Component Diagram](diagrams/component-diagram.puml) | Component interactions |
| [Scheduled Ingestion Sequence](diagrams/sequence/scheduled-ingestion-sequence.puml) | Automated ingestion flow |
| [API Query Sequence](diagrams/sequence/api-query-sequence.puml) | Request/response handling |
| [Manual Ingestion Sequence](diagrams/sequence/manual-ingestion-sequence.puml) | Manual trigger flow |

## API Reference

- [SIPSA REST API](SipsaRestController-API-Documentation.md) — Public `/api/sipsa` endpoints

---

## Quick Start for New Developers

1. Read [Architecture Review](architecture/architecture-review.md) — understand the system and known risks.
2. Read [ADR-004](adr/ADR-004-transaction-boundaries.md) — understand the transaction model (critical for ingestion changes).
3. Read [Technical Backlog](backlog/technical-backlog.md) — understand what is planned and why.
4. Read [Refactoring Roadmap](architecture/refactoring-roadmap.md) — understand what was *not* done and why.

## Viewing PlantUML Diagrams

Use [PlantUML Online](https://www.plantuml.com/plantuml/) or an IDE plugin (IntelliJ, VS Code).
