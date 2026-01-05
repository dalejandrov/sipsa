package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.api.dto.AuditEventRequest;
import com.dalejandrov.sipsa.api.dto.CreateRunRequest;
import com.dalejandrov.sipsa.api.dto.IngestionRequest;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.exception.WindowViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

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

    // Quality thresholds for rejections
    protected final double maxRejectRate;
    protected final int maxRejectCount;

    /**
     * Creates a new ingestion job with required dependencies and threshold configuration.
     *
     * @param windowPolicy policy for validating execution windows
     * @param controlService service for managing run state
     * @param auditService service for audit event logging
     * @param maxRejectRate maximum allowed rejection rate (0.01 = 1%)
     * @param maxRejectCount maximum number of absolute rejections allowed
     */
    protected IngestionJob(WindowPolicy windowPolicy, IngestionControlService controlService,
                           IngestionAuditService auditService,
                           @Value("${sipsa.ingestion.max-reject-rate:0.01}") double maxRejectRate,
                           @Value("${sipsa.ingestion.max-reject-count:5000}") int maxRejectCount) {
        this.windowPolicy = windowPolicy;
        this.controlService = controlService;
        this.auditService = auditService;
        this.maxRejectRate = maxRejectRate;
        this.maxRejectCount = maxRejectCount;
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
            log.warn("Skipping run for {}: {}", request.methodName(), e.getMessage());
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

        try {
            org.slf4j.MDC.put("runId", String.valueOf(runId));
            org.slf4j.MDC.put("method", request.methodName());
            org.slf4j.MDC.put("windowKey", windowKey);
            org.slf4j.MDC.put("requestId", request.requestId());
            org.slf4j.MDC.put("requestSource", request.requestSource().name());

            controlService.updateStatus(runId, "RUNNING");
            auditService.logEvent(AuditEventRequest.ingestionRunning(request, runId));
            log.info("Started Ingestion Job: {} (ID: {})", request.methodName(), runId);

            runIngestion(context);
            validateThresholds(context);

            controlService.updateStatus(runId, "SUCCEEDED");
            auditService.logEvent(AuditEventRequest.ingestionSucceeded(context));
            log.info("Ingestion Job SUCCEEDED: {} (ID: {}). Stats: {}", request.methodName(), runId, context.toLogSummary());

        } catch (Exception e) {
            log.error("Ingestion Job FAILED: {} (ID: {})", request.methodName(), runId, e);
            controlService.logError(runId, e.getMessage(), null, null);
            controlService.updateStatus(runId, "FAILED");
            auditService.logEvent(AuditEventRequest.ingestionFailed(request.requestId(), runId, request.requestSource(), e.getMessage()));

        } finally {
            controlService.updateMetrics(runId, context.getRecordsSeen(), context.getRecordsInserted(),
                    context.getRecordsUpdated(), context.getRejectCount());

            persistRejectedRecords(context);

            auditService.logEvent(AuditEventRequest.metricsUpdated(context));
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
