# Database Migrations — Developer Guide

**Tool:** Flyway (the only migration tool — see [ADR-009](../adr/ADR-009-database-migration-strategy.md)).
**Location:** `src/main/resources/db/migration/`
**Verification:** `FlywayMigrationsTest` (Testcontainers + real PostgreSQL, runs in `./mvnw clean verify` when Docker is available).

---

## Adding a migration

1. Create the file using the next version number:

   ```
   src/main/resources/db/migration/V{N}__{snake_case_description}.sql
   ```

   Example: `V2__add_index_sipsa_ciudad_fecha.sql`

2. Write plain PostgreSQL SQL. One logical change per migration (a migration is the
   unit of review and of failure).

3. Run the migration gate:

   ```bash
   ./mvnw clean verify
   ```

   `FlywayMigrationsTest` applies the full chain (V1 → your new migration) against a
   clean PostgreSQL container and boots the app with `ddl-auto: validate`. If your
   migration changes tables backing JPA entities, update the entities in the same PR —
   the gate fails on any drift.

4. If the change affects local data, note it in the PR description. Destructive changes
   (dropping/renaming columns with data) follow expand–migrate–contract across releases
   (ADR-009, rule 8).

## Rules (binding — full rationale in ADR-009)

| Rule | Enforced by |
|---|---|
| Never edit/rename/delete an applied migration; fix forward with `V{N+1}` | `validate-on-migrate: true` (startup + gate) |
| Strict version order, no gaps; renumber on rebase collisions | `out-of-order: false` |
| Schema changes only via migrations, never manual DDL | `ddl-auto: validate` in all runtime profiles |
| `flyway clean` never runs through the app | `clean-disabled: true` |
| Repeatable migrations (`R__*.sql`) only for idempotent objects (views, functions) | Review |

## Baseline (existing databases)

`baseline-on-migrate: true` + `baseline-version: 1` exist only to adopt Flyway on a
database created before Flyway was introduced: Flyway records a baseline marker and
skips `V1` (which mirrors that legacy schema). Empty databases run everything from `V1`.
Once every environment has `flyway_schema_history`, flip `baseline-on-migrate` to
`false` (tracked as a follow-up in ADR-009).

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
