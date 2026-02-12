package com.dalejandrov.sipsa.api.dto.request;

import com.dalejandrov.sipsa.domain.entity.RequestSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for triggering ingestion processes.
 * <p>
 * Encapsulates the parameters needed to trigger an ingestion job,
 * providing type safety and validation.
 */
public record IngestionTriggerRequest(
    @NotBlank(message = "method cannot be blank")
    String method,

    boolean force,

    @NotNull(message = "requestSource cannot be null")
    RequestSource requestSource
) {
    public IngestionTriggerRequest {
        if (method != null) {
            method = method.trim();
        }
        if (requestSource == null) {
            requestSource = RequestSource.MANUAL;
        }
    }
}

