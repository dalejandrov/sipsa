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
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Upgrade-path gate for V3 (TECH-119): a database already at V2 <b>with data</b> must
 * upgrade cleanly, lose ONLY the redundant explicit {@code key_hash} index, keep every
 * row and — critically — keep the {@code UNIQUE (key_hash)} protection enforced by the
 * constraint's own backing index.
 * <p>
 * {@code FlywayMigrationsTest} covers the V1→V2→V3 chain from an empty database; this
 * class covers the state real environments will be in when V3 arrives.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V3 upgrade: redundant key_hash index removal preserves data and uniqueness")
class ParcialKeyHashIndexMigrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Test
    @DisplayName("V2 → V3 with data: rows preserved, redundant index gone, unique violation still enforced")
    void upgradeFromV2PreservesDataAndUniqueness() throws Exception {
        Flyway toV2 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion("2"))
                .load();
        toV2.migrate();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = conn.createStatement()) {

            st.executeUpdate("""
                    INSERT INTO ingestion_runs (method_name, window_key, status)
                    VALUES ('promediosSipsaParcial', '2026-07-16', 'SUCCEEDED')""");
            st.executeUpdate("""
                    INSERT INTO sipsa_parcial
                        (key_hash, muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha, ingestion_run_id)
                    VALUES
                        ('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                         '05001', 10, 2, 101, '2026-07-15T05:00:00Z', 1),
                        ('bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
                         '05002', 10, 2, 102, '2026-07-15T05:00:00Z', 1)""");

            // Redundant index exists at V2 (created by V1).
            assertThat(countIndex(st, "idx_sipsa_parcial_key_hash")).isEqualTo(1);

            Flyway toLatest = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load();
            toLatest.migrate();

            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '3' AND success = true")) {
                rs.next();
                assertThat(rs.getInt(1)).as("V3 applied successfully").isEqualTo(1);
            }

            // Only the redundant index is gone; the constraint's backing index remains.
            assertThat(countIndex(st, "idx_sipsa_parcial_key_hash"))
                    .as("redundant explicit index dropped").isZero();
            assertThat(countIndex(st, "sipsa_parcial_key_hash_key"))
                    .as("UNIQUE constraint backing index preserved").isEqualTo(1);

            // No row was deleted; key_hash values are intact.
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*), COUNT(DISTINCT key_hash) FROM sipsa_parcial")) {
                rs.next();
                assertThat(rs.getInt(1)).as("rows preserved").isEqualTo(2);
                assertThat(rs.getInt(2)).as("key_hash values preserved").isEqualTo(2);
            }

            // The hash lookup can only use the unique index now. On this 2-row table the
            // planner rightly prefers a seq scan (any CREATE INDEX in a later migration
            // refreshes reltuples), so disable seq scans to ask the structural question:
            // "which index serves key_hash lookups?" — it must be the constraint's own.
            st.execute("SET enable_seqscan = off");
            try (ResultSet rs = st.executeQuery(
                    "EXPLAIN SELECT * FROM sipsa_parcial WHERE key_hash = "
                            + "'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'")) {
                StringBuilder plan = new StringBuilder();
                while (rs.next()) {
                    plan.append(rs.getString(1)).append('\n');
                }
                assertThat(plan.toString())
                        .as("planner uses the UNIQUE constraint's index for hash lookups")
                        .contains("sipsa_parcial_key_hash_key");
            } finally {
                st.execute("RESET enable_seqscan");
            }

            // Integrity after V3: inserting a duplicate key_hash must fail.
            assertThatExceptionOfType(SQLException.class)
                    .as("UNIQUE (key_hash) still rejects duplicates after dropping the redundant index")
                    .isThrownBy(() -> st.executeUpdate("""
                            INSERT INTO sipsa_parcial
                                (key_hash, muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha, ingestion_run_id)
                            VALUES
                                ('aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                                 '05001', 10, 2, 101, '2026-07-15T05:00:00Z', 1)"""))
                    .withMessageContaining("sipsa_parcial_key_hash_key");
        }
    }

    private static int countIndex(Statement st, String indexName) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'sipsa_parcial' "
                        + "AND indexname = '" + indexName + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
