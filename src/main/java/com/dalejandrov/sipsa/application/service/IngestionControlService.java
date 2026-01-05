package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.CreateRunRequest;
import com.dalejandrov.sipsa.domain.entity.IngestionReject;
import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRejectRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for managing ingestion run lifecycle and state.
 * <p>
 * This service is responsible for:
 * <ul>
 *   <li>Creating and initializing ingestion runs</li>
 *   <li>Updating run status and metrics during execution</li>
 *   <li>Recording rejected records that failed validation</li>
 *   <li>Logging errors with contextual information</li>
 *   <li>Checking run completion status</li>
 * </ul>
 * <p>
 * All operations use {@code REQUIRES_NEW} transaction propagation to ensure
 * that state changes are persisted independently of the main ingestion transaction.
 * This is crucial for maintaining accurate tracking even when ingestion fails.
 * <p>
 * The service enforces business rules around run uniqueness and restart logic:
 * <ul>
 *   <li>One active run per method/window combination</li>
 *   <li>Successful runs cannot be restarted without {@code force=true}</li>
 *   <li>Failed runs can be restarted automatically</li>
 * </ul>
 *
 * @see IngestionRun
 * @see IngestionReject
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionControlService {

    private final IngestionRunRepository runRepository;
    private final IngestionRejectRepository rejectRepository;

    /**
     * Creates a new ingestion run or restarts an existing one.
     * <p>
     * This method implements the following logic:
     * <ul>
     *   <li>If no run exists for method/window: creates new run</li>
     *   <li>If run succeeded and force=false: throws exception</li>
     *   <li>If run exists (not failed) and force=false: throws exception</li>
     *   <li>If force=true or previous failed: resets and restarts the run</li>
     * </ul>
     * <p>
     * The run is created with STARTED status and all metrics initialized to zero.
     * The requestId and requestSource are stored for correlation and auditing.
     *
     * @param methodName the ingestion method identifier
     * @param windowKey the time window key (e.g., "2026-01-02" for daily, "2026-01" for monthly)
     * @param force whether to bypass checks and force restart of existing runs
     * @param requestId unique correlation ID for tracking (UUID)
     * @param requestSource origin of the request (MANUAL, SCHEDULED, SYSTEM)
     * @return the run ID (either newly created or existing run ID)
     * @throws SipsaBusinessException if run already exists and cannot be restarted
     * @throws SipsaBusinessException if database integrity violation occurs
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long createRun(String methodName, String windowKey, boolean force, String requestId, RequestSource requestSource) {
        var existingRun = runRepository.findByMethodNameAndWindowKey(methodName, windowKey);

        if (existingRun.isPresent()) {
            IngestionRun run = existingRun.get();
            if (!force && "SUCCEEDED".equals(run.getStatus())) {
                throw new SipsaBusinessException(
                        "Run already succeeded for method: " + methodName + ", window: " + windowKey);
            }
            if (!force && !"FAILED".equals(run.getStatus())) {
                throw new SipsaBusinessException(
                        "Run already exists (Status: " + run.getStatus() + "). Use force=true to restart.");
            }

            // Restart logic: Reset metrics and status
            log.warn("Restarting existing run {}/{} (ID: {})", methodName, windowKey, run.getRunId());
            run.setStatus("STARTED");
            run.setStartTime(Instant.now());
            run.setEndTime(null);
            run.setRecordsSeen(0);
            run.setRecordsInserted(0);
            run.setRecordsUpdated(0);
            run.setRejectCount(0);
            run.setLastErrorMessage(null);
            run.setHttpStatus(null);
            run.setSoapFaultCode(null);
            run.setRequestId(requestId);
            run.setRequestSource(requestSource);

            run = runRepository.save(run);
            return run.getRunId();
        }

        try {
            IngestionRun run = IngestionRun.builder()
                    .methodName(methodName)
                    .windowKey(windowKey)
                    .requestId(requestId)
                    .requestSource(requestSource)
                    .status("STARTED")
                    .startTime(Instant.now())
                    .recordsSeen(0)
                    .recordsInserted(0)
                    .recordsUpdated(0)
                    .rejectCount(0)
                    .build();

            run = runRepository.save(run);
            return run.getRunId();
        } catch (DataIntegrityViolationException e) {
            throw new SipsaBusinessException("Failed to create run due to concurrency/integrity violation", e);
        }
    }

    /**
     * Creates a new ingestion run or restarts an existing one using DTO.
     * <p>
     * This method implements the same logic as {@link #createRun(String, String, boolean, String, RequestSource)}
     * but accepts a {@link CreateRunRequest} DTO for better encapsulation.
     *
     * @param request encapsulates all run creation parameters
     * @return the run ID (either newly created or existing run ID)
     * @throws SipsaBusinessException if run already exists and cannot be restarted
     * @throws SipsaBusinessException if database integrity violation occurs
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long createRun(CreateRunRequest request) {
        return createRun(request.methodName(), request.windowKey(), request.force(),
                        request.requestId(), request.requestSource());
    }

    /**
     * Updates the status of an ingestion run.
     * <p>
     * Valid statuses include: STARTED, RUNNING, SUCCEEDED, FAILED.
     * The status change is persisted in a separate transaction.
     *
     * @param runId the run identifier
     * @param status the new status value
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(long runId, String status) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setStatus(status);
            if ("SUCCEEDED".equals(status) || "FAILED".equals(status)) {
                run.setEndTime(Instant.now());
            }
            runRepository.save(run);
        });
    }

    /**
     * Updates the metrics of an ingestion run.
     * <p>
     * Metrics include counts of records seen, inserted, updated, and rejected.
     * This information is used for monitoring and alerting purposes.
     *
     * @param runId the run identifier
     * @param seen the number of records seen
     * @param inserted the number of records inserted
     * @param updated the number of records updated
     * @param rejected the number of records rejected
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMetrics(long runId, int seen, int inserted, int updated, int rejected) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setRecordsSeen(seen);
            run.setRecordsInserted(inserted);
            run.setRecordsUpdated(updated);
            run.setRejectCount(rejected);
            runRepository.save(run);
        });
    }

    /**
     * Logs an error that occurred during ingestion run.
     * <p>
     * The error information is saved to the database and can be used for troubleshooting
     * and alerting. It includes a message, HTTP status code, and SOAP fault code (if any).
     *
     * @param runId the run identifier
     * @param message the error message
     * @param httpStatus the HTTP status code (optional)
     * @param faultCode the SOAP fault code (optional)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logError(long runId, String message, Integer httpStatus, String faultCode) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setLastErrorMessage(message);
            run.setHttpStatus(httpStatus);
            run.setSoapFaultCode(faultCode);
            runRepository.save(run);
        });
    }

    /**
     * Records a rejected record for an ingestion run.
     * <p>
     * Rejected records are those that failed validation or processing and could not
     * be ingested. This method saves the raw data and reason for rejection, and marks
     * whether it was a parsing error.
     *
     * @param runId the run identifier
     * @param rawData the raw data of the rejected record
     * @param reason the reason for rejection
     * @param isParseError true if the rejection was due to a parsing error, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logReject(long runId, String rawData, String reason, boolean isParseError) {
        IngestionReject reject = IngestionReject.builder()
                .runId(runId)
                .rawData(rawData)
                .reason(reason)
                .isParseError(isParseError)
                .createdAt(Instant.now())
                .build();
        rejectRepository.save(reject);
    }

    /**
     * Checks if there is a completed run for the given method and window.
     * <p>
     * A run is considered complete if it has succeeded at least once for the given
     * method and window key.
     *
     * @param methodName the ingestion method identifier
     * @param windowKey the time window key
     * @return true if a completed run exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isRunComplete(String methodName, String windowKey) {
        return runRepository.countSucceeded(methodName, windowKey) > 0;
    }
}
