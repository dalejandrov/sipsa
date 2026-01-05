package com.dalejandrov.sipsa.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Data Transfer Object for audit trail responses.
 * <p>
 * This DTO aggregates all audit events related to a specific request,
 * providing a complete timeline of what happened during request processing.
 * <p>
 * Used by the audit API to return the complete history of an ingestion request,
 * including all events from initial receipt through final completion or failure.
 */
@Data
@Builder
public class AuditTrailDto {
    /**
     * Unique request identifier (UUID)
     */
    private String requestId;

    /**
     * Total number of audit events for this request
     */
    private int eventCount;

    /**
     * Timestamp of the first event in the trail
     */
    private OffsetDateTime firstEvent;

    /**
     * Timestamp of the last event in the trail
     */
    private OffsetDateTime lastEvent;

    /**
     * Ordered list of all audit events for this request
     */
    private List<AuditEventDto> events;

    /**
     * Data Transfer Object for individual audit events.
     * <p>
     * Represents a single event that occurred during request processing,
     * such as request received, ingestion started, records processed, etc.
     */
    @Data
    @Builder
    public static class AuditEventDto {
        /**
         * Unique audit event identifier
         */
        private Long auditId;

        /**
         * Associated ingestion run ID (null for pre-run events)
         */
        private Long runId;

        /**
         * Request source type: MANUAL, SCHEDULED, or SYSTEM
         */
        private String requestSource;

        /**
         * Type of audit event (e.g., REQUEST_RECEIVED, INGESTION_STARTED)
         */
        private String eventType;

        /**
         * Descriptive message about what happened
         */
        private String message;

        /**
         * Timestamp when the event occurred
         */
        private OffsetDateTime occurredAt;
    }
}
