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

   **Resolution (2026-08-03, TECH-116):** confirmed — every environment this app has run
   in (local docker-compose, CI Testcontainers, and the not-yet-provisioned AWS RDS) gets
   its schema exclusively via this app's own `flyway migrate`, never a manually-created
   schema followed by `flyway baseline`. Verified empirically against a fresh PostgreSQL
   18 container: `flyway_schema_history` shows exactly one row (`type=SQL`, `V1`) and zero
   `BASELINE` rows. `baseline-on-migrate` is now `false`; `baseline-version` was removed
   (inert once baseline-on-migrate is disabled).
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

---

## Addendum (2026-07-28) — Versioning scheme for growth, and the squash cut-off

**Context.** On 2026-07-28, with the system still pre-production (no shared environment,
no external contributors), `V2`–`V5` were squashed into `V1__initial_schema.sql` (see
[database-changelog.md](../database/database-changelog.md)) to avoid carrying forward a
migration history nobody outside this repo depends on. That squash is only safe because
nothing has consumed those versions yet. This addendum extends rules 1 and 3 above so the
scheme scales once the API, the migration count, and the contributor count all grow —
and so squashing is never attempted again once it would actually be unsafe.

**Decision.**

1. **Versioning switches from sequential integers to UTC timestamps.** Every migration
   from here on is named `V{yyyyMMddHHmmss}__{snake_case_description}.sql` (14-digit
   timestamp, generated with `date -u +%Y%m%d%H%M%S`). `V1__initial_schema.sql` is the
   sole grandfathered small-integer version. Rationale: sequential integers require a
   human to renumber on every rebase collision between two branches that both added a
   migration (rule 3's original text); a timestamp makes that class of collision
   structurally impossible, at the cost of a less "countable" filename — an acceptable
   trade given `flyway info` and the changelog already provide the ordered view.
2. **Enforcement is deferred, not built.** With a single contributor there is no branch
   to collide with, so a CI/local script validating the naming convention would be pure
   overhead today. The convention is binding by review discipline in the meantime. Build
   `scripts/validate-migrations.sh` (reject duplicate versions, non-timestamp versions
   beyond the `V1` exception, and filenames outside `V{timestamp}__*.sql` / `R__*.sql`)
   and wire it into `.github/workflows/ci.yml` ahead of the build — as a fast, Docker-free
   step — at the point a second contributor starts adding migrations, so the first real
   collision risk arrives with the gate already in place, not after.
3. **Squashing/rewriting history stops being an option the moment any migration reaches
   a shared or production environment** (per ADR-010, first real `terraform apply`
   deploying this schema — tracked as TECH-130..132 — or, sooner, the first time a
   second developer clones this repo and applies migrations locally against data worth
   keeping). From that point on, rule 2 (immutability) applies with zero exceptions,
   including "informal" ones like a same-day squash on an unreleased branch. Until then,
   consolidating pre-release migrations remains legitimate housekeeping, exactly as it
   was on 2026-07-28.
4. **`docs/database/database-changelog.md` keeps one entry per migration going forward**
   (intent, risk, validation evidence) — the 2026-07-28 squash collapsed the *historical*
   V1–V4 entries into one, but every new migration gets its own entry per the file's
   documented format, so the per-migration audit trail doesn't erode again as the count
   grows.

**Consequences.**

- New migration filenames are timestamps, not small integers — less immediately
  readable, but `flyway info` and the changelog present the ordered/human view instead.
- Until `scripts/validate-migrations.sh` exists, a malformed version or an accidental
  duplicate is only caught by `FlywayMigrationsTest` (slow, Testcontainers) or by review
  — a known, accepted gap while there is a single contributor.
- Rule 2 (immutability) was already binding; this addendum makes explicit *when* it
  starts being enforced with zero tolerance, closing the ambiguity that let the
  2026-07-28 squash be a legitimate, deliberate exception rather than a precedent for
  editing applied migrations later.
