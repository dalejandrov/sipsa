package com.dalejandrov.sipsa.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for ingestion trigger operations.
 * <p>
 * Contains the result of triggering an ingestion process,
 * including the request ID and status information.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IngestionTriggerResponse(
    String requestId,
    String status,
    String method,
    Boolean force
) {}
