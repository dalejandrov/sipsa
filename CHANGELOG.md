# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Added

- `src/main/resources/application-dev.yaml` — new `dev` profile holding everything that is
  convenient locally but must not reach production: default database credentials
  (`sipsa_user`/`sipsa_pass`), verbose per-package log levels, `format_sql`, full health
  details (`show-details: always`), and the Actuator `loggers` endpoint.
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
- Two tests in `WindowPolicyTest` are intentionally `@Disabled`, documenting the desired
  behavior of a confirmed-but-unfixed defect in `WindowPolicy.validateMonthly()` (see
  Findings below); they are the acceptance criteria for a future story (TECH-111).

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

### Findings (not fixed in this change — see `docs/architecture/scheduled-ingestion-validation.md`)

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
- Both are tracked for a follow-up story: TECH-111, since formalized in
  `docs/backlog/technical-backlog.md` with an approved implementation plan (not yet
  implemented).

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
