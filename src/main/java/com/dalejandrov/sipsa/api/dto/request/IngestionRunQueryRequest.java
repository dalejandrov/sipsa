package com.dalejandrov.sipsa.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request DTO for paginated ingestion run listing (TECH-054).
 * <p>
 * No filters — {@code GET /api/internal/ingestion/runs} lists every run, most recent
 * first. Only {@code page}/{@code size} are client-configurable, following the same
 * validation and default-clamping convention as {@link CiudadQueryRequest} and its
 * siblings (1-based {@code page}, {@code size} clamped to {@code [1, 100]}). The sort
 * order itself is fixed server-side ({@code startTime DESC, runId DESC} — see
 * {@code IngestionRunQueryService#getAllRuns}): the story calls for a stable order, not
 * a client-configurable one, so there is deliberately no {@code sort} field here.
 */
public record IngestionRunQueryRequest(
    @Min(value = 1, message = "page must be >= 1")
    Integer page,

    @Min(value = 1, message = "size must be >= 1")
    @Max(value = 100, message = "size must be <= 100")
    Integer size
) {
    public IngestionRunQueryRequest {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;
        if (size > 100) size = 100;
    }
}
