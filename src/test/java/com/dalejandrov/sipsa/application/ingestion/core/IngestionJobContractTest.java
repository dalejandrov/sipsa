package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.application.command.AuditEventRequest;
import com.dalejandrov.sipsa.application.command.IngestionRequest;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.domain.entity.AuditEventType;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TECH-042: {@link IngestionJob#execute} contract coverage for the behaviors not already
 * exercised by {@code IngestionJobMetricsTest} (TECH-032, outcome/metrics recording) or
 * {@code IngestionJobRejectThresholdTest} (TECH-135, {@code validateThresholds} in
 * isolation) — duplicate-run skip/override, rejected-record persistence, DB metrics
 * persistence, MDC lifecycle, and explicit status/audit-argument assertions per
 * transition. Reuses the {@code ScriptedIngestionJob} subclass-with-mocked-collaborators
 * pattern already established in {@code IngestionJobMetricsTest}.
 */
@DisplayName("IngestionJob.execute — contract coverage (TECH-042)")
class IngestionJobContractTest {

    private static class ScriptedIngestionJob extends IngestionJob {
        private final Runnable duringRunIngestion;
        private final Exception toThrow;
        private final int seen;
        private final List<IngestionContext.RejectedRecord> rejects;

        ScriptedIngestionJob(WindowPolicy windowPolicy, IngestionControlService controlService,
                              IngestionAuditService auditService, IngestionProperties properties,
                              IngestionMetrics metrics, Exception toThrow, int seen,
                              List<IngestionContext.RejectedRecord> rejects, Runnable duringRunIngestion) {
            super(windowPolicy, controlService, auditService, properties, metrics);
            this.toThrow = toThrow;
            this.seen = seen;
            this.rejects = rejects;
            this.duringRunIngestion = duringRunIngestion;
        }

        @Override
        protected void runIngestion(IngestionContext context) throws Exception {
            for (int i = 0; i < seen; i++) {
                context.incrementSeen();
            }
            for (IngestionContext.RejectedRecord r : rejects) {
                context.addRejectedRecord(r.rawData(), r.reason(), r.isParseError());
            }
            if (duringRunIngestion != null) {
                duringRunIngestion.run();
            }
            if (toThrow != null) {
                throw toThrow;
            }
        }
    }

    private final WindowPolicy windowPolicy = mock(WindowPolicy.class);
    private final IngestionControlService controlService = mock(IngestionControlService.class);
    private final IngestionAuditService auditService = mock(IngestionAuditService.class);
    private final IngestionMetrics metrics = mock(IngestionMetrics.class);
    private final IngestionProperties properties = new IngestionProperties();

    private void mockHappyPathUpToRunCreation(String methodName, String windowKey, long runId, boolean force) {
        when(windowPolicy.validateAndGetKey(eq(methodName), eq(force))).thenReturn(windowKey);
        when(controlService.isRunComplete(methodName, windowKey)).thenReturn(false);
        when(controlService.createRun(any())).thenReturn(runId);
        when(controlService.isRunCanceled(runId)).thenReturn(false);
        // SIPSA-F4-21: updateStatus now returns whether the conditional transition won: a
        // caller that doesn't opt into raced-cancellation coverage always expects it to win.
        when(controlService.updateStatus(anyLong(), any())).thenReturn(true);
        when(metrics.startRun()).thenReturn(Timer.start());
    }

    private ScriptedIngestionJob job(Exception toThrow, int seen, List<IngestionContext.RejectedRecord> rejects,
                                      Runnable duringRunIngestion) {
        return new ScriptedIngestionJob(windowPolicy, controlService, auditService, properties, metrics,
                toThrow, seen, rejects, duringRunIngestion);
    }

    private ScriptedIngestionJob job(Exception toThrow, int seen) {
        return job(toThrow, seen, List.of(), null);
    }

    // -----------------------------------------------------------------------
    // Duplicate run: skip without force, proceed with force
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("already-SUCCEEDED run, not forced: skipped before createRun, no run/MDC/metrics side effects")
    void duplicateRun_notForced_skippedNoRunCreated() {
        when(windowPolicy.validateAndGetKey("promediosSipsaCiudad", false)).thenReturn("2026-07-20");
        when(controlService.isRunComplete("promediosSipsaCiudad", "2026-07-20")).thenReturn(true);

        job(null, 0).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-dup-1"));

        verify(controlService, never()).createRun(any());
        verify(controlService, never()).updateStatus(anyLong(), any());
        verify(metrics, never()).startRun();
        verify(metrics, never()).recordRunCompleted(any(), any(), any());
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, times(1)).logEvent(audit.capture());
        assertThat(audit.getValue().eventType()).isEqualTo(AuditEventType.INGESTION_SKIPPED_DUPLICATE);
    }

    @Test
    @DisplayName("already-SUCCEEDED run, forced: createRun is called and the run proceeds normally")
    void duplicateRun_forced_runCreatedAndProceeds() {
        when(windowPolicy.validateAndGetKey("promediosSipsaCiudad", true)).thenReturn("2026-07-20");
        when(controlService.isRunComplete("promediosSipsaCiudad", "2026-07-20")).thenReturn(true);
        when(controlService.createRun(any())).thenReturn(50L);
        when(controlService.isRunCanceled(50L)).thenReturn(false);
        when(controlService.updateStatus(anyLong(), any())).thenReturn(true);
        when(metrics.startRun()).thenReturn(Timer.start());

        job(null, 3).execute(IngestionRequest.manualForced("promediosSipsaCiudad", "req-dup-2"));

        verify(controlService, times(1)).createRun(any());
        verify(controlService).updateStatus(50L, IngestionRunStatus.SUCCEEDED);
    }

    // -----------------------------------------------------------------------
    // Rejected records persisted in finally
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("rejected records are persisted via logReject, one call per record, correct arguments")
    void rejectedRecords_persistedViaLogRejectInFinally() {
        mockHappyPathUpToRunCreation("promediosSipsaParcial", "2026-07-20", 60L, false);
        List<IngestionContext.RejectedRecord> rejects = List.of(
                new IngestionContext.RejectedRecord("raw-1", "missing field", false),
                new IngestionContext.RejectedRecord("raw-2", "parse error", true));

        job(null, 10, rejects, null).execute(IngestionRequest.manual("promediosSipsaParcial", "req-rej-1"));

        verify(controlService, times(1)).logReject(60L, "raw-1", "missing field", false);
        verify(controlService, times(1)).logReject(60L, "raw-2", "parse error", true);
        verify(controlService, times(2)).logReject(eq(60L), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("no rejected records: logReject is never called")
    void noRejectedRecords_logRejectNeverCalled() {
        mockHappyPathUpToRunCreation("promediosSipsaParcial", "2026-07-20", 61L, false);

        job(null, 10).execute(IngestionRequest.manual("promediosSipsaParcial", "req-rej-2"));

        verify(controlService, never()).logReject(anyLong(), any(), any(), anyBoolean());
    }

    // -----------------------------------------------------------------------
    // updateMetrics (DB persistence of counts) called in finally
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("updateMetrics is called once in finally with the final counts, on success")
    void updateMetricsCalledInFinally_onSuccess() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 70L, false);

        job(null, 7).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-met-1"));

        verify(controlService, times(1)).updateMetrics(70L, 7, 0, 0, 0);
    }

    @Test
    @DisplayName("updateMetrics is called once in finally with the final counts, on failure")
    void updateMetricsCalledInFinally_onFailure() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 71L, false);

        job(new SipsaIngestionException("boom"), 4).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-met-2"));

        verify(controlService, times(1)).updateMetrics(71L, 4, 0, 0, 0);
    }

    // -----------------------------------------------------------------------
    // MDC lifecycle
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("MDC is populated with runId/method/windowKey/requestId/requestSource during runIngestion")
    void mdcPopulatedDuringRunIngestion() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 80L, false);
        Map<String, String> captured = new java.util.HashMap<>();

        job(null, 0, List.of(), () -> {
            captured.put("runId", MDC.get("runId"));
            captured.put("method", MDC.get("method"));
            captured.put("windowKey", MDC.get("windowKey"));
            captured.put("requestId", MDC.get("requestId"));
            captured.put("requestSource", MDC.get("requestSource"));
        }).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-mdc-1"));

        assertThat(captured).containsEntry("runId", "80")
                .containsEntry("method", "promediosSipsaCiudad")
                .containsEntry("windowKey", "2026-07-20")
                .containsEntry("requestId", "req-mdc-1")
                .containsEntry("requestSource", "MANUAL");
    }

    @Test
    @DisplayName("MDC is cleared after a successful run")
    void mdcClearedAfterSuccess() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 81L, false);

        job(null, 1).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-mdc-2"));

        assertThat(MDC.getCopyOfContextMap()).as("MDC map is null or empty after execute() returns")
                .satisfiesAnyOf(
                        map -> assertThat(map).isNull(),
                        map -> assertThat(map).isEmpty());
    }

    @Test
    @DisplayName("MDC is cleared after a failed run")
    void mdcClearedAfterFailure() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 82L, false);

        job(new SipsaIngestionException("boom"), 1).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-mdc-3"));

        assertThat(MDC.getCopyOfContextMap()).as("MDC map is null or empty after execute() returns")
                .satisfiesAnyOf(
                        map -> assertThat(map).isNull(),
                        map -> assertThat(map).isEmpty());
    }

    @Test
    @DisplayName("MDC does not leak between two sequential executions on the same thread")
    void mdcDoesNotLeakBetweenExecutions() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 90L, false);
        job(new SipsaIngestionException("first run fails"), 1)
                .execute(IngestionRequest.manual("promediosSipsaCiudad", "req-leak-1"));

        mockHappyPathUpToRunCreation("promediosSipsaParcial", "2026-07-21", 91L, false);
        Map<String, String> secondRunMdc = new java.util.HashMap<>();
        job(null, 1, List.of(), () -> secondRunMdc.put("runId", MDC.get("runId")))
                .execute(IngestionRequest.manual("promediosSipsaParcial", "req-leak-2"));

        assertThat(secondRunMdc).as("the second run's MDC must reflect only itself, not the first run's runId")
                .containsEntry("runId", "91");
    }

    // -----------------------------------------------------------------------
    // Explicit status transitions and audit event arguments
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("RUNNING transition: status updated and INGESTION_RUNNING audit emitted with the real runId")
    void runningTransition_statusAndAuditEmitted() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 100L, false);

        job(null, 1).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-run-1"));

        verify(controlService).updateStatus(100L, IngestionRunStatus.RUNNING);
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, times(4)).logEvent(audit.capture()); // started, running, succeeded, metricsUpdated
        List<AuditEventType> types = audit.getAllValues().stream().map(AuditEventRequest::eventType).toList();
        assertThat(types).contains(AuditEventType.INGESTION_RUNNING);
        AuditEventRequest running = audit.getAllValues().stream()
                .filter(e -> e.eventType() == AuditEventType.INGESTION_RUNNING).findFirst().orElseThrow();
        assertThat(running.runId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("SUCCEEDED transition: status updated and INGESTION_SUCCEEDED audit emitted")
    void succeededTransition_statusAndAuditEmitted() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 101L, false);

        job(null, 1).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-run-2"));

        verify(controlService).updateStatus(101L, IngestionRunStatus.SUCCEEDED);
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, times(4)).logEvent(audit.capture());
        assertThat(audit.getAllValues().stream().map(AuditEventRequest::eventType))
                .contains(AuditEventType.INGESTION_SUCCEEDED);
    }

    @Test
    @DisplayName("FAILED transition: status updated, error logged with httpStatus/soapFaultCode, INGESTION_FAILED audit with the exception message")
    void failedTransition_statusAndAuditEmitted() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 102L, false);
        SipsaExternalException external = new SipsaExternalException("DANE unavailable", 503, "SOAP-503");

        job(external, 1).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-run-3"));

        verify(controlService).updateStatus(102L, IngestionRunStatus.FAILED);
        verify(controlService).logError(102L, "DANE unavailable", 503, "SOAP-503");
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, times(4)).logEvent(audit.capture()); // started, running, failed, metricsUpdated (finally always runs)
        AuditEventRequest failed = audit.getAllValues().stream()
                .filter(e -> e.eventType() == AuditEventType.INGESTION_FAILED).findFirst().orElseThrow();
        assertThat(failed.message()).isEqualTo("Error: DANE unavailable");
    }

    @Test
    @DisplayName("FAILED transition from a non-external exception: logError receives null httpStatus/soapFaultCode")
    void failedTransition_nonExternalException_nullHttpAndFaultCode() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 103L, false);

        job(new SipsaIngestionException("threshold exceeded"), 1)
                .execute(IngestionRequest.manual("promediosSipsaCiudad", "req-run-4"));

        verify(controlService).logError(103L, "threshold exceeded", null, null);
    }

    @Test
    @DisplayName("CANCELED path: INGESTION_CANCELED audit emitted and status is never overwritten to SUCCEEDED")
    void canceledTransition_auditEmitted_statusNotOverwritten() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 104L, false);
        when(controlService.isRunCanceled(104L)).thenReturn(true);

        job(null, 1).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-run-5"));

        verify(controlService, never()).updateStatus(104L, IngestionRunStatus.SUCCEEDED);
        verify(controlService, never()).updateStatus(104L, IngestionRunStatus.FAILED);
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, atLeastOnce()).logEvent(audit.capture());
        assertThat(audit.getAllValues().stream().map(AuditEventRequest::eventType))
                .contains(AuditEventType.INGESTION_CANCELED);
    }

    // -----------------------------------------------------------------------
    // SIPSA-F4-21: late/lost transitions - updateStatus returns false because a
    // concurrent cancelRun already won the row.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("RUNNING transition lost to a concurrent cancel: runIngestion never runs, late-transition audit emitted")
    void runningTransitionLost_runIngestionNeverInvoked_lateTransitionAudited() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 110L, false);
        when(controlService.updateStatus(110L, IngestionRunStatus.RUNNING)).thenReturn(false);
        java.util.concurrent.atomic.AtomicBoolean ranIngestion = new java.util.concurrent.atomic.AtomicBoolean(false);

        job(null, 1, List.of(), () -> ranIngestion.set(true))
                .execute(IngestionRequest.manual("promediosSipsaCiudad", "req-late-running"));

        assertThat(ranIngestion).as("runIngestion must not execute once the run already left STARTED").isFalse();
        verify(controlService, never()).updateStatus(110L, IngestionRunStatus.SUCCEEDED);
        verify(controlService, never()).updateStatus(110L, IngestionRunStatus.FAILED);
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, atLeastOnce()).logEvent(audit.capture());
        assertThat(audit.getAllValues().stream().map(AuditEventRequest::eventType))
                .contains(AuditEventType.INGESTION_LATE_TRANSITION_IGNORED)
                .doesNotContain(AuditEventType.INGESTION_RUNNING, AuditEventType.INGESTION_SUCCEEDED);
    }

    @Test
    @DisplayName("SUCCEEDED transition lost to a concurrent cancel: no INGESTION_SUCCEEDED audit, late-transition audit emitted instead")
    void succeededTransitionLost_lateTransitionAuditedInsteadOfSucceeded() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 111L, false);
        when(controlService.updateStatus(111L, IngestionRunStatus.SUCCEEDED)).thenReturn(false);

        job(null, 1).execute(IngestionRequest.manual("promediosSipsaCiudad", "req-late-succeeded"));

        verify(controlService).updateStatus(111L, IngestionRunStatus.SUCCEEDED);
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, atLeastOnce()).logEvent(audit.capture());
        List<AuditEventType> types = audit.getAllValues().stream().map(AuditEventRequest::eventType).toList();
        assertThat(types).contains(AuditEventType.INGESTION_LATE_TRANSITION_IGNORED)
                .doesNotContain(AuditEventType.INGESTION_SUCCEEDED);
        // Metrics are still persisted for the run's own final tally, even though the
        // SUCCEEDED transition itself was ignored (SIPSA-F4-21 design decision).
        verify(controlService, times(1)).updateMetrics(111L, 1, 0, 0, 0);
    }

    @Test
    @DisplayName("FAILED transition lost to a concurrent cancel: error still logged, no INGESTION_FAILED audit, late-transition audit emitted instead")
    void failedTransitionLost_errorStillLogged_lateTransitionAuditedInsteadOfFailed() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 112L, false);
        when(controlService.updateStatus(112L, IngestionRunStatus.FAILED)).thenReturn(false);

        job(new SipsaIngestionException("boom after cancel"), 1)
                .execute(IngestionRequest.manual("promediosSipsaCiudad", "req-late-failed"));

        verify(controlService).logError(112L, "boom after cancel", null, null);
        verify(controlService).updateStatus(112L, IngestionRunStatus.FAILED);
        ArgumentCaptor<AuditEventRequest> audit = ArgumentCaptor.forClass(AuditEventRequest.class);
        verify(auditService, atLeastOnce()).logEvent(audit.capture());
        List<AuditEventType> types = audit.getAllValues().stream().map(AuditEventRequest::eventType).toList();
        assertThat(types).contains(AuditEventType.INGESTION_LATE_TRANSITION_IGNORED)
                .doesNotContain(AuditEventType.INGESTION_FAILED);
    }
}
