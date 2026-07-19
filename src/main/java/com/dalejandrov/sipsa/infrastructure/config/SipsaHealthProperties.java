package com.dalejandrov.sipsa.infrastructure.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed configuration for {@link com.dalejandrov.sipsa.infrastructure.observability.SipsaHealthIndicator}'s
 * data-staleness thresholds (TECH-031) — the single source of truth for how old the last
 * successful run of a monitored SOAP method may be before {@code /actuator/health}
 * reports the system {@code DOWN}.
 * <p>
 * Binds {@code sipsa.health.*}:
 * <pre>{@code
 * sipsa:
 *   health:
 *     daily-staleness-threshold: ${SIPSA_HEALTH_DAILY_STALENESS_THRESHOLD:36h}
 *     monthly-staleness-threshold: ${SIPSA_HEALTH_MONTHLY_STALENESS_THRESHOLD:840h}
 * }</pre>
 * Resolution precedence: environment variable → Spring property → typed canonical
 * default.
 * <p>
 * <b>Canonical defaults</b> preserve the indicator's previous hardcoded behavior exactly:
 * <ul>
 *   <li>{@code 36h} for methods in {@code SipsaHealthIndicator.DAILY_METHODS} — the daily
 *       ingestion window's own contractual 24h cadence plus a 12h buffer;</li>
 *   <li>{@code 840h} (= 35 × 24h) for every other monitored method (the monthly ones) —
 *       expressed in hours, not days, because that is the exact unit the indicator
 *       compares against ({@code Duration.between(lastSuccess, now).toHours()}); {@code
 *       35d} would bind to the same elapsed duration, but {@code 840h} states the
 *       comparison unit literally, with no rounding or calendar-day assumption involved.</li>
 * </ul>
 * <p>
 * A threshold of zero is rejected rather than merely discouraged: it would make the
 * indicator report every method {@code STALE} immediately after its own successful run
 * (any nonzero age already exceeds {@code 0}), which is never a meaningful staleness
 * signal.
 */
@Component
@ConfigurationProperties(prefix = "sipsa.health")
@Validated
@Data
@Slf4j
public class SipsaHealthProperties {

    /** Canonical thresholds — the values {@code SipsaHealthIndicator} always hardcoded. */
    public static final Duration DEFAULT_DAILY_STALENESS_THRESHOLD = Duration.ofHours(36);
    public static final Duration DEFAULT_MONTHLY_STALENESS_THRESHOLD = Duration.ofHours(35 * 24);

    /**
     * Maximum age of the last successful run for a "daily" monitored method
     * (see {@code SipsaHealthIndicator.DAILY_METHODS}) before it is considered stale.
     */
    @NotNull(message = "sipsa.health.daily-staleness-threshold must not be null")
    private Duration dailyStalenessThreshold = DEFAULT_DAILY_STALENESS_THRESHOLD;

    /**
     * Maximum age of the last successful run for a monthly monitored method before it is
     * considered stale.
     */
    @NotNull(message = "sipsa.health.monthly-staleness-threshold must not be null")
    private Duration monthlyStalenessThreshold = DEFAULT_MONTHLY_STALENESS_THRESHOLD;

    @AssertTrue(message = "sipsa.health.daily-staleness-threshold must be positive")
    public boolean isDailyStalenessThresholdPositive() {
        return dailyStalenessThreshold == null || !dailyStalenessThreshold.isNegative() && !dailyStalenessThreshold.isZero();
    }

    @AssertTrue(message = "sipsa.health.monthly-staleness-threshold must be positive")
    public boolean isMonthlyStalenessThresholdPositive() {
        return monthlyStalenessThreshold == null || !monthlyStalenessThreshold.isNegative() && !monthlyStalenessThreshold.isZero();
    }

    /** One startup log line so operators can confirm the resolved staleness thresholds. */
    @PostConstruct
    void logEffectiveConfiguration() {
        log.info("SIPSA health staleness thresholds: daily = {}, monthly = {}",
                dailyStalenessThreshold, monthlyStalenessThreshold);
    }
}
