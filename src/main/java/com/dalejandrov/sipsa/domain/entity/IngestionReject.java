package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for storing rejected records during ingestion.
 * <p>
 * This entity captures records that failed validation or parsing during
 * the ingestion process. It preserves:
 * <ul>
 *   <li>The raw data that was rejected</li>
 *   <li>The reason for rejection</li>
 *   <li>Whether it was a parse error vs validation error</li>
 *   <li>When the rejection occurred</li>
 * </ul>
 * <p>
 * <b>Purpose:</b>
 * <ul>
 *   <li>Data quality analysis - identify patterns in bad data</li>
 *   <li>Debugging - understand why records were excluded</li>
 *   <li>Reprocessing - recover data after fixing issues</li>
 *   <li>Auditing - prove that validation rules were applied</li>
 * </ul>
 * <p>
 * <b>Storage Strategy:</b><br>
 * Rejected records are accumulated in memory during processing and
 * batch-inserted at the end of the run to minimize transaction overhead.
 * This prevents rejected records from impacting ingestion performance.
 * <p>
 * <b>Typical Rejection Reasons:</b>
 * <ul>
 *   <li>Missing required fields (null values)</li>
 *   <li>Invalid data formats</li>
 *   <li>Constraint violations</li>
 *   <li>Malformed XML/data structures</li>
 * </ul>
 *
 * @see com.dalejandrov.sipsa.application.service.IngestionControlService#logReject
 * @see com.dalejandrov.sipsa.application.ingestion.core.IngestionContext#addRejectedRecord
 */
@Entity
@Table(name = "ingestion_rejects")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "rejectId")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionReject {

    /** Primary key - auto-generated reject record identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reject_id")
    private Long rejectId;

    /** Associated run ID that encountered this rejection */
    @Column(name = "run_id")
    private Long runId;

    /** Raw data of the rejected record (XML fragment, JSON, or formatted string) */
    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    /** Human-readable explanation of why the record was rejected */
    @Column(name = "reason")
    private String reason;

    /** True if rejection was due to parsing failure, false if validation failure */
    @Column(name = "is_parse_error")
    @Builder.Default
    private Boolean isParseError = false;

    /** Timestamp when the rejection occurred */
    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
