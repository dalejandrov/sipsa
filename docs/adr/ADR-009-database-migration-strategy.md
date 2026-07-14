# ADR-009 — Database Migration Strategy

**Status:** Accepted
**Date:** 2026-07-14

## Context

The project manages its PostgreSQL schema with Flyway (`V1__initial_schema.sql`,
8 tables). Two incidents on 2026-07-14 showed the migration setup was fragile:

1. **Silent total failure.** The Spring Boot 4 migration dropped Flyway without anyone
   noticing: Spring Boot 4 moved Flyway auto-configuration out of
   `spring-boot-autoconfigure` into the dedicated `spring-boot-flyway` module, which was
   missing from `pom.xml`. With only `flyway-core` on the classpath, no migration ever
   ran; a container deployment against a clean database failed Hibernate schema
   validation (`missing table [ingestion_audit]`).
2. **No safety net.** The unit suite runs on H2 with Flyway disabled and
   `ddl-auto: create-drop`, so neither the migrations themselves nor the match between
   the migrated schema and the JPA entities is ever exercised by `./mvnw clean verify`.

The team also asked whether Liquibase (and its `db/changelog` convention) should be
adopted instead, so the tool choice needed an explicit decision.

## Decision drivers

- One database engine (PostgreSQL), small schema, SQL-literate team.
- Migrations must be exercised by the standard build, against the real engine.
- Schema history already exists in Flyway format (`flyway_schema_history` + `V1`).
- Long-term sustainability depends more on discipline (immutability of applied
  migrations, strict ordering, CI validation) than on tool features.

## Options considered

### Option A — Keep Flyway, harden it, add a real-database migration gate (chosen)

- Plain versioned SQL under `src/main/resources/db/migration/` (Flyway's standard
  convention — the equivalent of Liquibase's `db/changelog`, not an inferior layout).
- Explicit safety configuration: `validate-on-migrate`, `clean-disabled`,
  `out-of-order: false`, documented baseline policy.
- `FlywayMigrationsTest`: Testcontainers boots the same PostgreSQL version as
  `docker-compose.yml`, applies every migration, and starts the full Spring context with
  `ddl-auto: validate` — failing on broken SQL, on missing Flyway auto-configuration, and
  on entity/schema drift. Skipped automatically when Docker is unavailable.

### Option B — Switch to Liquibase (rejected)

- **Pros:** database-agnostic changesets, declarative per-changeset rollback,
  contexts/labels, `db.changelog` master file with include structure.
- **Cons:** none of those strengths applies here (single engine, no automatic-rollback
  requirement, no per-environment changesets); switching requires converting or
  baselining the existing Flyway history for zero functional gain; YAML/XML changesets
  are less auditable than plain SQL for a SQL-first team.

### Option C — Both tools (rejected outright)

Two sources of truth for one schema guarantees drift and split history.

## Decision

**Keep Flyway as the only migration tool** (Option A), with the following binding rules:

1. **Location & naming:** versioned migrations live in
   `src/main/resources/db/migration/` as `V{N}__{snake_case_description}.sql`, `{N}`
   strictly increasing integers with no gaps by convention. Repeatable migrations
   (`R__{description}.sql`) are allowed only for idempotent objects (views, functions).
2. **Applied migrations are immutable.** Never edit, rename, or delete a migration that
   may have run anywhere (any environment, any teammate's machine). Fixes go in a new
   `V{N+1}` migration. `validate-on-migrate: true` enforces this at startup.
3. **Order is strict:** `out-of-order: false`. Parallel branches that both add
   migrations must renumber on rebase.
4. **Schema changes only through migrations.** `ddl-auto` stays `validate` in every
   runtime profile; no manual DDL in any environment.
5. **`flyway clean` is disabled** (`clean-disabled: true`) in application configuration.
6. **Baseline policy:** `baseline-on-migrate: true` with explicit `baseline-version: 1`
   exists solely to adopt Flyway on a database that predates it (marker recorded, `V1`
   skipped — correct because `V1` mirrors that legacy schema). On an empty schema no
   baseline is recorded and everything runs from `V1`. If all environments are confirmed
   to have Flyway history, this setting should be flipped to `false` in a follow-up.
7. **Every migration is CI-gated:** `FlywayMigrationsTest` must pass with the new
   migration applied on top of the existing chain against real PostgreSQL.
8. **Destructive changes** (DROP/ALTER that loses data) require an explicit
   expand–migrate–contract sequence across releases and a note in the PR description.

See [docs/development/database-migrations.md](../development/database-migrations.md) for
the day-to-day workflow.

## Consequences

- `./mvnw clean verify` now fails if a migration is broken, if Flyway stops running
  (auto-configuration regression), or if entities drift from the migrated schema — the
  two 2026-07-14 incidents become impossible to reintroduce silently.
- Developers need Docker for the migration gate; without it the test is skipped, so the
  suite still passes offline (the gate then runs on any machine with Docker).
- Testcontainers (managed by the Spring Boot BOM) is added as a test dependency. This
  also settles the Testcontainers half of TECH-044; the WireMock half remains open.
- Adopting Liquibase later would require converting history; this ADR accepts that cost
  as the price of not maintaining two tools now.
