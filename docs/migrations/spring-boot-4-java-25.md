# Migration: Spring Boot 4.1.0 + Java 25

## Versions

| Component | Before | After |
|---|---|---|
| Java | 21 | 25 (LTS, Temurin 25.0.3) |
| Spring Boot | 3.5.9 | 4.1.0 |
| Spring Framework | 6.x | 7.x (managed by Boot) |
| Spring Cloud | 2025.0.0 (Northfields) | 2025.1.2 (Oakwood) |
| Apache CXF | 4.1.4 | 4.2.2 |
| Hibernate | 6.x | 7.4.1 Final (managed by Boot) |
| Jackson | 2.x | 3.x (managed by Boot) |
| Flyway | managed by Boot 3 | managed by Boot 4 |
| PostgreSQL driver | managed by Boot 3 | managed by Boot 4 |
| MapStruct | 1.6.3 | 1.6.3 (unchanged) |
| WireMock | 3.13.2 | 3.13.2 (unchanged, standalone) |
| Docker build image | maven:3.9.9-eclipse-temurin-21 | maven:3.9.9-eclipse-temurin-25 |
| Docker runtime image | eclipse-temurin:21-jre-jammy | eclipse-temurin:25-jre-noble |

## Dependencies Updated

### Managed by Spring Boot BOM
All versions managed by the Spring Boot 4.1.0 parent BOM. No manual version pins
required for: Spring Framework 7, Hibernate 7, Flyway, PostgreSQL JDBC driver,
Jackson 3, Micrometer, Logback, JUnit 5, Mockito, and AssertJ.

### Manual version pins retained
| Artifact | Version | Reason |
|---|---|---|
| `org.apache.cxf:cxf-bom` | 4.2.2 | Spring Boot BOM does not manage CXF |
| `org.mapstruct:mapstruct` | 1.6.3 | Spring Boot BOM does not manage MapStruct |
| `org.wiremock:wiremock` | 3.13.2 | Test dependency, standalone |

### Removed dependencies
| Artifact | Reason |
|---|---|
| `io.github.resilience4j:resilience4j-spring-boot3` | Spring Boot 3 specific; removed |
| `io.github.resilience4j:resilience4j-spring6` | Spring 6 specific; removed |

Resilience4j is now sourced exclusively via `spring-cloud-starter-circuitbreaker-resilience4j`
managed by Spring Cloud 2025.1.2 BOM.

## Breaking Changes and Fixes

### 1. Maven Wrapper recreated
**Problem**: `.mvn/` directory was missing from the repository. The `mvnw` script
referenced `.mvn/wrapper/maven-wrapper.properties` which did not exist.

**Fix**: Recreated `.mvn/wrapper/maven-wrapper.properties` pointing to Maven 3.9.9.

### 2. Actuator health package relocation (Spring Boot 4)
**Problem**: `org.springframework.boot.actuate.health.{Health,HealthIndicator}` moved
to `org.springframework.boot.health.contributor` in Spring Boot 4.

**Affected file**: `SipsaHealthIndicator.java`

**Fix**:
```java
// Before (Spring Boot 3)
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

// After (Spring Boot 4)
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
```

### 3. HTTP 422 renamed to align with RFC 9110 (Spring Framework 7)
**Problem**: `HttpStatus.UNPROCESSABLE_ENTITY` deprecated in Spring Framework 7
in favor of `HttpStatus.UNPROCESSABLE_CONTENT` (RFC 9110 alignment).

**Affected file**: `GlobalExceptionHandler.java`

**Fix**:
```java
// Before
HttpStatus.UNPROCESSABLE_ENTITY

// After
HttpStatus.UNPROCESSABLE_CONTENT
```

### 4. `@NonNull` from `org.springframework.lang` deprecated (Spring Framework 7)
**Problem**: `org.springframework.lang.NonNull` deprecated in Spring Framework 7;
Spring now uses JSpecify annotations internally.

**Affected file**: `TimezoneFilter.java` (override of `OncePerRequestFilter.doFilterInternal`)

**Fix**: Removed `@NonNull` annotations and the import. The override works correctly
without them since null-safety is enforced by the parent class contract.

### 5. Maven compiler plugin: `source`/`target` → `release`
**Problem**: Using separate `<source>` and `<target>` settings does not enforce API
compatibility. The `<release>` flag ensures only Java 25 APIs are used.

**Fix**:
```xml
<!-- Before -->
<source>${java.version}</source>
<target>${java.version}</target>

<!-- After -->
<release>${java.version}</release>
```

