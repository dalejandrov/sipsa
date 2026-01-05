package com.dalejandrov.sipsa.infrastructure.observability;

import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring Boot Actuator health indicator for SIPSA ingestion monitoring.
 * <p>
 * This component provides health status based on the freshness of ingested data.
 * It monitors the last successful run for each ingestion method and reports
 * the system as unhealthy if data becomes stale.
 * <p>
 * <b>Health Criteria:</b>
 * <ul>
 *   <li><b>Daily methods:</b> Data older than 36 hours (24h + 12h buffer) is considered stale</li>
 *   <li><b>Monthly methods:</b> Data older than 35 days is considered stale</li>
 * </ul>
 * <p>
 * <b>Daily Methods Monitored:</b>
 * <ul>
 *   <li>promediosSipsaCiudad (City pricing)</li>
 *   <li>promediosSipsaParcial (Municipal partial data)</li>
 *   <li>promediosSipsaSemanaMadr (Weekly wholesale)</li>
 * </ul>
 * <p>
 * <b>Access Health Endpoint:</b>
 * <pre>
 * GET /actuator/health
 * GET /actuator/health/sipsa
 * </pre>
 * <p>
 * <b>Example Response:</b>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "details": {
 *     "promediosSipsaCiudad": "Age: 12h",
 *     "promediosSipsaParcial": "Age: 15h",
 *     "promediosSipsaSemanaMadr": "Age: 18h"
 *   }
 * }
 * }</pre>
 *
 * @see org.springframework.boot.actuate.health.HealthIndicator
 * @see IngestionRunRepository
 */
@Component
@RequiredArgsConstructor
public class SipsaHealthIndicator implements HealthIndicator {

    /** Daily methods that should run every day */
    private static final Set<String> DAILY_METHODS = Set.of(
            "promediosSipsaCiudad",
            "promediosSipsaParcial",
            "promediosSipsaSemanaMadr"
    );

    private final IngestionRunRepository runRepository;

    /**
     * Performs health check by analyzing data freshness.
     * <p>
     * This method:
     * <ol>
     *   <li>Retrieves last successful run for each method</li>
     *   <li>Calculates age of data in hours</li>
     *   <li>Compares against thresholds (36h for daily, 35 days for monthly)</li>
     *   <li>Returns UP if all data is fresh, DOWN if any is stale</li>
     * </ol>
     * <p>
     * The health check provides visibility into:
     * <ul>
     *   <li>Whether scheduled jobs are running correctly</li>
     *   <li>If there are ingestion failures</li>
     *   <li>Data staleness for each method</li>
     * </ul>
     *
     * @return Health status with details about each ingestion method
     */
    @Override
    public Health health() {
        List<Object[]> lastRuns = runRepository.findLastSuccessPerMethod();

        Map<String, Object> details = new HashMap<>();
        boolean isUp = true;
        Instant now = Instant.now();

        for (Object[] row : lastRuns) {
            String method = (String) row[0];
            Instant lastSuccess = (Instant) row[1];

            long ageHours = Duration.between(lastSuccess, now).toHours();
            details.put(method, "Age: " + ageHours + "h");

            if (DAILY_METHODS.contains(method)) {
                // 24h + 12h buffer
                if (ageHours > 36) {
                    isUp = false;
                    details.put(method + "_status", "STALE");
                }
            } else {
                // 35 days
                if (ageHours > (35 * 24)) {
                    isUp = false;
                    details.put(method + "_status", "STALE");
                }
            }
        }

        if (lastRuns.isEmpty()) {
            return Health.unknown().withDetail("message", "No successful runs found yet").build();
        }

        return isUp ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }
}
