package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * JPA Repository for managing {@link IngestionRun} entities.
 * <p>
 * Provides data access methods for tracking ingestion run lifecycle,
 * including queries for:
 * <ul>
 *   <li>Finding runs by method and window (for duplicate detection)</li>
 *   <li>Counting successful runs (for completion verification)</li>
 *   <li>Finding last successful run per method (for health monitoring)</li>
 * </ul>
 * <p>
 * This repository is central to the ingestion control system, enabling
 * idempotent processing and preventing duplicate data ingestion.
 *
 * @see IngestionRun
 * @see com.dalejandrov.sipsa.application.service.IngestionControlService
 */
@Repository
public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    /**
     * Finds an ingestion run by method name and window key.
     * <p>
     * Used for:
     * <ul>
     *   <li>Duplicate detection before starting a new run</li>
     *   <li>Restart logic for failed runs</li>
     *   <li>Enforcing unique constraint per window</li>
     * </ul>
     * <p>
     * The combination of methodName + windowKey is unique in the database.
     *
     * @param methodName the SOAP method name (e.g., "promediosSipsaCiudad")
     * @param windowKey the time window key (e.g., "2026-01-02" or "2026-01-M8")
     * @return Optional containing the run if found, empty otherwise
     */
    Optional<IngestionRun> findByMethodNameAndWindowKey(String methodName, String windowKey);

    /**
     * Counts the number of runs for a method/window/status combination.
     * <p>
     * Used to verify if data has been successfully ingested for a given
     * time period, preventing duplicate processing.
     * <p>
     * Returns 0 if no runs exist with the given status, 1 if data has been ingested.
     *
     * @param methodName the SOAP method name
     * @param windowKey the time window key
     * @param status the status to count
     * @return count of runs with the given status (typically 0 or 1)
     */
    @Query("SELECT COUNT(r) FROM IngestionRun r WHERE r.methodName = :methodName AND r.windowKey = :windowKey AND r.status = :status")
    long countByMethodNameAndWindowKeyAndStatus(@Param("methodName") String methodName, @Param("windowKey") String windowKey, @Param("status") IngestionRunStatus status);

    /**
     * Finds the timestamp of the last run for each method by status.
     * <p>
     * Used by the health indicator to monitor data freshness and detect
     * stale data or failed scheduled jobs.
     * <p>
     * Returns an array where:
     * <ul>
     *   <li>Object[0] = String methodName</li>
     *   <li>Object[1] = Instant lastRunTime</li>
     * </ul>
     *
     * @param status the status to filter by
     * @return list of [methodName, maxStartTime] pairs for all methods with runs of the given status
     */
    @Query("SELECT r.methodName, MAX(r.startTime) FROM IngestionRun r WHERE r.status = :status GROUP BY r.methodName")
    java.util.List<Object[]> findLastRunPerMethodByStatus(@Param("status") IngestionRunStatus status);

    /**
     * Finds ingestion runs by their status values.
     * <p>
     * Useful for querying runs in specific states, such as active runs.
     *
     * @param statuses list of status values to match
     * @return list of runs with matching statuses
     */
    java.util.List<IngestionRun> findByStatusIn(java.util.List<IngestionRunStatus> statuses);

    /**
     * Atomically restarts the run for a given method/window, resetting it to
     * {@code STARTED}, but only if its current status is one of {@code allowedStatuses}.
     * <p>
     * This is a single conditional {@code UPDATE ... WHERE status IN (...)} statement: the
     * database itself evaluates and enforces the "may this row be restarted" decision as
     * part of the write, so two concurrent callers can never both restart the same row
     * (SIPSA-F4-01). Under PostgreSQL's default READ COMMITTED isolation, a second
     * transaction that reaches this statement while the first is still in flight blocks on
     * the row lock and, once unblocked, re-evaluates the {@code WHERE} clause against the
     * row's latest committed state - if the first transaction already moved the status out
     * of {@code allowedStatuses} (e.g. FAILED -> STARTED), the second update matches zero
     * rows instead of clobbering the first one. Callers MUST NOT gate this call behind a
     * prior read of the run's status to decide whether to call it; the read may only be used
     * afterward, to build a human-readable message when the affected-row count is zero.
     * <p>
     * Resets every field that {@code createRun}'s original (non-atomic) restart logic reset:
     * status, startTime, endTime, all four metric counters, lastErrorMessage, httpStatus,
     * soapFaultCode, requestId and requestSource.
     *
     * @param methodName the SOAP method name
     * @param windowKey the time window key
     * @param allowedStatuses the set of current statuses from which a restart is permitted
     * @param startTime the new start time to record (STARTED)
     * @param requestId correlation ID of the request performing the restart
     * @param requestSource origin of the request performing the restart
     * @return number of rows updated: 1 if the restart won, 0 if the row's status was not in
     *         {@code allowedStatuses} (already restarted by a concurrent winner, active, or
     *         otherwise not eligible)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IngestionRun r
               SET r.status = com.dalejandrov.sipsa.domain.entity.IngestionRunStatus.STARTED,
                   r.startTime = :startTime,
                   r.endTime = null,
                   r.recordsSeen = 0,
                   r.recordsInserted = 0,
                   r.recordsUpdated = 0,
                   r.rejectCount = 0,
                   r.lastErrorMessage = null,
                   r.httpStatus = null,
                   r.soapFaultCode = null,
                   r.requestId = :requestId,
                   r.requestSource = :requestSource
             WHERE r.methodName = :methodName
               AND r.windowKey = :windowKey
               AND r.status IN :allowedStatuses
            """)
    int restartIfStatusIn(@Param("methodName") String methodName,
                           @Param("windowKey") String windowKey,
                           @Param("allowedStatuses") Collection<IngestionRunStatus> allowedStatuses,
                           @Param("startTime") Instant startTime,
                           @Param("requestId") String requestId,
                           @Param("requestSource") RequestSource requestSource);
}
