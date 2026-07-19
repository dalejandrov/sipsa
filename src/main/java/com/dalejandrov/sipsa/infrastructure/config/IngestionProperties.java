package com.dalejandrov.sipsa.infrastructure.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;

/**
 * Configuration properties for SIPSA data ingestion batching and windows.
 * <p>
 * Binds to {@code sipsa.ingestion.*} properties in application.yaml and is the
 * <b>single source of truth</b> for the ingestion batch size and the monthly
 * ingestion window start. Consumers must obtain these values from this class —
 * never from their own {@code @Value} defaults — so every component sees the
 * same, centrally validated configuration.
 * <p>
 * <b>Example Configuration:</b>
 * <pre>{@code
 * sipsa:
 *   ingestion:
 *     batch-size: ${INGESTION_BATCH_SIZE:500}
 *     monthly-window-start: ${INGESTION_MONTHLY_WINDOW_START:14:00}
 * }</pre>
 * <p>
 * <b>Resolution precedence (batch size):</b>
 * <ol>
 *   <li>{@code INGESTION_BATCH_SIZE} environment variable</li>
 *   <li>{@code sipsa.ingestion.batch-size} property</li>
 *   <li>Canonical default {@value #DEFAULT_BATCH_SIZE}</li>
 * </ol>
 * <p>
 * <b>Canonical default (500):</b> validated with the full DANE dataset
 * (676,210 records, TECH-011 evidence): stable memory, {@code IN} lookups
 * capped at 500 bind parameters, ~1,353 batches per run.
 * <p>
 * <b>Validation:</b> the batch size must be between 1 and
 * {@value #MAX_BATCH_SIZE}. Invalid values (zero, negative, non-numeric or
 * beyond the maximum) abort application startup with a binding/validation
 * error instead of failing mid-ingestion. The upper bound exists because every
 * dedup lookup binds one JDBC parameter per batch element (e.g.
 * {@code findByKeyHashIn}, {@code findByBusinessKeys}) and the PostgreSQL JDBC
 * driver rejects statements with more than 32,767 bind parameters; 10,000
 * leaves ample headroom while also bounding the number of entities held in the
 * persistence context per flush.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.handler.IngestionHandler
 */
@Component
@ConfigurationProperties(prefix = "sipsa.ingestion")
@Validated
@Data
@Slf4j
public class IngestionProperties {

    /**
     * Canonical ingestion batch size, validated against the full DANE dataset
     * (TECH-011). Must match the fallback in {@code application.yaml}
     * ({@code ${INGESTION_BATCH_SIZE:500}}).
     */
    public static final int DEFAULT_BATCH_SIZE = 500;

    /**
     * Preventive upper bound: keeps per-batch {@code IN} lookups far below the
     * PostgreSQL JDBC hard limit of 32,767 bind parameters per statement and
     * bounds persistence-context growth per flush.
     */
    public static final int MAX_BATCH_SIZE = 10_000;

    /**
     * Number of records accumulated before each batch flush to the database.
     * Also the number of bind parameters used by the per-batch dedup lookups.
     */
    @Min(value = 1, message = "sipsa.ingestion.batch-size must be a positive integer (>= 1)")
    @Max(value = MAX_BATCH_SIZE, message = "sipsa.ingestion.batch-size must not exceed " + MAX_BATCH_SIZE
            + " (keeps IN-clause bind parameters far below the PostgreSQL JDBC limit of 32767)")
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * Canonical monthly ingestion window start (America/Bogota, see
     * {@code sipsa.timezone}). 14:00 is the value {@code application.yaml} has
     * always made effective — DANE publishes the monthly datasets around 14:00
     * COT and the monthly crons fire at 14:30 — retained for operational
     * compatibility (TECH-133). It replaces the never-effective {@code 06:00}
     * Java-level fallback that {@code WindowPolicy} used to carry.
     */
    public static final LocalTime DEFAULT_MONTHLY_WINDOW_START = LocalTime.of(14, 0);

    /**
     * Earliest time of day (HH:mm, in the zone configured by
     * {@code sipsa.timezone}) at which {@code WindowPolicy} authorizes a
     * monthly ingestion run on its publication or grace day. This is an
     * authorization gate consulted when a run is attempted — it is NOT the
     * time the scheduler fires (the monthly crons fire at 14:30).
     * <p>
     * Bound from {@code sipsa.ingestion.monthly-window-start}
     * (env: {@code INGESTION_MONTHLY_WINDOW_START}). Non-parseable values
     * (e.g. {@code 24:00}, {@code 14:60}, free text) fail the binding and
     * abort startup; an explicitly empty value is rejected by {@code @NotNull}.
     */
    @NotNull(message = "sipsa.ingestion.monthly-window-start must be a valid HH:mm time (e.g. 14:00)")
    private LocalTime monthlyWindowStart = DEFAULT_MONTHLY_WINDOW_START;

    /**
     * Canonical maximum reject rate (1%), the value {@code application.yaml} has
     * always made effective ({@code ${MAX_REJECT_RATE:0.01}}). Expressed as a
     * fraction of {@code recordsSeen} in {@code [0..1]} — NOT a percentage.
     */
    public static final double DEFAULT_MAX_REJECT_RATE = 0.01;

    /**
     * Canonical maximum absolute reject count, the value {@code application.yaml}
     * has always made effective ({@code ${MAX_REJECT_COUNT:5000}}).
     */
    public static final int DEFAULT_MAX_REJECT_COUNT = 5_000;

    /**
     * Quality gate (TECH-135, C-04): maximum fraction of rejected records
     * tolerated per run, in {@code [0..1]} (e.g. {@code 0.01} = 1%). Evaluated
     * ONCE at the end of ingestion by {@code IngestionJob.validateThresholds} —
     * over the run's final totals, never per batch — and only when
     * {@code recordsSeen > 0}; a run whose rate strictly exceeds this value is
     * marked FAILED. Combined with {@link #maxRejectCount} by OR: exceeding
     * either threshold fails the run.
     * <p>
     * Bound from {@code sipsa.ingestion.max-reject-rate}
     * (env: {@code MAX_REJECT_RATE}).
     */
    @DecimalMin(value = "0.0", message = "sipsa.ingestion.max-reject-rate must be >= 0 (fraction of seen records)")
    @DecimalMax(value = "1.0", message = "sipsa.ingestion.max-reject-rate must be <= 1"
            + " (it is a fraction in [0..1], not a percentage — 0.01 means 1%)")
    private double maxRejectRate = DEFAULT_MAX_REJECT_RATE;

    /**
     * Quality gate (TECH-135, C-04): maximum absolute number of rejected records
     * tolerated per run. Evaluated at the end of ingestion together with
     * {@link #maxRejectRate} (OR semantics); a run whose reject count strictly
     * exceeds this value is marked FAILED. This is the only gate that applies
     * when {@code recordsSeen == 0}.
     * <p>
     * Bound from {@code sipsa.ingestion.max-reject-count}
     * (env: {@code MAX_REJECT_COUNT}).
     */
    @Min(value = 0, message = "sipsa.ingestion.max-reject-count must be >= 0")
    private int maxRejectCount = DEFAULT_MAX_REJECT_COUNT;

    /**
     * Logs the effective values once at startup so operators can confirm the
     * resolved configuration without per-batch or per-evaluation log noise.
     */
    @PostConstruct
    void logEffectiveConfiguration() {
        log.info("Ingestion batch size = {}", batchSize);
        log.info("Monthly ingestion window start = {}", monthlyWindowStart);
        log.info("Ingestion reject thresholds: max rate = {}, max count = {}", maxRejectRate, maxRejectCount);
    }
}
