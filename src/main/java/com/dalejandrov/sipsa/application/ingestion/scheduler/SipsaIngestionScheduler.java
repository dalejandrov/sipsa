package com.dalejandrov.sipsa.application.ingestion.scheduler;

import com.dalejandrov.sipsa.api.dto.IngestionRequest;
import com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Scheduler for automatic execution of SIPSA ingestion jobs.
 * <p>
 * This component triggers ingestion jobs at predefined times using Spring's
 * {@code @Scheduled} annotation. It manages:
 * <ul>
 *   <li>Daily ingestion window (14:20 America/Bogota) for Ciudad, Parcial, and Semana</li>
 *   <li>Monthly ingestion on day 8 (06:00) for MesMadr</li>
 *   <li>Monthly ingestion on day 10 (06:00) for AbasMes</li>
 * </ul>
 * <p>
 * All scheduled executions use {@link RequestSource#SCHEDULED} to differentiate
 * them from manual API calls. Each execution generates a unique UUID for tracking.
 * <p>
 * <b>Execution is sequential within each window</b> to prevent resource contention
 * and ensure data consistency. If one job fails, subsequent jobs still execute.
 * <p>
 * <b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code sipsa.ingestion.cron.daily} - Daily window cron (default: 0 20 14 * * *)</li>
 *   <li>{@code sipsa.ingestion.cron.monthly-mes} - Day 8 cron (default: 0 0 6 8 * *)</li>
 *   <li>{@code sipsa.ingestion.cron.monthly-abas} - Day 10 cron (default: 0 0 6 10 * *)</li>
 *   <li>{@code sipsa.timezone} - Timezone for scheduling (default: America/Bogota)</li>
 * </ul>
 *
 * @see GenericIngestionJob
 * @see RequestSource
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SipsaIngestionScheduler {

    private final GenericIngestionJob ingestionJob;

    /**
     * Executes the daily ingestion window.
     * <p>
     * Triggered daily at 14:20 (America/Bogota timezone).
     * Runs three methods sequentially:
     * <ol>
     *   <li>Ciudad (city-level pricing)</li>
     *   <li>Parcial (municipality-level market data)</li>
     *   <li>Semana (weekly wholesale market data)</li>
     * </ol>
     * <p>
     * Each method runs with force=false, respecting window validation
     * and duplicate prevention logic.
     */
    @Scheduled(cron = "${sipsa.ingestion.cron.daily:0 20 14 * * *}", zone = "${sipsa.timezone:America/Bogota}")
    public void runDailyWindow() {
        log.info("Triggering Daily Ingestion Window");

        // 1. Ciudad
        runSafely("promediosSipsaCiudad");

        // 2. Parcial
        runSafely("promediosSipsaParcial");

        // 3. Semana
        runSafely("promediosSipsaSemanaMadr");
    }

    /**
     * Executes monthly MesMadr ingestion.
     * <p>
     * Triggered on day 8 of each month at 06:00 (America/Bogota timezone).
     * Processes monthly wholesale market aggregated data.
     */
    @Scheduled(cron = "${sipsa.ingestion.cron.monthly-mes:0 0 6 8 * *}", zone = "${sipsa.timezone:America/Bogota}")
    public void runMonthlyMes() {
        log.info("Triggering Monthly MesMadr");
        runSafely("promediosSipsaMesMadr");
    }

    /**
     * Executes monthly AbasMes ingestion.
     * <p>
     * Triggered on day 10 of each month at 06:00 (America/Bogota timezone).
     * Processes monthly supply data to wholesale markets.
     */
    @Scheduled(cron = "${sipsa.ingestion.cron.monthly-abas:0 0 6 10 * *}", zone = "${sipsa.timezone:America/Bogota}")
    public void runMonthlyAbas() {
        log.info("Triggering Monthly AbasMes");
        runSafely("promedioAbasSipsaMesMadr");
    }

    /**
     * Executes a single ingestion method safely.
     * <p>
     * Wraps the ingestion call in exception handling to ensure that
     * one method's failure doesn't prevent subsequent methods from running.
     * <p>
     * Generates a unique requestId for tracking and uses {@link RequestSource#SCHEDULED}
     * to mark the execution as automatic.
     *
     * @param methodName the SOAP method name to execute
     */
    private void runSafely(String methodName) {
        String requestId = UUID.randomUUID().toString();

        try {
            log.info("Scheduler triggering method={} requestId={} source=SCHEDULED", methodName, requestId);
            IngestionRequest request = IngestionRequest.scheduled(methodName, requestId);
            ingestionJob.execute(request);
        } catch (Exception e) {
            log.error("Scheduler failed to trigger {} requestId={} source=SCHEDULED", methodName, requestId, e);
            // We continue to next task in sequence (handled by caller logic or separate crons)
        }
    }
}
