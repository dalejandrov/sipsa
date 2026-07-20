package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.application.command.IngestionRequest;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TECH-032: {@link IngestionJob#execute} calls {@link IngestionMetrics#recordRunCompleted}
 * exactly once per run attempt, with the outcome matching the run's actual final status —
 * verified by mocking {@link IngestionMetrics} and inspecting call counts/arguments,
 * complementing {@code IngestionMetricsTest} (which verifies the meter values themselves
 * against a real registry).
 * <p>
 * Uses a minimal concrete {@link IngestionJob} subclass whose {@code runIngestion} either
 * increments a few counters and returns, or throws — the same pattern already established
 * for testing this abstract class in {@code IngestionJobRejectThresholdTest}.
 */
@DisplayName("IngestionJob.execute — one recordRunCompleted call per run, correct outcome")
class IngestionJobMetricsTest {

    private static class ScriptedIngestionJob extends IngestionJob {
        private final Exception toThrow;
        private final int seen;

        ScriptedIngestionJob(WindowPolicy windowPolicy, IngestionControlService controlService,
                              IngestionAuditService auditService, IngestionProperties properties,
                              IngestionMetrics metrics, Exception toThrow, int seen) {
            super(windowPolicy, controlService, auditService, properties, metrics);
            this.toThrow = toThrow;
            this.seen = seen;
        }

        @Override
        protected void runIngestion(IngestionContext context) throws Exception {
            for (int i = 0; i < seen; i++) {
                context.incrementSeen();
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

    private void mockHappyPathUpToRunCreation(String methodName, String windowKey, long runId) {
        when(windowPolicy.validateAndGetKey(eq(methodName), eq(false))).thenReturn(windowKey);
        when(controlService.isRunComplete(methodName, windowKey)).thenReturn(false);
        when(controlService.createRun(any())).thenReturn(runId);
        when(controlService.isRunCanceled(runId)).thenReturn(false);
        when(metrics.startRun()).thenReturn(Timer.start());
    }

    @Test
    @DisplayName("a successful run calls recordRunCompleted exactly once with outcome=success")
    void successfulRun_recordsOutcomeSuccessOnce() {
        mockHappyPathUpToRunCreation("promediosSipsaCiudad", "2026-07-20", 42L);
        ScriptedIngestionJob job = new ScriptedIngestionJob(
                windowPolicy, controlService, auditService, properties, metrics, null, 10);

        job.execute(IngestionRequest.manual("promediosSipsaCiudad", "req-1"));

        verify(metrics, times(1)).recordRunCompleted(any(), any(IngestionContext.class), eq(IngestionMetrics.OUTCOME_SUCCESS));
    }

    @Test
    @DisplayName("a failed run calls recordRunCompleted exactly once with outcome=failure")
    void failedRun_recordsOutcomeFailureOnce() {
        mockHappyPathUpToRunCreation("promediosSipsaParcial", "2026-07-20", 43L);
        ScriptedIngestionJob job = new ScriptedIngestionJob(
                windowPolicy, controlService, auditService, properties, metrics,
                new SipsaIngestionException("boom"), 5);

        job.execute(IngestionRequest.manual("promediosSipsaParcial", "req-2"));

        verify(metrics, times(1)).recordRunCompleted(any(), any(IngestionContext.class), eq(IngestionMetrics.OUTCOME_FAILURE));
    }

    @Test
    @DisplayName("a canceled run calls recordRunCompleted exactly once with outcome=canceled")
    void canceledRun_recordsOutcomeCanceledOnce() {
        mockHappyPathUpToRunCreation("promediosSipsaSemanaMadr", "2026-07-20", 44L);
        when(controlService.isRunCanceled(44L)).thenReturn(true);
        ScriptedIngestionJob job = new ScriptedIngestionJob(
                windowPolicy, controlService, auditService, properties, metrics, null, 3);

        job.execute(IngestionRequest.manual("promediosSipsaSemanaMadr", "req-3"));

        verify(metrics, times(1)).recordRunCompleted(any(), any(IngestionContext.class), eq(IngestionMetrics.OUTCOME_CANCELED));
    }

    @Test
    @DisplayName("a run skipped by the window policy never reaches recordRunCompleted — no context/run was ever created")
    void windowSkippedRun_neverRecordsMetrics() {
        when(windowPolicy.validateAndGetKey(any(), eq(false)))
                .thenThrow(new com.dalejandrov.sipsa.domain.exception.WindowViolationException("outside window"));
        ScriptedIngestionJob job = new ScriptedIngestionJob(
                windowPolicy, controlService, auditService, properties, metrics, null, 0);

        job.execute(IngestionRequest.manual("promediosSipsaCiudad", "req-4"));

        verify(metrics, never()).recordRunCompleted(any(), any(), any());
        verify(metrics, never()).startRun();
    }
}
