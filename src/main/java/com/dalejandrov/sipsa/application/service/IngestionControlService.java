package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.application.command.CreateRunRequest;
import com.dalejandrov.sipsa.domain.entity.IngestionReject;
import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRejectRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
 *   <li>A STARTED or RUNNING run can never be restarted, even with {@code force=true}</li>
 *   <li>Successful or canceled runs can only be restarted with {@code force=true}</li>
 *   <li>Failed runs can be restarted with or without {@code force=true}</li>
 * </ul>
 * These rules are enforced by a single atomic conditional {@code UPDATE} in the database
 * (see {@link IngestionRunRepository#restartIfStatusIn}), not by a Java-side check followed
 * by a save - see {@link #createRun(String, String, boolean, String, RequestSource)} for why
 * that distinction matters under concurrency (SIPSA-F4-01).
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
     * Fixed predecessor for each {@code updateStatus} target (SIPSA-F4-21).
     * <p>
     * {@code CANCELED} is deliberately absent as a key: it is reachable exclusively through
     * {@link #cancelRun(long)}'s own atomic {@code STARTED/RUNNING -> CANCELED} update, never
     * through this generic transition. {@code STARTED} is absent too: it is only ever set by
     * {@link #createRun}. Calling {@link #updateStatus} with a {@code to} not in this map is a
     * programming error, not a runtime race, and fails fast via {@link IllegalArgumentException}.
     */
    private static final java.util.Map<IngestionRunStatus, IngestionRunStatus> REQUIRED_PREDECESSOR =
            java.util.Map.of(
                    IngestionRunStatus.RUNNING, IngestionRunStatus.STARTED,
                    IngestionRunStatus.SUCCEEDED, IngestionRunStatus.RUNNING,
                    IngestionRunStatus.FAILED, IngestionRunStatus.RUNNING
            );

    /** Statuses whose arrival records {@code endTime} - the run has stopped changing. */
    private static final Set<IngestionRunStatus> TERMINAL_STATUSES =
            Set.of(IngestionRunStatus.SUCCEEDED, IngestionRunStatus.FAILED, IngestionRunStatus.CANCELED);

    /** Statuses a restart may originate from when {@code force=false}: only FAILED. */
    private static final Set<IngestionRunStatus> RESTARTABLE_WITHOUT_FORCE = Set.of(IngestionRunStatus.FAILED);

    /**
     * Statuses a restart may originate from when {@code force=true}.
     * <p>
     * STARTED and RUNNING are deliberately never included, at any force setting: an active
     * run must never be restarted, since that would silently clobber its in-flight metrics
     * and startTime (SIPSA-F4-01).
     * <p>
     * CANCELED is included here to preserve the prior (non-atomic) implementation's
     * behavior: no product rule was ever documented for restarting a CANCELED run, and the
     * old code treated it exactly like SUCCEEDED - rejected without force, allowed with
     * force. That behavior is carried over unchanged rather than silently broadened or
     * narrowed.
     */
    private static final Set<IngestionRunStatus> RESTARTABLE_WITH_FORCE =
            Set.of(IngestionRunStatus.FAILED, IngestionRunStatus.SUCCEEDED, IngestionRunStatus.CANCELED);

    /**
     * Creates a new ingestion run or restarts an existing one.
     * <p>
     * This method implements the following logic:
     * <ul>
     *   <li>If no run exists for method/window: creates new run</li>
     *   <li>If the existing run is STARTED or RUNNING: always rejected, even with force=true
     *       - an active run's row, metrics and startTime are never touched</li>
     *   <li>If the existing run is FAILED: restart allowed with or without force=true</li>
     *   <li>If the existing run is SUCCEEDED or CANCELED: restart allowed only with
     *       force=true</li>
     * </ul>
     * The restart itself is a single atomic conditional {@code UPDATE} keyed on the row's
     * current status (see {@link IngestionRunRepository#restartIfStatusIn}) - the decision
     * is made and enforced by the database in one statement, so two concurrent restart
     * attempts against the same row can never both succeed. The run is created (or reset)
     * with STARTED status and all metrics initialized to zero. The requestId and
     * requestSource are stored for correlation and auditing.
     *
     * @param methodName the ingestion method identifier
     * @param windowKey the time window key (e.g., "2026-01-02" for daily, "2026-01-M8" or "2026-01-M10" for monthly)
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
            long runId = run.getRunId();

            // The concurrency-safe decision happens exclusively inside this single
            // conditional UPDATE (see IngestionRunRepository#restartIfStatusIn): the DB
            // evaluates "is this row currently in an allowed status" and performs the reset
            // atomically, so two concurrent restart attempts can never both win, and an
            // active (STARTED/RUNNING) run can never be restarted regardless of force.
            Set<IngestionRunStatus> allowedSourceStatuses = force ? RESTARTABLE_WITH_FORCE : RESTARTABLE_WITHOUT_FORCE;
            int updatedRows = runRepository.restartIfStatusIn(
                    methodName, windowKey, allowedSourceStatuses, Instant.now(), requestId, requestSource);

            if (updatedRows == 0) {
                // The row wasn't eligible for restart (or a concurrent caller already won
                // it). This read is only used to produce a helpful message - it plays no
                // part in the concurrency-safety decision above, which already happened.
                IngestionRunStatus currentStatus = runRepository.findByMethodNameAndWindowKey(methodName, windowKey)
                        .map(IngestionRun::getStatus)
                        .orElse(run.getStatus());
                throw restartConflict(currentStatus, force, methodName, windowKey);
            }

            log.warn("Restarted existing run {}/{} (ID: {})", methodName, windowKey, runId);
            return runId;
        }

        try {
            IngestionRun run = IngestionRun.builder()
                    .methodName(methodName)
                    .windowKey(windowKey)
                    .requestId(requestId)
                    .requestSource(requestSource)
                    .status(IngestionRunStatus.STARTED)
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
     * Builds the business exception for a restart that {@link IngestionRunRepository#restartIfStatusIn}
     * rejected (zero rows affected).
     * <p>
     * {@code currentStatus} is read after the fact purely to phrase a helpful message; it
     * has no bearing on concurrency safety, which was already fully decided by the atomic
     * UPDATE's {@code WHERE} clause.
     *
     * @param currentStatus the run's status as observed after the rejected update
     * @param force whether the caller requested force restart
     * @param methodName the ingestion method identifier
     * @param windowKey the time window key
     * @return a {@link SipsaBusinessException} describing why the restart was refused
     */
    private static SipsaBusinessException restartConflict(IngestionRunStatus currentStatus, boolean force,
                                                            String methodName, String windowKey) {
        if (currentStatus == IngestionRunStatus.STARTED || currentStatus == IngestionRunStatus.RUNNING) {
            return new SipsaBusinessException(
                    "Run is active (status: " + currentStatus + ") for method: " + methodName
                            + ", window: " + windowKey + ". Active runs cannot be restarted, even with force=true.");
        }
        if (currentStatus == IngestionRunStatus.SUCCEEDED && !force) {
            return new SipsaBusinessException(
                    "Run already succeeded for method: " + methodName + ", window: " + windowKey);
        }
        // Covers: CANCELED without force, and the case where a concurrent caller already
        // won the restart between our read and our conditional UPDATE.
        return new SipsaBusinessException(
                "Run already exists (Status: " + currentStatus + "). Use force=true to restart.");
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
     * Atomically transitions an ingestion run to {@code to}, conditioned on the run's current
     * status still being the fixed predecessor {@link #REQUIRED_PREDECESSOR} defines for it
     * (SIPSA-F4-21: {@code STARTED->RUNNING}, {@code RUNNING->SUCCEEDED},
     * {@code RUNNING->FAILED}).
     * <p>
     * Implemented as a single conditional {@code UPDATE ... WHERE status = :expectedFrom} (see
     * {@link IngestionRunRepository#transitionStatusIfCurrentIs}), the same pattern
     * {@link #createRun} already uses for restarts (SIPSA-F4-01): the database decides and
     * enforces "is this row still in the expected predecessor state" as part of the write, so
     * this call can never silently overwrite a terminal state a concurrent
     * {@link #cancelRun(long)} already committed.
     * <p>
     * Returns a boolean rather than throwing when the row is no longer in the expected
     * predecessor state, because whether that should be a hard conflict or a silently-ignored
     * late transition is caller-specific (Etapa 5): {@link com.dalejandrov.sipsa.application.ingestion.core.IngestionJob}
     * treats {@code false} as "this run was concurrently canceled" and skips ahead without
     * throwing, since throwing here would incorrectly route a canceled run through the
     * failure-handling path.
     *
     * @param runId the run identifier
     * @param to the target status; must be a key of {@link #REQUIRED_PREDECESSOR}
     * @return {@code true} if the transition was applied, {@code false} if the run's status was
     *         no longer the required predecessor (most commonly: already {@code CANCELED})
     * @throws IllegalArgumentException if {@code to} has no defined predecessor (i.e. is
     *         {@code STARTED} or {@code CANCELED} - both are set exclusively by
     *         {@link #createRun} and {@link #cancelRun(long)} respectively, never by this method)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateStatus(long runId, IngestionRunStatus to) {
        IngestionRunStatus expectedFrom = REQUIRED_PREDECESSOR.get(to);
        if (expectedFrom == null) {
            throw new IllegalArgumentException(
                    "updateStatus does not support transitioning to " + to
                            + "; STARTED is only set by createRun(), CANCELED only by cancelRun().");
        }
        Instant endTime = TERMINAL_STATUSES.contains(to) ? Instant.now() : null;
        int updatedRows = runRepository.transitionStatusIfCurrentIs(runId, expectedFrom, to, endTime);
        return updatedRows > 0;
    }

    /**
     * Updates only the four metric counters of an ingestion run (SIPSA-F4-21).
     * <p>
     * Implemented as a column-scoped {@code UPDATE} (see
     * {@link IngestionRunRepository#updateMetricsColumns}) that never touches {@code status},
     * {@code endTime}, error fields or request metadata - unlike the prior
     * {@code findById -> mutate -> save}, which re-persisted the entity's in-memory
     * {@code status} as read at the start of this call, silently reverting a concurrent
     * transition (e.g. a {@link #cancelRun(long)}) that committed in between.
     * <p>
     * Applied unconditionally, regardless of the run's current status: in the only real caller
     * ({@code IngestionJob.execute()}'s {@code finally} block), this is the run's own final
     * tally, reported exactly once after that same execution already learned its own outcome
     * (including cancellation) - not a second, independent writer that could still be racing
     * the lifecycle state. Gating it on status would not close any additional race (this
     * statement structurally cannot touch the state machine either way) and would instead drop
     * the legitimate final metrics of every canceled run.
     *
     * @param runId the run identifier
     * @param seen the number of records seen
     * @param inserted the number of records inserted
     * @param updated the number of records updated
     * @param rejected the number of records rejected
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMetrics(long runId, int seen, int inserted, int updated, int rejected) {
        runRepository.updateMetricsColumns(runId, seen, inserted, updated, rejected);
    }

    /**
     * Updates only the three error-report columns of an ingestion run (SIPSA-F4-21).
     * <p>
     * Implemented as a column-scoped {@code UPDATE} (see
     * {@link IngestionRunRepository#updateErrorColumns}), for the same reason as
     * {@link #updateMetrics}: it never touches {@code status}, so it cannot cause a lost update
     * of the lifecycle state regardless of when it runs, and is therefore applied
     * unconditionally. A late error - e.g. an exception surfacing in {@code runIngestion} after
     * the run was already concurrently canceled - is still recorded: it is diagnostically
     * useful (it explains why the job errored even though the operator had already canceled
     * it) and never changes the run's status. This method never transitions status itself; the
     * FAILED transition remains a separate, explicit {@link #updateStatus} call by the caller.
     *
     * @param runId the run identifier
     * @param message the error message
     * @param httpStatus the HTTP status code (optional)
     * @param faultCode the SOAP fault code (optional)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logError(long runId, String message, Integer httpStatus, String faultCode) {
        runRepository.updateErrorColumns(runId, message, httpStatus, faultCode);
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
        return runRepository.countByMethodNameAndWindowKeyAndStatus(methodName, windowKey, IngestionRunStatus.SUCCEEDED) > 0;
    }

    /**
     * Retrieves an ingestion run by its ID.
     *
     * @param runId the run identifier
     * @return the IngestionRun entity, or {@link Optional#empty()} if not found
     */
    @Transactional(readOnly = true)
    public Optional<IngestionRun> getRun(long runId) {
        return runRepository.findById(runId);
    }

    /**
     * Retrieves all currently active ingestion runs.
     * <p>
     * Returns runs that are in STARTED or RUNNING status.
     *
     * @return list of active run entities
     */
    @Transactional(readOnly = true)
    public List<IngestionRun> findActiveRuns() {
        return runRepository.findByStatusIn(
                List.of(IngestionRunStatus.STARTED, IngestionRunStatus.RUNNING)
        );
    }

    /**
     * Retrieves all ingestion runs with pagination.
     *
     * @param pageable pagination information
     * @return page of run entities
     */
    @Transactional(readOnly = true)
    public Page<IngestionRun> findAllRuns(Pageable pageable) {
        return runRepository.findAll(pageable);
    }

    /**
     * Checks if an ingestion run has been canceled.
     * <p>
     * This method should be called periodically during long-running ingestion processes
     * to allow for graceful cancellation.
     *
     * @param runId the run identifier
     * @return true if the run status is CANCELED, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean isRunCanceled(long runId) {
        IngestionRun run = runRepository.findById(runId).orElse(null);
        return run != null && run.getStatus() == IngestionRunStatus.CANCELED;
    }

    /**
     * Cancels an active ingestion run.
     * <p>
     * Only runs with status STARTED or RUNNING can be canceled. The cancellation itself is a
     * single atomic conditional {@code UPDATE} (see {@link IngestionRunRepository#cancelIfActive},
     * SIPSA-F4-21) - the database evaluates and enforces "is this row still cancelable" as part
     * of the write, mirroring {@link #createRun}'s restart pattern (SIPSA-F4-01). This closes
     * the TOCTOU window the prior {@code findById -> check -> save} sequence had: a
     * finalization ({@link #updateStatus} to {@code SUCCEEDED}/{@code FAILED}) committing
     * between the read and the write could previously be silently clobbered back to
     * {@code CANCELED}, or vice versa. Under this method, whichever side loses the row lock
     * always affects zero rows instead of overwriting the winner.
     * <p>
     * When the atomic update affects zero rows, the run is re-read once purely to build an
     * accurate exception message - that read plays no part in the concurrency-safety decision,
     * which already happened. Behavior on that path is unchanged from before this fix: a
     * missing run is a 404-mapped {@link SipsaNotFoundException}; an existing but inactive run
     * - including one that is already {@code CANCELED} - is a 422-mapped
     * {@link SipsaBusinessException}. Double-cancellation is therefore not idempotent, exactly
     * as it was not before: the second caller (whether racing a concurrent cancel or canceling
     * an already-canceled run in a later call) gets a controlled conflict, never a silent no-op.
     *
     * @param runId the run identifier
     * @throws SipsaNotFoundException if run not found
     * @throws SipsaBusinessException if run exists but is not active (including already CANCELED)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelRun(long runId) {
        int updatedRows = runRepository.cancelIfActive(runId, Instant.now());
        if (updatedRows > 0) {
            return;
        }

        // The row wasn't cancelable (or a concurrent caller already won). This read is only
        // used to produce a helpful message - it plays no part in the concurrency-safety
        // decision above, which already happened.
        IngestionRun run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            throw new SipsaNotFoundException("Run not found: " + runId);
        }
        throw new SipsaBusinessException("Run is not active (status: " + run.getStatus() + ")");
    }
}
