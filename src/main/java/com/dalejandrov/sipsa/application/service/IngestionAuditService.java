package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.AuditEventRequest;
import com.dalejandrov.sipsa.domain.entity.AuditEventType;
import com.dalejandrov.sipsa.domain.entity.IngestionAudit;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service for managing ingestion audit trail.
 * <p>
 * This service is responsible for recording and retrieving audit events
 * that occur throughout the ingestion lifecycle. It provides:
 * <ul>
 *   <li>Asynchronous event logging (non-blocking)</li>
 *   <li>Synchronous event logging (for critical events)</li>
 *   <li>Query capabilities by request ID or run ID</li>
 * </ul>
 * <p>
 * All audit events are persisted in a separate transaction ({@code REQUIRES_NEW})
 * to ensure they're saved even if the main ingestion transaction fails.
 * This guarantees a complete audit trail even for failed operations.
 * <p>
 * The audit trail is essential for:
 * <ul>
 *   <li>Debugging ingestion failures</li>
 *   <li>Tracking request lifecycle from start to finish</li>
 *   <li>Compliance and operational transparency</li>
 *   <li>Performance analysis and monitoring</li>
 * </ul>
 *
 * @see IngestionAudit
 * @see AuditEventType
 * @see RequestSource
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionAuditService {

    private final IngestionAuditRepository auditRepository;

    /**
     * Log an audit event asynchronously using DTO.
     * <p>
     * This method runs in a separate transaction to ensure audit logs
     * are persisted even if the main transaction fails.
     *
     * @param request encapsulates all audit event parameters
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(AuditEventRequest request) {
        try {
            IngestionAudit audit = IngestionAudit.builder()
                    .requestId(request.requestId())
                    .runId(request.runId())
                    .requestSource(request.requestSource())
                    .eventType(request.eventType().name())
                    .message(request.message())
                    .occurredAt(Instant.now())
                    .build();

            auditRepository.save(audit);

            log.debug("Audit event logged: requestId={} source={} eventType={} message={}",
                    request.requestId(), request.requestSource(), request.eventType(), request.message());
        } catch (Exception e) {
            // Don't fail the main process if audit logging fails
            log.error("Failed to log audit event: requestId={} eventType={}",
                    request.requestId(), request.eventType(), e);
        }
    }

    /**
     * Logs an audit event synchronously using DTO (blocking).
     * <p>
     * Unlike {@link #logEvent}, this method blocks until the event is persisted
     * to the database. Use this for critical events that must be recorded
     * immediately.
     *
     * @param request encapsulates all audit event parameters
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEventSync(AuditEventRequest request) {
        try {
            IngestionAudit audit = IngestionAudit.builder()
                    .requestId(request.requestId())
                    .runId(request.runId())
                    .requestSource(request.requestSource())
                    .eventType(request.eventType().name())
                    .message(request.message())
                    .occurredAt(Instant.now())
                    .build();

            auditRepository.save(audit);

            log.debug("Audit event logged (sync): requestId={} source={} eventType={} message={}",
                    request.requestId(), request.requestSource(), request.eventType(), request.message());
        } catch (Exception e) {
            log.error("Failed to log audit event synchronously: requestId={} eventType={}",
                    request.requestId(), request.eventType(), e);
        }
    }

    /**
     * Retrieves all audit events for a specific request ID.
     * <p>
     * Returns events ordered chronologically (oldest first), allowing
     * reconstruction of the complete request lifecycle.
     * <p>
     * This is the primary method for debugging and tracking individual requests.
     *
     * @param requestId the unique request identifier (UUID)
     * @return list of audit events, empty if no events found
     */
    @Transactional(readOnly = true)
    public List<IngestionAudit> getAuditTrail(String requestId) {
        return auditRepository.findByRequestIdOrderByOccurredAtAsc(requestId);
    }

    /**
     * Retrieves all audit events for a specific ingestion run.
     * <p>
     * Useful when you have a run ID from {@code ingestion_runs} table
     * and want to see all events that occurred during that execution.
     * <p>
     * Events are ordered chronologically (oldest first).
     *
     * @param runId the ingestion run identifier
     * @return list of audit events for the run, empty if none found
     */
    @Transactional(readOnly = true)
    public List<IngestionAudit> getAuditTrailByRunId(Long runId) {
        return auditRepository.findByRunIdOrderByOccurredAtAsc(runId);
    }

    /**
     * Retrieves the most recent audit events across all requests.
     * <p>
     * Returns up to 100 most recent events, ordered newest first.
     * Useful for monitoring system activity and quickly identifying
     * recent issues without knowing specific request IDs.
     *
     * @return list of recent audit events (max 100), newest first
     */
    @Transactional(readOnly = true)
    public List<IngestionAudit> getRecentEvents() {
        return auditRepository.findTop100ByOrderByOccurredAtDesc();
    }
}

