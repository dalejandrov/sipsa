# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

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

### Documentation

- `docs/architecture/architecture-review.md` — Full architectural review with 25 accepted
  findings, evidence methodology, transaction boundary diagram, and accepted/discarded
  recommendations.
- `docs/architecture/technical-debt.md` — 28-item debt registry classified by area and priority.
- `docs/architecture/refactoring-roadmap.md` — 10 deferred refactorings with justification
  and conditions for revisiting.
- `docs/architecture/implementation-roadmap.md` — 6-phase implementation plan.
- `docs/architecture/testing-strategy.md` — Test pyramid, mandatory test cases, tooling strategy.
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
- `docs/backlog/technical-backlog.md` — 28 prioritized technical stories.
- `docs/migrations/spring-boot-4-java-25.md` — Migration notes, breaking changes, validation.
- `CHANGELOG.md` — This file.
- `CONTRIBUTING.md` — Developer guide for contributions.
- `.github/` — CI workflow, issue templates, and PR template.

### Added

- `com.h2database:h2` test dependency. Enables `SipsaApplicationTests.contextLoads()` to
  run without a PostgreSQL instance, making `./mvnw clean verify` self-contained.
- `src/test/resources/application.yaml` — Test configuration with H2 in-memory database,
  disabled Flyway, and minimal SOAP configuration for context load tests.
