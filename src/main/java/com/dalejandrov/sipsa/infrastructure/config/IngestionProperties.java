package com.dalejandrov.sipsa.infrastructure.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for SIPSA data ingestion batching.
 * <p>
 * Binds to {@code sipsa.ingestion.*} properties in application.yaml and is the
 * <b>single source of truth</b> for the ingestion batch size. All ingestion
 * handlers must obtain the batch size from this class — never from their own
 * {@code @Value} defaults — so every handler processes batches of the same,
 * centrally validated size.
 * <p>
 * <b>Example Configuration:</b>
 * <pre>{@code
 * sipsa:
 *   ingestion:
 *     batch-size: ${INGESTION_BATCH_SIZE:500}
 * }</pre>
 * <p>
 * <b>Resolution precedence:</b>
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
     * Logs the effective batch size once at startup so operators can confirm
     * the resolved configuration without per-batch log noise.
     */
    @PostConstruct
    void logEffectiveConfiguration() {
        log.info("Ingestion batch size = {}", batchSize);
    }
}
