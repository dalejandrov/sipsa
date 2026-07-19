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
 * Application-level concurrency gate for TECH-117: two REAL ingestion job executions
 * ({@code GenericIngestionJob}, the exact code path behind
 * {@code POST /api/internal/ingestion/run?method=promediosSipsaParcial&force=true})
 * processing the same publication at the same time, against real PostgreSQL.
 * <p>
 * The SOAP source is a controlled fixture: both executions download the same six-record
 * dataset, and the mocked gateway holds both streams at a rendezvous latch so neither
 * job starts parsing/persisting before the other has its data — the persistence phases
 * genuinely overlap. With {@code force=true} both triggers share the same
 * {@code ingestion_runs} row (restart semantics of {@code createRun}), which is exactly
 * what two concurrent force-triggers produce in production.
 * <p>
 * Expected (TECH-117): neither execution fails, the audit trail records two
 * {@code INGESTION_SUCCEEDED} events and zero {@code INGESTION_FAILED}, every key ends
 * up stored exactly once, and the run row closes SUCCEEDED with coherent metrics and no
 * unique-violation error message.
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
    @DisplayName("both executions succeed, collisions count as skipped, one copy per key")
    void twoConcurrentJobsSamePublication() throws Exception {
        String xml = fixture(RECORDS);
        // Production shape of the race: trigger B arrives with force=true while run A is
        // mid-download, restarts the shared run row, and both persistence phases overlap.
        // (Two byte-identical simultaneous triggers are already serialized at run
        // creation by uq_ingestion_runs_window — that guard is out of TECH-117's scope.)
        CountDownLatch firstDownloading = new CountDownLatch(1);
        CountDownLatch secondDownloading = new CountDownLatch(1);
        when(soapGateway.getParcialData()).thenAnswer(inv -> {
            if (firstDownloading.getCount() > 0) {
                firstDownloading.countDown();
                // Hold the first job until the second is also about to parse/persist.
                assertThat(secondDownloading.await(20, TimeUnit.SECONDS)).isTrue();
            } else {
                secondDownloading.countDown();
            }
            return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() ->
                    job.execute(IngestionRequest.manualForced("promediosSipsaParcial", "tech117-a")));
            assertThat(firstDownloading.await(20, TimeUnit.SECONDS))
                    .as("first job reached its download").isTrue();
            Future<?> second = executor.submit(() ->
                    job.execute(IngestionRequest.manualForced("promediosSipsaParcial", "tech117-b")));
            first.get(60, TimeUnit.SECONDS);
            second.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Data integrity: one copy per key, no duplicates.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sipsa_parcial", Long.class))
                .isEqualTo((long) RECORDS);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM (SELECT key_hash FROM sipsa_parcial GROUP BY key_hash HAVING COUNT(*) > 1) dup",
                Long.class)).isZero();

        // Both executions must leave success evidence — one INGESTION_SUCCEEDED per
        // execution (filtered by its own requestId, immune to unrelated events) and
        // zero INGESTION_FAILED. The expectation stays EXACTLY two events: audit rows
        // are append-only inserts (BIGSERIAL PK, no unique constraint), so the
        // architecture guarantees one row per execution.
        //
        // The wait is condition-based, not a fixed sleep: IngestionAuditService.logEvent
        // is @Async + REQUIRES_NEW, so the events commit on another thread AFTER the job
        // futures complete. Measured visibility lag is 1-2 ms locally; on constrained CI
        // runners the window stretched enough for the previous immediate query to catch
        // only one of the two events (CI failure of 2026-07-19). Bounded at 10 s.
        await().atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    assertThat(succeededEventsFor("tech117-a"))
                            .as("execution A audited as succeeded; audit state: " + auditDump())
                            .isEqualTo(1L);
                    assertThat(succeededEventsFor("tech117-b"))
                            .as("execution B audited as succeeded; audit state: " + auditDump())
                            .isEqualTo(1L);
                    assertThat(jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ingestion_audit WHERE event_type = 'INGESTION_FAILED'",
                            Long.class))
                            .as("no execution failed; audit state: " + auditDump())
                            .isEqualTo(0L);
                });

        // Shared run row (force=true restart semantics): SUCCEEDED, coherent metrics,
        // no unique-violation error recorded.
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
        assertThat(((Number) run.get("reject_count")).intValue()).isZero();
        int inserted = ((Number) run.get("records_inserted")).intValue();
        assertThat(inserted).as("whichever execution reported last: 0 <= inserted <= seen")
                .isBetween(0, RECORDS);
    }

    private Long succeededEventsFor(String requestId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ingestion_audit "
                        + "WHERE event_type = 'INGESTION_SUCCEEDED' AND request_id = ?",
                Long.class, requestId);
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
