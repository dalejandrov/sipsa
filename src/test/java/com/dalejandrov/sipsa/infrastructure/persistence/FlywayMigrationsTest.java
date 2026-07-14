package com.dalejandrov.sipsa.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Migration gate: applies every Flyway migration against a real PostgreSQL
 * (same major version as docker-compose.yml) and boots the full Spring context
 * with {@code ddl-auto: validate}.
 *
 * <p>This test exists because of two real incidents this class of check would
 * have caught (see ADR-009):
 * <ul>
 *   <li>Spring Boot 4 moved Flyway auto-configuration to the dedicated
 *       {@code spring-boot-flyway} module; without it, migrations silently
 *       never ran. The unit suite (H2, Flyway disabled) could not notice.</li>
 *   <li>Schema/entity drift is invisible until production startup, because the
 *       unit suite lets Hibernate create the H2 schema from the entities
 *       instead of validating the entities against the migrated schema.</li>
 * </ul>
 *
 * <p>The context boot itself is an assertion: with {@code ddl-auto: validate},
 * any mismatch between the JPA entities and the schema produced by the
 * migrations fails the test. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("Flyway migrations against real PostgreSQL (Testcontainers)")
class FlywayMigrationsTest {

    /** Same PostgreSQL major/minor as docker-compose.yml to validate what production runs. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway auto-configuration is active and applied at least one migration")
    void flywayAppliedMigrations() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("Schema history contains no failed migrations")
    void schemaHistoryHasNoFailures() {
        Integer failed = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false",
                Integer.class);

        assertThat(failed).isZero();
    }

    @Test
    @DisplayName("Every table owned by the application exists after migration")
    void allApplicationTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "ingestion_runs",
                "ingestion_audit",
                "ingestion_rejects",
                "sipsa_ciudad",
                "sipsa_parcial",
                "sipsa_mayoristas_semanal",
                "sipsa_mayoristas_mensual",
                "sipsa_abastecimientos_mensual");
    }

    @Test
    @DisplayName("Context boots with ddl-auto=validate: entities match the migrated schema")
    void entitiesMatchMigratedSchema() {
        // Reaching this point means Hibernate validated every @Entity against
        // the schema created exclusively by the Flyway migrations.
        assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
    }
}
