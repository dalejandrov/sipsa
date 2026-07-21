# Architecture Decision Records

This directory contains Architecture Decision Records (ADRs) for the SIPSA Integration Service.

An ADR documents a significant architectural decision: its context, the alternatives considered,
the decision taken, and its consequences.

## Status values

| Status | Meaning |
|---|---|
| `Proposed` | Decision is under discussion; not yet approved |
| `Accepted` | Decision is approved and active |
| `Deprecated` | Decision was valid but has been superseded |
| `Superseded by ADR-XXX` | Replaced by a newer decision |

## ADR Index

| ADR | Title | Status | Date |
|---|---|---|---|
| [ADR-000](ADR-000-current-architecture.md) | Current Architecture Snapshot | Accepted | 2026-07-13 |
| [ADR-001](ADR-001-data-deduplication.md) | Data Deduplication Strategy | **Accepted** | 2026-07-16 |
| [ADR-002](ADR-002-internal-endpoint-security.md) | Internal Endpoint Security | **Accepted** | 2026-07-15 |
| [ADR-003](ADR-003-error-response-model.md) | Error Response Model | Proposed | 2026-07-13 |
| [ADR-004](ADR-004-transaction-boundaries.md) | Transaction Boundaries in Ingestion | **Accepted** | 2026-07-13 |
| [ADR-005](ADR-005-scheduler-execution-model.md) | Scheduler Execution Model | Proposed | 2026-07-13 |
| [ADR-006](ADR-006-ingestion-handler-contract.md) | Ingestion Handler Contract | Proposed | 2026-07-13 |
| [ADR-007](ADR-007-package-boundaries-and-internal-models.md) | Package Boundaries and Internal Models | Accepted (scoped to F1, F2, F4, F5 — F3 pending SPIKE) | 2026-07-13 |
| [ADR-008](ADR-008-timezone-locale-and-date-semantics.md) | Timezone, Locale, and Date Semantics Strategy | Proposed | 2026-07-13 |
| [ADR-009](ADR-009-database-migration-strategy.md) | Database Migration Strategy (Flyway) | **Accepted** | 2026-07-14 |
| [ADR-010](ADR-010-aws-infrastructure-as-code.md) | AWS Infrastructure-as-Code Tooling | Proposed | 2026-07-21 |

## How to create a new ADR

1. Copy the template structure from any existing ADR.
2. Use the next available number.
3. Set status to `Proposed`.
4. Add it to the index above.
5. Link it from the relevant backlog story and technical debt entry.
