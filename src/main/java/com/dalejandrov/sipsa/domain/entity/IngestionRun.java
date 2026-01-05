package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity representing an ingestion run execution.
 * <p>
 * This entity tracks the complete lifecycle of a single ingestion execution,
 * from initiation through completion or failure. It stores:
 * <ul>
 *   <li>Run identification and correlation data</li>
 *   <li>Execution timestamps and duration</li>
 *   <li>Processing metrics (records seen, inserted, updated, rejected)</li>
 *   <li>Status and error information</li>
 * </ul>
 * <p>
 * <b>Unique Constraint:</b><br>
 * The combination of {@code (method_name, window_key)} is unique, ensuring
 * only one successful run exists per method per time window. This prevents
 * duplicate data ingestion.
 * <p>
 * <b>Status Values:</b>
 * <ul>
 *   <li>STARTED - Run created and initialized</li>
 *   <li>RUNNING - Actively processing data</li>
 *   <li>SUCCEEDED - Completed successfully</li>
 *   <li>FAILED - Terminated with error</li>
 * </ul>
 * <p>
 * <b>Window Keys:</b>
 * <ul>
 *   <li>Daily methods: {@code YYYY-MM-DD} (e.g., "2026-01-02")</li>
 *   <li>Monthly methods: {@code YYYY-MM-M8} or {@code YYYY-MM-M10}</li>
 * </ul>
 *
 * @see com.dalejandrov.sipsa.application.service.IngestionControlService
 * @see com.dalejandrov.sipsa.application.ingestion.core.IngestionJob
 */
@Entity
@Table(name = "ingestion_runs", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ingestion_runs_window", columnNames = { "method_name", "window_key" })
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "runId")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionRun {

    /** Primary key - auto-generated run identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long runId;

    /** SOAP method name being executed (e.g., "promediosSipsaCiudad") */
    @Column(name = "method_name", nullable = false)
    private String methodName;

    /** Time window key for idempotency (e.g., "2026-01-02" or "2026-01-M8") */
    @Column(name = "window_key", nullable = false)
    private String windowKey;

    /** Correlation ID from the original request (UUID) */
    @Column(name = "request_id", length = 100)
    private String requestId;

    /** Source of the request (MANUAL, SCHEDULED, or SYSTEM) */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_source", length = 20)
    @Builder.Default
    private RequestSource requestSource = RequestSource.MANUAL;

    /** Timestamp when the run started */
    @Column(name = "start_time", nullable = false)
    @Builder.Default
    private Instant startTime = Instant.now();

    /** Timestamp when the run ended (null if still running) */
    @Column(name = "end_time")
    private Instant endTime;

    /** Current status: STARTED, RUNNING, SUCCEEDED, or FAILED */
    @Column(name = "status", nullable = false)
    private String status;

    /** Total number of records encountered during parsing */
    @Column(name = "records_seen")
    @Builder.Default
    private Integer recordsSeen = 0;

    /** Number of new records inserted into database */
    @Column(name = "records_inserted")
    @Builder.Default
    private Integer recordsInserted = 0;

    /** Number of existing records updated */
    @Column(name = "records_updated")
    @Builder.Default
    private Integer recordsUpdated = 0;

    /** Number of records rejected due to validation failures */
    @Column(name = "reject_count")
    @Builder.Default
    private Integer rejectCount = 0;

    /** Last error message if run failed */
    @Column(name = "last_error_message")
    private String lastErrorMessage;

    /** HTTP status code if error was from HTTP/SOAP call */
    @Column(name = "http_status")
    private Integer httpStatus;

    /** SOAP fault code if error was from SOAP service */
    @Column(name = "soap_fault_code")
    private String soapFaultCode;
}
