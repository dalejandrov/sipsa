package com.dalejandrov.sipsa.api.dto;

import com.dalejandrov.sipsa.domain.entity.RequestSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request object for creating an ingestion run.
 * <p>
 * Encapsulates all parameters needed to create a new ingestion run record,
 * providing better readability, type safety, and extensibility.
 * <p>
 * This DTO is used by the application layer to pass run creation
 * information to the control service.
 *
 * @param methodName the ingestion method identifier
 * @param windowKey the window key for this run
 * @param requestId correlation ID for tracking
 * @param requestSource origin of the request
 * @param force whether this run bypasses normal checks
 */
public record CreateRunRequest(
    @NotBlank(message = "methodName cannot be blank")
    String methodName,

    @NotBlank(message = "windowKey cannot be blank")
    String windowKey,

    @NotBlank(message = "requestId cannot be blank")
    String requestId,

    @NotNull(message = "requestSource cannot be null")
    RequestSource requestSource,

    boolean force
) {
    /**
     * Creates a run request from an ingestion request and window key.
     *
     * @param ingestionRequest the ingestion request
     * @param windowKey the validated window key
     * @return new CreateRunRequest instance
     */
    public static CreateRunRequest from(IngestionRequest ingestionRequest, String windowKey) {
        return new CreateRunRequest(
            ingestionRequest.methodName(),
            windowKey,
            ingestionRequest.requestId(),
            ingestionRequest.requestSource(),
            ingestionRequest.force()
        );
    }
}

