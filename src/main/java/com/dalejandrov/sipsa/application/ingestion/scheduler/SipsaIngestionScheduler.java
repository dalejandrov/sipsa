package com.dalejandrov.sipsa.application.ingestion.scheduler;

import com.dalejandrov.sipsa.domain.entity.RequestSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for automatic execution of SIPSA ingestion jobs.
 * <p>
 * This component triggers ingestion at predefined times using Spring's
 * {@code @Scheduled} annotation. Schedules are aligned with DANE's data publication times:
 * <ul>
 *   <li>Daily/Weekly data (Ciudad, Parcial, Semana): 14:20 COT (20 min after DANE publishes at 14:00)</li>
 *   <li>Monthly wholesale data (MesMadr): Day 8 at 14:30 COT (30 min after DANE publishes)</li>
 *   <li>Monthly supply data (AbasMes): Day 10 at 14:30 COT (30 min after DANE publishes)</li>
 * </ul>
 * <p>
 * <b>DANE Publication Schedule:</b>
 * <ul>
 *   <li>Mayoristas (daily/weekly): Available from 2:00 PM (14:00) each day</li>
 *   <li>Mayoristas (monthly): Updated on day 8 of each month</li>
 *   <li>Abastecimientos (monthly): Updated on day 10 of each month</li>
 * </ul>
 * <p>
 * <b>TECH-053:</b> each {@code @Scheduled} method here only logs the trigger and hands
 * off to {@link ScheduledIngestionDispatcher}, which runs the actual ingestion on the
 * managed {@code ingestionTaskExecutor} pool ({@code ingestion-async-*} threads) — this
 * class never calls {@link com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob}
 * directly, and a {@code @Scheduled} method here returns as soon as the dispatch call is
 * made, not when the ingestion finishes. See {@link ScheduledIngestionDispatcher} for
 * why dispatch happens at the window level (one async call per trigger), not once per
 * method — the daily window's Ciudad → Parcial → Semana sequence stays sequential, on
 * one worker thread, exactly as before this story.
 * <p>
 * All scheduled executions use {@link RequestSource#SCHEDULED} to differentiate
 * them from manual API calls. Each execution generates a unique UUID for tracking
 * (in {@link ScheduledIngestionDispatcher}).
 * <p>
 * <b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code sipsa.ingestion.cron.daily} - Daily window cron (default: 0 20 14 * * *)</li>
 *   <li>{@code sipsa.ingestion.cron.monthly-mes} - Day 8 cron (default: 0 30 14 8 * *)</li>
 *   <li>{@code sipsa.ingestion.cron.monthly-abas} - Day 10 cron (default: 0 30 14 10 * *)</li>
 *   <li>{@code sipsa.timezone} - Timezone for scheduling (default: America/Bogota)</li>
 * </ul>
 *
 * @see ScheduledIngestionDispatcher
 * @see RequestSource
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SipsaIngestionScheduler {

    private final ScheduledIngestionDispatcher dispatcher;

    /**
     * Triggers the daily ingestion window.
     * <p>
     * Fires daily at 14:20 (America/Bogota timezone). Dispatches Ciudad, Parcial, and
     * Semana to the managed executor as one sequential unit of work and returns
     * immediately — it does not wait for any of them to finish.
     */
    @Scheduled(cron = "${sipsa.ingestion.cron.daily:0 20 14 * * *}", zone = "${sipsa.timezone:America/Bogota}")
    public void runDailyWindow() {
        log.info("Triggering Daily Ingestion Window");
        dispatcher.dispatchDailyWindow();
    }

    /**
     * Triggers monthly MesMadr ingestion.
     * <p>
     * Fires on day 8 of each month at 14:30 (America/Bogota timezone). Dispatches to
     * the managed executor and returns immediately.
     */
    @Scheduled(cron = "${sipsa.ingestion.cron.monthly-mes:0 30 14 8 * *}", zone = "${sipsa.timezone:America/Bogota}")
    public void runMonthlyMes() {
        log.info("Triggering Monthly MesMadr");
        dispatcher.dispatchMonthlyMes();
    }

    /**
     * Triggers monthly AbasMes ingestion.
     * <p>
     * Fires on day 10 of each month at 14:30 (America/Bogota timezone). Dispatches to
     * the managed executor and returns immediately.
     */
    @Scheduled(cron = "${sipsa.ingestion.cron.monthly-abas:0 30 14 10 * *}", zone = "${sipsa.timezone:America/Bogota}")
    public void runMonthlyAbas() {
        log.info("Triggering Monthly AbasMes");
        dispatcher.dispatchMonthlyAbas();
    }
}
