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

    /**
     * Atomically transitions a run's status from {@code expectedFrom} to {@code to}, but only
     * if the row's current status is still {@code expectedFrom} at the moment of the write.
     * <p>
     * Same shape as {@link #restartIfStatusIn}, specialized to a single expected predecessor
     * (SIPSA-F4-21): the caller in {@link com.dalejandrov.sipsa.application.service.IngestionControlService}
     * derives {@code expectedFrom} from the lifecycle's fixed predecessor map, so two racing
     * writers (e.g. the job thread finalizing a run while an operator concurrently cancels it
     * via {@link #cancelIfActive}) can never both succeed - the loser's {@code WHERE} clause
     * simply matches zero rows instead of overwriting the winner's terminal state.
     *
     * @param runId the run identifier
     * @param expectedFrom the only status this call is allowed to transition away from
     * @param to the target status
     * @param endTime the end time to record, or {@code null} when {@code to} is not terminal
     * @return 1 if the transition won, 0 if the row's status was no longer {@code expectedFrom}
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IngestionRun r
               SET r.status = :to,
                   r.endTime = :endTime
             WHERE r.runId = :runId
               AND r.status = :expectedFrom
            """)
    int transitionStatusIfCurrentIs(@Param("runId") long runId,
                                     @Param("expectedFrom") IngestionRunStatus expectedFrom,
                                     @Param("to") IngestionRunStatus to,
                                     @Param("endTime") Instant endTime);

    /**
     * Atomically cancels a run: sets it to {@code CANCELED} only if it is currently
     * {@code STARTED} or {@code RUNNING} (SIPSA-F4-21).
     * <p>
     * Replaces the former {@code findById -> check -> save} sequence in
     * {@link com.dalejandrov.sipsa.application.service.IngestionControlService#cancelRun},
     * which had a TOCTOU window between the read and the write: a finalization
     * ({@code SUCCEEDED}/{@code FAILED}, see {@link #transitionStatusIfCurrentIs}) racing the
     * same row could commit in that window and be silently clobbered back to
     * {@code CANCELED}. The {@code WHERE} clause here makes the "is this row still
     * cancelable" decision and the write a single atomic statement, so the loser (whichever
     * side loses the row lock) always sees zero affected rows instead of stomping the winner.
     *
     * @param runId the run identifier
     * @param endTime the end time to record for the cancellation
     * @return 1 if the cancellation won, 0 if the row was no longer STARTED/RUNNING (already
     *         terminal, or a concurrent finalization/cancellation already won)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IngestionRun r
               SET r.status = com.dalejandrov.sipsa.domain.entity.IngestionRunStatus.CANCELED,
                   r.endTime = :endTime
             WHERE r.runId = :runId
               AND r.status IN (com.dalejandrov.sipsa.domain.entity.IngestionRunStatus.STARTED,
                                 com.dalejandrov.sipsa.domain.entity.IngestionRunStatus.RUNNING)
            """)
    int cancelIfActive(@Param("runId") long runId, @Param("endTime") Instant endTime);

    /**
     * Partially updates only the four metric counters of a run, leaving status, timestamps,
     * error fields and request metadata untouched (SIPSA-F4-21).
     * <p>
     * Replaces the former {@code findById -> mutate 4 fields on a fully-loaded entity -> save}
     * in {@link com.dalejandrov.sipsa.application.service.IngestionControlService#updateMetrics},
     * which re-persisted every column of the entity as loaded at read time - if a concurrent
     * transition (e.g. {@link #cancelIfActive}) committed between this method's read and its
     * save, the stale in-memory {@code status}/{@code endTime} it still held would silently
     * overwrite the concurrent transition. Column-scoped by construction, this statement
     * cannot touch {@code status} even in principle, so it needs no status precondition: it is
     * always safe to apply, regardless of which lifecycle state the row is currently in.
     *
     * @param runId the run identifier
     * @param seen records seen
     * @param inserted records inserted
     * @param updated records updated
     * @param rejected records rejected
     * @return 1 if the run exists, 0 otherwise (silent no-op, same as the prior
     *         {@code findById(...).ifPresent(...)} behavior)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IngestionRun r
               SET r.recordsSeen = :seen,
                   r.recordsInserted = :inserted,
                   r.recordsUpdated = :updated,
                   r.rejectCount = :rejected
             WHERE r.runId = :runId
            """)
    int updateMetricsColumns(@Param("runId") long runId,
                              @Param("seen") int seen,
                              @Param("inserted") int inserted,
                              @Param("updated") int updated,
                              @Param("rejected") int rejected);

    /**
     * Partially updates only the three error-report columns of a run, leaving status,
     * timestamps, metrics and request metadata untouched (SIPSA-F4-21).
     * <p>
     * Same column-scoping rationale as {@link #updateMetricsColumns}: replaces a
     * {@code findById -> mutate -> save} in
     * {@link com.dalejandrov.sipsa.application.service.IngestionControlService#logError} that
     * could otherwise resurrect a stale status/endTime read before a concurrent transition.
     * Diagnostic error information is recorded unconditionally (even for a run that is already
     * {@code CANCELED} or a terminal state reached by another writer) because it never affects
     * the lifecycle state machine and losing it would hide the real cause of a late failure.
     *
     * @param runId the run identifier
     * @param message the error message
     * @param httpStatus the HTTP status code (nullable)
     * @param faultCode the SOAP fault code (nullable)
     * @return 1 if the run exists, 0 otherwise (silent no-op, same as the prior
     *         {@code findById(...).ifPresent(...)} behavior)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IngestionRun r
               SET r.lastErrorMessage = :message,
                   r.httpStatus = :httpStatus,
                   r.soapFaultCode = :faultCode
             WHERE r.runId = :runId
            """)
    int updateErrorColumns(@Param("runId") long runId,
                            @Param("message") String message,
                            @Param("httpStatus") Integer httpStatus,
                            @Param("faultCode") String faultCode);
}
