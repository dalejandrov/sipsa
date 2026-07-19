package com.dalejandrov.sipsa.infrastructure.observability;

import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.infrastructure.config.SipsaHealthProperties;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
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
 *   <li><b>Daily methods:</b> Data older than {@code sipsa.health.daily-staleness-threshold}
 *       (default 36 hours: 24h + 12h buffer) is considered stale</li>
 *   <li><b>Monthly methods:</b> Data older than {@code sipsa.health.monthly-staleness-threshold}
 *       (default 840 hours = 35 days) is considered stale</li>
 * </ul>
 * <p>
 * Thresholds are externalized and validated in {@link SipsaHealthProperties} (TECH-031) —
 * the defaults above preserve the indicator's original hardcoded behavior exactly.
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
    private final SipsaHealthProperties healthProperties;

    /**
     * Clock used to determine "now" when computing data age.
     * <p>
     * Defaults to a UTC system clock, behaviorally identical to the previous
     * {@code Instant.now()} call ({@code Instant} carries no zone, so
     * {@code Clock.systemUTC().instant()} and {@code Instant.now()} return the same
     * value). Package-private mutability exists solely so tests in this package can
     * substitute a {@link Clock#fixed} instance for deterministic staleness-boundary
     * testing without depending on wall-clock time. Production code never calls
     * {@link #setClock}.
     */
    private Clock clock = Clock.systemUTC();

    /**
     * Performs health check by analyzing data freshness.
     * <p>
     * This method:
     * <ol>
     *   <li>Retrieves last successful run for each method</li>
     *   <li>Calculates age of data in hours</li>
     *   <li>Compares against the configured staleness thresholds
     *       ({@link SipsaHealthProperties})</li>
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
        List<Object[]> lastRuns = runRepository.findLastRunPerMethodByStatus(IngestionRunStatus.SUCCEEDED);

        Map<String, Object> details = new HashMap<>();
        boolean isUp = true;
        Instant now = Instant.now(clock);
        long dailyThresholdHours = healthProperties.getDailyStalenessThreshold().toHours();
        long monthlyThresholdHours = healthProperties.getMonthlyStalenessThreshold().toHours();

        for (Object[] row : lastRuns) {
            String method = (String) row[0];
            Instant lastSuccess = (Instant) row[1];

            long ageHours = Duration.between(lastSuccess, now).toHours();
            details.put(method, "Age: " + ageHours + "h");

            long thresholdHours = DAILY_METHODS.contains(method) ? dailyThresholdHours : monthlyThresholdHours;
            if (ageHours > thresholdHours) {
                isUp = false;
                details.put(method + "_status", "STALE");
            }
        }

        if (lastRuns.isEmpty()) {
            return Health.unknown().withDetail("message", "No successful runs found yet").build();
        }

        return isUp ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
    }

    /**
     * Test-only seam: replaces the clock used for "now" calculations.
     * <p>
     * Package-private by design — only test classes in
     * {@code com.dalejandrov.sipsa.infrastructure.observability} may call this. Not part
     * of the public API and not used by any production code path.
     *
     * @param clock a fixed or otherwise controlled clock for deterministic testing
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
