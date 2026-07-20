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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TECH-060: {@code SipsaMayoristasSemanalRepository.upsertFallbackBatch()} no longer
 * issues one {@code findByBusinessKeys} SELECT per (deduplicated) item — replaced by a
 * single atomic {@code INSERT … ON CONFLICT (arti_id, fuen_id, fecha_ini) DO NOTHING}
 * JDBC batch, the same technique {@code SipsaParcialRepository.batchUpsert} already uses
 * (TECH-117). Runs against real PostgreSQL via Testcontainers — {@code ON CONFLICT} syntax
 * and the {@code ux_semana_fallback} unique constraint are both PostgreSQL-specific, so H2
 * cannot exercise this path.
 * <p>
 * Every case here preserves the pre-existing semantics exactly: skip-only (never update),
 * in-batch duplicates silently uncounted (matching the original string-key
 * {@code LinkedHashMap} dedup — {@code inserted + skipped} equals the number of *unique*
 * keys, not {@code items.size()}), and a {@code null} business-key component always
 * inserts (the removed per-row lookup could never match a {@code null} parameter either).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-060: SipsaMayoristasSemanal fallback batch upsert, no N+1")
class SipsaMayoristasSemanalFallbackUpsertTest {

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

    private SipsaMayoristasSemanal item(long artiId, long fuenId, Instant fechaIni) {
        return SipsaMayoristasSemanal.builder()
                .artiId(artiId)
                .artiNombre("ARTICULO " + artiId)
                .fuenId(fuenId)
                .fuenNombre("FUENTE " + fuenId)
                .futiId(1L)
                .fechaIni(fechaIni)
                .promedioKg(new BigDecimal("1000.00"))
                .maximoKg(new BigDecimal("1100.00"))
                .minimoKg(new BigDecimal("900.00"))
                .ingestionRunId(runId)
                .build();
    }

    // -----------------------------------------------------------------------
    // 1-6, 10-12: functional cases
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("1. empty batch: no query, zero counts")
    void emptyBatch() {
        UpsertMetrics metrics = repository.upsertFallbackBatch(List.of());

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isZero();
    }

    @Test
    @DisplayName("2. one new record: inserted=1")
    void oneNewRecord() {
        UpsertMetrics metrics = repository.upsertFallbackBatch(
                List.of(item(1L, 10L, Instant.parse("2026-07-06T00:00:00Z"))));

        assertThat(metrics.inserted()).isEqualTo(1);
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("3. several new records: all inserted")
    void severalNewRecords() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        List<SipsaMayoristasSemanal> batch = List.of(
                item(1L, 10L, fecha), item(2L, 10L, fecha), item(3L, 10L, fecha),
                item(4L, 10L, fecha), item(5L, 10L, fecha));

        UpsertMetrics metrics = repository.upsertFallbackBatch(batch);

        assertThat(metrics.inserted()).isEqualTo(5);
        assertThat(metrics.skipped()).isZero();
    }

    @Test
    @DisplayName("4. all already existing: all skipped, none inserted")
    void allExisting() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        List<SipsaMayoristasSemanal> batch = List.of(
                item(1L, 10L, fecha), item(2L, 10L, fecha), item(3L, 10L, fecha));
        repository.upsertFallbackBatch(batch);

