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
    @DisplayName("Flyway auto-configuration is active and applied the migration chain")
    void flywayAppliedMigrations() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).hasSizeGreaterThanOrEqualTo(1);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
    }

    @Test
    @DisplayName("The 4 DANE calendar-date columns (5 columns across 5 tables) are DATE, not TIMESTAMPTZ (TECH-104)")
    void calendarDateColumnsAreDate() {
        record ColumnRef(String table, String column) {}
        List<ColumnRef> calendarColumns = List.of(
                new ColumnRef("sipsa_ciudad", "fecha_captura"),
                new ColumnRef("sipsa_parcial", "enma_fecha"),
                new ColumnRef("sipsa_mayoristas_semanal", "fecha_ini"),
                new ColumnRef("sipsa_mayoristas_mensual", "fecha_mes_ini"),
                new ColumnRef("sipsa_abastecimientos_mensual", "fecha_mes_ini"));

        for (ColumnRef ref : calendarColumns) {
            String dataType = jdbc.queryForObject(
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_name = ? AND column_name = ?",
                    String.class, ref.table(), ref.column());
            assertThat(dataType).as("%s.%s", ref.table(), ref.column()).isEqualTo("date");
        }

        // fecha_creacion is a genuine instant, not a calendar date, and must stay timestamptz.
        String untouched = jdbc.queryForObject(
                "SELECT data_type FROM information_schema.columns "
                        + "WHERE table_name = 'sipsa_ciudad' AND column_name = 'fecha_creacion'",
                String.class);
        assertThat(untouched).isEqualTo("timestamp with time zone");
    }

    @Test
    @DisplayName("sipsa_parcial article-filter covering index exists with the expected shape (TECH-124)")
    void parcialArticleQueryIndexExists() {
        String indexdef = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'sipsa_parcial' "
                        + "AND indexname = 'idx_sipsa_parcial_article_date'",
                String.class);

        assertThat(indexdef)
                .contains("id_arti_semana")
                .contains("enma_fecha DESC")
                .contains("INCLUDE (id)");
    }

    @Test
    @DisplayName("sipsa_parcial has no redundant key_hash index; the UNIQUE constraint index remains (TECH-119)")
    void redundantKeyHashIndexAbsentAndConstraintPreserved() {
        Integer redundant = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sipsa_parcial' "
                        + "AND indexname = 'idx_sipsa_parcial_key_hash'",
                Integer.class);
        Integer constraintBacking = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sipsa_parcial' "
                        + "AND indexname = 'sipsa_parcial_key_hash_key'",
                Integer.class);
        Integer uniqueConstraint = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint con JOIN pg_class tbl ON tbl.oid = con.conrelid "
                        + "WHERE tbl.relname = 'sipsa_parcial' AND con.contype = 'u' "
                        + "AND con.conname = 'sipsa_parcial_key_hash_key'",
                Integer.class);

        assertThat(redundant).as("no explicit duplicate index over key_hash").isZero();
        assertThat(constraintBacking).as("UNIQUE constraint backing index preserved").isEqualTo(1);
        assertThat(uniqueConstraint).as("UNIQUE (key_hash) constraint still active").isEqualTo(1);
    }

    @Test
    @DisplayName("sipsa_parcial natural-key support index exists (TECH-011)")
    void parcialNaturalKeyIndexExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sipsa_parcial' "
                        + "AND indexname = 'idx_sipsa_parcial_natural_key'",
                Integer.class);

        assertThat(count).isEqualTo(1);
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
