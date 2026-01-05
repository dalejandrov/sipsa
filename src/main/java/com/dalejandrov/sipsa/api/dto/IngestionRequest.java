package com.dalejandrov.sipsa.api.dto;

import com.dalejandrov.sipsa.domain.entity.RequestSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request object for initiating an ingestion job.
 * <p>
 * Encapsulates all parameters needed to start an ingestion process,
 * providing better readability, type safety, and extensibility.
 * <p>
 * This DTO is used by the API layer to pass ingestion requests
 * to the application layer services.
 *
 * @param methodName the ingestion method identifier (e.g., "promediosSipsaCiudad")
 * @param force if true, bypasses window checks and duplicate prevention
 * @param requestId unique correlation ID for tracking (UUID format)
 * @param requestSource origin of the request (MANUAL, SCHEDULED, SYSTEM)
 */
public record IngestionRequest(
    @NotBlank(message = "methodName cannot be blank")
    String methodName,

    boolean force,

    @NotBlank(message = "requestId cannot be blank")
    String requestId,

    @NotNull(message = "requestSource cannot be null")
    RequestSource requestSource
) {
    /**
     * Creates a standard manual ingestion request.
     *
     * @param methodName the ingestion method
     * @param requestId the correlation ID
     * @return new IngestionRequest instance
     */
    public static IngestionRequest manual(String methodName, String requestId) {
        return new IngestionRequest(methodName, false, requestId, RequestSource.MANUAL);
    }

    /**
     * Creates a forced manual ingestion request (bypasses window checks).
     *
     * @param methodName the ingestion method
     * @param requestId the correlation ID
     * @return new IngestionRequest instance
     */
    public static IngestionRequest manualForced(String methodName, String requestId) {
        return new IngestionRequest(methodName, true, requestId, RequestSource.MANUAL);
    }

    /**
     * Creates a scheduled ingestion request.
     *
     * @param methodName the ingestion method
     * @param requestId the correlation ID
     * @return new IngestionRequest instance
     */
    public static IngestionRequest scheduled(String methodName, String requestId) {
        return new IngestionRequest(methodName, false, requestId, RequestSource.SCHEDULED);
    }
}