        UpsertMetrics metrics = repository.upsertFallbackBatch(List.of(
                item(1L, 10L, fecha), item(2L, 10L, fecha), item(3L, 10L, fecha)));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isEqualTo(3L);
    }

    @Test
    @DisplayName("5. mix of new and existing: correct split")
    void mixNewAndExisting() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        repository.upsertFallbackBatch(List.of(item(1L, 10L, fecha), item(2L, 10L, fecha)));

        UpsertMetrics metrics = repository.upsertFallbackBatch(List.of(
                item(1L, 10L, fecha),  // existing -> skip
                item(2L, 10L, fecha),  // existing -> skip
                item(3L, 10L, fecha),  // new -> insert
                item(4L, 10L, fecha),  // new -> insert
                item(5L, 10L, fecha)));// new -> insert

        assertThat(metrics.inserted()).isEqualTo(3);
        assertThat(metrics.skipped()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class)).isEqualTo(5L);
    }

    @Test
    @DisplayName("6. duplicates within the batch: last occurrence wins, silently uncounted (matches prior behavior)")
    void intraBatchDuplicates_notDoubleCounted() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        SipsaMayoristasSemanal dup1 = item(1L, 10L, fecha);
        dup1.setPromedioKg(new BigDecimal("100.00"));
        SipsaMayoristasSemanal dup2 = item(1L, 10L, fecha);
        dup2.setPromedioKg(new BigDecimal("200.00")); // last occurrence - this one should win
        SipsaMayoristasSemanal other = item(2L, 10L, fecha);

        UpsertMetrics metrics = repository.upsertFallbackBatch(List.of(dup1, dup2, other));

        // 3 items in, but only 2 unique keys -> inserted + skipped == 2, not 3.
        assertThat(metrics.inserted()).isEqualTo(2);
        assertThat(metrics.skipped()).isZero();
        assertThat(metrics.inserted() + metrics.skipped())
                .as("in-batch duplicate is silently dropped, not counted as inserted or skipped")
                .isEqualTo(2);

        BigDecimal stored = jdbc.queryForObject(
                "SELECT promedio_kg FROM sipsa_mayoristas_semanal WHERE arti_id = 1 AND fuen_id = 10",
                BigDecimal.class);
        assertThat(stored).as("last occurrence in the batch wins").isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("7. null business-key components: always insert, never match an existing row (mirrors the removed per-row lookup)")
    void nullBusinessKeyComponents_alwaysInsert() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        SipsaMayoristasSemanal nullFecha = item(1L, 10L, null);

        UpsertMetrics first = repository.upsertFallbackBatch(List.of(nullFecha));
        assertThat(first.inserted()).isEqualTo(1);
        assertThat(first.skipped()).isZero();

        // A second, separate call with an identical null-key row must ALSO insert - a
        // NULL component can never satisfy the unique constraint's conflict target, so
        // it never resolves as a duplicate, exactly like findByBusinessKeys(1, 10, null)
        // always returned empty under the previous implementation.
        UpsertMetrics second = repository.upsertFallbackBatch(List.of(item(1L, 10L, null)));
        assertThat(second.inserted()).isEqualTo(1);
        assertThat(second.skipped()).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sipsa_mayoristas_semanal WHERE arti_id = 1 AND fuen_id = 10 AND fecha_ini IS NULL",
                Long.class)).as("two independent rows, the constraint never fired").isEqualTo(2L);

        // Within the SAME batch, two rows with the identical (1, 10, null) key still
        // collapse to one via in-memory dedup (Map key equality, unlike SQL NULL).
        UpsertMetrics sameBatch = repository.upsertFallbackBatch(List.of(item(2L, 10L, null), item(2L, 10L, null)));
        assertThat(sameBatch.inserted()).isEqualTo(1);
        assertThat(sameBatch.skipped()).isZero();
    }

    @Test
    @DisplayName("11+12. skip is never an update: an existing row's stored values are untouched")
    void skipNeverUpdates() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        SipsaMayoristasSemanal original = item(1L, 10L, fecha);
        original.setPromedioKg(new BigDecimal("111.11"));
        repository.upsertFallbackBatch(List.of(original));
        Instant originalSync = jdbc.queryForObject(
                "SELECT fecha_sincronizacion FROM sipsa_mayoristas_semanal WHERE arti_id = 1 AND fuen_id = 10",
                java.sql.Timestamp.class).toInstant();

        SipsaMayoristasSemanal attemptedUpdate = item(1L, 10L, fecha);
        attemptedUpdate.setPromedioKg(new BigDecimal("999.99"));
        UpsertMetrics metrics = repository.upsertFallbackBatch(List.of(attemptedUpdate));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isEqualTo(1);
        BigDecimal storedPrice = jdbc.queryForObject(
                "SELECT promedio_kg FROM sipsa_mayoristas_semanal WHERE arti_id = 1 AND fuen_id = 10",
                BigDecimal.class);
        assertThat(storedPrice).as("skip means skip - the existing row's price is unchanged").isEqualByComparingTo("111.11");
        Instant storedSync = jdbc.queryForObject(
                "SELECT fecha_sincronizacion FROM sipsa_mayoristas_semanal WHERE arti_id = 1 AND fuen_id = 10",
                java.sql.Timestamp.class).toInstant();
        assertThat(storedSync).as("fecha_sincronizacion of the existing row is untouched").isEqualTo(originalSync);
    }

    @Test
    @DisplayName("9. rollback: no row persists if the surrounding transaction rolls back")
    void rollback_noRowsPersisted() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            repository.upsertFallbackBatch(List.of(item(1L, 10L, fecha)));
            throw new RuntimeException("simulated failure after insert");
        })).hasMessage("simulated failure after insert");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class))
                .as("the rolled-back transaction leaves no row behind").isZero();
    }

    // -----------------------------------------------------------------------
    // 10. Structural N+1 evidence
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("10. structural: batches of 1, 10, and 100 all issue zero Hibernate-tracked queries (no per-row SELECT, regardless of batch size)")
    void noPerRowHibernateQuery_regardlessOfBatchSize() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (int batchSize : List.of(1, 10, 100)) {
            jdbc.update("DELETE FROM sipsa_mayoristas_semanal");
            statistics.clear();

            List<SipsaMayoristasSemanal> batch = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                batch.add(item(i, 10L, fecha));
            }

            UpsertMetrics metrics = repository.upsertFallbackBatch(batch);

            assertThat(metrics.inserted()).isEqualTo(batchSize);
            // The old implementation issued one Hibernate findByBusinessKeys SELECT per
            // unique item - queryExecutionCount would equal batchSize. The new
            // implementation does all its work through a single raw-JDBC ON CONFLICT
            // batch, entirely bypassing Hibernate, so this stays 0 at every batch size.
            assertThat(statistics.getQueryExecutionCount())
                    .as("batch size %d must not produce Hibernate queries proportional to it", batchSize)
                    .isZero();
        }
    }

    // -----------------------------------------------------------------------
    // 8. Concurrency
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("8. concurrent collision: two transactions racing on the same business key - loser is skipped, not failed")
    void concurrentCollision_loserSkippedNotFailed() throws Exception {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch winnerCommitted = new CountDownLatch(1);
            TransactionTemplate tx = new TransactionTemplate(txManager);

            Future<UpsertMetrics> winner = executor.submit(() -> tx.execute(status -> {
                UpsertMetrics m = repository.upsertFallbackBatch(List.of(item(1L, 10L, fecha)));
                return m;
            }));
            // Ensure the winner's transaction has committed before the racer starts, so
            // this test deterministically exercises the ON CONFLICT path (a genuine
            // uncommitted in-flight race is already covered by ParcialConcurrentDedupTest
            // for the identical technique on SipsaParcial).
            UpsertMetrics winnerMetrics = winner.get(30, TimeUnit.SECONDS);
            winnerCommitted.countDown();

            Future<UpsertMetrics> racer = executor.submit(() ->
                    repository.upsertFallbackBatch(List.of(item(1L, 10L, fecha))));
            UpsertMetrics racerMetrics = racer.get(30, TimeUnit.SECONDS);

            assertThat(winnerMetrics.inserted()).isEqualTo(1);
            assertThat(racerMetrics.inserted())
                    .as("the racer must not fail with a unique-violation exception").isZero();
            assertThat(racerMetrics.skipped()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_mayoristas_semanal", Long.class))
                    .as("exactly one row for the contested key").isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("8b. concurrent constraint enforcement: the ux_semana_fallback constraint is real and backs ON CONFLICT")
    void uniqueConstraintExists_backsOnConflict() {
        Instant fecha = Instant.parse("2026-07-06T00:00:00Z");
        jdbc.update("""
                INSERT INTO sipsa_mayoristas_semanal
                    (arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_ini, ingestion_run_id)
                VALUES (1, 'X', 10, 'Y', 1, ?, ?)""", java.sql.Timestamp.from(fecha), runId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sipsa_mayoristas_semanal
                    (arti_id, arti_nombre, fuen_id, fuen_nombre, futi_id, fecha_ini, ingestion_run_id)
                VALUES (1, 'X2', 10, 'Y2', 1, ?, ?)""", java.sql.Timestamp.from(fecha), runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_semana_fallback");
    }
}
