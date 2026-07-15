# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Security

- **TECH-001/TECH-002 — application security layer** ([ADR-002](docs/adr/ADR-002-internal-endpoint-security.md),
  now `Accepted` with the layered AWS model: API Gateway API keys + Cognito JWT +
  Resource Server + private networking). Spring Boot is now an OAuth 2.0 Resource Server:
  - `/api/internal/**` requires a Cognito access token with the per-operation scope —
    `sipsa/ingestion.execute` (run), `sipsa/ingestion.cancel` (cancel),
    `sipsa/ingestion.read` (run queries), `sipsa/audit.read` (audit trail). **Breaking for
    previously-unauthenticated operational scripts, by design.**
  - JWT validation: issuer/signature/expiry, `token_use=access` (Cognito ID tokens
    rejected), optional client allowlist via `SIPSA_JWT_ALLOWED_CLIENT_IDS` (fail-fast on
    malformed values). Issuer configured via `SIPSA_JWT_ISSUER_URI` (required — the app
    refuses to start without it; no secrets involved, none versioned).
  - Stateless chain: no sessions, no CSRF surface, no form login, no HTTP Basic, no
    cookies. Default deny for undeclared routes. `401`/`403` are JSON in the
    `ErrorResponse` shape with generic messages (no HTML, no stack traces, no hint of
    which validation failed).
  - `GET /api/sipsa/**` stays public in the application — per-consumer API keys, quotas
    and throttling are API Gateway's job (TECH-131). `/actuator/health` stays public for
    container healthchecks; every other Actuator endpoint now requires a valid token
    (closing TECH-002), and Actuator is excluded from the public gateway surface.
  - Local development runs without AWS: `docker compose up` now includes a mock OIDC
    service (`ghcr.io/navikt/mock-oauth2-server:5.0.2`, config in
    `docker/mock-oidc-config.json`); the `dev` profile defaults the issuer to
    `http://localhost:9000/default`. See CONTRIBUTING.md for token commands.
  - New dependencies: `spring-boot-starter-oauth2-resource-server`;
    `spring-security-test` and `spring-boot-webmvc-test` (test scope — Spring Boot 4
    moved `@AutoConfigureMockMvc` into the dedicated `spring-boot-webmvc-test` module).
  - Infrastructure layers formalized as TECH-130 (Cognito), TECH-131 (API Gateway),
    TECH-132 (private networking) — pending, tracked in the backlog.

### Added

- **TECH-120 — CI pipeline** (`.github/workflows/ci.yml`). Every pull request and every
  push to `main` now runs `./mvnw clean verify` on GitHub Actions (Temurin JDK 25, Maven
  Wrapper, Maven dependency cache). The Testcontainers-based Flyway migration gate
  (`FlywayMigrationsTest`, ADR-009) executes against the runner's Docker, and a dedicated
  guard step fails the pipeline if that suite is skipped — the local-only Docker self-skip
  can no longer void the gate. Superseded runs of the same branch/PR are cancelled;
  `GITHUB_TOKEN` is restricted to `contents: read`; no secrets or `.env` are used; on
  failure the surefire/failsafe reports are uploaded as a `test-reports` artifact.
  Documented in `CONTRIBUTING.md` (Continuous Integration section) and
  `docs/development/development-workflow.md` (Step 6). Closes post-migration
  recommendation #3 of the Spring Boot 4 migration.

- `src/main/resources/application-dev.yaml` — new `dev` profile holding everything that is
  convenient locally but must not reach production: default database credentials
  (`sipsa_user`/`sipsa_pass`), verbose per-package log levels, `format_sql`, full health
  details (`show-details: always`), and the Actuator `loggers` endpoint.
- **[ADR-009](docs/adr/ADR-009-database-migration-strategy.md)** — database migration
  strategy: Flyway confirmed as the only migration tool (Liquibase evaluated and
  rejected), with binding conventions (immutable applied migrations, strict ordering,
  fix-forward, expand–migrate–contract for destructive changes). Day-to-day workflow in
  [docs/development/database-migrations.md](docs/development/database-migrations.md).
