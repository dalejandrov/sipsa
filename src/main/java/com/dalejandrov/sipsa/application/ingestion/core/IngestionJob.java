package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.application.command.AuditEventRequest;
import com.dalejandrov.sipsa.application.command.CreateRunRequest;
import com.dalejandrov.sipsa.application.command.IngestionRequest;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.exception.WindowViolationException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for all ingestion jobs.
 * <p>
 * This class provides the core ingestion workflow and template method pattern
 * for processing data from external sources. It handles:
 * <ul>
 *   <li>Window validation and duplicate run prevention</li>
 *   <li>Run lifecycle management (create, start, complete, fail)</li>
 *   <li>Audit event logging throughout the process</li>
 *   <li>MDC logging context setup for correlation</li>
 *   <li>Metrics tracking and threshold validation</li>
 *   <li>Rejected records persistence</li>
 *   <li>Error handling and recovery</li>
 * </ul>
 * <p>
 * Subclasses must implement {@link #runIngestion(IngestionContext)} to define
 * the specific data extraction and processing logic for their domain.
 * <p>
 * <b>Quality Controls:</b>
 * <ul>
 *   <li>Max reject rate: Configurable via {@code sipsa.ingestion.max-reject-rate}</li>
 *   <li>Max reject count: Configurable via {@code sipsa.ingestion.max-reject-count}</li>
 * </ul>
 * <p>
 * <b>Transaction Handling:</b><br>
 * Each stage (create run, update status, update metrics) uses separate transactions
 * ({@code REQUIRES_NEW}) to ensure partial progress is saved even on failure.
 *
 * @see IngestionContext
 * @see WindowPolicy
 * @see IngestionControlService
 * @see IngestionAuditService
 */
public abstract class IngestionJob {

    private static final Logger log = LoggerFactory.getLogger(IngestionJob.class);

    protected WindowPolicy windowPolicy;
    protected IngestionControlService controlService;
    protected IngestionAuditService auditService;
    protected final IngestionMetrics metrics;

    /* Quality thresholds for rejections. Sourced from IngestionProperties (TECH-135,
     * C-04) — the single, validated binding of sipsa.ingestion.max-reject-rate /
     * max-reject-count; jobs no longer carry their own @Value defaults. */
    protected final double maxRejectRate;
    protected final int maxRejectCount;

    /**
     * Creates a new ingestion job with required dependencies and threshold configuration.
     *
     * @param windowPolicy policy for validating execution windows
     * @param controlService service for managing run state
     * @param auditService service for audit event logging
     * @param ingestionProperties centrally validated ingestion configuration
     *                            (reject thresholds are read from it at construction)
     * @param metrics Micrometer instrumentation for run duration, outcomes, and
     *                record counts (TECH-032)
     */
    protected IngestionJob(WindowPolicy windowPolicy, IngestionControlService controlService,
                           IngestionAuditService auditService,
                           IngestionProperties ingestionProperties,
                           IngestionMetrics metrics) {
        this.windowPolicy = windowPolicy;
        this.controlService = controlService;
        this.auditService = auditService;
        this.maxRejectRate = ingestionProperties.getMaxRejectRate();
        this.maxRejectCount = ingestionProperties.getMaxRejectCount();
        this.metrics = metrics;
    }

    /**
     * Executes the complete ingestion workflow.
     * <p>
     * This is the main entry point that orchestrates:
     * <ol>
     *   <li>Window validation</li>
     *   <li>Duplicate check</li>
     *   <li>Run creation/restart</li>
     *   <li>Audit context setup (MDC)</li>
     *   <li>Data ingestion (delegates to subclass)</li>
     *   <li>Quality threshold validation</li>
     *   <li>Metrics update</li>
     *   <li>Rejected records persistence</li>
     *   <li>Status finalization (SUCCEEDED/FAILED)</li>
     * </ol>
     * <p>
     * All exceptions are caught and logged, with the run marked as FAILED.
     * Audit events are recorded at each significant step.
     *
     * @param request encapsulates all ingestion parameters
     */
    public void execute(IngestionRequest request) {
        String windowKey;
        try {
            windowKey = windowPolicy.validateAndGetKey(request.methodName(), request.force());
        } catch (WindowViolationException e) {
            log.debug("Skipping run for {}: {}", request.methodName(), e.getMessage());
            auditService.logEvent(AuditEventRequest.ingestionSkippedWindow(request, e.getMessage()));
            return;
        }

        if (controlService.isRunComplete(request.methodName(), windowKey) && !request.force()) {
            log.info("Run already SUCCEEDED for {}/{}. Skipping.", request.methodName(), windowKey);
            auditService.logEvent(AuditEventRequest.ingestionSkippedDuplicate(request, windowKey));
            return;
        }

        long runId;
        try {
            runId = controlService.createRun(CreateRunRequest.from(request, windowKey));
            auditService.logEvent(AuditEventRequest.ingestionStarted(request, runId, windowKey));
        } catch (SipsaBusinessException e) {
            log.info("Skipping run for {}/{}: {}", request.methodName(), windowKey, e.getMessage());
            auditService.logEvent(AuditEventRequest.ingestionSkippedDuplicate(request, windowKey, e.getMessage()));
            return;
        }

        IngestionContext context = new IngestionContext(runId, request.methodName(), windowKey,
                                                       request.requestId(), request.requestSource());

        Timer.Sample runSample = metrics.startRun();
        String outcome = IngestionMetrics.OUTCOME_FAILURE;

        try {
            org.slf4j.MDC.put("runId", String.valueOf(runId));
            org.slf4j.MDC.put("method", request.methodName());
            org.slf4j.MDC.put("windowKey", windowKey);
            org.slf4j.MDC.put("requestId", request.requestId());
            org.slf4j.MDC.put("requestSource", request.requestSource().name());

            boolean startedRunning = controlService.updateStatus(runId, IngestionRunStatus.RUNNING);
            if (!startedRunning) {
                // Lost the race to a concurrent cancelRun before this job could even leave
                // STARTED (SIPSA-F4-21) - nothing was ingested, so there is nothing to run.
                log.warn("Ingestion Job never reached RUNNING: {} (ID: {}) - run left STARTED " +
                        "concurrently (most likely canceled)", request.methodName(), runId);
                auditService.logEvent(AuditEventRequest.ingestionLateTransitionIgnored(
                        request.requestId(), runId, request.requestSource(),
                        "RUNNING", "run was no longer STARTED (likely canceled before it could start)"));
                outcome = IngestionMetrics.OUTCOME_CANCELED;
                return;
            }
            auditService.logEvent(AuditEventRequest.ingestionRunning(request, runId));
            log.info("Started Ingestion Job: {} (ID: {})", request.methodName(), runId);

            runIngestion(context);

            // Check if the run was canceled during processing
            if (controlService.isRunCanceled(runId)) {
                log.warn("Ingestion Job CANCELED: {} (ID: {})", request.methodName(), runId);
                auditService.logEvent(AuditEventRequest.ingestionCanceled(request.requestId(), runId, request.requestSource()));
                outcome = IngestionMetrics.OUTCOME_CANCELED;
                return; // Exit early without marking as succeeded
            }

            validateThresholds(context);

            // Defense in depth against the residual TOCTOU window between the isRunCanceled
            // check above and this write: updateStatus's own WHERE clause re-verifies RUNNING
            // atomically, so a cancelRun landing in that narrow window still cannot be
            // overwritten (SIPSA-F4-21).
            boolean succeeded = controlService.updateStatus(runId, IngestionRunStatus.SUCCEEDED);
            if (succeeded) {
                auditService.logEvent(AuditEventRequest.ingestionSucceeded(context));
                log.info("Ingestion Job SUCCEEDED: {} (ID: {}). Stats: {}", request.methodName(), runId, context.toLogSummary());
                outcome = IngestionMetrics.OUTCOME_SUCCESS;
            } else {
                log.warn("Ingestion Job finished processing but could not transition to SUCCEEDED: " +
                        "{} (ID: {}) - run was concurrently canceled", request.methodName(), runId);
                auditService.logEvent(AuditEventRequest.ingestionLateTransitionIgnored(
                        request.requestId(), runId, request.requestSource(),
                        "SUCCEEDED", "run was concurrently canceled"));
                outcome = IngestionMetrics.OUTCOME_CANCELED;
            }

        } catch (Exception e) {
            log.error("Ingestion Job FAILED: {} (ID: {})", request.methodName(), runId, e);

            Integer httpStatus = null;
            String soapFaultCode = null;

            if (e instanceof SipsaExternalException externalEx) {
                httpStatus = externalEx.getHttpStatus();
                soapFaultCode = externalEx.getSoapFaultCode();
            }

            // Recorded unconditionally - diagnostically useful even if the FAILED transition
            // below turns out to be late, since it explains why the job errored at all.
            controlService.logError(runId, e.getMessage(), httpStatus, soapFaultCode);
            boolean failed = controlService.updateStatus(runId, IngestionRunStatus.FAILED);
            if (failed) {
                auditService.logEvent(AuditEventRequest.ingestionFailed(request.requestId(), runId, request.requestSource(), e.getMessage()));
            } else {
                log.warn("Ingestion Job errored but could not transition to FAILED: {} (ID: {}) " +
                        "- run was concurrently canceled", request.methodName(), runId);
                auditService.logEvent(AuditEventRequest.ingestionLateTransitionIgnored(
                        request.requestId(), runId, request.requestSource(),
                        "FAILED", "run was concurrently canceled"));
                outcome = IngestionMetrics.OUTCOME_CANCELED;
            }

        } finally {
            controlService.updateMetrics(runId, context.getRecordsSeen(), context.getRecordsInserted(),
                    context.getRecordsUpdated(), context.getRejectCount());

            persistRejectedRecords(context);

            auditService.logEvent(AuditEventRequest.metricsUpdated(context));
            metrics.recordRunCompleted(runSample, context, outcome);
            org.slf4j.MDC.clear();
        }
    }

    /**
     * Performs the actual data ingestion logic.
     * <p>
     * This is the template method that subclasses must implement to define
     * their specific data extraction and processing logic.
     * <p>
     * The implementation should:
     * <ul>
     *   <li>Fetch data from the external source</li>
     *   <li>Parse and validate records</li>
     *   <li>Transform records to domain entities</li>
     *   <li>Persist entities to database</li>
     *   <li>Update context metrics (seen, inserted, updated, rejected)</li>
     *   <li>Log rejected records with reasons</li>
     * </ul>
     *
     * @param context mutable context containing run metadata and metrics
     * @throws Exception if ingestion fails for any reason
     */
    protected abstract void runIngestion(IngestionContext context) throws Exception;

    /**
     * Validates that rejection counts are within acceptable thresholds.
     * <p>
     * Checks both absolute count and percentage rate of rejections.
     * If either threshold is exceeded, throws an exception which will
     * mark the run as FAILED.
     *
     * @param context the ingestion context with rejection metrics
     * @throws SipsaIngestionException if rejection thresholds are exceeded
     */
    protected void validateThresholds(IngestionContext context) {
        if (context.getRejectCount() > maxRejectCount) {
            throw new SipsaIngestionException("Reject count exceeded threshold: " + context.getRejectCount());
        }

        if (context.getRecordsSeen() > 0) {
            double rate = (double) context.getRejectCount() / context.getRecordsSeen();
            if (rate > maxRejectRate) {
                throw new SipsaIngestionException(
                        "Reject rate " + String.format("%.2f", rate) + " exceeded threshold " + maxRejectRate);
            }
        }
    }

    /**
     * Persists all rejected records to the database.
     * <p>
     * This method is called in the finally block to ensure rejected records
     * are saved even if the ingestion fails. Rejected records are critical
     * for debugging and data quality analysis.
     * <p>
     * Each rejected record includes:
     * <ul>
     *   <li>Raw data that was rejected</li>
     *   <li>Reason for rejection</li>
     *   <li>Whether it was a parse error vs validation error</li>
     * </ul>
     *
     * @param context the ingestion context containing rejected records
     */
    private void persistRejectedRecords(IngestionContext context) {
        if (context.getRejectedRecords().isEmpty()) {
            return;
        }

        try {
            for (IngestionContext.RejectedRecord rejected : context.getRejectedRecords()) {
                controlService.logReject(
                        context.getRunId(),
                        rejected.rawData(),
                        rejected.reason(),
                        rejected.isParseError()
                );
            }
            log.info("Persisted {} rejected records for run {}",
                    context.getRejectedRecords().size(), context.getRunId());
        } catch (Exception e) {
            log.error("Failed to persist rejected records for run {}: {}",
                    context.getRunId(), e.getMessage(), e);
        }
    }
}
