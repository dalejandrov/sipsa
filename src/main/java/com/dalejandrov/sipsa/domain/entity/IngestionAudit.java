package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for tracking audit events during ingestion processes.
 * <p>
 * This entity maintains a detailed, chronological log of all events that occur
 * during an ingestion request's lifecycle. It enables:
 * <ul>
 *   <li>Complete request traceability from initiation to completion</li>
 *   <li>Debugging of failed or problematic ingestions</li>
 *   <li>Performance analysis and monitoring</li>
 *   <li>Compliance auditing and operational transparency</li>
 * </ul>
 * <p>
 * <b>Event Persistence:</b><br>
 * Audit events are persisted in separate transactions ({@code REQUIRES_NEW})
 * to ensure they're saved even if the main ingestion transaction fails.
 * This guarantees a complete audit trail for all operations.
 * <p>
 * <b>Indexing:</b><br>
 * The table has indexes on:
 * <ul>
 *   <li>{@code request_id} - For querying all events of a request</li>
 *   <li>{@code run_id} - For querying all events of a specific run</li>
 *   <li>{@code request_source} - For filtering by source type</li>
 *   <li>{@code occurred_at} - For time-based queries and ordering</li>
 * </ul>
 *
 * @see AuditEventType
 * @see RequestSource
 * @see com.dalejandrov.sipsa.application.service.IngestionAuditService
 */
@Entity
@Table(name = "ingestion_audit")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "auditId")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionAudit {

    /** Primary key - auto-generated audit event identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    /** Associated run ID (null for pre-run events like REQUEST_RECEIVED) */
    @Column(name = "run_id")
    private Long runId;

    /** Correlation ID from the original request (UUID) */
    @Column(name = "request_id", length = 100)
    private String requestId;

    /** Source of the request (MANUAL, SCHEDULED, or SYSTEM) */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_source", length = 20)
    @Builder.Default
    private RequestSource requestSource = RequestSource.MANUAL;

    /** Type of event (from AuditEventType enum, stored as string) */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /** Descriptive message with event details and context */
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    /** Timestamp when the event occurred */
    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();
}