- `FlywayMigrationsTest` — migration gate on Testcontainers: applies the full migration
  chain against a real PostgreSQL 18 container (same image as `docker-compose.yml`) and
  boots the full context with `ddl-auto: validate`, failing on broken SQL, on a missing
  Flyway auto-configuration (the 2026-07-14 regression), and on entity/schema drift.
  Skipped automatically when Docker is unavailable. New test dependencies (managed by
  the Spring Boot BOM): `spring-boot-testcontainers`, `testcontainers-postgresql`,
  `testcontainers-junit-jupiter`. Suite: 69 tests (was 65).
- Flyway hardening in `application.yaml`: `validate-on-migrate: true`,
  `clean-disabled: true`, `out-of-order: false`, and an explicit, documented baseline
  policy (`baseline-version: 1`).
- `src/main/resources/application-docker.yaml` — explicit `docker` profile so
  `docker-compose.yml`'s `SPRING_PROFILES_ACTIVE=docker` points at a real profile instead
  of silently falling back to the base configuration. Sets only container-topology facts
  (database host defaults to the `db` service); credentials still have no defaults.

### Changed

- `src/main/resources/application.yaml` is now a production-safe baseline:
  - `DB_USERNAME`/`DB_PASSWORD` no longer have hardcoded defaults — the application fails
    fast at startup if they are missing outside the `dev` profile.
  - DANE-contractual values are fixed in the file instead of being environment variables
    (property names unchanged): `sipsa.timezone`, `sipsa.soap.namespace`, ingestion
    windows (`daily-window-start/end`, `monthly-run-days`, `monthly-window-start`), cron
    expressions, and the pagination policy.
  - Actuator no longer exposes `loggers` by default (dev-only now; partially addresses
    TECH-002) and `show-details` changed from `always` to `when-authorized`.
  - Baseline logging reduced to production-safe levels; verbose levels moved to the dev
    profile.
- `.env.example` trimmed to the variables that remain configurable (credentials,
  endpoint, timeouts, tuning knobs) and repositioned as a versioned reference template
  only — no `.env` file is read at runtime; variables are provided via the shell,
  `docker-compose`, or the deployment platform. Documents that contractual values now
  live in `application.yaml`.
- `docker-compose.yml` — database name/credentials now use `${VAR:-default}`
  interpolation (overridable from the shell without any `.env` file) and are passed
  consistently to both the `db` and `app` services; the `pg_isready` healthcheck uses the
  same interpolated values.
- `README.md` — configuration section rewritten: documents the `dev`/`docker`/base
  profile split and removes the instruction to copy `.env.example` to `.env`.

### Removed

- `req.xml` (root) — manual SOAP smoke-test artifact with embedded `curl` commands, not
  referenced by any code, build, or documentation.

### Fixed

