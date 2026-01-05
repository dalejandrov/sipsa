package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.dto.AuditTrailDto;
import com.dalejandrov.sipsa.api.util.TimezoneUtil;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.domain.entity.IngestionAudit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for querying ingestion audit trail.
 * <p>
 * This controller provides endpoints to track the complete lifecycle of ingestion requests
 * by their request ID or run ID. It allows operators and developers to:
 * <ul>
 *   <li>View the complete history of a specific request</li>
 *   <li>Retrieve all events associated with an ingestion run</li>
 *   <li>Monitor recent audit activity across all requests</li>
 * </ul>
 * <p>
 * All endpoints are under {@code /internal/audit} and should be protected
 * in production environments (e.g., Spring Security, IP allowlist).
 * <p>
 * The audit trail includes events such as:
 * <ul>
 *   <li>Request received and accepted</li>
 *   <li>Ingestion process lifecycle (started, running, completed, failed)</li>
 *   <li>Data processing metrics and rejected records</li>
 *   <li>Error details and system events</li>
 * </ul>
 *
 * @see IngestionAuditService
 * @see AuditTrailDto
 */
@RestController
@RequestMapping("/internal/audit")
@Slf4j
@RequiredArgsConstructor
public class IngestionAuditController {

    private final IngestionAuditService auditService;

    /**
     * Retrieves the complete audit trail for a specific request ID.
     * <p>
     * Returns all audit events that occurred during the processing of the request,
     * ordered chronologically from first to last event. This provides a complete
     * timeline of what happened during the request lifecycle.
     * <p>
     * The response includes:
     * <ul>
     *   <li>Request correlation ID</li>
     *   <li>Total event count</li>
     *   <li>First and last event timestamps</li>
     *   <li>Complete list of events with details</li>
     * </ul>
     * <p>
     * <b>Example usage:</b>
     * <pre>
     * GET /internal/audit/request/abc123-def456-789
     * </pre>
     *
     * @param requestId the unique request identifier (UUID) to query
     * @return HTTP 200 with audit trail if found, HTTP 404 if no events exist for this request
     */
    @GetMapping("/request/{requestId}")
    public ResponseEntity<AuditTrailDto> getAuditTrail(@PathVariable String requestId) {
        log.debug("Querying audit trail for requestId={}", requestId);

        List<IngestionAudit> audits = auditService.getAuditTrail(requestId);

        if (audits.isEmpty()) {
            log.debug("No audit trail found for requestId={}", requestId);
            return ResponseEntity.notFound().build();
        }

        AuditTrailDto trail = AuditTrailDto.builder()
                .requestId(requestId)
                .eventCount(audits.size())
                .firstEvent(TimezoneUtil.convertToOffsetDateTime(audits.getFirst().getOccurredAt(), true))
                .lastEvent(TimezoneUtil.convertToOffsetDateTime(audits.getLast().getOccurredAt(), true))
                .events(audits.stream()
                        .map(this::mapToAuditEventDto)
                        .collect(Collectors.toList()))
                .build();

        log.debug("Found {} audit events for requestId={}", trail.getEventCount(), requestId);
        return ResponseEntity.ok(trail);
    }

    /**
     * Retrieves all audit events for a specific ingestion run ID.
     * <p>
     * This endpoint is useful when you know the run ID and want to see all
     * events that occurred during that specific ingestion execution.
     * The events are ordered chronologically.
     * <p>
     * Each run can have multiple events tracking its progress through
     * various stages (started, running, processing records, completed/failed).
     * <p>
     * <b>Example usage:</b>
     * <pre>
     * GET /internal/audit/run/12345
     * </pre>
     *
     * @param runId the ingestion run identifier from the database
     * @return HTTP 200 with list of audit events if found, HTTP 404 if no events exist for this run
     */
    @GetMapping("/run/{runId}")
    public ResponseEntity<List<AuditTrailDto.AuditEventDto>> getAuditTrailByRunId(@PathVariable Long runId) {
        log.debug("Querying audit trail for runId={}", runId);

        List<IngestionAudit> audits = auditService.getAuditTrailByRunId(runId);

        if (audits.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<AuditTrailDto.AuditEventDto> events = audits.stream()
                .map(this::mapToAuditEventDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    /**
     * Retrieves the most recent audit events across all requests.
     * <p>
     * This endpoint returns the last 100 audit events from the system,
     * ordered by occurrence time (most recent first). It's useful for:
     * <ul>
     *   <li>Monitoring system activity in real-time</li>
     *   <li>Quickly identifying recent errors or issues</li>
     *   <li>Getting an overview of what's happening in the system</li>
     *   <li>Debugging without knowing specific request IDs</li>
     * </ul>
     * <p>
     * Events from all sources (MANUAL, SCHEDULED, SYSTEM) are included
     * and can be filtered client-side by the {@code requestSource} field.
     * <p>
     * <b>Example usage:</b>
     * <pre>
     * GET /internal/audit/recent
     * </pre>
     *
     * @return HTTP 200 with list of recent audit events (maximum 100 events)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<AuditTrailDto.AuditEventDto>> getRecentEvents() {
        log.info("Querying recent audit events");

        List<IngestionAudit> audits = auditService.getRecentEvents();

        List<AuditTrailDto.AuditEventDto> events = audits.stream()
                .map(this::mapToAuditEventDto)
                .collect(Collectors.toList());

        log.debug("Found {} recent audit events", events.size());
        return ResponseEntity.ok(events);
    }

    /**
     * Maps an IngestionAudit entity to an AuditEventDto.
     * <p>
     * This method centralizes the mapping logic to avoid code duplication
     * across multiple controller methods.
     *
     * @param audit the IngestionAudit entity to map
     * @return the corresponding AuditEventDto
     */
    private AuditTrailDto.AuditEventDto mapToAuditEventDto(IngestionAudit audit) {
        return AuditTrailDto.AuditEventDto.builder()
                .auditId(audit.getAuditId())
                .runId(audit.getRunId())
                .requestSource(audit.getRequestSource() != null ? audit.getRequestSource().name() : null)
                .eventType(audit.getEventType())
                .message(audit.getMessage())
                .occurredAt(TimezoneUtil.convertToOffsetDateTime(audit.getOccurredAt(), true))
                .build();
    }
}
