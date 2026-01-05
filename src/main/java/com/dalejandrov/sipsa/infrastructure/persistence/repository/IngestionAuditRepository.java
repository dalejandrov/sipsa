package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.IngestionAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * JPA Repository for managing {@link IngestionAudit} entities.
 * <p>
 * Provides data access methods for audit event queries, supporting:
 * <ul>
 *   <li>Request lifecycle tracking by requestId</li>
 *   <li>Run-specific event retrieval by runId</li>
 *   <li>Recent activity monitoring across all requests</li>
 * </ul>
 * <p>
 * All query methods return events in chronological order for timeline reconstruction.
 * <p>
 * <b>Indexing:</b> The table has indexes on requestId, runId, requestSource,
 * and occurredAt for optimal query performance.
 *
 * @see IngestionAudit
 * @see com.dalejandrov.sipsa.application.service.IngestionAuditService
 */
@Repository
public interface IngestionAuditRepository extends JpaRepository<IngestionAudit, Long> {

    /**
     * Retrieves all audit events for a specific request, ordered chronologically.
     * <p>
     * Returns the complete timeline of events from request receipt through
     * final completion or failure. This is the primary method for debugging
     * and tracking individual requests.
     *
     * @param requestId the unique request identifier (UUID)
     * @return list of audit events ordered by occurredAt (oldest first), empty if none found
     */
    List<IngestionAudit> findByRequestIdOrderByOccurredAtAsc(String requestId);

    /**
     * Retrieves all audit events for a specific ingestion run, ordered chronologically.
     * <p>
     * Useful when you have a runId from the ingestion_runs table and want to see
     * all events that occurred during that execution.
     *
     * @param runId the ingestion run identifier
     * @return list of audit events for the run ordered by occurredAt, empty if none found
     */
    List<IngestionAudit> findByRunIdOrderByOccurredAtAsc(Long runId);

    /**
     * Retrieves the most recent audit events across all requests.
     * <p>
     * Returns up to 100 most recent events, ordered newest first. Useful for:
     * <ul>
     *   <li>Real-time monitoring of system activity</li>
     *   <li>Quick identification of recent issues</li>
     *   <li>Dashboard displays</li>
     * </ul>
     *
     * @return list of up to 100 most recent audit events, newest first
     */
    List<IngestionAudit> findTop100ByOrderByOccurredAtDesc();
}
