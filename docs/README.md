# SIPSA Documentation

Technical documentation for the SIPSA Integration Service.

---

## Sources of Truth

When two documents disagree, the one below wins for its topic; fix the other one.

| Topic | Authoritative document |
|---|---|
| Delivered changes (per release / `[Unreleased]`) | [`CHANGELOG.md`](../CHANGELOG.md) |
| Story status (Pending / Done / Blocked) | [Technical Backlog](backlog/technical-backlog.md) |
| Accepted architectural decisions | The [ADRs](adr/README.md) (`Accepted` status only) |
| Active debt and resolved debt items | [Technical Debt Registry](architecture/technical-debt.md) |
| Local operation and verifiable procedures | [`CONTRIBUTING.md`](../CONTRIBUTING.md) and the [development guides](#development-process) |
| Migration history, risks and recommendations | [Migration notes](migrations/spring-boot-4-java-25.md) — historical record; resolved items are marked inline, not deleted |

---

## Getting Started

| Document | Description |
|---|---|
| [Local Development](getting-started/local-development.md) | Run the app directly on your machine: prerequisites, environment variables, build, run, tests |
| [Docker](getting-started/docker.md) | Run the full stack (app + PostgreSQL + mock OIDC) with Docker Compose |

## API

| Document | Description |
|---|---|
| [API Overview](api/README.md) | Entry point: authentication, response codes, where to find examples |
| [SIPSA REST API Reference](api/sipsa-rest-api.md) | Every endpoint, parameters, request/response examples, error codes |
| [HTTP Request Collection](../http/sipsa-api.http) | Runnable requests for IntelliJ HTTP Client / VS Code REST Client — setup in [`http/README.md`](../http/README.md) |

---

## Architecture

| Document | Description |
|---|---|
| [**Project Architecture**](architecture/project-architecture.md) | **Start here.** The canonical, consolidated description of the currently approved architecture — vision, style, principles, package rules, request flow, domain model, scheduling, time strategy, error handling, testing, technical debt, and future evolution. |
| [Agent Architecture Guide](architecture/AGENT_ARCHITECTURE.md) | Short, agent-focused quickstart: what to read, what never to do, when to create an ADR or a story, when to stop and ask. |
| [Architecture Review](architecture/architecture-review.md) | Findings, accepted/discarded recommendations, known risks, domain model assessment |
| [Technical Debt Registry](architecture/technical-debt.md) | All known technical debt, classified by category and priority |
| [Refactoring Roadmap](architecture/refactoring-roadmap.md) | Active and deferred refactorings, with justifications for deferral |
| [Implementation Roadmap](architecture/implementation-roadmap.md) | Phased plan for all backlog items |
| [Testing Strategy](architecture/testing-strategy.md) | Test pyramid, mandatory/recommended/optional tests, tooling |
| [Scheduled Ingestion Validation](architecture/scheduled-ingestion-validation.md) | Evidence-backed validation of the scheduled ingestion pipeline (TECH-110): job inventory, cron table, DANE contrast matrix, findings F-WP-01/02/03 (all fixed by TECH-111, 2026-07-14) |
| [Timezone/Locale Strategy Review](architecture/timezone-locale-date-strategy-review.md) | Temporal inventory, DANE contrast matrix, `TimezoneFilter`/`WindowPolicy` evaluation, alternatives comparison — evidence for ADR-008 (Proposed) |
| [AWS Production Readiness](architecture/aws-production-readiness.md) | Classification and evidence for the AWS target architecture (TECH-130/131/132) — declared as Terraform code, **not yet deployed** |

## Architecture Decision Records

| ADR | Title | Status |
|---|---|---|
| [ADR-000](adr/ADR-000-current-architecture.md) | Current Architecture Snapshot | Accepted |
| [ADR-001](adr/ADR-001-data-deduplication.md) | Data Deduplication Strategy | **Accepted** |
| [ADR-002](adr/ADR-002-internal-endpoint-security.md) | Internal Endpoint Security | **Accepted** |
| [ADR-003](adr/ADR-003-error-response-model.md) | Error Response Model | Proposed |
| [ADR-004](adr/ADR-004-transaction-boundaries.md) | Transaction Boundaries in Ingestion | **Accepted** |
| [ADR-005](adr/ADR-005-scheduler-execution-model.md) | Scheduler Execution Model | Proposed |
| [ADR-006](adr/ADR-006-ingestion-handler-contract.md) | Ingestion Handler Contract | Proposed |
| [ADR-007](adr/ADR-007-package-boundaries-and-internal-models.md) | Package Boundaries and Internal Models | **Accepted** (scoped to F1, F2, F4, F5) |
| [ADR-008](adr/ADR-008-timezone-locale-and-date-semantics.md) | Timezone, Locale, and Date Semantics Strategy | Proposed |
| [ADR-009](adr/ADR-009-database-migration-strategy.md) | Database Migration Strategy (Flyway) | **Accepted** |

See [ADR Index](adr/README.md) for the full list and status guide.

## Operations

| Document | Description |
|---|---|
| [AWS Production Preflight](operations/aws-production-preflight.md) | Deployment prerequisites checked locally (TECH-144, Done) vs. blocked on real AWS credentials (TECH-143, unmerged) |
| [Database Migrations](development/database-migrations.md) | Day-to-day Flyway workflow (ADR-009): naming, conventions, the Testcontainers migration gate — see also [Development Process](#development-process) |
| [Spring Boot 4 + Java 25 Migration](migrations/spring-boot-4-java-25.md) | Framework migration notes, breaking changes, validation results |

## Project

| Document | Description |
|---|---|
| [Implementation Roadmap](architecture/implementation-roadmap.md) | Phased plan for all backlog items |
| [Technical Backlog](backlog/technical-backlog.md) | All stories, prioritized with acceptance criteria and Git branches — the official source of truth for story status |
| [Changelog](../CHANGELOG.md) | Notable changes per release, `[Unreleased]` section for delivered-but-unreleased work |

## Diagrams

| Diagram | Description |
|---|---|
| [ER Diagram](diagrams/er-diagram.puml) | Database schema |
| [Class Diagram](diagrams/class-diagram.puml) | System architecture by layer |
| [Component Diagram](diagrams/component-diagram.puml) | Component interactions |
| [Scheduled Ingestion Sequence](diagrams/sequence/scheduled-ingestion-sequence.puml) | Automated ingestion flow |
| [API Query Sequence](diagrams/sequence/api-query-sequence.puml) | Request/response handling |
| [Manual Ingestion Sequence](diagrams/sequence/manual-ingestion-sequence.puml) | Manual trigger flow |

---

## Development Process

| Document | Description |
|---|---|
| [Development Workflow](development/development-workflow.md) | Full cycle: story → branch → implement → test → PR |
| [Database Migrations](development/database-migrations.md) | Day-to-day Flyway workflow (ADR-009): naming, conventions, the Testcontainers migration gate |
| [Branching Strategy](development/branching-strategy.md) | Branch naming, lifecycle, merge strategy |
| [Implementation Guidelines](development/implementation-guidelines.md) | Scope rules, testing rules, documentation checklist |
| [Pull Request Checklist](development/pull-request-checklist.md) | Mandatory checklist for every PR |
| [Code Review Guidelines](development/code-review-guidelines.md) | What to check and how to comment |

## Quick Start for New Developers and AI Agents

**Start here:** [`/AGENTS.md`](../AGENTS.md) — authoritative guide for contributors (human and AI).

Then:
1. Read [Project Architecture](architecture/project-architecture.md) — understand the system's current, approved structure in a few minutes. AI agents should also read the shorter [Agent Architecture Guide](architecture/AGENT_ARCHITECTURE.md).
2. Read [Technical Backlog](backlog/technical-backlog.md) — find the next story to implement.
3. Read [ADR-004](adr/ADR-004-transaction-boundaries.md) — critical before any ingestion changes.
4. Read [Refactoring Roadmap](architecture/refactoring-roadmap.md) — what NOT to refactor and why.

## Viewing PlantUML Diagrams

Use [PlantUML Online](https://www.plantuml.com/plantuml/) or an IDE plugin (IntelliJ, VS Code).
