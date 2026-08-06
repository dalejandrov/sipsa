package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
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
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SIPSA-F4-21: dedicated concurrency gate for the four {@link IngestionControlService}
 * lifecycle mutators that F4-01 left untouched - {@code updateStatus}, {@code updateMetrics},
 * {@code logError} and {@code cancelRun}.
 * <p>
 * Before this fix, all four were {@code findById -> mutate -> save}: a full-entity save that
 * re-persisted whatever the in-memory {@code status}/{@code endTime} happened to be at read
 * time. Two of them racing the same row (most realistically: the ingestion job thread
 * finalizing a run while an operator concurrently calls {@code cancelRun} via the ops API)
 * could silently overwrite each other's terminal state - a lost update with no error, no log,
 * no signal.
 * <p>
 * The fix (mirroring F4-01's {@code restartIfStatusIn} pattern) replaces status transitions
 * with a conditional {@code UPDATE ... WHERE status = :expectedFrom} and replaces the
 * metrics/error mutators with column-scoped partial {@code UPDATE}s that never touch
 * {@code status} at all. Every test here calls the real {@link IngestionControlService} bean
 * against real PostgreSQL via Testcontainers - never H2 - because the property under test is a
 * database-level conditional-write / row-lock guarantee. Two real overlapping transactions are
 * produced with a {@link CyclicBarrier}, not {@code Thread.sleep}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("SIPSA-F4-21: IngestionRun lifecycle mutators under concurrency")
class IngestionControlServiceLifecycleConcurrentTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private IngestionControlService controlService;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String METHOD_NAME = "sipsaF4LifecycleTest";

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM ingestion_runs");
    }

    // ------------------------------------------------------------------
    // 1. Cancellation vs. success
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelRun vs updateStatus(SUCCEEDED): exactly one terminal transition wins, never overwritten")
    void cancelVsSucceeded_exactlyOneWins() throws Exception {
        String windowKey = "cancel-vs-succeeded";
        seedRun(windowKey, IngestionRunStatus.RUNNING, Instant.parse("2026-08-05T10:00:00Z"),
                "seed-req", 5, 3, 1, 1, null, null, null);
        long runId = runIdFor(windowKey);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> cancelWon = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                try {
                    controlService.cancelRun(runId);
                    return true;
                } catch (SipsaBusinessException e) {
                    return false;
                }
            });
            Future<Boolean> succeedWon = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                return controlService.updateStatus(runId, IngestionRunStatus.SUCCEEDED);
            });

            boolean canceled = cancelWon.get(30, TimeUnit.SECONDS);
            boolean succeeded = succeedWon.get(30, TimeUnit.SECONDS);

            assertThat(canceled ^ succeeded)
                    .as("exactly one of cancel/succeeded wins: canceled=%s succeeded=%s", canceled, succeeded)
                    .isTrue();

            Map<String, Object> row = fetchRun(windowKey);
            assertThat(row.get("status")).isEqualTo(canceled ? "CANCELED" : "SUCCEEDED");
            assertThat(row.get("end_time")).as("the winning terminal transition always records endTime").isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // 2. Cancellation vs. failure
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelRun vs updateStatus(FAILED): exactly one terminal transition wins, never overwritten")
    void cancelVsFailed_exactlyOneWins() throws Exception {
        String windowKey = "cancel-vs-failed";
        seedRun(windowKey, IngestionRunStatus.RUNNING, Instant.parse("2026-08-05T10:00:00Z"),
                "seed-req", 2, 0, 0, 0, null, null, null);
        long runId = runIdFor(windowKey);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> cancelWon = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                try {
                    controlService.cancelRun(runId);
                    return true;
                } catch (SipsaBusinessException e) {
                    return false;
                }
            });
            Future<Boolean> failWon = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                return controlService.updateStatus(runId, IngestionRunStatus.FAILED);
            });

            boolean canceled = cancelWon.get(30, TimeUnit.SECONDS);
            boolean failed = failWon.get(30, TimeUnit.SECONDS);

            assertThat(canceled ^ failed)
                    .as("exactly one of cancel/failed wins: canceled=%s failed=%s", canceled, failed)
                    .isTrue();

            Map<String, Object> row = fetchRun(windowKey);
            assertThat(row.get("status")).isEqualTo(canceled ? "CANCELED" : "FAILED");
            assertThat(row.get("end_time")).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // 3. Double cancellation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("double concurrent cancelRun: exactly one succeeds, the other gets a controlled 422 conflict, final status CANCELED")
    void doubleCancel_exactlyOneWins_otherConflicts() throws Exception {
        String windowKey = "double-cancel";
        seedRun(windowKey, IngestionRunStatus.RUNNING, Instant.parse("2026-08-05T10:00:00Z"),
                "seed-req", 0, 0, 0, 0, null, null, null);
        long runId = runIdFor(windowKey);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Optional<String>>> futures = List.of(
                    executor.submit(() -> attemptCancel(barrier, runId)),
                    executor.submit(() -> attemptCancel(barrier, runId)));

            List<Optional<String>> results = futures.stream().map(f -> {
                try {
                    return f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            long wins = results.stream().filter(Optional::isEmpty).count();
            long conflicts = results.stream().filter(Optional::isPresent).count();
            assertThat(wins).as("exactly one cancellation wins: " + results).isEqualTo(1);
            assertThat(conflicts).as("exactly one controlled conflict: " + results).isEqualTo(1);
            results.stream().filter(Optional::isPresent)
                    .forEach(msg -> assertThat(msg).contains("Run is not active (status: CANCELED)"));

            assertThat(fetchRun(windowKey).get("status")).isEqualTo("CANCELED");
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<String> attemptCancel(CyclicBarrier barrier, long runId) throws Exception {
        barrier.await(20, TimeUnit.SECONDS);
        try {
            controlService.cancelRun(runId);
            return Optional.empty();
        } catch (SipsaBusinessException e) {
            return Optional.of(e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 4. Late transition after a terminal state
    // ------------------------------------------------------------------

    @Test
    @DisplayName("late transition: a run already CANCELED cannot be moved to SUCCEEDED; status and endTime unchanged")
    void lateTransition_alreadyCanceled_succeededIgnored() {
        String windowKey = "late-transition";
        Instant canceledAt = Instant.parse("2026-08-05T11:00:00Z");
        seedRun(windowKey, IngestionRunStatus.CANCELED, Instant.parse("2026-08-05T10:00:00Z"),
                "seed-req", 2, 1, 0, 0, null, null, null);
        long runId = runIdFor(windowKey);
        jdbc.update("UPDATE ingestion_runs SET end_time = ? WHERE run_id = ?", Timestamp.from(canceledAt), runId);

        boolean transitioned = controlService.updateStatus(runId, IngestionRunStatus.SUCCEEDED);

        assertThat(transitioned).as("a transition away from a terminal state must be refused").isFalse();
        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(((Timestamp) row.get("end_time")).toInstant()).isEqualTo(canceledAt);
    }

    // ------------------------------------------------------------------
    // 5. Metrics vs. cancellation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("updateMetrics concurrent with cancelRun: status is never restored, and the metrics write is preserved")
    void metricsVsCancel_statusNeverRestored_metricsPreserved() throws Exception {
        String windowKey = "metrics-vs-cancel";
        seedRun(windowKey, IngestionRunStatus.RUNNING, Instant.parse("2026-08-05T10:00:00Z"),
                "seed-req", 10, 5, 2, 1, null, null, null);
        long runId = runIdFor(windowKey);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cancelFuture = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                controlService.cancelRun(runId);
                return null;
            });
            Future<?> metricsFuture = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                controlService.updateMetrics(runId, 50, 40, 5, 5);
                return null;
            });
            cancelFuture.get(30, TimeUnit.SECONDS);
            metricsFuture.get(30, TimeUnit.SECONDS);

            Map<String, Object> row = fetchRun(windowKey);
            assertThat(row.get("status"))
                    .as("a concurrent metrics write must never resurrect a stale status")
                    .isEqualTo("CANCELED");
            assertThat(row.get("end_time")).isNotNull();
            assertThat(((Number) row.get("records_seen")).intValue())
                    .as("the run's own final metrics tally is preserved even though the run was concurrently canceled")
                    .isEqualTo(50);
            assertThat(((Number) row.get("records_inserted")).intValue()).isEqualTo(40);
            assertThat(((Number) row.get("records_updated")).intValue()).isEqualTo(5);
            assertThat(((Number) row.get("reject_count")).intValue()).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // 6. Error vs. terminal transition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("logError concurrent with updateStatus(FAILED): both writes preserved, no field lost")
    void errorVsFailedTransition_bothPreserved() throws Exception {
        String windowKey = "error-vs-failed";
        seedRun(windowKey, IngestionRunStatus.RUNNING, Instant.parse("2026-08-05T10:00:00Z"),
                "seed-req", 3, 1, 0, 0, null, null, null);
        long runId = runIdFor(windowKey);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> failFuture = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                return controlService.updateStatus(runId, IngestionRunStatus.FAILED);
            });
            Future<?> errorFuture = executor.submit(() -> {
                barrier.await(20, TimeUnit.SECONDS);
                controlService.logError(runId, "boom concurrent", 502, "FAULT-X");
                return null;
            });

            boolean failed = failFuture.get(30, TimeUnit.SECONDS);
            errorFuture.get(30, TimeUnit.SECONDS);

            assertThat(failed).as("FAILED transitions uncontested from the seeded RUNNING state").isTrue();
            Map<String, Object> row = fetchRun(windowKey);
            assertThat(row.get("status")).isEqualTo("FAILED");
            assertThat(row.get("end_time")).isNotNull();
            assertThat(row.get("last_error_message")).isEqualTo("boom concurrent");
            assertThat(((Number) row.get("http_status")).intValue()).isEqualTo(502);
            assertThat(row.get("soap_fault_code")).isEqualTo("FAULT-X");
        } finally {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // 7. Regression: normal single-threaded flows
    // ------------------------------------------------------------------

    @Test
    @DisplayName("regression: normal STARTED -> RUNNING -> FAILED path transitions atomically at each step")
    void normalFailurePath_regression() {
        String windowKey = "normal-failure-path";
        seedRun(windowKey, IngestionRunStatus.STARTED, Instant.parse("2026-08-05T09:00:00Z"),
                "seed-req", 0, 0, 0, 0, null, null, null);
        long runId = runIdFor(windowKey);

        assertThat(controlService.updateStatus(runId, IngestionRunStatus.RUNNING)).isTrue();
        controlService.logError(runId, "downstream boom", 500, "FAULT-Y");
        assertThat(controlService.updateStatus(runId, IngestionRunStatus.FAILED)).isTrue();

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("last_error_message")).isEqualTo("downstream boom");
        assertThat(row.get("end_time")).isNotNull();
    }

    @Test
    @DisplayName("regression: cancelRun on a STARTED run (never reached RUNNING) succeeds")
    void cancelStartedRun_regression() {
        String windowKey = "cancel-started";
        seedRun(windowKey, IngestionRunStatus.STARTED, Instant.parse("2026-08-05T09:00:00Z"),
                "seed-req", 0, 0, 0, 0, null, null, null);
        long runId = runIdFor(windowKey);

        controlService.cancelRun(runId);

        Map<String, Object> row = fetchRun(windowKey);
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(row.get("end_time")).isNotNull();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void seedRun(String windowKey, IngestionRunStatus status, Instant startTime, String requestId,
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
    }

    private long runIdFor(String windowKey) {
        return jdbc.queryForObject(
                "SELECT run_id FROM ingestion_runs WHERE method_name = ? AND window_key = ?",
                Long.class, METHOD_NAME, windowKey);
    }

    private Map<String, Object> fetchRun(String windowKey) {
        return jdbc.queryForMap(
                "SELECT * FROM ingestion_runs WHERE method_name = ? AND window_key = ?",
                METHOD_NAME, windowKey);
    }
}
