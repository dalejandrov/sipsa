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

### Removed explicit dependencies
| Artifact | Reason |
|---|---|
| `io.github.resilience4j:resilience4j-spring-boot3` | Explicit direct dependency removed |
| `io.github.resilience4j:resilience4j-spring6` | Explicit direct dependency removed |

Resilience4j is now sourced exclusively via `spring-cloud-starter-circuitbreaker-resilience4j`
managed by Spring Cloud 2025.1.2 BOM.

### New test dependency
| Artifact | Version | Reason |
|---|---|---|
| `com.h2database:h2` | managed by Boot BOM | Enables context load test without PostgreSQL |

### Known transitive dependency incompatibility: Resilience4j Spring Boot 3 in Spring Boot 4

**Evidence** (from `./mvnw dependency:tree`):
```
spring-cloud-starter-circuitbreaker-resilience4j:5.0.2
  └─ spring-cloud-circuitbreaker-resilience4j:5.0.2
       └─ resilience4j-spring-boot3:2.3.0   ← Spring Boot 3 integration
```

Spring Cloud 2025.1.2 has not yet updated its internal dependency to
`resilience4j-spring-boot4`. This means `resilience4j-spring-boot3:2.3.0` is on
the runtime classpath of a Spring Boot 4.1.0 application.

**Impact assessment** (verified by bytecode inspection):

`resilience4j-spring-boot3` registers auto-configurations via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
The health indicator auto-configurations declare:

```java
@ConditionalOnClass(value = {
    CircuitBreaker.class,
    org.springframework.boot.actuate.health.HealthIndicator.class,   // moved in SB4
    org.springframework.boot.actuate.health.StatusAggregator.class   // moved in SB4
})
```

In Spring Boot 4, `org.springframework.boot.actuate.health.HealthIndicator` and
`StatusAggregator` do not exist in the actuator jar (they moved to
`org.springframework.boot.health.contributor` and
`org.springframework.boot.health.actuate.endpoint` respectively).

**Result**: The `@ConditionalOnClass` check fails silently. Spring Boot skips
`CircuitBreakersHealthIndicatorAutoConfiguration` and
`RateLimitersHealthIndicatorAutoConfiguration` without throwing an error. The
application starts normally. No `ClassNotFoundException` is thrown.

**Verified**: `./mvnw clean verify` passes with Spring Boot 4.1.0 and this
transitive dependency present. The test `contextLoads()` passes with H2.

**Functional consequence**: Resilience4j health endpoint integration
(`/actuator/health/circuitbreakers`) is non-functional. Since the project does
not use `@CircuitBreaker` annotations or monitor circuit breaker state, this has
no current impact.

**Resolution path**: When Spring Cloud releases a version that uses
`resilience4j-spring-boot4`, the issue will resolve automatically. Until then,
the health monitoring gap is acceptable for this project's current usage level.

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
| `./mvnw clean compile -Dmaven.compiler.showDeprecation=true` | BUILD SUCCESS (zero source deprecations) |
| `./mvnw clean package -DskipTests` | BUILD SUCCESS |
| `./mvnw clean verify` | BUILD SUCCESS — 1 test, 0 failures, 0 errors |
| `docker compose config` | Valid |
| Docker build | Not tested (Docker daemon unavailable in build environment) |
| PostgreSQL + Docker Compose startup | Not tested (Docker daemon unavailable) |

### Test infrastructure
A `src/test/resources/application.yaml` was added that substitutes PostgreSQL with
H2 (in-memory, PostgreSQL compatibility mode) for the `contextLoads()` test only.
Flyway is disabled in the test profile; Hibernate creates the schema from JPA entities
via `create-drop`. This makes `./mvnw clean verify` self-contained.

Production configuration in `src/main/resources/application.yaml` is unchanged.

To run tests against real PostgreSQL:
```bash
docker compose up -d db
./mvnw test
```

## Rollback Procedure

> **Historical note (2026-07-13):** this section was written while the migration lived on
> `chore/migrate-spring-boot-4-java-25` and `main` still ran Spring Boot 3.5.9 + Java 21.
> The migration is now **merged**: `main` runs Spring Boot 4.1.0 + Java 25. Rolling back
> today would mean reverting the migration merge commit on `main` (`git revert -m 1 <merge>`),
> not switching branches. The original guidance is kept below for the record.

```bash
# Restore main branch
git checkout main

# Or cherry-pick only what you want to keep
git log chore/migrate-spring-boot-4-java-25 --oneline
```

At the time of writing, the main branch retained Spring Boot 3.5.9 + Java 21. No schema or
data changes were made, so no database rollback is needed.

## Pending Risks

1. **Docker build not verified** — ✅ **Resolved 2026-07-14** (branch
   `chore/config-cleanup-dev-profile`). The verification found two real defects:
   the build-stage image `maven:3.9.9-eclipse-temurin-25` does not exist on Docker
   Hub (replaced with `eclipse-temurin:25-jdk-noble` + Maven Wrapper), and Flyway
   migrations silently never ran on Spring Boot 4 (auto-configuration moved to the
   `spring-boot-flyway` module, which was missing from `pom.xml`). After both
   fixes, the full checklist passes: `docker compose build --no-cache && docker
   compose up -d` → healthcheck `UP`, Flyway creates the full `V1` schema,
   `GET /api/sipsa/ciudad` → 200.

2. **WireMock 3.x on Java 25** — WireMock 3.13.2 (standalone) is present in the
   classpath but not used in any test currently. If future tests use WireMock,
   validate compatibility with Java 25 or migrate to `wiremock-spring-boot:4.x`.

3. **Resilience4j Spring Boot 3 integration on Spring Boot 4 classpath** — Documented
   in detail above. Health indicator auto-configurations are silently skipped by
   `@ConditionalOnClass`. Application starts and functions normally. No action needed
   until circuit breaker features are used or Spring Cloud updates to
   `resilience4j-spring-boot4`.

4. **SOAP endpoint not validated** — `SipsaSoapClientConfig` creates a JAX-WS proxy
   at startup pointing to the live DANE service. With Docker not available, the
   endpoint `https://appweb.dane.gov.co/sipsaWS/SrvSipsaUpraBeanService` has not
   been reached in this validation cycle. Validate after Docker deployment.

5. **`// ...existing code...` placeholder comments in 4 handlers** —
   `CiudadIngestionHandler.java:115`, `SemanaIngestionHandler.java:103`,
   `AbasIngestionHandler.java:123`, `MesIngestionHandler.java:109` contain
   placeholder comments inside exception handlers. The code surrounding them is
   functional (catch → warn → flush → rethrow), but the comments indicate incomplete
   refinement of that error path. No functional impact detected; review in the
   architecture improvement phase.

## Post-Migration Recommendations

1. Add Testcontainers to enable `@SpringBootTest` without requiring a locally
   running PostgreSQL instance.
2. Consider migrating WireMock to `wiremock-spring-boot:4.x` for Spring Boot 4
   integration tests.
3. Review and upgrade GitHub CI workflows (none exist in the repository currently).
4. Add OWASP Dependency Check or Renovate to detect future vulnerable dependencies.
