package com.dalejandrov.sipsa.api.dto.response;

import java.time.OffsetDateTime;

/**
 * Response DTO for ingestion run summaries.
 * <p>
 * This DTO provides a lightweight representation of an ingestion run,
 * suitable for listing active or recent runs without full details.
 * <p>
 * Used in operational endpoints to display running ingestion processes.
 *
 * @param runId unique run identifier
 * @param methodName ingestion method name
 * @param windowKey window key for this run
 * @param status current run status
 * @param startTime run start timestamp
 * @param requestId correlation request ID
 */
public record IngestionRunResponse(
    Long runId,
    String methodName,
    String windowKey,
    String status,
    OffsetDateTime startTime,
    String requestId
) {}
