package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SIPSA-F4-01: dedicated concurrency gate for {@link IngestionControlService#createRun}'s
 * atomic restart.
 * <p>
 * Prior to this fix, the "restart an existing run" branch was a classic TOCTOU: read the
 * row, decide in Java whether force/status allow a restart, then {@code save()}. Two
 * concurrent callers could both read the same row, both decide "allowed", and both write -
 * silently clobbering an active run's metrics/startTime or double-resetting a FAILED row.
 * <p>
 * The fix replaces that with a single conditional {@code UPDATE ... WHERE status IN (...)}
 * (see {@link com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository#restartIfStatusIn}).
 * Every test here calls the real {@link IngestionControlService} bean (real
 * {@code REQUIRES_NEW} transactions) against real PostgreSQL via Testcontainers - never
 * H2 - because the safety property under test is a database-level conditional-write
 * guarantee (row lock + WHERE-clause re-evaluation on unblock under READ COMMITTED), which
 * an in-memory database or a mocked repository cannot exercise.
 * <p>
 * Two real overlapping transactions are produced with a {@link CyclicBarrier}, not
 * {@code Thread.sleep}: both worker threads block on the barrier immediately before calling
 * {@code createRun}, so both transactions begin their conditional UPDATE at essentially the
 * same instant, and PostgreSQL's own row lock is what serializes them - not test timing.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("SIPSA-F4-01: atomic conditional restart of ingestion_runs under concurrency")
class IngestionControlServiceAtomicRestartConcurrentTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private IngestionControlService controlService;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String METHOD_NAME = "sipsaF4RestartTest";

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM ingestion_runs");
    }

    // ------------------------------------------------------------------
    // 1. Two concurrent restarts of a FAILED run
    // ------------------------------------------------------------------

    @Test
    @DisplayName("two concurrent FAILED restarts (force=false): exactly one wins, one row, reset once")
    void concurrentRestart_bothFailed_exactlyOneWins() throws Exception {
        String windowKey = "2026-08-01-failed";
        long seededRunId = seedRun(windowKey, IngestionRunStatus.FAILED, Instant.parse("2026-08-01T00:00:00Z"),
                "seed-request", 5, 2, 1, 1, "previous boom", 500, "FAULT-PREV");

        List<Outcome> outcomes = runConcurrently(
                barrier -> attemptRestart(barrier, windowKey, false, "req-a"),
                barrier -> attemptRestart(barrier, windowKey, false, "req-b"));

        assertExactlyOneWinnerOneConflict(outcomes);
        Outcome winner = winnerOf(outcomes);

        assertThat(countRuns(windowKey)).as("single row per method/window").isEqualTo(1);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("run_id")).as("same runId preserved across restart").isEqualTo(seededRunId);
        assertThat(row.get("status")).isEqualTo("STARTED");
        assertThat(row.get("request_id")).as("winner's requestId persisted").isEqualTo(winner.requestId());
        assertThat(((Number) row.get("records_seen")).intValue()).isZero();
        assertThat(((Number) row.get("records_inserted")).intValue()).isZero();
        assertThat(((Number) row.get("records_updated")).intValue()).isZero();
        assertThat(((Number) row.get("reject_count")).intValue()).isZero();
        assertThat(row.get("last_error_message")).as("reset exactly once, no stale error").isNull();
        assertThat(row.get("http_status")).isNull();
        assertThat(row.get("soap_fault_code")).isNull();
        assertThat(row.get("end_time")).isNull();
    }

    // ------------------------------------------------------------------
    // 2. Two concurrent restarts of a SUCCEEDED run with force=true
    // ------------------------------------------------------------------

    @Test
    @DisplayName("two concurrent SUCCEEDED restarts (force=true): exactly one wins, one row, coherent metrics")
    void concurrentRestart_bothSucceededForce_exactlyOneWins() throws Exception {
        String windowKey = "2026-08-01-succeeded";
        long seededRunId = seedRun(windowKey, IngestionRunStatus.SUCCEEDED, Instant.parse("2026-08-01T00:00:00Z"),
                "seed-request", 10, 8, 2, 0, null, null, null);

        List<Outcome> outcomes = runConcurrently(
                barrier -> attemptRestart(barrier, windowKey, true, "req-a"),
                barrier -> attemptRestart(barrier, windowKey, true, "req-b"));

        assertExactlyOneWinnerOneConflict(outcomes);
        Outcome winner = winnerOf(outcomes);

        assertThat(countRuns(windowKey)).as("single row per method/window").isEqualTo(1);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("run_id")).isEqualTo(seededRunId);
        assertThat(row.get("status")).isEqualTo("STARTED");
        assertThat(row.get("request_id")).isEqualTo(winner.requestId());
        assertThat(((Number) row.get("records_seen")).intValue()).isZero();
        assertThat(((Number) row.get("records_inserted")).intValue()).isZero();
        assertThat(((Number) row.get("records_updated")).intValue()).isZero();
        assertThat(((Number) row.get("reject_count")).intValue()).isZero();
        assertThat(row.get("end_time")).isNull();
    }

    // ------------------------------------------------------------------
    // 3. RUNNING + force=true: always rejected, row untouched
    // ------------------------------------------------------------------

    @Test
    @DisplayName("RUNNING run rejects restart even with force=true; row, metrics and startTime stay intact")
    void restart_runningWithForce_isRejected_rowIntact() {
        String windowKey = "2026-08-01-running";
        Instant seededStart = Instant.parse("2026-08-01T12:00:00Z");
        long seededRunId = seedRun(windowKey, IngestionRunStatus.RUNNING, seededStart,
                "seed-request", 3, 1, 1, 0, null, null, null);

        assertThatThrownBy(() -> controlService.createRun(METHOD_NAME, windowKey, true, "req-attacker", RequestSource.MANUAL))
                .isInstanceOf(SipsaBusinessException.class);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("run_id")).isEqualTo(seededRunId);
        assertThat(row.get("status")).isEqualTo("RUNNING");
        assertThat(((Timestamp) row.get("start_time")).toInstant()).isEqualTo(seededStart);
        assertThat(row.get("request_id")).isEqualTo("seed-request");
        assertThat(((Number) row.get("records_seen")).intValue()).isEqualTo(3);
        assertThat(((Number) row.get("records_inserted")).intValue()).isEqualTo(1);
        assertThat(((Number) row.get("records_updated")).intValue()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 4. STARTED + force=true: always rejected, row untouched
    // ------------------------------------------------------------------

    @Test
    @DisplayName("STARTED run rejects restart even with force=true; row stays intact")
    void restart_startedWithForce_isRejected_rowIntact() {
        String windowKey = "2026-08-01-started";
        Instant seededStart = Instant.parse("2026-08-01T13:00:00Z");
        long seededRunId = seedRun(windowKey, IngestionRunStatus.STARTED, seededStart,
                "seed-request", 0, 0, 0, 0, null, null, null);

        assertThatThrownBy(() -> controlService.createRun(METHOD_NAME, windowKey, true, "req-attacker", RequestSource.MANUAL))
                .isInstanceOf(SipsaBusinessException.class);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("run_id")).isEqualTo(seededRunId);
        assertThat(row.get("status")).isEqualTo("STARTED");
        assertThat(((Timestamp) row.get("start_time")).toInstant()).isEqualTo(seededStart);
        assertThat(row.get("request_id")).isEqualTo("seed-request");
    }

    // ------------------------------------------------------------------
    // 5. FAILED + force=false: allowed, fields reset
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FAILED run restarts without force; fields reset correctly")
    void restart_failedWithoutForce_isAllowed_fieldsReset() {
        String windowKey = "2026-08-01-failed-noforce";
        long seededRunId = seedRun(windowKey, IngestionRunStatus.FAILED, Instant.parse("2026-08-01T00:00:00Z"),
                "seed-request", 7, 4, 1, 2, "boom", 502, "FAULT-X");

        long runId = controlService.createRun(METHOD_NAME, windowKey, false, "req-retry", RequestSource.MANUAL);

        assertThat(runId).isEqualTo(seededRunId);
        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("status")).isEqualTo("STARTED");
        assertThat(row.get("request_id")).isEqualTo("req-retry");
        assertThat(((Number) row.get("records_seen")).intValue()).isZero();
        assertThat(((Number) row.get("records_inserted")).intValue()).isZero();
        assertThat(((Number) row.get("records_updated")).intValue()).isZero();
        assertThat(((Number) row.get("reject_count")).intValue()).isZero();
        assertThat(row.get("last_error_message")).isNull();
        assertThat(row.get("http_status")).isNull();
        assertThat(row.get("soap_fault_code")).isNull();
        assertThat(row.get("end_time")).isNull();
    }

    // ------------------------------------------------------------------
    // 6. SUCCEEDED + force=false: rejected, row untouched
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SUCCEEDED run rejects restart without force; row stays intact")
    void restart_succeededWithoutForce_isRejected_rowIntact() {
        String windowKey = "2026-08-01-succeeded-noforce";
        Instant seededStart = Instant.parse("2026-08-01T00:00:00Z");
        long seededRunId = seedRun(windowKey, IngestionRunStatus.SUCCEEDED, seededStart,
                "seed-request", 9, 9, 0, 0, null, null, null);

        assertThatThrownBy(() -> controlService.createRun(METHOD_NAME, windowKey, false, "req-late", RequestSource.MANUAL))
                .isInstanceOf(SipsaBusinessException.class);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("run_id")).isEqualTo(seededRunId);
        assertThat(row.get("status")).isEqualTo("SUCCEEDED");
        assertThat(((Timestamp) row.get("start_time")).toInstant()).isEqualTo(seededStart);
        assertThat(row.get("request_id")).isEqualTo("seed-request");
        assertThat(((Number) row.get("records_seen")).intValue()).isEqualTo(9);
    }

    // ------------------------------------------------------------------
    // 7. Concurrent creation with no prior row: uq_ingestion_runs_window still protects
    // ------------------------------------------------------------------

    @Test
    @DisplayName("two concurrent creations with no prior row: exactly one wins via uq_ingestion_runs_window")
    void concurrentCreate_noExistingRow_exactlyOneWins() throws Exception {
        String windowKey = "2026-08-01-create";

        List<Outcome> outcomes = runConcurrently(
                barrier -> attemptRestart(barrier, windowKey, false, "req-a"),
                barrier -> attemptRestart(barrier, windowKey, false, "req-b"));

        assertExactlyOneWinnerOneConflict(outcomes);
        assertThat(countRuns(windowKey)).as("single row created").isEqualTo(1);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("status")).isEqualTo("STARTED");
        assertThat(row.get("request_id")).isEqualTo(winnerOf(outcomes).requestId());
    }

    // ------------------------------------------------------------------
    // 8. Regression: normal single-threaded flow is unaffected
    // ------------------------------------------------------------------

    @Test
    @DisplayName("regression: create -> RUNNING -> metrics -> SUCCEEDED still works end to end")
    void normalFlow_regression() {
        String windowKey = "2026-08-01-normal-flow";

        long runId = controlService.createRun(METHOD_NAME, windowKey, false, "req-normal", RequestSource.MANUAL);
        controlService.updateStatus(runId, IngestionRunStatus.RUNNING);
        controlService.updateMetrics(runId, 12, 10, 2, 0);
        controlService.updateStatus(runId, IngestionRunStatus.SUCCEEDED);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("run_id")).isEqualTo(runId);
        assertThat(row.get("status")).isEqualTo("SUCCEEDED");
        assertThat(((Number) row.get("records_seen")).intValue()).isEqualTo(12);
        assertThat(((Number) row.get("records_inserted")).intValue()).isEqualTo(10);
        assertThat(((Number) row.get("records_updated")).intValue()).isEqualTo(2);
        assertThat(row.get("end_time")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private record Outcome(String requestId, Long runId, SipsaBusinessException conflict) {
        boolean succeeded() {
            return runId != null;
        }
    }

    private Outcome attemptRestart(CyclicBarrier barrier, String windowKey, boolean force, String requestId) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("barrier synchronization failed", e);
        }
        try {
            long runId = controlService.createRun(METHOD_NAME, windowKey, force, requestId, RequestSource.MANUAL);
            return new Outcome(requestId, runId, null);
        } catch (SipsaBusinessException e) {
            return new Outcome(requestId, null, e);
        }
    }

    @SafeVarargs
    private List<Outcome> runConcurrently(java.util.function.Function<CyclicBarrier, Outcome>... attempts) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(attempts.length);
        ExecutorService executor = Executors.newFixedThreadPool(attempts.length);
        try {
            List<Callable<Outcome>> tasks = List.of(attempts).stream()
                    .map(fn -> (Callable<Outcome>) () -> fn.apply(barrier))
                    .toList();
            List<Future<Outcome>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            return futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertExactlyOneWinnerOneConflict(List<Outcome> outcomes) {
        long successCount = outcomes.stream().filter(Outcome::succeeded).count();
        assertThat(successCount).as("exactly one restart/create wins: " + outcomes).isEqualTo(1);
        long conflictCount = outcomes.stream().filter(o -> !o.succeeded()).count();
        assertThat(conflictCount).as("exactly one conflict: " + outcomes).isEqualTo(1);
        outcomes.stream().filter(o -> !o.succeeded())
                .forEach(o -> assertThat(o.conflict()).isInstanceOf(SipsaBusinessException.class));
    }

    private Outcome winnerOf(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::succeeded).findFirst()
                .orElseThrow(() -> new AssertionError("no winner among: " + outcomes));
    }

    private long seedRun(String windowKey, IngestionRunStatus status, Instant startTime, String requestId,
                          int seen, int inserted, int updated, int rejected,
                          String lastError, Integer httpStatus, String faultCode) {
        jdbc.update("""
                INSERT INTO ingestion_runs
                    (request_id, request_source, method_name, window_key, start_time, end_time, status,
                     records_seen, records_inserted, records_updated, reject_count,
                     last_error_message, http_status, soap_fault_code)
                VALUES (?, 'MANUAL', ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId, METHOD_NAME, windowKey, Timestamp.from(startTime), status.name(),
                seen, inserted, updated, rejected, lastError, httpStatus, faultCode);
        return jdbc.queryForObject(
                "SELECT run_id FROM ingestion_runs WHERE method_name = ? AND window_key = ?",
                Long.class, METHOD_NAME, windowKey);
    }

    private Map<String, Object> fetchRun(String windowKey) {
        return jdbc.queryForMap(
                "SELECT * FROM ingestion_runs WHERE method_name = ? AND window_key = ?",
                METHOD_NAME, windowKey);
    }

    private long countRuns(String windowKey) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ingestion_runs WHERE method_name = ? AND window_key = ?",
                Long.class, METHOD_NAME, windowKey);
    }
}
