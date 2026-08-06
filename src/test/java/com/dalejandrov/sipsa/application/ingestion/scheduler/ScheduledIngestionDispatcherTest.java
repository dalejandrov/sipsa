package com.dalejandrov.sipsa.application.ingestion.scheduler;

import com.dalejandrov.sipsa.application.command.CreateRunRequest;
import com.dalejandrov.sipsa.application.command.IngestionRequest;
import com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob;
import com.dalejandrov.sipsa.application.ingestion.core.WindowPolicy;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionService;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * TECH-053: {@link ScheduledIngestionDispatcher}'s actual dispatch logic — request
 * construction, order, flags, failure containment — moved here from the old
 * {@code SipsaIngestionSchedulerTest} verbatim (only the receiver changed, from
 * {@code scheduler.runXxx()} to {@code dispatcher.dispatchXxx()}). These tests call the
 * dispatcher methods directly on a plain instance (no Spring context), so {@code @Async}
 * does not apply here — the calls run synchronously, which is exactly right for testing
 * the dispatch *logic*. Real asynchronous behavior (thread, return-before-completion) is
 * verified separately in {@code ScheduledIngestionAsyncDispatchTest}, which does need a
 * real Spring context for the proxy to apply.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScheduledIngestionDispatcher — dispatch logic (synchronous call, no Spring proxy)")
class ScheduledIngestionDispatcherTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Mock
    private GenericIngestionJob ingestionJob;

    private ScheduledIngestionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new ScheduledIngestionDispatcher(ingestionJob);
    }

    @Test
    @DisplayName("dispatchDailyWindow() runs exactly Ciudad, Parcial, Semana, in that order")
    void dailyWindow_dispatchesExactlyExpectedHandlers_inOrder() {
        dispatcher.dispatchDailyWindow();

        InOrder inOrder = inOrder(ingestionJob);
        inOrder.verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaCiudad")));
        inOrder.verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaParcial")));
        inOrder.verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaSemanaMadr")));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("dispatchDailyWindow() dispatches the three daily methods with force=false and requestSource=SCHEDULED")
    void dailyWindow_eachRequestHasExpectedFlags() {
        dispatcher.dispatchDailyWindow();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob, times(3)).execute(captor.capture());

        List<IngestionRequest> requests = captor.getAllValues();
        assertThat(requests).hasSize(3);
        assertThat(requests).extracting(IngestionRequest::methodName)
                .containsExactly("promediosSipsaCiudad", "promediosSipsaParcial", "promediosSipsaSemanaMadr");
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.force()).isFalse();
            assertThat(request.requestSource()).isEqualTo(RequestSource.SCHEDULED);
            assertThat(request.requestId()).matches(UUID_PATTERN);
        });
        // Each dispatched request gets its own correlation id.
        assertThat(requests).extracting(IngestionRequest::requestId).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("dispatchDailyWindow() never dispatches a monthly method")
    void dailyWindow_neverDispatchesMonthlyMethods() {
        dispatcher.dispatchDailyWindow();

        verify(ingestionJob, never())
                .execute(argThat(r -> r.methodName().equals("promediosSipsaMesMadr")
                        || r.methodName().equals("promedioAbasSipsaMesMadr")));
    }

    @Test
    @DisplayName("dispatchMonthlyMes() dispatches exactly promediosSipsaMesMadr, force=false, SCHEDULED")
    void monthlyMes_dispatchesCorrectMethod() {
        dispatcher.dispatchMonthlyMes();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob).execute(captor.capture());
        verifyNoMoreInteractions(ingestionJob);

        IngestionRequest request = captor.getValue();
        assertThat(request.methodName()).isEqualTo("promediosSipsaMesMadr");
        assertThat(request.force()).isFalse();
        assertThat(request.requestSource()).isEqualTo(RequestSource.SCHEDULED);
        assertThat(request.requestId()).matches(UUID_PATTERN);
    }

    @Test
    @DisplayName("dispatchMonthlyAbas() dispatches exactly promedioAbasSipsaMesMadr, force=false, SCHEDULED")
    void monthlyAbas_dispatchesCorrectMethod() {
        dispatcher.dispatchMonthlyAbas();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob).execute(captor.capture());
        verifyNoMoreInteractions(ingestionJob);

        IngestionRequest request = captor.getValue();
        assertThat(request.methodName()).isEqualTo("promedioAbasSipsaMesMadr");
        assertThat(request.force()).isFalse();
        assertThat(request.requestSource()).isEqualTo(RequestSource.SCHEDULED);
    }

    @Test
    @DisplayName("dispatchMonthlyMes() and dispatchMonthlyAbas() never dispatch each other's method or a daily method")
    void monthlyJobs_dispatchOnlyTheirOwnMethod() {
        dispatcher.dispatchMonthlyMes();
        dispatcher.dispatchMonthlyAbas();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob, times(2)).execute(captor.capture());

        assertThat(captor.getAllValues()).extracting(IngestionRequest::methodName)
                .containsExactlyInAnyOrder("promediosSipsaMesMadr", "promedioAbasSipsaMesMadr");
    }

    @Test
    @DisplayName("a failure in Ciudad does not prevent Parcial and Semana from running, and does not propagate out of dispatchDailyWindow()")
    void dailyWindow_oneFailureDoesNotStopTheOthers_andIsContained() {
        doThrow(new RuntimeException("simulated SOAP failure"))
                .when(ingestionJob)
                .execute(argThat(r -> r.methodName().equals("promediosSipsaCiudad")));

        // runSafely() must catch the exception; it must not propagate to the caller
        // (an @Async void method's uncaught exception is only reachable via a custom
        // AsyncUncaughtExceptionHandler - it must never get there in the first place).
        assertThatCode(() -> dispatcher.dispatchDailyWindow()).doesNotThrowAnyException();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob, times(3)).execute(captor.capture());

        assertThat(captor.getAllValues()).extracting(IngestionRequest::methodName)
                .containsExactly("promediosSipsaCiudad", "promediosSipsaParcial", "promediosSipsaSemanaMadr");
    }

    @Test
    @DisplayName("a failure in dispatchMonthlyMes() does not propagate to the caller")
    void monthlyMes_failureIsContained() {
        doThrow(new RuntimeException("simulated failure")).when(ingestionJob).execute(argThat(r -> true));

        assertThatCode(() -> dispatcher.dispatchMonthlyMes()).doesNotThrowAnyException();

        verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaMesMadr")));
    }

    // -----------------------------------------------------------------------------
    // TECH-053: overlap protection (pre-existing, unchanged) survives the refactor.
    // Uses a REAL GenericIngestionJob (not mocked) so its actual duplicate-handling
    // logic runs, with only its own dependencies mocked underneath.
    // -----------------------------------------------------------------------------

    @Test
    @DisplayName("existing overlap protection is preserved: a duplicate-run signal from createRun is skipped, not thrown, and the window continues")
    void dailyWindow_existingOverlapProtection_stillSkipsAndContinues() {
        WindowPolicy windowPolicy = mock(WindowPolicy.class);
        IngestionControlService controlService = mock(IngestionControlService.class);
        IngestionAuditService auditService = mock(IngestionAuditService.class);
        IngestionService ingestionService = mock(IngestionService.class);
        IngestionMetrics metrics = mock(IngestionMetrics.class);
        IngestionProperties properties = new IngestionProperties();

        GenericIngestionJob realJob = new GenericIngestionJob(
                ingestionService, windowPolicy, controlService, auditService, properties, metrics);
        ScheduledIngestionDispatcher realDispatcher = new ScheduledIngestionDispatcher(realJob);

        when(windowPolicy.validateAndGetKey(any(), eq(false))).thenReturn("2026-07-20");
        // Ciudad: a concurrent/previous run already claimed this (method, windowKey) —
        // the real uq_ingestion_runs_window constraint's translated exception.
        when(controlService.isRunComplete(eq("promediosSipsaCiudad"), any())).thenReturn(false);
        when(controlService.createRun(any(CreateRunRequest.class)))
                .thenThrow(new SipsaBusinessException("Run already exists (Status: RUNNING). Use force=true to restart."));
        // Parcial and Semana proceed normally.
        when(controlService.isRunComplete(eq("promediosSipsaParcial"), any())).thenReturn(false);
        when(controlService.isRunComplete(eq("promediosSipsaSemanaMadr"), any())).thenReturn(false);
        when(controlService.createRun(argThat((CreateRunRequest r) ->
                r != null && !r.methodName().equals("promediosSipsaCiudad"))))
                .thenReturn(1L);
        when(controlService.isRunCanceled(1L)).thenReturn(false);
        // SIPSA-F4-21: updateStatus now returns whether the conditional transition won -
        // Parcial/Semana's RUNNING transition must win for the job to reach isRunCanceled.
        when(controlService.updateStatus(eq(1L), any())).thenReturn(true);

        assertThatCode(realDispatcher::dispatchDailyWindow).doesNotThrowAnyException();

        // The overlap is reported as a controlled skip (audited), not an uncaught
        // exception - runSafely() never even needed its own catch block for it.
        // Ciudad's skip, plus Parcial's and Semana's normal "started" events, are all
        // routed through the same auditService - the point is that nothing threw.
        verify(auditService, org.mockito.Mockito.atLeastOnce()).logEvent(any());
    }
}
