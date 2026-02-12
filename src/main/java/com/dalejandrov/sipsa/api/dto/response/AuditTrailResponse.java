package com.dalejandrov.sipsa.api.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for audit trail queries.
 * <p>
 * This DTO aggregates all audit events related to a specific request,
 * providing a complete timeline of what happened during request processing.
 * <p>
 * Used by the audit API to return the complete history of an ingestion request,
 * including all events from initial receipt through final completion or failure.
 *
 * @param requestId unique request identifier (UUID)
 * @param eventCount total number of audit events for this request
 * @param firstEvent timestamp of the first event in the trail
 * @param lastEvent timestamp of the last event in the trail
 * @param events ordered list of all audit events for this request
 */
public record AuditTrailResponse(
    String requestId,
    int eventCount,
    OffsetDateTime firstEvent,
    OffsetDateTime lastEvent,
    List<AuditEventResponse> events
) {
    /**
     * Response DTO for individual audit events.
     * <p>
     * Represents a single event that occurred during request processing,
     * such as request received, ingestion started, records processed, etc.
     *
     * @param auditId unique audit event identifier
     * @param runId associated ingestion run ID (null for pre-run events)
     * @param requestSource request source type: MANUAL, SCHEDULED, or SYSTEM
     * @param eventType type of audit event (e.g., REQUEST_RECEIVED, INGESTION_STARTED)
     * @param message descriptive message about what happened
     * @param occurredAt timestamp when the event occurred
     */
    public record AuditEventResponse(
        Long auditId,
        Long runId,
        String requestSource,
        String eventType,
        String message,
        OffsetDateTime occurredAt
    ) {}
}