### 6. Hibernate 7 explicit dialect requirement
**Problem**: Hibernate 7 requires an actual JDBC connection to auto-detect the
dialect. Hibernate 6 could infer `PostgreSQLDialect` from the JDBC URL string alone.
This caused startup failures when the database was unavailable.

**Fix**: Added explicit dialect to `application.yaml`:
```yaml
spring:
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
```

### 7. Jackson annotations — no change required
`com.fasterxml.jackson.annotation.JsonInclude` still uses the `com.fasterxml.jackson`
package even in Jackson 3 (only the core/databind modules moved to `tools.jackson`).
No source changes were needed.

### 8. `javax.xml.*` imports — no change required
`javax.xml.stream.*` and `javax.xml.namespace.QName` are JDK classes (not Jakarta EE).
They remain `javax.xml.*` in Java 25. No source changes needed in StAX parsers.

## Docker Changes

```dockerfile
# Before
FROM maven:3.9.9-eclipse-temurin-21 AS build
FROM eclipse-temurin:21-jre-jammy

# After
FROM maven:3.9.9-eclipse-temurin-25 AS build
FROM eclipse-temurin:25-jre-noble
```

JVM flags in `ENTRYPOINT` (`-XX:MaxRAMPercentage=75`, `-XX:+UseG1GC`,
`-Djava.security.egd=file:/dev/./urandom`) are all compatible with Java 25.

## Database Changes

No schema changes. The Flyway migration `V1__initial_schema.sql` is unchanged.
PostgreSQL 18 compatibility was already present before this migration.

## Tests Executed

| Command | Result |
|---|---|
| `./mvnw clean compile` | BUILD SUCCESS |
| `./mvnw clean compile -Dmaven.compiler.showDeprecation=true` | BUILD SUCCESS (no source deprecations) |
| `./mvnw clean package -DskipTests` | BUILD SUCCESS |
| `./mvnw test` | 1 test run, 1 error (PostgreSQL not running — pre-existing) |
| `docker compose config` | Valid |
| Docker build | Not tested (Docker daemon not running in build environment) |

### Note on test failure
`SipsaApplicationTests.contextLoads()` uses `@SpringBootTest` with `ddl-auto: validate`
and Flyway enabled. This test REQUIRES a running PostgreSQL instance. The failure is
pre-existing and occurs with any Spring Boot version when no database is available.
To run tests: start PostgreSQL with `docker compose up -d db` before running `./mvnw test`.

## Rollback Procedure

```bash
# Restore main branch
git checkout main

# Or cherry-pick only what you want to keep
git log chore/migrate-spring-boot-4-java-25 --oneline
```

The main branch retains Spring Boot 3.5.9 + Java 21. No schema or data changes were
made, so no database rollback is needed.

## Pending Risks

1. **Docker build not verified** — Docker daemon was not available in the build
   environment. The Dockerfile uses `eclipse-temurin:25-jre-noble` which should be
   a valid Eclipse Temurin image, but requires manual verification.

2. **WireMock 3.x on Java 25** — WireMock 3.13.2 (standalone) is used in tests.
   It should work on Java 25 (JDK compatibility), but has not been tested since no
   WireMock-based tests exist currently. If future tests use WireMock, validate
   compatibility or migrate to `wiremock-spring-boot 4.x`.

3. **Resilience4j version mismatch** — Spring Cloud 2025.1.2 manages Resilience4j
   2.3.0. The `resilience4j-spring-boot4` artifact was introduced in 2.4.0 and is
   NOT managed by the current Spring Cloud BOM. The project removed the explicit
   Resilience4j Boot starter dependency; if Resilience4j annotations or auto-config
   are needed in the future, add `resilience4j-spring-boot4:2.4.0` explicitly.

4. **Spring Cloud Circuit Breaker runtime** — The `spring-cloud-starter-circuitbreaker-resilience4j`
   integration has not been validated end-to-end since the project does not use
   `@CircuitBreaker` annotations. If circuit breaker features are added later,
   verify the integration against Spring Cloud 2025.1.2.

## Post-Migration Recommendations

1. Add Testcontainers to enable `@SpringBootTest` without requiring a locally
   running PostgreSQL instance.
2. Consider migrating WireMock to `wiremock-spring-boot:4.x` for Spring Boot 4
   integration tests.
3. Review and upgrade GitHub CI workflows (none exist in the repository currently).
4. Add OWASP Dependency Check or Renovate to detect future vulnerable dependencies.
