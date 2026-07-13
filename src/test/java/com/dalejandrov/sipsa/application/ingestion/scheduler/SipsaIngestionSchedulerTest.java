package com.dalejandrov.sipsa.application.ingestion.scheduler;

import com.dalejandrov.sipsa.api.dto.request.IngestionRequest;
import com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link SipsaIngestionScheduler}, with {@link GenericIngestionJob} mocked.
 * <p>
 * These tests invoke the {@code @Scheduled} methods directly (no real Spring scheduling,
 * no {@code Thread.sleep}, no waiting for the system clock) and verify only the dispatch
 * behavior: which method names are triggered, with what {@code force}/{@code requestSource}
 * values, in what order, and whether one method's failure affects the others in the same
 * window. Whether {@code GenericIngestionJob.execute()} itself does the right thing is
 * covered separately by {@code IngestionJobTest} (TECH-042); whether the window/cron timing
 * is correct is covered by {@link WindowPolicyTest} and {@link SipsaSchedulingCronTest}. No
 * real SOAP call, database write, or Spring context is involved here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SipsaIngestionScheduler")
class SipsaIngestionSchedulerTest {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Mock
    private GenericIngestionJob ingestionJob;

    private SipsaIngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SipsaIngestionScheduler(ingestionJob);
    }

    @Test
    @DisplayName("runDailyWindow() dispatches exactly Ciudad, Parcial, Semana, in that order")
    void dailyWindow_dispatchesExactlyExpectedHandlers_inOrder() {
        scheduler.runDailyWindow();

        InOrder inOrder = inOrder(ingestionJob);
        inOrder.verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaCiudad")));
        inOrder.verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaParcial")));
        inOrder.verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaSemanaMadr")));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    @DisplayName("runDailyWindow() dispatches the three daily methods with force=false and requestSource=SCHEDULED")
    void dailyWindow_eachRequestHasExpectedFlags() {
        scheduler.runDailyWindow();

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
    @DisplayName("runDailyWindow() never dispatches a monthly method")
    void dailyWindow_neverDispatchesMonthlyMethods() {
        scheduler.runDailyWindow();

        verify(ingestionJob, never())
                .execute(argThat(r -> r.methodName().equals("promediosSipsaMesMadr")
                        || r.methodName().equals("promedioAbasSipsaMesMadr")));
    }

    @Test
    @DisplayName("runMonthlyMes() dispatches exactly promediosSipsaMesMadr, force=false, SCHEDULED")
    void monthlyMes_dispatchesCorrectMethod() {
        scheduler.runMonthlyMes();

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
    @DisplayName("runMonthlyAbas() dispatches exactly promedioAbasSipsaMesMadr, force=false, SCHEDULED")
    void monthlyAbas_dispatchesCorrectMethod() {
        scheduler.runMonthlyAbas();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob).execute(captor.capture());
        verifyNoMoreInteractions(ingestionJob);

        IngestionRequest request = captor.getValue();
        assertThat(request.methodName()).isEqualTo("promedioAbasSipsaMesMadr");
        assertThat(request.force()).isFalse();
        assertThat(request.requestSource()).isEqualTo(RequestSource.SCHEDULED);
    }

    @Test
    @DisplayName("runMonthlyMes() and runMonthlyAbas() never dispatch each other's method or a daily method")
    void monthlyJobs_dispatchOnlyTheirOwnMethod() {
        scheduler.runMonthlyMes();
        scheduler.runMonthlyAbas();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob, times(2)).execute(captor.capture());

        assertThat(captor.getAllValues()).extracting(IngestionRequest::methodName)
                .containsExactlyInAnyOrder("promediosSipsaMesMadr", "promedioAbasSipsaMesMadr");
    }

    @Test
    @DisplayName("a failure in Ciudad does not prevent Parcial and Semana from running, and does not propagate out of runDailyWindow()")
    void dailyWindow_oneFailureDoesNotStopTheOthers_andIsContained() {
        doThrow(new RuntimeException("simulated SOAP failure"))
                .when(ingestionJob)
                .execute(argThat(r -> r.methodName().equals("promediosSipsaCiudad")));

        // runSafely() must catch the exception; it must not propagate to the scheduler caller
        // (Spring would log an uncaught @Scheduled exception and, depending on version,
        // could suppress future executions of the same trigger).
        assertThatCode(() -> scheduler.runDailyWindow()).doesNotThrowAnyException();

        ArgumentCaptor<IngestionRequest> captor = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(ingestionJob, times(3)).execute(captor.capture());

        assertThat(captor.getAllValues()).extracting(IngestionRequest::methodName)
                .containsExactly("promediosSipsaCiudad", "promediosSipsaParcial", "promediosSipsaSemanaMadr");
    }

    @Test
    @DisplayName("a failure in runMonthlyMes() does not propagate to the caller")
    void monthlyMes_failureIsContained() {
        doThrow(new RuntimeException("simulated failure")).when(ingestionJob).execute(argThat(r -> true));

        assertThatCode(() -> scheduler.runMonthlyMes()).doesNotThrowAnyException();

        verify(ingestionJob).execute(argThat(r -> r.methodName().equals("promediosSipsaMesMadr")));
    }
}
