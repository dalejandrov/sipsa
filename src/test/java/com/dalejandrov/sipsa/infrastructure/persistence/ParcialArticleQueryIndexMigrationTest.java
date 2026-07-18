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

/**
 * Upgrade-path gate for V4 (TECH-124): a database already at V3 <b>with data</b> must
 * upgrade cleanly, keep every row and every pre-existing index, gain the article-filter
 * covering index, and the planner must actually use it for the endpoint's queries.
 * <p>
 * {@code FlywayMigrationsTest} covers the V1→V4 chain from an empty database; this class
 * covers the state real environments will be in when V4 arrives. The plan assertions run
 * against enough generated volume (60K rows, ANALYZEd) that the planner's index choice is
 * a real decision, not an artifact of a near-empty table; they assert only the chosen
 * index name, not the exact plan shape.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V4 upgrade: article-filter covering index added, data and prior indexes preserved")
class ParcialArticleQueryIndexMigrationTest {

    private static final String NEW_INDEX = "idx_sipsa_parcial_article_date";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Test
    @DisplayName("V3 → V4 with data: rows preserved, prior indexes intact, new index present and used")
    void upgradeFromV3AddsArticleIndexAndPreservesState() throws Exception {
        Flyway toV3 = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion("3"))
                .load();
        toV3.migrate();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement st = conn.createStatement()) {

            st.executeUpdate("""
                    INSERT INTO ingestion_runs (method_name, window_key, status)
                    VALUES ('promediosSipsaParcial', '2026-07-16', 'SUCCEEDED')""");
            // 60K rows across 12 articles (~5K rows each, ~8% selectivity), unique key_hash.
            st.executeUpdate("""
                    INSERT INTO sipsa_parcial
                        (key_hash, muni_id, fuen_id, futi_id, id_arti_semana, enma_fecha, ingestion_run_id)
                    SELECT
                        md5(g::text) || md5((g * 31)::text),
                        lpad(((g % 90) + 1)::text, 5, '0'),
                        (g % 40) + 1,
                        (g % 5) + 1,
                        (g % 12) + 1,
                        TIMESTAMPTZ '2024-01-01 05:00:00Z' + ((g % 900) || ' hours')::interval,
                        1
                    FROM generate_series(1, 60000) g""");

            assertThat(countIndex(st, NEW_INDEX)).as("index absent at V3").isZero();

            Flyway toLatest = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .load();
            toLatest.migrate();

            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '4' AND success = true")) {
                rs.next();
                assertThat(rs.getInt(1)).as("V4 applied successfully").isEqualTo(1);
            }

            // New index present, every pre-existing index untouched.
            assertThat(countIndex(st, NEW_INDEX)).as("covering index created by V4").isEqualTo(1);
            for (String preserved : new String[]{
                    "sipsa_parcial_pkey",
                    "sipsa_parcial_key_hash_key",
                    "idx_sipsa_parcial_fecha",
                    "idx_sipsa_parcial_muni",
                    "idx_sipsa_parcial_ingestion_run",
                    "idx_sipsa_parcial_natural_key"}) {
                assertThat(countIndex(st, preserved)).as("index preserved: " + preserved).isEqualTo(1);
            }

            // No data change.
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sipsa_parcial")) {
                rs.next();
                assertThat(rs.getLong(1)).as("rows preserved").isEqualTo(60000L);
            }

            // Definition matches the migration: key columns + covered id.
            try (ResultSet rs = st.executeQuery(
                    "SELECT indexdef FROM pg_indexes WHERE indexname = '" + NEW_INDEX + "'")) {
                rs.next();
                assertThat(rs.getString(1))
                        .contains("id_arti_semana")
                        .contains("enma_fecha DESC")
                        .contains("INCLUDE (id)");
            }

            st.execute("ANALYZE sipsa_parcial");

            // The per-page count query (Hibernate emits count(id)) uses the covering index.
            assertThat(plan(st,
                    "SELECT count(id) FROM sipsa_parcial WHERE id_arti_semana = 7"))
                    .as("count query served by the covering index")
                    .contains(NEW_INDEX);

            // The article-filtered, date-ordered page query is index-served (the planner may
            // legitimately pick either the new index or a backward walk of the date index for
            // frequent articles, so assert structure — no sequential scan — not a single plan).
            assertThat(plan(st, """
                    SELECT * FROM sipsa_parcial WHERE id_arti_semana = 7
                    ORDER BY enma_fecha DESC OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY"""))
                    .as("page query never falls back to a sequential scan")
                    .doesNotContain("Seq Scan");

            // A non-existent article was the pre-V4 worst case (full parallel seq scan);
            // with the index the planner resolves it from the article index directly.
            assertThat(plan(st, """
                    SELECT * FROM sipsa_parcial WHERE id_arti_semana = 9999
                    ORDER BY enma_fecha DESC OFFSET 0 ROWS FETCH FIRST 20 ROWS ONLY"""))
                    .as("non-existent article resolved from the covering index")
                    .contains(NEW_INDEX);
        }
    }

    private static String plan(Statement st, String sql) throws SQLException {
        StringBuilder plan = new StringBuilder();
        try (ResultSet rs = st.executeQuery("EXPLAIN " + sql)) {
            while (rs.next()) {
                plan.append(rs.getString(1)).append('\n');
            }
        }
        return plan.toString();
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
