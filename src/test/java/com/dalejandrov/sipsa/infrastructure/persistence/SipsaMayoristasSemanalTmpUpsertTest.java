package com.dalejandrov.sipsa.infrastructure.persistence;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasSemanalRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasSemanalRepository.UpsertMetrics;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code SipsaMayoristasSemanalRepository.upsertTmpBatch()} no longer issues one
 * {@code findByTmpId} SELECT per (deduplicated) item — replaced by a single atomic
 * {@code INSERT … ON CONFLICT (tmp_mayo_sem_id) DO NOTHING} JDBC batch, the same technique
 * its business-key sibling {@code upsertFallbackBatch} already uses (TECH-060) and
 * {@code SipsaParcialRepository.batchUpsert} uses (TECH-117). Runs against real PostgreSQL
 * via Testcontainers — {@code ON CONFLICT} syntax and the {@code ux_semana_tmp} unique
 * constraint are both PostgreSQL-specific, so H2 cannot exercise this path.
 * <p>
 * Every case here preserves the pre-existing semantics exactly: skip-only (never update),
 * in-batch duplicate tmpIds collapse to the last occurrence, and a {@code null} tmpId is
 * never sent to the insert (mirrors the original {@code if (tmpMayoSemId != null)} guard).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("SipsaMayoristasSemanal tmp batch upsert, atomic ON CONFLICT DO NOTHING")
