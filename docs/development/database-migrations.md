# Database Migrations — Developer Guide

**Tool:** Flyway (the only migration tool — see [ADR-009](../adr/ADR-009-database-migration-strategy.md)).
**Location:** `src/main/resources/db/migration/`
**Verification:** `FlywayMigrationsTest` (Testcontainers + real PostgreSQL, runs in `./mvnw clean verify` when Docker is available).

---

## Adding a migration

1. Create the file with a UTC timestamp version (14 digits, `yyyyMMddHHmmss`) — **not**
   the next sequential integer:

   ```
   src/main/resources/db/migration/V{yyyyMMddHHmmss}__{snake_case_description}.sql
   ```

   Example: `V20260801091500__add_index_sipsa_ciudad_fecha.sql`

   Why a timestamp and not `V2`, `V3`, ...: two branches created at different times can
   never collide on a version number, so there is nothing to renumber on rebase. `V1`
   (`V1__initial_schema.sql`) is the sole grandfathered exception — the foundational
   schema, predating this convention (ADR-009 addendum, 2026-07-28). Generate the
   timestamp with `date -u +%Y%m%d%H%M%S`.

2. Write plain PostgreSQL SQL. One logical change per migration (a migration is the
   unit of review and of failure).

3. Run the migration gate:

   ```bash
   ./mvnw clean verify
   ```

   `FlywayMigrationsTest` applies the full chain (`V1` → your new migration) against a
   clean PostgreSQL container and boots the app with `ddl-auto: validate`. If your
   migration changes tables backing JPA entities, update the entities in the same PR —
   the gate fails on any drift.

   There is no automated check yet for naming/version collisions — with a single
   contributor there is nothing to collide with. Review the filename by eye against the
   convention above; add `scripts/validate-migrations.sh` (documented, not yet built —
   ADR-009 addendum) plus a CI step once a second person starts adding migrations in
   parallel.

4. Add an entry to [`docs/database/database-changelog.md`](../database/database-changelog.md)
   for the migration (intent, risk, validation evidence) — see that file's entry format.

5. If the change affects local data, note it in the PR description. Destructive changes
   (dropping/renaming columns with data) follow expand–migrate–contract across releases
   (ADR-009, rule 8).

## Rules (binding — full rationale in ADR-009)

| Rule | Enforced by |
|---|---|
| Never edit/rename/delete an applied migration; fix forward with a new migration | `validate-on-migrate: true` (startup + gate) |
| Version = UTC timestamp `yyyyMMddHHmmss` (`V1` grandfathered); no two migrations share a version | Manual review today; automate in `scripts/validate-migrations.sh` + CI once there is more than one contributor |
| Strict version order (Flyway sorts numerically; timestamps never need renumbering) | `out-of-order: false` |
| Schema changes only via migrations, never manual DDL | `ddl-auto: validate` in all runtime profiles |
| `flyway clean` never runs through the app | `clean-disabled: true` |
| Repeatable migrations (`R__*.sql`) only for idempotent objects (views, functions) | Review |
| **Once a migration has reached any shared/production environment, it — and every migration before it — can never be squashed or rewritten again**, even during pre-production churn | ADR-009 addendum; review discipline (no tooling gate) |

## Baseline (existing databases) — disabled (TECH-116, 2026-08-03)

`baseline-on-migrate` exists only to adopt Flyway on a database created before Flyway
was introduced: it would record a baseline marker and skip `V1` (which would need to
mirror that legacy schema). That scenario has never occurred here — every environment
this app has run in (local docker-compose, CI Testcontainers, and the not-yet-provisioned
AWS RDS) gets its schema exclusively via this app's own `flyway migrate`, never a
manually-created schema. Verified empirically against a fresh PostgreSQL 18 container:
`flyway_schema_history` shows exactly one row (`type=SQL`, the `V1` migration) and zero
`BASELINE` rows. Now `false` in `application.yaml` — a future non-empty, no-history
schema should fail startup loudly, not be silently adopted as "already migrated."

## Troubleshooting

- **`FlywayMigrationsTest` skipped** — Docker is not running. The gate silently skips
  without Docker; start Docker to run it locally.
- **"Migration checksum mismatch"** — an applied migration was edited. Revert the edit
  and create a new `V{N+1}` migration instead. (If it never reached a shared
  environment: `DELETE FROM flyway_schema_history WHERE version = '{N}'` on your local
  database, or recreate your local volume.)
- **"missing table" on startup with a fresh database** — Flyway did not run. Check that
  `spring-boot-flyway` is on the classpath (see CHANGELOG 2026-07-14 incident) and that
  `spring.flyway.enabled` is not `false` in the active profile.
- **Reset local Docker database completely:**

  ```bash
  docker compose down && docker volume rm sipsa-postgres-data && docker compose up -d
  ```
