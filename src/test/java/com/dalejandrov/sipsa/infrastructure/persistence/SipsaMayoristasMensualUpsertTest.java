package com.dalejandrov.sipsa.infrastructure.persistence;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasMensualRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasMensualRepository.UpsertMetrics;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code SipsaMayoristasMensualRepository.upsertTmpBatch()} and
 * {@code upsertFallbackBatch()} no longer issue one per-row SELECT per (deduplicated)
 * item — both replaced by a single atomic {@code INSERT … ON CONFLICT DO NOTHING} JDBC
 * batch, the same technique {@code SipsaMayoristasSemanalRepository} uses (TECH-060) and
 * {@code SipsaParcialRepository.batchUpsert} uses (TECH-117). Runs against real PostgreSQL
 * via Testcontainers — {@code ON CONFLICT} syntax and the {@code ux_mes_tmp}/
 * {@code ux_mes_fallback} unique constraints are both PostgreSQL-specific, so H2 cannot
 * exercise this path.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("SipsaMayoristasMensual batch upsert (tmp + fallback), atomic ON CONFLICT DO NOTHING")
class SipsaMayoristasMensualUpsertTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private SipsaMayoristasMensualRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long runId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sipsa_mayoristas_mensual");
        runId = jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaMesMadr', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "test-" + System.nanoTime());
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().clear();
    }

    private SipsaMayoristasMensual tmpItem(Long tmpId, long artiId) {
        return SipsaMayoristasMensual.builder()
                .tmpMayoMesId(tmpId)
                .artiId(artiId)
                .artiNombre("ARTICULO " + artiId)
                .fuenId(10L)
                .fuenNombre("FUENTE 10")
                .futiId(1L)
                .fechaMesIni(LocalDate.of(2026, 7, 1))
                .promedioKg(new BigDecimal("1000.00"))
                .ingestionRunId(runId)
                .build();
    }

    private SipsaMayoristasMensual fallbackItem(long artiId, long fuenId, LocalDate fecha) {
        return SipsaMayoristasMensual.builder()
                .artiId(artiId)
                .artiNombre("ARTICULO " + artiId)
                .fuenId(fuenId)
                .fuenNombre("FUENTE " + fuenId)
                .futiId(1L)
                .fechaMesIni(fecha)
                .promedioKg(new BigDecimal("1000.00"))
                .ingestionRunId(runId)
                .build();
    }

    // ---------------------------------------------------------------
    // Tmp path
    // ---------------------------------------------------------------

    @Test
    @DisplayName("tmp: empty batch, one new, mix of new/existing")
    void tmpBatch_basicCases() {
        assertThat(repository.upsertTmpBatch(List.of()).inserted()).isZero();

        UpsertMetrics one = repository.upsertTmpBatch(List.of(tmpItem(100L, 1L)));
        assertThat(one.inserted()).isEqualTo(1);
        assertThat(one.skipped()).isZero();

        UpsertMetrics mix = repository.upsertTmpBatch(List.of(tmpItem(100L, 1L), tmpItem(101L, 2L)));
        assertThat(mix.inserted()).isEqualTo(1);
        assertThat(mix.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("tmp: duplicate tmpId within batch collapses to last occurrence")
    void tmpBatch_intraBatchDuplicate() {
        SipsaMayoristasMensual dup1 = tmpItem(100L, 1L);
        dup1.setPromedioKg(new BigDecimal("100.00"));
        SipsaMayoristasMensual dup2 = tmpItem(100L, 1L);
        dup2.setPromedioKg(new BigDecimal("200.00"));

        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(dup1, dup2));

        assertThat(metrics.inserted()).isEqualTo(1);
        BigDecimal stored = jdbc.queryForObject(
                "SELECT promedio_kg FROM sipsa_mayoristas_mensual WHERE tmp_mayo_mes_id = 100", BigDecimal.class);
        assertThat(stored).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("tmp: null tmpId is never inserted")
    void tmpBatch_nullTmpIdNeverInserted() {
        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(tmpItem(null, 1L)));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_mensual", Long.class)).isZero();
    }

    @Test
    @DisplayName("tmp: skip never updates the stored row")
    void tmpBatch_skipNeverUpdates() {
        SipsaMayoristasMensual original = tmpItem(100L, 1L);
        original.setPromedioKg(new BigDecimal("111.11"));
        repository.upsertTmpBatch(List.of(original));

        SipsaMayoristasMensual attempted = tmpItem(100L, 1L);
        attempted.setPromedioKg(new BigDecimal("999.99"));
        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(attempted));

        assertThat(metrics.skipped()).isEqualTo(1);
        BigDecimal stored = jdbc.queryForObject(
                "SELECT promedio_kg FROM sipsa_mayoristas_mensual WHERE tmp_mayo_mes_id = 100", BigDecimal.class);
        assertThat(stored).isEqualByComparingTo("111.11");
    }

    @Test
    @DisplayName("tmp: rollback leaves no row behind")
    void tmpBatch_rollback() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            repository.upsertTmpBatch(List.of(tmpItem(100L, 1L)));
            throw new RuntimeException("simulated failure");
        })).hasMessage("simulated failure");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_mensual", Long.class)).isZero();
    }

    @Test
    @DisplayName("tmp: no per-row Hibernate query regardless of batch size")
    void tmpBatch_noPerRowHibernateQuery() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<SipsaMayoristasMensual> batch = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            batch.add(tmpItem((long) (200 + i), i));
        }

        UpsertMetrics metrics = repository.upsertTmpBatch(batch);

        assertThat(metrics.inserted()).isEqualTo(50);
        assertThat(statistics.getQueryExecutionCount()).isZero();
    }

    @Test
    @DisplayName("tmp: concurrent collision - loser skipped, not failed")
    void tmpBatch_concurrentCollision() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate tx = new TransactionTemplate(txManager);
            Future<UpsertMetrics> winner = executor.submit(() -> tx.execute(status ->
                    repository.upsertTmpBatch(List.of(tmpItem(100L, 1L)))));
            UpsertMetrics winnerMetrics = winner.get(30, TimeUnit.SECONDS);

            Future<UpsertMetrics> racer = executor.submit(() ->
                    repository.upsertTmpBatch(List.of(tmpItem(100L, 1L))));
            UpsertMetrics racerMetrics = racer.get(30, TimeUnit.SECONDS);

            assertThat(winnerMetrics.inserted()).isEqualTo(1);
            assertThat(racerMetrics.inserted()).isZero();
            assertThat(racerMetrics.skipped()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_mensual", Long.class)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("tmp: ux_mes_tmp constraint is real and backs ON CONFLICT")
    void tmpBatch_uniqueConstraintExists() {
        jdbc.update("""
                INSERT INTO sipsa_mayoristas_mensual
                    (tmp_mayo_mes_id, arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_mes_ini, ingestion_run_id)
                VALUES (100, 1, 'X', 10, 'Y', 1, ?, ?)""", java.sql.Date.valueOf(LocalDate.of(2026, 7, 1)), runId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sipsa_mayoristas_mensual
                    (tmp_mayo_mes_id, arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_mes_ini, ingestion_run_id)
                VALUES (100, 2, 'X2', 10, 'Y2', 1, ?, ?)""", java.sql.Date.valueOf(LocalDate.of(2026, 7, 1)), runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_mes_tmp");
    }

    // ---------------------------------------------------------------
    // Fallback path
    // ---------------------------------------------------------------

    @Test
    @DisplayName("fallback: empty batch, one new, mix of new/existing")
    void fallbackBatch_basicCases() {
        LocalDate fecha = LocalDate.of(2026, 7, 1);
        assertThat(repository.upsertFallbackBatch(List.of()).inserted()).isZero();

        UpsertMetrics one = repository.upsertFallbackBatch(List.of(fallbackItem(1L, 10L, fecha)));
        assertThat(one.inserted()).isEqualTo(1);

        UpsertMetrics mix = repository.upsertFallbackBatch(List.of(
                fallbackItem(1L, 10L, fecha), fallbackItem(2L, 10L, fecha)));
        assertThat(mix.inserted()).isEqualTo(1);
        assertThat(mix.skipped()).isEqualTo(1);
    }

    @Test
    @DisplayName("fallback: skip never updates the stored row")
    void fallbackBatch_skipNeverUpdates() {
        LocalDate fecha = LocalDate.of(2026, 7, 1);
        SipsaMayoristasMensual original = fallbackItem(1L, 10L, fecha);
        original.setPromedioKg(new BigDecimal("111.11"));
        repository.upsertFallbackBatch(List.of(original));

        SipsaMayoristasMensual attempted = fallbackItem(1L, 10L, fecha);
        attempted.setPromedioKg(new BigDecimal("999.99"));
        UpsertMetrics metrics = repository.upsertFallbackBatch(List.of(attempted));

        assertThat(metrics.skipped()).isEqualTo(1);
        BigDecimal stored = jdbc.queryForObject(
                "SELECT promedio_kg FROM sipsa_mayoristas_mensual WHERE arti_id = 1 AND fuen_id = 10", BigDecimal.class);
        assertThat(stored).isEqualByComparingTo("111.11");
    }

    @Test
    @DisplayName("fallback: rollback leaves no row behind")
    void fallbackBatch_rollback() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        LocalDate fecha = LocalDate.of(2026, 7, 1);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            repository.upsertFallbackBatch(List.of(fallbackItem(1L, 10L, fecha)));
            throw new RuntimeException("simulated failure");
        })).hasMessage("simulated failure");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_mensual", Long.class)).isZero();
    }

    @Test
    @DisplayName("fallback: no per-row Hibernate query regardless of batch size")
    void fallbackBatch_noPerRowHibernateQuery() {
        LocalDate fecha = LocalDate.of(2026, 7, 1);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<SipsaMayoristasMensual> batch = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            batch.add(fallbackItem(i, 10L, fecha));
        }

        UpsertMetrics metrics = repository.upsertFallbackBatch(batch);

        assertThat(metrics.inserted()).isEqualTo(50);
        assertThat(statistics.getQueryExecutionCount()).isZero();
    }

    @Test
    @DisplayName("fallback: concurrent collision - loser skipped, not failed")
    void fallbackBatch_concurrentCollision() throws Exception {
        LocalDate fecha = LocalDate.of(2026, 7, 1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate tx = new TransactionTemplate(txManager);
            Future<UpsertMetrics> winner = executor.submit(() -> tx.execute(status ->
                    repository.upsertFallbackBatch(List.of(fallbackItem(1L, 10L, fecha)))));
            UpsertMetrics winnerMetrics = winner.get(30, TimeUnit.SECONDS);

            Future<UpsertMetrics> racer = executor.submit(() ->
                    repository.upsertFallbackBatch(List.of(fallbackItem(1L, 10L, fecha))));
            UpsertMetrics racerMetrics = racer.get(30, TimeUnit.SECONDS);

            assertThat(winnerMetrics.inserted()).isEqualTo(1);
            assertThat(racerMetrics.inserted()).isZero();
            assertThat(racerMetrics.skipped()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_mensual", Long.class)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("fallback: ux_mes_fallback constraint is real and backs ON CONFLICT")
    void fallbackBatch_uniqueConstraintExists() {
        LocalDate fecha = LocalDate.of(2026, 7, 1);
        jdbc.update("""
                INSERT INTO sipsa_mayoristas_mensual
                    (arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_mes_ini, ingestion_run_id)
                VALUES (1, 'X', 10, 'Y', 1, ?, ?)""", java.sql.Date.valueOf(fecha), runId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sipsa_mayoristas_mensual
                    (arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_mes_ini, ingestion_run_id)
                VALUES (1, 'X2', 10, 'Y2', 1, ?, ?)""", java.sql.Date.valueOf(fecha), runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_mes_fallback");
    }
}