- **TECH-111 — monthly `WindowPolicy` rules corrected** (F-WP-01/02/03, confirmed by
  TECH-110's validation). `validateMonthly()` now binds each monthly method to its own
  DANE publication rule — `promediosSipsaMesMadr`: principal day 8, grace day 9;
  `promedioAbasSipsaMesMadr`: principal day 10, grace day 11 — rejecting the cross-method
  acceptance it previously allowed, and the `monthly-window-start` time gate now applies
  to grace days too (day 9/11 at midnight is no longer accepted). The monthly `windowKey`
  is now the stable per-period marker `YYYY-MM-M8`/`YYYY-MM-M10` documented all along in
  the Javadoc, so a grace-day retry reuses the principal day's key and the
  `(method_name, window_key)` idempotency guarantee holds across retries; `force=true`
  still bypasses the window but returns the correct period key instead of the raw forced-on
  date. Rule resolution checks `abas` before `mesmadr` (the Abas method name contains both
  fragments), protected by an explicit test. `sipsa.ingestion.monthly-run-days` is
  repurposed as a startup sanity check (fails fast with `SipsaConfigurationException` if
  days 8 and 10 are missing) and no longer participates in per-run validation; property
  name unchanged. No data migration: historical raw-date monthly keys coexist safely with
  the new format. The two `@Disabled` tests from TECH-110 are re-enabled; daily/weekly
  validation, cron expressions, timezone, REST contracts, and DB schema are untouched.
  **Deployment note:** deploy outside days 8–11 — if a monthly method already succeeded in
  the current month under the old key format, one redundant (upsert-safe) re-ingestion of
  that period can occur during the transition month.

- **Flyway migrations silently never ran on Spring Boot 4** — Spring Boot 4 moved the
  Flyway auto-configuration out of `spring-boot-autoconfigure` into the dedicated
  `spring-boot-flyway` module. With only `flyway-core`/`flyway-database-postgresql` on the
  classpath, the application started against an empty database and failed Hibernate schema
  validation (`missing table [ingestion_audit]`). Undetected until now because the test
  suite disables Flyway (H2 `create-drop`). Added `org.springframework.boot:spring-boot-flyway`;
  verified against a clean PostgreSQL container: all `V1` tables created, app `UP`.
- **Docker build was broken** — the build stage referenced `maven:3.9.9-eclipse-temurin-25`,
  a tag that does not exist on Docker Hub (pending risk #1 of the Spring Boot 4 migration).
  Replaced with `eclipse-temurin:25-jdk-noble`: the Maven Wrapper already pins Maven 3.9.9,
  keeping a single source of truth for the Maven version. Full
  `docker compose build && up` verified: healthcheck `UP`, `GET /api/sipsa/ciudad` → 200.
- `.dockerignore` excluded `docker-compose.yaml` but the real file is `docker-compose.yml`;
  now covers both, plus `CHANGELOG.md`, `CONTRIBUTING.md`, `AGENTS.md`, `.github/`, `.claude/`.

### Docker

- The runtime image now sets `SPRING_PROFILES_ACTIVE=docker` as a default (overridable), so
  a standalone `docker run` fails fast on missing credentials instead of silently starting
  with the `dev` profile defaults.
- Removed the obsolete `-Djava.security.egd=file:/dev/./urandom` JVM flag (legacy workaround,
  unnecessary on Java 25) and added `--no-install-recommends` to the `curl` install.

### Testing

- **TECH-110/TECH-040** — Added a full automated validation suite for scheduled ingestion:
  `WindowPolicyTest` (25 cases, deterministic via injected `Clock`), `SipsaSchedulingCronTest`
  (18 cases validating the 3 production cron expressions with `CronExpression`, no system
  clock dependency), `SipsaIngestionSchedulerTest` (8 cases verifying dispatch, `force`,
  `requestSource`, and per-job exception isolation with `GenericIngestionJob` mocked), and
  two Spring context tests (`SipsaSchedulingContextTest`,
  `SipsaSchedulingDisabledByDefaultTest`) proving the scheduling wiring both when enabled
  and disabled. Total: 59 tests (was 1), 0 failures, 2 tests intentionally `@Disabled`
  pending TECH-111. See `docs/architecture/scheduled-ingestion-validation.md`.
- Two tests in `WindowPolicyTest` were intentionally `@Disabled` at the time of TECH-110,
  documenting the desired behavior of a confirmed-but-unfixed defect in
  `WindowPolicy.validateMonthly()` (see Findings below); they were the acceptance criteria
  for TECH-111 and have since been re-enabled by it (see Fixed above).

### Added

- `WindowPolicy` now accepts an injectable `Clock` via a package-private, test-only seam
  (`setClock`, defaults to `Clock.system(zone)` — behaviorally identical to the previous
  `ZonedDateTime.now(zone)` call). No public API or functional behavior changed.
- `sipsa.scheduling.enabled` property (default `true`, backward-compatible). Set to
  `false` to prevent Spring from registering `@EnableScheduling`'s bean post-processor at
  all, guaranteeing no `@Scheduled` method fires. Used by
  `src/test/resources/application.yaml` to disable real scheduling for the test suite by
  default.

- `com.h2database:h2` test dependency. Enables `SipsaApplicationTests.contextLoads()` to
  run without a PostgreSQL instance, making `./mvnw clean verify` self-contained.
- `src/test/resources/application.yaml` — Test configuration with H2 in-memory database,
  disabled Flyway, and minimal SOAP configuration for context load tests.
- `InternalIngestionCommandsTest` — unit tests for `IngestionRequest`, `CreateRunRequest`,
  and `AuditEventRequest` static factory methods, verifying construction behavior is
  unchanged after their move to `application.command` (TECH-090).

### Findings (not fixed at the time of TECH-110; since fixed by TECH-111 — see Fixed above and `docs/architecture/scheduled-ingestion-validation.md`)

- **Confirmed bug (F-WP-01):** `WindowPolicy.validateMonthly()` does not bind the allowed
  day-of-month to the specific ingestion method — `promedioAbasSipsaMesMadr` (documented
  day 10) currently passes validation on day 8, and `promediosSipsaMesMadr` (documented
  day 8) currently passes on day 10. The cron scheduler itself is correctly separated by
  method; only `WindowPolicy`'s independent safety check is affected.
- **Confirmed bug (F-WP-02):** the monthly `windowKey` is the raw run date
  (`yyyy-MM-dd`), not the `YYYY-MM-M8`/`YYYY-MM-M10` format documented in `WindowPolicy`'s
  own Javadoc, so a retry on the grace day (e.g., day 9 after a day 8 attempt) mints a new
  key instead of reusing the one for the same logical period — breaking the
  `(method_name, window_key)` idempotency guarantee across grace-day retries.
- Both were tracked as TECH-111, formalized in `docs/backlog/technical-backlog.md` with an
  approved implementation plan and since implemented (see the TECH-111 entry under Fixed).

### Changed

- **Java 21 → Java 25 (LTS)**. Runtime updated to Eclipse Temurin 25.0.3. Maven compiler
  plugin updated from `<source>/<target>` to `<release>25</release>` to enforce API boundaries.

- **Spring Boot 3.5.9 → Spring Boot 4.1.0**. Requires Java 17+ (Java 25 used).
  Pulls in Spring Framework 7, Hibernate 7.4.1, and Jackson 3.

- **Spring Framework 6.x → Spring Framework 7.x** (managed by Spring Boot 4.1.0).
  Deprecated `HttpStatus.UNPROCESSABLE_ENTITY` replaced with `HttpStatus.UNPROCESSABLE_CONTENT`
  (RFC 9110 alignment).  
  Deprecated `@NonNull` from `org.springframework.lang` removed from `TimezoneFilter`
  (Spring 7 migrates to JSpecify internally).

- **Spring Cloud 2025.0.0 (Northfields) → Spring Cloud 2025.1.2 (Oakwood)**. Compatible with
  Spring Boot 4.1.x.

- **Apache CXF 4.1.4 → 4.2.2**. Adds Jakarta EE 11 support and Spring Boot 4 / Spring
  Framework 7 compatibility.

- **Hibernate 6.x → Hibernate 7.4.1 Final** (managed by Spring Boot 4.1.0).
  Hibernate 7 requires an actual JDBC connection to auto-detect the database dialect.
  Added `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect` to
  `application.yaml` to provide the dialect explicitly.

- **Jackson 2.x → Jackson 3.x** (managed by Spring Boot 4.1.0).
  The `jackson-annotations` module retains `com.fasterxml.jackson.annotation` package names
  in Jackson 3. No source changes were required for annotation imports.

- **Actuator health package relocated**. `org.springframework.boot.actuate.health.{Health,
  HealthIndicator}` moved to `org.springframework.boot.health.contributor` in Spring Boot 4.
  `SipsaHealthIndicator` updated accordingly.

- **Resilience4j explicit starters removed**. `resilience4j-spring-boot3` and
  `resilience4j-spring6` direct dependencies removed. Resilience4j is now sourced exclusively
  via `spring-cloud-starter-circuitbreaker-resilience4j` managed by Spring Cloud BOM.
  Note: Spring Cloud 2025.1.2 still pulls `resilience4j-spring-boot3:2.3.0` transitively;
  its health indicator auto-configurations are silently skipped by `@ConditionalOnClass` on
  Spring Boot 4 (no runtime impact).

- **Docker images updated**.
  Build stage: `maven:3.9.9-eclipse-temurin-21` → `maven:3.9.9-eclipse-temurin-25`.
  Runtime stage: `eclipse-temurin:21-jre-jammy` → `eclipse-temurin:25-jre-noble`.

- **Maven Wrapper recreated**. `.mvn/wrapper/maven-wrapper.properties` was missing from the
  repository. Recreated pointing to Maven 3.9.9. File removed from `.gitignore`.

- **Internal ingestion commands moved out of the HTTP DTO package** ([ADR-007](docs/adr/ADR-007-package-boundaries-and-internal-models.md),
  TECH-090). `IngestionRequest`, `CreateRunRequest`, and `AuditEventRequest` moved from
  `api.dto.request` to `application.command` — they were never bound from an HTTP request
  and are internal to the ingestion/audit pipeline. Import-only change in the 6 consumer
  classes; no REST route, JSON body, or HTTP status changed.

- **`TimezoneFilter` relocated to the API layer** (ADR-007, TECH-091). Moved from
  `infrastructure.config` to `api.filter` — it is an HTTP request filter, not a technical
  config class, and was the codebase's only `infrastructure → api` dependency. Package
  declaration only; behavior, filter order, headers, `ThreadLocal` lifecycle, and Spring
  bean registration are unchanged.

- **`SoapGateway` no longer references an infrastructure class** (ADR-007, TECH-095).
  Removed the `SoapGatewayImpl` import used only for a Javadoc `@see` tag — the codebase's
  only `domain → infrastructure` dependency. Javadoc-only change.

### Fixed

- `SipsaHealthIndicator` import updated from `org.springframework.boot.actuate.health` to
  `org.springframework.boot.health.contributor` after package relocation in Spring Boot 4.

- `HttpStatus.UNPROCESSABLE_ENTITY` → `HttpStatus.UNPROCESSABLE_CONTENT` in
  `GlobalExceptionHandler` (deprecated in Spring Framework 7 per RFC 9110).

- `@NonNull` annotation from `org.springframework.lang` removed from
  `TimezoneFilter.doFilterInternal()` (deprecated in Spring Framework 7).

- `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect` added to
  `application.yaml`. Without this, Hibernate 7 cannot start without a database connection
  to auto-detect the dialect (unlike Hibernate 6 which inferred it from the JDBC URL).

- `SchedulingConfig`'s Javadoc incorrectly stated the two monthly ingestion jobs run at
  "06:00 COT"; the actual `@Scheduled` cron expressions fire at 14:30 COT (06:00 was
  `WindowPolicy`'s unrelated Java-level fallback default for `monthly-window-start`, never
  what `application.yaml` or the cron itself configure). Comment-only fix, no behavior
  change. Found during TECH-110's scheduling inventory.

### Documentation

- `docs/architecture/architecture-review.md` — Full architectural review with 25 accepted
  findings, evidence methodology, transaction boundary diagram, and accepted/discarded
  recommendations.
- `docs/architecture/technical-debt.md` — 28-item debt registry classified by area and priority.
- `docs/architecture/refactoring-roadmap.md` — 10 deferred refactorings with justification
  and conditions for revisiting.
- `docs/architecture/implementation-roadmap.md` — 6-phase implementation plan.
- `docs/architecture/testing-strategy.md` — Test pyramid, mandatory test cases, tooling strategy.
  Updated to reflect the `WindowPolicyTest`/`SipsaSchedulingCronTest`/`SipsaIngestionSchedulerTest`/
  scheduling-context tests actually implemented by TECH-110.
- `docs/architecture/scheduled-ingestion-validation.md` — Full validation of the scheduled
  ingestion pipeline: job inventory, cron table, `WindowPolicy` contrast, DANE schedule
  matrix (with 2020 currency caveat), concurrency analysis, and classified findings
  (TECH-110).
- `docs/architecture/timezone-locale-date-strategy-review.md` — Temporal inventory (all
  `Instant`/`LocalDate`/`OffsetDateTime` fields across entities, DTOs, and infrastructure),
  a contrast matrix against DANE's documented SOAP method semantics (with the March-2020
  currency caveat), an evaluation of `TimezoneFilter`/`WindowPolicy`, and a comparison of
  four timezone/locale strategy alternatives. Evidence for ADR-008.
- `docs/adr/ADR-008-timezone-locale-and-date-semantics.md` — Proposed strategy for
  timezone, locale, and date-semantics handling across the API. **Status: Proposed, not
  accepted.** No code changed as part of this documentation.
- `docs/adr/ADR-000-current-architecture.md` — Architecture snapshot after migration.
- `docs/adr/ADR-001` through `ADR-006` — Architecture decision records (ADR-004 accepted;
  ADR-001, ADR-002, ADR-003, ADR-005, ADR-006 proposed).
- `docs/backlog/technical-backlog.md` — Prioritized technical stories with acceptance
  criteria (36 as of 2026-07-13; the count grows as validations produce new stories).
- `docs/migrations/spring-boot-4-java-25.md` — Migration notes, breaking changes, validation.
- `CHANGELOG.md` — This file.
- `CONTRIBUTING.md` — Developer guide for contributions.
- `.github/` — Issue templates and PR template (no CI workflow exists yet — see the
  post-migration recommendations in `docs/migrations/spring-boot-4-java-25.md`).
