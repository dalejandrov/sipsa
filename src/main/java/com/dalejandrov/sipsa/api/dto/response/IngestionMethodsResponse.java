package com.dalejandrov.sipsa.api.dto.response;

import java.util.Set;

/**
 * Response DTO for available ingestion methods.
 */
public record IngestionMethodsResponse(
    Set<String> methods,
    int count
) {}