class SipsaMayoristasSemanalTmpUpsertTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private SipsaMayoristasSemanalRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long runId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sipsa_mayoristas_semanal");
        runId = jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaSemanaMadr', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "test-" + System.nanoTime());
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().clear();
    }

    private SipsaMayoristasSemanal item(Long tmpId, long artiId) {
        return SipsaMayoristasSemanal.builder()
                .tmpMayoSemId(tmpId)
                .artiId(artiId)
                .artiNombre("ARTICULO " + artiId)
                .fuenId(10L)
                .fuenNombre("FUENTE 10")
                .futiId(1L)
                .fechaIni(LocalDate.of(2026, 7, 6))
                .promedioKg(new BigDecimal("1000.00"))
                .maximoKg(new BigDecimal("1100.00"))
                .minimoKg(new BigDecimal("900.00"))
                .ingestionRunId(runId)
                .build();
    }

    @Test
    @DisplayName("empty batch: zero counts")
    void emptyBatch() {
        UpsertMetrics metrics = repository.upsertTmpBatch(List.of());

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isZero();
    }

    @Test
    @DisplayName("one new record: inserted=1")
    void oneNewRecord() {
        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(item(100L, 1L)));

        assertThat(metrics.inserted()).isEqualTo(1);
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("all already existing: all skipped, none inserted")
    void allExisting() {
        repository.upsertTmpBatch(List.of(item(100L, 1L), item(101L, 2L)));

        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(item(100L, 1L), item(101L, 2L)));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isEqualTo(2L);
    }

    @Test
    @DisplayName("mix of new and existing: correct split")
    void mixNewAndExisting() {
        repository.upsertTmpBatch(List.of(item(100L, 1L), item(101L, 2L)));

        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(
                item(100L, 1L),   // existing -> skip
                item(101L, 2L),   // existing -> skip
                item(102L, 3L))); // new -> insert

        assertThat(metrics.inserted()).isEqualTo(1);
        assertThat(metrics.skipped()).isEqualTo(2);
    }

    @Test
    @DisplayName("duplicate tmpId within the batch: last occurrence wins, silently uncounted")
    void intraBatchDuplicates_notDoubleCounted() {
        SipsaMayoristasSemanal dup1 = item(100L, 1L);
        dup1.setPromedioKg(new BigDecimal("100.00"));
        SipsaMayoristasSemanal dup2 = item(100L, 1L);
        dup2.setPromedioKg(new BigDecimal("200.00")); // last occurrence - this one should win
        SipsaMayoristasSemanal other = item(101L, 2L);

        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(dup1, dup2, other));

        assertThat(metrics.inserted()).isEqualTo(2);
        assertThat(metrics.skipped()).isZero();

        BigDecimal stored = jdbc.queryForObject(
                "SELECT promedio_kg FROM sipsa_mayoristas_semanal WHERE tmp_mayo_sem_id = 100",
                BigDecimal.class);
        assertThat(stored).as("last occurrence in the batch wins").isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("null tmpId: item is never sent to the insert (matches the removed guard)")
    void nullTmpId_neverInserted() {
        SipsaMayoristasSemanal nullTmp = item(null, 1L);

        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(nullTmp));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isZero();
    }

    @Test
    @DisplayName("skip is never an update: an existing row's stored values are untouched")
    void skipNeverUpdates() {
        SipsaMayoristasSemanal original = item(100L, 1L);
        original.setPromedioKg(new BigDecimal("111.11"));
        repository.upsertTmpBatch(List.of(original));

        SipsaMayoristasSemanal attemptedUpdate = item(100L, 1L);
        attemptedUpdate.setPromedioKg(new BigDecimal("999.99"));
        UpsertMetrics metrics = repository.upsertTmpBatch(List.of(attemptedUpdate));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isEqualTo(1);
        BigDecimal storedPrice = jdbc.queryForObject(
                "SELECT promedio_kg FROM sipsa_mayoristas_semanal WHERE tmp_mayo_sem_id = 100",
                BigDecimal.class);
        assertThat(storedPrice).as("skip means skip - the existing row's price is unchanged").isEqualByComparingTo("111.11");
    }

    @Test
    @DisplayName("rollback: no row persists if the surrounding transaction rolls back")
    void rollback_noRowsPersisted() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            repository.upsertTmpBatch(List.of(item(100L, 1L)));
            throw new RuntimeException("simulated failure after insert");
        })).hasMessage("simulated failure after insert");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class))
                .as("the rolled-back transaction leaves no row behind").isZero();
    }

    @Test
    @DisplayName("structural: zero Hibernate-tracked queries (no per-row SELECT)")
    void noPerRowHibernateQuery() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        List<SipsaMayoristasSemanal> batch = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) {
            batch.add(item((long) (200 + i), i));
        }

        UpsertMetrics metrics = repository.upsertTmpBatch(batch);

        assertThat(metrics.inserted()).isEqualTo(50);
        assertThat(statistics.getQueryExecutionCount())
                .as("no Hibernate query proportional to batch size").isZero();
    }

    @Test
    @DisplayName("concurrent collision: two transactions racing on the same tmpId - loser is skipped, not failed")
    void concurrentCollision_loserSkippedNotFailed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch winnerCommitted = new CountDownLatch(1);
            TransactionTemplate tx = new TransactionTemplate(txManager);

            Future<UpsertMetrics> winner = executor.submit(() -> tx.execute(status ->
                    repository.upsertTmpBatch(List.of(item(100L, 1L)))));
            UpsertMetrics winnerMetrics = winner.get(30, TimeUnit.SECONDS);
            winnerCommitted.countDown();

            Future<UpsertMetrics> racer = executor.submit(() ->
                    repository.upsertTmpBatch(List.of(item(100L, 1L))));
            UpsertMetrics racerMetrics = racer.get(30, TimeUnit.SECONDS);

            assertThat(winnerMetrics.inserted()).isEqualTo(1);
            assertThat(racerMetrics.inserted())
                    .as("the racer must not fail with a unique-violation exception").isZero();
            assertThat(racerMetrics.skipped()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class))
                    .as("exactly one row for the contested tmpId").isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("concurrent constraint enforcement: the ux_semana_tmp constraint is real and backs ON CONFLICT")
    void uniqueConstraintExists_backsOnConflict() {
        jdbc.update("""
                INSERT INTO sipsa_mayoristas_semanal
                    (tmp_mayo_sem_id, arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_ini, ingestion_run_id)
                VALUES (100, 1, 'X', 10, 'Y', 1, ?, ?)""", java.sql.Date.valueOf(LocalDate.of(2026, 7, 6)), runId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sipsa_mayoristas_semanal
                    (tmp_mayo_sem_id, arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_ini, ingestion_run_id)
                VALUES (100, 2, 'X2', 10, 'Y2', 1, ?, ?)""", java.sql.Date.valueOf(LocalDate.of(2026, 7, 6)), runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_semana_tmp");
    }
}
