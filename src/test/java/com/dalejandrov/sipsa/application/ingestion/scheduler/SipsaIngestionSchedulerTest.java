package com.dalejandrov.sipsa.application.ingestion.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * TECH-053: {@link SipsaIngestionScheduler} only logs and delegates to
 * {@link ScheduledIngestionDispatcher} — it no longer calls
 * {@code GenericIngestionJob.execute} directly at all (that dependency is gone from this
 * class entirely). What each dispatch actually does — request construction, order,
 * failure containment — moved to {@code ScheduledIngestionDispatcherTest} along with the
 * logic itself. Real async behavior (thread, return-before-completion) is covered by
 * {@code ScheduledIngestionAsyncDispatchTest}. Cron/window timing is covered by
 * {@code SipsaSchedulingCronTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SipsaIngestionScheduler — delegates to the dispatcher, nothing else")
class SipsaIngestionSchedulerTest {

    @Mock
    private ScheduledIngestionDispatcher dispatcher;

    private SipsaIngestionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SipsaIngestionScheduler(dispatcher);
    }

    @Test
    @DisplayName("runDailyWindow() calls dispatcher.dispatchDailyWindow() exactly once, nothing else")
    void dailyWindow_delegatesToDispatcher_once() {
        scheduler.runDailyWindow();

        verify(dispatcher, times(1)).dispatchDailyWindow();
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    @DisplayName("runMonthlyMes() calls dispatcher.dispatchMonthlyMes() exactly once, nothing else")
    void monthlyMes_delegatesToDispatcher_once() {
        scheduler.runMonthlyMes();

        verify(dispatcher, times(1)).dispatchMonthlyMes();
        verifyNoMoreInteractions(dispatcher);
    }

    @Test
    @DisplayName("runMonthlyAbas() calls dispatcher.dispatchMonthlyAbas() exactly once, nothing else")
    void monthlyAbas_delegatesToDispatcher_once() {
        scheduler.runMonthlyAbas();

        verify(dispatcher, times(1)).dispatchMonthlyAbas();
        verifyNoMoreInteractions(dispatcher);
    }
}
