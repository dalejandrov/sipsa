package com.dalejandrov.sipsa.api.dto.response;

/**
 * Response DTO for ingestion cancellation operations.
 */
public record IngestionCancelResponse(
    Long runId,
    String status
) {}
