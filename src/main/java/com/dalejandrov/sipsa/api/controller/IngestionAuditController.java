package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.dto.response.AuditTrailResponse;
import com.dalejandrov.sipsa.api.dto.request.AuditQueryRequest;
import com.dalejandrov.sipsa.api.dto.response.ApiResponse;
import com.dalejandrov.sipsa.api.util.PaginationUtils;
import com.dalejandrov.sipsa.application.service.AuditTrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for querying ingestion audit trail.
 * <p>
 * <b>Controller Responsibilities (HTTP Layer):</b>
 * <ul>
 *   <li>Handle HTTP requests and responses</li>
 *   <li>Map path/query parameters to DTOs</li>
 *   <li>Delegate business logic to service layer</li>
 *   <li>Return appropriate HTTP status codes (200, 404)</li>
 * </ul>
 * <p>
 * <b>NOT Responsible For:</b>
 * <p>
 * All business logic is delegated to {@link AuditTrailService}.
 *
 * @see AuditTrailService
 */
@RestController
@RequestMapping("/api/internal/audit")
@Slf4j
@RequiredArgsConstructor
public class IngestionAuditController {

    private final AuditTrailService auditTrailService;

    /**
     * Retrieves the complete audit trail for a specific request ID.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     * Service throws exception if not found (handled by GlobalExceptionHandler).
     *
     * @param requestId the unique request identifier (UUID) to query
     * @return HTTP 200 with audit trail
     */
    @GetMapping("/request/{requestId}")
    public ResponseEntity<AuditTrailResponse> getAuditTrail(@PathVariable String requestId) {
        AuditTrailResponse trail = auditTrailService.getAuditTrailByRequestId(requestId);
        return ResponseEntity.ok(trail);
    }

    /**
     * Retrieves all audit events for a specific ingestion run ID.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     * Service throws exception if not found (handled by GlobalExceptionHandler).
     *
     * @param runId the ingestion run identifier from the database
     * @return HTTP 200 with list of audit events
     */
    @GetMapping("/run/{runId}")
    public ResponseEntity<List<AuditTrailResponse.AuditEventResponse>> getAuditTrailByRunId(@PathVariable Long runId) {
        List<AuditTrailResponse.AuditEventResponse> events = auditTrailService.getAuditEventsByRunId(runId);
        return ResponseEntity.ok(events);
    }

    /**
     * Retrieves the most recent audit events across all requests.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP response.
     *
     * @return HTTP 200 with list of recent audit events (maximum 100 events)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<AuditTrailResponse.AuditEventResponse>> getRecentEvents() {
        List<AuditTrailResponse.AuditEventResponse> events = auditTrailService.getRecentEvents();
        return ResponseEntity.ok(events);
    }

    /**
     * Retrieves a paginated list of audit events based on optional filters.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP response.
     *
     * @param request the query parameters encapsulated in a DTO
     * @return HTTP 200 with paginated list of audit events matching the criteria
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<AuditTrailResponse.AuditEventResponse>> getAllAuditEvents(
            AuditQueryRequest request) {
        Page<AuditTrailResponse.AuditEventResponse> eventPage = auditTrailService.queryAuditEvents(request);
        return ResponseEntity.ok(PaginationUtils.toApiResponse(eventPage));
    }
}
