package com.dalejandrov.sipsa.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upgrade-path gate for the V2 natural-key support index (TECH-011 expand phase).
 * <p>
 * {@code FlywayMigrationsTest} proves the full chain from an EMPTY database; this test
 * proves the scenario a real environment will face: a database already at V1 <b>with
 * legacy duplicated data</b> (same natural key persisted twice under different random
 * UUID {@code key_hash} values — exactly what TECH-012 measured). V2 must apply cleanly
 * on top: the support index is non-unique by design, so pre-existing duplicates must not
 * break the migration, and the migration must not touch any data.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V2 upgrade on a V1 database with legacy duplicates")
class ParcialMigrationUpgradeTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Test
    @DisplayName("V1 → latest applies over duplicated legacy data without modifying it")
    void upgradeFromV1WithLegacyDuplicates() throws Exception {
        Flyway toV1 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion("1"))
                .load();
        toV1.migrate();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = conn.createStatement()) {

            // Legacy state: one run, the same natural key twice under two random UUIDs.
            st.executeUpdate("""
                    INSERT INTO ingestion_runs (method_name, window_key, status)
                    VALUES ('promediosSipsaParcial', '2026-07-15', 'SUCCEEDED')""");
            st.executeUpdate("""
                    INSERT INTO sipsa_parcial
                        (key_hash, muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha, ingestion_run_id)
                    VALUES
                        ('11111111-1111-1111-1111-111111111111', '05001', 10, 2, 101,
                         '2026-07-15T05:00:00Z', 1),
                        ('22222222-2222-2222-2222-222222222222', '05001', 10, 2, 101,
                         '2026-07-15T05:00:00Z', 1)""");

            Flyway toLatest = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load();
            toLatest.migrate();

            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = true")) {
                rs.next();
                assertThat(rs.getInt(1)).as("V2 applied successfully").isEqualTo(1);
            }
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sipsa_parcial' "
                            + "AND indexname = 'idx_sipsa_parcial_natural_key'")) {
                rs.next();
                assertThat(rs.getInt(1)).as("natural-key support index exists").isEqualTo(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sipsa_parcial")) {
                rs.next();
                assertThat(rs.getInt(1))
                        .as("expand migration performs no data changes: both duplicates remain")
                        .isEqualTo(2);
            }
        }
    }
}
