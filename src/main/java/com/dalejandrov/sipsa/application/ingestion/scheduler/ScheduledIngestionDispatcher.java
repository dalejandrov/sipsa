package com.dalejandrov.sipsa.application.ingestion.scheduler;

import com.dalejandrov.sipsa.application.command.IngestionRequest;
import com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dispatches scheduled ingestion windows to the managed {@code ingestionTaskExecutor}
 * pool, so {@link SipsaIngestionScheduler}'s {@code @Scheduled} methods return
 * immediately instead of blocking a scheduler thread for the whole window (TECH-053).
 * <p>
 * <b>Why a separate bean:</b> Spring's {@code @Async} proxy only intercepts calls that
 * arrive from <i>outside</i> the declaring bean — a method calling {@code @Async} on
 * itself runs synchronously, silently. {@link SipsaIngestionScheduler} calling a method
 * on this separate bean is what makes the proxy apply.
 * <p>
 * <b>One dispatch = one window, not one dispatch per method:</b> the daily window
 * deliberately runs Ciudad, Parcial, and Semana <i>sequentially, on the same worker
 * thread</i> — exactly as {@link SipsaIngestionScheduler} did before this story
 * ("prevent resource contention and ensure data consistency"). Dispatching each of the
 * three methods as its own separate {@code @Async} call would let them race each other
 * on the pool — that is internal parallelization of the ingestion window, which is
 * explicitly not this story's goal. {@link #dispatchDailyWindow()} is there for exactly
 * this reason: one {@code @Async} call per scheduled trigger, sequential execution
 * inside it, unchanged from before.
 * <p>
 * <b>Executor:</b> the existing {@code ingestionTaskExecutor} ({@link
 * com.dalejandrov.sipsa.infrastructure.config.AsyncConfig}, pool 2/10/25/60s,
 * {@code CallerRunsPolicy}) — already used by manual-trigger ingestion
 * ({@code AsyncIngestionService}) and audit logging
 * ({@code IngestionAuditService.logEvent}). Not a new executor: nothing in this story's
 * scope justifies a dedicated one, and reusing the existing, already-validated pool
 * config avoids introducing a second one to reason about.
 * <p>
 * <b>{@code CallerRunsPolicy} and this story's goal:</b> under sustained saturation
 * (pool exhausted, queue full), {@code CallerRunsPolicy} runs the rejected task on the
 * <i>calling</i> thread instead of dropping it — here, that calling thread is a
 * scheduler thread, which would then block for the run's duration, exactly the
 * behavior this story removes in the common case. This is accepted, documented
 * backpressure, not a bug: it only triggers when the pool is already saturated (2 core
 * / 10 max threads, 25-deep queue — daily/monthly windows are low-frequency and
 * short-lived relative to that capacity), and blocking under genuine overload is a
 * deliberate, existing choice (the same policy already governs audit logging and manual
 * triggers). This story does not claim "the scheduler thread never blocks" — only that
 * it does not block in the normal, non-saturated case.
 * <p>
 * <b>Overlap protection is unchanged, not introduced here:</b> two dispatches for the
 * same {@code (methodName, windowKey)} are still resolved by the existing
 * {@code uq_ingestion_runs_window UNIQUE (method_name, window_key)} constraint —
 * {@link com.dalejandrov.sipsa.application.service.IngestionControlService#createRun}
 * already translates the resulting {@code DataIntegrityViolationException} into
 * {@link com.dalejandrov.sipsa.domain.exception.SipsaBusinessException}, which {@link
 * GenericIngestionJob#execute} already treats as a controlled "skip duplicate" outcome
 * (audited, not thrown). Nothing in this class changes that path; it is exercised
 * identically whether {@code execute()} runs on a scheduler thread or an
 * {@code ingestion-async-*} thread.
 *
 * @see SipsaIngestionScheduler
 * @see com.dalejandrov.sipsa.infrastructure.config.AsyncConfig
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ScheduledIngestionDispatcher {

    private final GenericIngestionJob ingestionJob;

    /**
     * Dispatches the daily window (Ciudad, Parcial, Semana, in that order, sequentially)
     * to the managed executor. Returns immediately; the sequence runs on one
     * {@code ingestion-async-*} thread.
     */
    @Async("ingestionTaskExecutor")
    public void dispatchDailyWindow() {
        runSafely("promediosSipsaCiudad");
        runSafely("promediosSipsaParcial");
        runSafely("promediosSipsaSemanaMadr");
    }

    /**
     * Dispatches the monthly MesMadr ingestion to the managed executor. Returns
     * immediately.
     */
    @Async("ingestionTaskExecutor")
    public void dispatchMonthlyMes() {
        runSafely("promediosSipsaMesMadr");
    }

    /**
     * Dispatches the monthly AbasMes ingestion to the managed executor. Returns
     * immediately.
     */
    @Async("ingestionTaskExecutor")
    public void dispatchMonthlyAbas() {
        runSafely("promedioAbasSipsaMesMadr");
    }

    /**
     * Executes a single ingestion method safely, on whatever thread calls it.
     * <p>
     * Wraps the ingestion call in exception handling to ensure that one method's
     * failure doesn't prevent subsequent methods in the same dispatch from running, and
     * never propagates out to the caller (a scheduler thread must never see an uncaught
     * exception from here). Generates a unique {@code requestId} for tracking and uses
     * {@link com.dalejandrov.sipsa.domain.entity.RequestSource#SCHEDULED} to mark the
     * execution as automatic — unchanged from before this story.
     *
     * @param methodName the SOAP method name to execute
     */
    private void runSafely(String methodName) {
        String requestId = UUID.randomUUID().toString();

        try {
            log.debug("Dispatcher triggering method={} requestId={} source=SCHEDULED thread={}",
                    methodName, requestId, Thread.currentThread().getName());
            IngestionRequest request = IngestionRequest.scheduled(methodName, requestId);
            ingestionJob.execute(request);
        } catch (Exception e) {
            log.error("Dispatcher failed to trigger {} requestId={} source=SCHEDULED", methodName, requestId, e);
            // Continue to next task in sequence (handled by caller logic or separate crons)
        }
    }
}
