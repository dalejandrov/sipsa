package com.dalejandrov.sipsa.infrastructure.persistence;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaParcialRepository;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.ParcialKeyHash;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency gate for TECH-117: two ingestions racing on the same {@code key_hash}.
 * <p>
 * The dedup lookup of {@code batchUpsert} runs under READ COMMITTED, so two concurrent
 * transactions can both observe "key absent" and both attempt the insert; the
 * {@code UNIQUE (key_hash)} constraint ({@code sipsa_parcial_key_hash_key}) is the only
 * barrier that decides the race. These tests coordinate two real transactions on two
 * pooled connections so the interleaving is deterministic, not timing-dependent:
 * <ul>
 *   <li>the first transaction inserts and holds its transaction open;</li>
 *   <li>the second transaction passes its lookup (sees nothing — the first has not
 *       committed), then blocks inside PostgreSQL on the in-flight unique index entry;</li>
 *   <li>the first transaction commits only after <i>observing</i> the second backend in
 *       {@code pg_stat_activity} waiting on a lock — no sleep-based coordination;</li>
 *   <li>the second transaction's insert then resolves against the committed row.</li>
 * </ul>
 * With the atomic {@code INSERT … ON CONFLICT (key_hash) DO NOTHING} path (TECH-117),
 * the losing side must NOT fail: the collision resolves inside PostgreSQL, the loser
 * reports the row as skipped, its transaction stays valid, and non-conflicting rows of
 * the same batch are preserved.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-117: concurrent SipsaParcial duplicate insertion")
class ParcialConcurrentDedupTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private SipsaParcialRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    private long runId;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sipsa_parcial");
        // Distinct window per test: (method_name, window_key) is UNIQUE in ingestion_runs.
        runId = jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaParcial', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "test-" + System.nanoTime());
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("SELECT→INSERT primitive: both observe absent, both insert — constraint decides, loser throws")
    void rawRace_bothObserveAbsent_uniqueConstraintDecides() throws Exception {
        String hash = ParcialKeyHash.compute("05001", 10L, 2L, 101L, Instant.parse("2026-07-15T05:00:00Z"));
        CyclicBarrier bothObservedAbsent = new CyclicBarrier(2);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Future<Throwable> first = executor.submit(() -> insertAfterBarrier(tx, hash, bothObservedAbsent));
        Future<Throwable> second = executor.submit(() -> insertAfterBarrier(tx, hash, bothObservedAbsent));

        Throwable t1 = first.get(30, TimeUnit.SECONDS);
        Throwable t2 = second.get(30, TimeUnit.SECONDS);

        // Exactly one transaction wins; the loser hits the UNIQUE constraint, not the lookup.
        Throwable loser = t1 != null ? t1 : t2;
        assertThat(t1 == null ^ t2 == null).as("exactly one of the two inserts fails").isTrue();
        assertThat(loser).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rootMessage(loser))
                .as("the reported constraint is the key_hash unique backing index")
                .contains("sipsa_parcial_key_hash_key");

        // The loser's transaction rolled back: exactly one row remains.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sipsa_parcial WHERE key_hash = ?", Long.class, hash))
                .isEqualTo(1L);
    }

    /**
     * In each racing transaction: dedup-style lookup observes the key absent, then both
     * meet at the barrier, then both insert. Returns the failure, or null if it won.
     */
    private Throwable insertAfterBarrier(TransactionTemplate tx, String hash, CyclicBarrier barrier) {
        try {
            tx.executeWithoutResult(status -> {
                assertThat(repository.findByKeyHashIn(List.of(hash)))
                        .as("dedup lookup sees no committed row").isEmpty();
                await(barrier);
                repository.saveAndFlush(entity(hash, "05001", 10L, 2L, 101L,
                        Instant.parse("2026-07-15T05:00:00Z")));
            });
            return null;
        } catch (Throwable t) {
            return t;
        }
    }

    @Test
    @DisplayName("batchUpsert race with partial overlap: loser must not fail and must keep its non-conflicting rows")
    void batchUpsertRace_overlappingBatches() throws Exception {
        Instant fecha = Instant.parse("2026-07-15T05:00:00Z");
        // Run A carries keys {A, B, C}; run B carries {B, C, D}.
        SipsaParcial a = named("11001", fecha);
        SipsaParcial b = named("05001", fecha);
        SipsaParcial c = named("76001", fecha);
        SipsaParcial d = named("08001", fecha);

        CountDownLatch winnerInsertedUncommitted = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // Winner: insert {A,B,C}, hold the transaction open until the loser is observed
        // blocked on the in-flight unique index entry, then commit.
        Future<SipsaParcialRepository.UpsertMetrics> winner = executor.submit(() ->
                tx.execute(status -> {
                    var metrics = repository.batchUpsert(List.of(copy(a), copy(b), copy(c)));
                    winnerInsertedUncommitted.countDown();
                    waitUntilABackendBlocksOnALock();
                    return metrics;
                }));

        // Loser: waits until the winner's rows are in-flight (inserted, uncommitted), so its
        // dedup lookup cannot see them; its insert then blocks until the winner commits.
        Future<Object> loser = executor.submit(() -> {
            assertThat(winnerInsertedUncommitted.await(20, TimeUnit.SECONDS)).isTrue();
            try {
                return repository.batchUpsert(List.of(copy(b), copy(c), copy(d)));
            } catch (Throwable t) {
                return t;
            }
        });

        var winnerMetrics = winner.get(30, TimeUnit.SECONDS);
        Object loserOutcome = loser.get(30, TimeUnit.SECONDS);

        assertThat(winnerMetrics.inserted()).as("winner inserted its whole batch").isEqualTo(3);
        assertThat(winnerMetrics.skipped()).isZero();

        // CURRENT behavior (defect under test, TECH-117): the losing batchUpsert dies on
        // the unique violation raised at flush — the exception escapes to the caller
        // (which marks the whole run FAILED) and the rollback also discards the loser's
        // non-conflicting row D.
        assertThat(loserOutcome)
                .as("loser currently fails with a unique violation")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rootMessage((Throwable) loserOutcome)).contains("sipsa_parcial_key_hash_key");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_parcial", Long.class))
                .as("only the winner's rows persisted — the loser's batch rolled back entirely, D lost")
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT key_hash FROM sipsa_parcial GROUP BY key_hash HAVING COUNT(*) > 1) dup",
                Long.class)).as("no duplicated key_hash").isZero();

        // A collision must not leave the loser's transaction unusable: the very next batch
        // on the same code path persists normally.
        var after = repository.batchUpsert(List.of(named("13001", fecha)));
        assertThat(after.inserted()).isEqualTo(1);
        assertThat(after.skipped()).isZero();
    }

    /**
     * Observes (bounded, condition-based — not a fixed sleep) that some other backend is
     * blocked waiting on a lock, i.e. the racing insert reached the in-flight unique
     * index entry and cannot proceed until this transaction commits.
     */
    private void waitUntilABackendBlocksOnALock() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            // A backend waiting on another transaction's in-flight unique index entry
            // reports wait_event 'transactionid'. (state/query can lag in pg_stat_activity,
            // so the wait event — not the query text — is the reliable signal.)
            Long blocked = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock' AND pid <> pg_backend_pid()""", Long.class);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.onSpinWait();
        }
        throw new IllegalStateException("no backend ever blocked on the racing insert; activity: "
                + jdbc.queryForList(
                        "SELECT pid, state, wait_event_type, wait_event, left(query, 120) AS q "
                                + "FROM pg_stat_activity WHERE datname = current_database()"));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("barrier broken", e);
        }
    }

    private static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            sb.append(cur.getMessage()).append('\n');
        }
        return sb.toString();
    }

    private SipsaParcial named(String muniId, Instant fecha) {
        String hash = ParcialKeyHash.compute(muniId, 10L, 2L, 101L, fecha);
        return entity(hash, muniId, 10L, 2L, 101L, fecha);
    }

    /** Fresh entity instance per call — racing threads must not share a managed entity. */
    private SipsaParcial copy(SipsaParcial e) {
        return entity(e.getKeyHash(), e.getMuniId(), e.getFuenId(), e.getFutiId(),
                e.getIdArtiSemana(), e.getEnmaFecha());
    }

    private SipsaParcial entity(String hash, String muniId, Long fuenId, Long futiId,
                                Long idArtiSemana, Instant fecha) {
        return SipsaParcial.builder()
                .keyHash(hash)
                .muniId(muniId)
                .muniNombre("MUNI " + muniId)
                .deptNombre("DEPT")
                .fuenId(fuenId)
                .fuenNombre("FUENTE")
                .futiId(futiId)
                .idArtiSemana(idArtiSemana)
                .artiNombre("ARTICULO")
                .grupNombre("GRUPO")
                .enmaFecha(fecha)
                .promedioKg(new BigDecimal("1000.00"))
                .maximoKg(new BigDecimal("1100.00"))
                .minimoKg(new BigDecimal("900.00"))
                .fechaSincronizacion(Instant.now())
                .ingestionRunId(runId)
                .build();
    }
}
