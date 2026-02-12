package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.request.AuditQueryRequest;
import com.dalejandrov.sipsa.api.dto.response.AuditTrailResponse;
import com.dalejandrov.sipsa.api.mapper.IngestionAuditMapper;
import com.dalejandrov.sipsa.api.util.TimezoneUtil;
import com.dalejandrov.sipsa.domain.entity.IngestionAudit;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Application service for audit trail queries and aggregation.
 * <p>
 * <b>Service Responsibilities:</b>
 * <ul>
 *   <li>Aggregate audit events into meaningful responses</li>
 *   <li>Convert entities to response DTOs</li>
 *   <li>Handle pagination logic</li>
 *   <li>Apply timezone conversions</li>
 *   <li>Throw business exceptions when data not found</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditTrailService {

    private final IngestionAuditService auditService;
    private final IngestionAuditMapper mapper;

    /**
     * Builds a complete audit trail response for a specific request.
     *
     * @param requestId the unique request identifier
     * @return audit trail response
     * @throws SipsaBusinessException if no events found for the request
     */
    @Transactional(readOnly = true)
    public AuditTrailResponse getAuditTrailByRequestId(String requestId) {
        log.debug("Building audit trail for requestId={}", requestId);

        List<IngestionAudit> audits = auditService.getAuditTrail(requestId);

        if (audits.isEmpty()) {
            log.warn("No audit trail found for requestId={}", requestId);
            throw new SipsaBusinessException("No audit events found for request: " + requestId);
        }

        AuditTrailResponse trail = new AuditTrailResponse(
                requestId,
                audits.size(),
                TimezoneUtil.convertToOffsetDateTime(audits.getFirst().getOccurredAt(), true),
                TimezoneUtil.convertToOffsetDateTime(audits.getLast().getOccurredAt(), true),
                audits.stream()
                        .map(mapper::toAuditEventRequest)
                        .collect(Collectors.toList())
        );

        log.debug("Built audit trail with {} events for requestId={}", trail.eventCount(), requestId);
        return trail;
    }

    /**
     * Retrieves all audit events for a specific ingestion run.
     *
     * @param runId the ingestion run identifier
     * @return list of audit event responses
     * @throws SipsaBusinessException if no events found for the run
     */
    @Transactional(readOnly = true)
    public List<AuditTrailResponse.AuditEventResponse> getAuditEventsByRunId(Long runId) {
        log.debug("Retrieving audit events for runId={}", runId);

        List<IngestionAudit> audits = auditService.getAuditTrailByRunId(runId);

        if (audits.isEmpty()) {
            log.warn("No audit events found for runId={}", runId);
            throw new SipsaBusinessException("No audit events found for run: " + runId);
        }

        return audits.stream()
                .map(mapper::toAuditEventRequest)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the most recent audit events across all requests.
     *
     * @return list of recent audit event responses (max 100)
     */
    @Transactional(readOnly = true)
    public List<AuditTrailResponse.AuditEventResponse> getRecentEvents() {
        log.debug("Retrieving recent audit events");

        List<IngestionAudit> audits = auditService.getRecentEvents();

        List<AuditTrailResponse.AuditEventResponse> events = audits.stream()
                .map(mapper::toAuditEventRequest)
                .collect(Collectors.toList());

        log.debug("Retrieved {} recent audit events", events.size());
        return events;
    }

    /**
     * Queries audit events with filters and pagination.
     *
     * @param request the audit query request with filters and pagination
     * @return page of audit event responses
     */
    @Transactional(readOnly = true)
    public Page<AuditTrailResponse.AuditEventResponse> queryAuditEvents(AuditQueryRequest request) {
        log.debug("Querying audit events with filters - requestId: {}, fecha: {}, startDate: {}, endDate: {}",
                request.requestId(), request.fecha(), request.startDate(), request.endDate());

        Pageable pageable = PageRequest.of(
                request.page() - 1,
                request.size(),
                Sort.by(request.sort().split(","))
        );

        Page<IngestionAudit> auditPage = auditService.getAudits(request, pageable);

        return auditPage.map(mapper::toAuditEventRequest);
    }
}

