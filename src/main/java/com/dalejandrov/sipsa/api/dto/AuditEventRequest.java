package com.dalejandrov.sipsa.api.dto;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.domain.entity.AuditEventType;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request object for logging audit events.
 * <p>
 * Encapsulates all parameters needed to log an audit event,
 * providing better readability, type safety, and extensibility.
 * <p>
 * This DTO is used by the application layer to pass audit event
 * information to the audit service.
 *
 * @param requestId correlation ID for the request
 * @param runId ingestion run ID (nullable for events before run creation)
 * @param requestSource origin of the request
 * @param eventType type of audit event
 * @param message descriptive message for the event
 */
public record AuditEventRequest(
    @NotBlank(message = "requestId cannot be blank")
    String requestId,

    Long runId,

    @NotNull(message = "requestSource cannot be null")
    RequestSource requestSource,

    @NotNull(message = "eventType cannot be null")
    AuditEventType eventType,

    @NotBlank(message = "message cannot be blank")
    String message
) {
    /**
     * Creates audit event for ingestion start.
     *
     * @param ingestionRequest the ingestion request context
     * @param runId the ingestion run ID
     * @param windowKey the window key
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest ingestionStarted(IngestionRequest ingestionRequest,
                                                     long runId, String windowKey) {
        return new AuditEventRequest(
            ingestionRequest.requestId(),
            runId,
            ingestionRequest.requestSource(),
            AuditEventType.INGESTION_STARTED,
            String.format("Method: %s, Window: %s, Force: %s",
                         ingestionRequest.methodName(), windowKey, ingestionRequest.force())
        );
    }

    /**
     * Creates audit event for ingestion success.
     *
     * @param context the ingestion context with metrics
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest ingestionSucceeded(IngestionContext context) {
        return new AuditEventRequest(
            context.getRequestId(),
            context.getRunId(),
            context.getRequestSource(),
            AuditEventType.INGESTION_SUCCEEDED,
            String.format("Completed successfully - Seen: %d, Inserted: %d, Updated: %d, Rejected: %d",
                         context.getRecordsSeen(), context.getRecordsInserted(),
                         context.getRecordsUpdated(), context.getRejectCount())
        );
    }

    /**
     * Creates audit event for ingestion failure.
     *
     * @param requestId the correlation ID
     * @param runId the ingestion run ID (nullable)
     * @param requestSource the request source
     * @param errorMessage the error message
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest ingestionFailed(String requestId, Long runId,
                                                    RequestSource requestSource, String errorMessage) {
        return new AuditEventRequest(
            requestId,
            runId,
            requestSource,
            AuditEventType.INGESTION_FAILED,
            "Error: " + errorMessage
        );
    }

    /**
     * Creates audit event for request reception.
     *
     * @param requestId the correlation ID
     * @param requestSource the request source
     * @param methodName the ingestion method
     * @param force whether force flag was used
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest requestReceived(String requestId, RequestSource requestSource,
                                                    String methodName, boolean force) {
        return new AuditEventRequest(
            requestId,
            null,
            requestSource,
            AuditEventType.REQUEST_RECEIVED,
            String.format("Method: %s, Force: %s", methodName, force)
        );
    }

    /**
     * Creates audit event for request rejection.
     *
     * @param requestId the correlation ID
     * @param requestSource the request source
     * @param reason the rejection reason
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest requestRejected(String requestId, RequestSource requestSource,
                                                    String reason) {
        return new AuditEventRequest(
            requestId,
            null,
            requestSource,
            AuditEventType.REQUEST_REJECTED,
            reason
        );
    }

    /**
     * Creates audit event for request acceptance.
     *
     * @param requestId the correlation ID
     * @param requestSource the request source
     * @param methodName the ingestion method
     * @param force whether force flag was used
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest requestAccepted(String requestId, RequestSource requestSource,
                                                    String methodName, boolean force) {
        return new AuditEventRequest(
            requestId,
            null,
            requestSource,
            AuditEventType.REQUEST_ACCEPTED,
            String.format("Request accepted for async processing - Method: %s, Force: %s", methodName, force)
        );
    }

    /**
     * Creates audit event for ingestion skip due to window violation.
     *
     * @param request the ingestion request
     * @param reason the skip reason
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest ingestionSkippedWindow(IngestionRequest request, String reason) {
        return new AuditEventRequest(
            request.requestId(),
            null,
            request.requestSource(),
            AuditEventType.INGESTION_SKIPPED_WINDOW,
            "Method: " + request.methodName() + " - " + reason
        );
    }

    /**
     * Creates audit event for ingestion skip due to duplicate run.
     *
     * @param request the ingestion request
     * @param windowKey the window key
     * @param reason additional reason details
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest ingestionSkippedDuplicate(IngestionRequest request, String windowKey, String reason) {
        return new AuditEventRequest(
            request.requestId(),
            null,
            request.requestSource(),
            AuditEventType.INGESTION_SKIPPED_DUPLICATE,
            "Method: " + request.methodName() + ", Window: " + windowKey + " - " + reason
        );
    }

    /**
     * Creates audit event for ingestion skip due to duplicate run (simple version).
     *
     * @param request the ingestion request
     * @param windowKey the window key
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest ingestionSkippedDuplicate(IngestionRequest request, String windowKey) {
        return new AuditEventRequest(
            request.requestId(),
            null,
            request.requestSource(),
            AuditEventType.INGESTION_SKIPPED_DUPLICATE,
            "Method: " + request.methodName() + ", Window: " + windowKey
        );
    }

    /**
     * Creates audit event for ingestion running.
     *
     * @param request the ingestion request
     * @param runId the ingestion run ID
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest ingestionRunning(IngestionRequest request, long runId) {
        return new AuditEventRequest(
            request.requestId(),
            runId,
            request.requestSource(),
            AuditEventType.INGESTION_RUNNING,
            "Starting data ingestion for method: " + request.methodName()
        );
    }

    /**
     * Creates audit event for metrics update.
     *
     * @param context the ingestion context with final metrics
     * @return new AuditEventRequest instance
     */
    public static AuditEventRequest metricsUpdated(IngestionContext context) {
        return new AuditEventRequest(
            context.getRequestId(),
            context.getRunId(),
            context.getRequestSource(),
            AuditEventType.METRICS_UPDATED,
            String.format("Final metrics - Seen: %d, Inserted: %d, Updated: %d, Rejected: %d",
                         context.getRecordsSeen(), context.getRecordsInserted(),
                         context.getRecordsUpdated(), context.getRejectCount())
        );
    }
}
