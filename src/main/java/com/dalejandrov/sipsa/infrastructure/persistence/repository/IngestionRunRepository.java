package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
