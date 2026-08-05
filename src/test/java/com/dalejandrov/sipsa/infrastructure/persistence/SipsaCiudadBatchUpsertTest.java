package com.dalejandrov.sipsa.infrastructure.persistence;

import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaCiudadRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaCiudadRepository.UpsertMetrics;
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
import java.time.LocalDate;
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
 * {@code SipsaCiudadRepository.batchUpsert()} no longer issues one {@code findByBusinessKeys}
 * SELECT per batch followed by {@code saveAll + flush} — replaced by a single atomic
 * {@code INSERT … ON CONFLICT (reg_id, cod_producto) DO NOTHING} JDBC batch, the same
 * technique {@code SipsaParcialRepository.batchUpsert} uses (TECH-117). Runs against real
 * PostgreSQL via Testcontainers — {@code ON CONFLICT} syntax and the {@code ux_ciudad}
 * unique constraint are both PostgreSQL-specific, so H2 cannot exercise this path.
 * <p>
 * Every case here preserves the pre-existing semantics exactly: skip-only (never update),
 * in-batch duplicates silently uncounted (matching the original string-key
 * {@code LinkedHashMap} dedup — {@code inserted + skipped} equals the number of *unique*
 * keys, not {@code items.size()}).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("SipsaCiudad batch upsert, atomic ON CONFLICT DO NOTHING")
class SipsaCiudadBatchUpsertTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private SipsaCiudadRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long runId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sipsa_ciudad");
        runId = jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaCiudad', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "test-" + System.nanoTime());
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().clear();
    }

    private SipsaCiudad item(long regId, long codProducto) {
        return SipsaCiudad.builder()
                .regId(regId)
                .ciudad("BOGOTA")
                .codProducto(codProducto)
                .producto("PRODUCTO " + codProducto)
                .fechaCaptura(LocalDate.of(2026, 7, 6))
                .precioPromedio(new BigDecimal("1000.00"))
                .ingestionRunId(runId)
                .build();
    }

    @Test
    @DisplayName("empty batch: zero counts")
    void emptyBatch() {
        UpsertMetrics metrics = repository.batchUpsert(List.of());

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_ciudad", Long.class)).isZero();
    }

    @Test
    @DisplayName("one new record: inserted=1")
    void oneNewRecord() {
        UpsertMetrics metrics = repository.batchUpsert(List.of(item(1L, 10L)));

        assertThat(metrics.inserted()).isEqualTo(1);
        assertThat(metrics.skipped()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_ciudad", Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("several new records: all inserted")
    void severalNewRecords() {
        List<SipsaCiudad> batch = List.of(
                item(1L, 10L), item(2L, 10L), item(3L, 10L), item(4L, 10L), item(5L, 10L));

        UpsertMetrics metrics = repository.batchUpsert(batch);

        assertThat(metrics.inserted()).isEqualTo(5);
        assertThat(metrics.skipped()).isZero();
    }

    @Test
    @DisplayName("all already existing: all skipped, none inserted")
    void allExisting() {
        List<SipsaCiudad> batch = List.of(item(1L, 10L), item(2L, 10L), item(3L, 10L));
        repository.batchUpsert(batch);

        UpsertMetrics metrics = repository.batchUpsert(
                List.of(item(1L, 10L), item(2L, 10L), item(3L, 10L)));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_ciudad", Long.class)).isEqualTo(3L);
    }

    @Test
    @DisplayName("mix of new and existing: correct split")
    void mixNewAndExisting() {
        repository.batchUpsert(List.of(item(1L, 10L), item(2L, 10L)));

        UpsertMetrics metrics = repository.batchUpsert(List.of(
                item(1L, 10L),  // existing -> skip
                item(2L, 10L),  // existing -> skip
                item(3L, 10L),  // new -> insert
                item(4L, 10L),  // new -> insert
                item(5L, 10L)));// new -> insert

        assertThat(metrics.inserted()).isEqualTo(3);
        assertThat(metrics.skipped()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_ciudad", Long.class)).isEqualTo(5L);
    }

    @Test
    @DisplayName("duplicates within the batch: last occurrence wins, silently uncounted (matches prior behavior)")
    void intraBatchDuplicates_notDoubleCounted() {
        SipsaCiudad dup1 = item(1L, 10L);
        dup1.setPrecioPromedio(new BigDecimal("100.00"));
        SipsaCiudad dup2 = item(1L, 10L);
        dup2.setPrecioPromedio(new BigDecimal("200.00")); // last occurrence - this one should win
        SipsaCiudad other = item(2L, 10L);

        UpsertMetrics metrics = repository.batchUpsert(List.of(dup1, dup2, other));

        assertThat(metrics.inserted()).isEqualTo(2);
        assertThat(metrics.skipped()).isZero();
        assertThat(metrics.inserted() + metrics.skipped())
                .as("in-batch duplicate is silently dropped, not counted as inserted or skipped")
                .isEqualTo(2);

        BigDecimal stored = jdbc.queryForObject(
                "SELECT precio_promedio FROM sipsa_ciudad WHERE reg_id = 1 AND cod_producto = 10",
                BigDecimal.class);
        assertThat(stored).as("last occurrence in the batch wins").isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("skip is never an update: an existing row's stored values are untouched")
    void skipNeverUpdates() {
        SipsaCiudad original = item(1L, 10L);
        original.setPrecioPromedio(new BigDecimal("111.11"));
        repository.batchUpsert(List.of(original));
        Instant originalSync = jdbc.queryForObject(
                "SELECT fecha_sincronizacion FROM sipsa_ciudad WHERE reg_id = 1 AND cod_producto = 10",
                java.sql.Timestamp.class).toInstant();

        SipsaCiudad attemptedUpdate = item(1L, 10L);
        attemptedUpdate.setPrecioPromedio(new BigDecimal("999.99"));
        UpsertMetrics metrics = repository.batchUpsert(List.of(attemptedUpdate));

        assertThat(metrics.inserted()).isZero();
        assertThat(metrics.skipped()).isEqualTo(1);
        BigDecimal storedPrice = jdbc.queryForObject(
                "SELECT precio_promedio FROM sipsa_ciudad WHERE reg_id = 1 AND cod_producto = 10",
                BigDecimal.class);
        assertThat(storedPrice).as("skip means skip - the existing row's price is unchanged").isEqualByComparingTo("111.11");
        Instant storedSync = jdbc.queryForObject(
                "SELECT fecha_sincronizacion FROM sipsa_ciudad WHERE reg_id = 1 AND cod_producto = 10",
                java.sql.Timestamp.class).toInstant();
        assertThat(storedSync).as("fecha_sincronizacion of the existing row is untouched").isEqualTo(originalSync);
    }

    @Test
    @DisplayName("rollback: no row persists if the surrounding transaction rolls back")
    void rollback_noRowsPersisted() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            repository.batchUpsert(List.of(item(1L, 10L)));
            throw new RuntimeException("simulated failure after insert");
        })).hasMessage("simulated failure after insert");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_ciudad", Long.class))
                .as("the rolled-back transaction leaves no row behind").isZero();
    }

    @Test
    @DisplayName("structural: batches of 1, 10, and 100 all issue zero Hibernate-tracked queries (no per-row SELECT)")
    void noPerRowHibernateQuery_regardlessOfBatchSize() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        for (int batchSize : List.of(1, 10, 100)) {
            jdbc.update("DELETE FROM sipsa_ciudad");
            statistics.clear();

            List<SipsaCiudad> batch = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                batch.add(item(i, 10L));
            }

            UpsertMetrics metrics = repository.batchUpsert(batch);

            assertThat(metrics.inserted()).isEqualTo(batchSize);
            assertThat(statistics.getQueryExecutionCount())
                    .as("batch size %d must not produce Hibernate queries proportional to it", batchSize)
                    .isZero();
        }
    }

    @Test
    @DisplayName("concurrent collision: two transactions racing on the same business key - loser is skipped, not failed")
    void concurrentCollision_loserSkippedNotFailed() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch winnerCommitted = new CountDownLatch(1);
            TransactionTemplate tx = new TransactionTemplate(txManager);

            Future<UpsertMetrics> winner = executor.submit(() -> tx.execute(status ->
                    repository.batchUpsert(List.of(item(1L, 10L)))));
            UpsertMetrics winnerMetrics = winner.get(30, TimeUnit.SECONDS);
            winnerCommitted.countDown();

            Future<UpsertMetrics> racer = executor.submit(() ->
                    repository.batchUpsert(List.of(item(1L, 10L))));
            UpsertMetrics racerMetrics = racer.get(30, TimeUnit.SECONDS);

            assertThat(winnerMetrics.inserted()).isEqualTo(1);
            assertThat(racerMetrics.inserted())
                    .as("the racer must not fail with a unique-violation exception").isZero();
            assertThat(racerMetrics.skipped()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_ciudad", Long.class))
                    .as("exactly one row for the contested key").isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("concurrent constraint enforcement: the ux_ciudad constraint is real and backs ON CONFLICT")
    void uniqueConstraintExists_backsOnConflict() {
        jdbc.update("""
                INSERT INTO sipsa_ciudad (reg_id, ciudad, cod_producto, producto, ingestion_run_id)
                VALUES (1, 'X', 10, 'Y', ?)""", runId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO sipsa_ciudad (reg_id, ciudad, cod_producto, producto, ingestion_run_id)
                VALUES (1, 'X2', 10, 'Y2', ?)""", runId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ux_ciudad");
    }
}
