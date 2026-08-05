package com.dalejandrov.sipsa.application.ingestion;

import com.dalejandrov.sipsa.application.command.IngestionRequest;
import com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

/**
 * Application-level concurrency gate for TECH-117 / SIPSA-F4-01: two REAL ingestion job
 * executions ({@code GenericIngestionJob}, the exact code path behind
 * {@code POST /api/internal/ingestion/run?method=promediosSipsaParcial&force=true})
 * triggered for the same publication while the first is still active, against real
 * PostgreSQL.
 * <p>
 * <b>Why this scenario changed (SIPSA-F4-01):</b> the original TECH-117 version of this
 * test had both executions call {@code createRun(force=true)} while the first was still
 * {@code STARTED} (mid-download), relying on the pre-F4-01 {@code createRun} to let a
 * {@code force=true} caller blindly restart an <em>active</em> run - which is exactly the
 * TOCTOU bug SIPSA-F4-01 closes. Under the fixed, atomically-conditioned restart, a
 * STARTED/RUNNING row can never be restarted, even with {@code force=true}
 * (see {@code IngestionControlService#createRun}), so the second trigger is now rejected
 * at {@code createRun} - synchronously, before it ever reaches the SOAP gateway - instead
 * of racing the first execution's persistence. That makes the outcome of this scenario
 * fully deterministic (previously it was not: see the removed range assertion on
 * {@code records_inserted}), so the assertions below are tightened to exact values rather
 * than softened.
 * <p>
 * Expected (SIPSA-F4-01): execution A runs to completion alone and succeeds; execution B,
 * arriving while A's run is still active, is rejected as a controlled business conflict
 * (audited as {@code INGESTION_SKIPPED_DUPLICATE}, never {@code INGESTION_FAILED}) and
 * never touches the SOAP gateway, {@code sipsa_parcial}, or A's run row. Exactly one
 * {@code ingestion_runs} row exists, closing SUCCEEDED with metrics equal to the fixture
 * size (deterministic, since only A ever wrote).
 * <p>
 * <b>Audit synchronization:</b> {@code IngestionAuditService.logEvent} is
 * {@code @Async} + {@code REQUIRES_NEW} — audit rows commit on another thread AFTER the
 * job futures complete (measured lag 1–2 ms locally; wider on constrained CI runners,
 * which is exactly how the 2026-07-19 CI failure surfaced). Audit assertions therefore
 * use a bounded condition-based wait (Awaitility, ships with spring-boot-starter-test),
 * never a fixed sleep. Run-status and data assertions stay immediate: {@code
 * updateStatus}/{@code updateMetrics} and the batch inserts are synchronous inside the
 * job thread, so they are committed by the time each future completes.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-117: two concurrent real ingestion jobs over the same publication")
class ParcialConcurrentIngestionAppTest {

    private static final int RECORDS = 6;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private GenericIngestionJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private SoapGateway soapGateway;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM sipsa_parcial");
        jdbc.update("DELETE FROM ingestion_audit");
        jdbc.update("DELETE FROM ingestion_rejects");
        jdbc.update("DELETE FROM ingestion_runs");
    }

    @Test
    @DisplayName("trigger B rejected while A is active; A alone succeeds deterministically")
    void secondTriggerWhileFirstActive_isRejected_firstSucceedsAlone() throws Exception {
        String xml = fixture(RECORDS);
        // A is held mid-download by this latch/gate pair, entirely under test control -
        // B never touches the gateway, so nothing B does can release or observe it.
        CountDownLatch firstDownloading = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(soapGateway.getParcialData()).thenAnswer(inv -> {
            firstDownloading.countDown();
            assertThat(releaseFirst.await(20, TimeUnit.SECONDS)).isTrue();
            return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        });

        ExecutorService executor = Executors.newFixedThreadPool(1);
        try {
            Future<?> first = executor.submit(() ->
                    job.execute(IngestionRequest.manualForced("promediosSipsaParcial", "f401-a")));
            assertThat(firstDownloading.await(20, TimeUnit.SECONDS))
                    .as("first job reached its download; run row is now STARTED/active").isTrue();

            // B arrives while A's run is STARTED (active): createRun must reject it
            // synchronously - SIPSA-F4-01 forbids restarting an active run even with
            // force=true - so this call returns immediately without ever invoking
            // soapGateway (the mock above is only ever satisfied by A).
            job.execute(IngestionRequest.manualForced("promediosSipsaParcial", "f401-b"));

            releaseFirst.countDown();
            first.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Data integrity: only A ever wrote, one copy per key, no duplicates.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_parcial", Long.class))
                .isEqualTo((long) RECORDS);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT key_hash FROM sipsa_parcial GROUP BY key_hash HAVING COUNT(*) > 1) dup",
                Long.class)).isZero();

        // A succeeded, B was skipped as a duplicate/conflict, neither ever failed.
        //
        // The wait is condition-based, not a fixed sleep: IngestionAuditService.logEvent
        // is @Async + REQUIRES_NEW, so the events commit on another thread AFTER the job
        // futures complete. Measured visibility lag is 1-2 ms locally; on constrained CI
        // runners the window stretched enough for an immediate query to miss an event
        // (CI failure of 2026-07-19). Bounded at 10 s.
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    assertThat(eventsFor("f401-a", "INGESTION_SUCCEEDED"))
                            .as("execution A audited as succeeded; audit state: " + auditDump())
                            .isEqualTo(1L);
                    assertThat(eventsFor("f401-b", "INGESTION_SKIPPED_DUPLICATE"))
                            .as("execution B audited as skipped duplicate/conflict; audit state: " + auditDump())
                            .isEqualTo(1L);
                    assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ingestion_audit WHERE event_type = 'INGESTION_FAILED'",
                            Long.class))
                            .as("no execution failed; audit state: " + auditDump())
                            .isEqualTo(0L);
                });

        // Single run row, owned by A alone: SUCCEEDED, with metrics that are now fully
        // deterministic (only one execution ever wrote), unlike the pre-F4-01 version of
        // this test which had to tolerate a range because two executions could race.
        List<Map<String, Object>> runs = jdbc.queryForList("""
                SELECT status, records_seen, records_inserted, records_updated, reject_count,
                       last_error_message, start_time, end_time
                FROM ingestion_runs WHERE method_name = 'promediosSipsaParcial'""");
        assertThat(runs).hasSize(1);
        Map<String, Object> run = runs.getFirst();
        assertThat(run.get("status")).isEqualTo("SUCCEEDED");
        assertThat(run.get("last_error_message")).as("no unique-violation stack recorded").isNull();
        assertThat(run.get("end_time")).isNotNull();
        assertThat(((Number) run.get("records_seen")).intValue()).isEqualTo(RECORDS);
        assertThat(((Number) run.get("records_inserted")).intValue())
                .as("deterministic: only A ever wrote, all keys are fresh inserts").isEqualTo(RECORDS);
        assertThat(((Number) run.get("records_updated")).intValue()).isZero();
        assertThat(((Number) run.get("reject_count")).intValue()).isZero();
    }

    private Long eventsFor(String requestId, String eventType) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ingestion_audit "
                        + "WHERE event_type = ? AND request_id = ?",
                Long.class, eventType, requestId);
    }

    /**
     * Diagnostic snapshot for await-timeout failures: every audit event (type,
     * request correlation, run, timestamp) plus the run row's final state, so a
     * future CI failure is diagnosable from the assertion message alone.
     * No payloads or secrets — metadata only.
     */
    private String auditDump() {
        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT event_type, request_id, run_id, occurred_at
                FROM ingestion_audit ORDER BY occurred_at""");
        List<Map<String, Object>> runs = jdbc.queryForList("""
                SELECT run_id, status, records_seen, records_inserted, reject_count, last_error_message
                FROM ingestion_runs WHERE method_name = 'promediosSipsaParcial'""");
        return "audit events=" + events + "; runs=" + runs;
    }

    private static String fixture(int records) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<response>\n");
        for (int i = 1; i <= records; i++) {
            sb.append("""
                      <return>
                        <muniId>%05d</muniId>
                        <muniNombre>MUNI %d</muniNombre>
                        <fuenId>10</fuenId>
                        <futiId>2</futiId>
                        <idArtiSemana>%d</idArtiSemana>
                        <enmaFecha>2026-07-15T05:00:00Z</enmaFecha>
                        <promedioKg>1500.50</promedioKg>
                      </return>
                    """.formatted(5000 + i, i, 100 + i));
        }
        return sb.append("</response>\n").toString();
    }
}
