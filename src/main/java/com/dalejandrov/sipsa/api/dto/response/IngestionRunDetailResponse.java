package com.dalejandrov.sipsa.api.dto.response;

import java.time.OffsetDateTime;

/**
 * Response DTO for detailed ingestion run information.
 * <p>
 * This DTO provides complete information about an ingestion run,
 * including all metrics and status details.
 * <p>
 * Used in operational endpoints to display full run history with pagination.
 *
 * @param runId unique run identifier
 * @param methodName ingestion method name
 * @param windowKey window key for this run
 * @param status current run status
 * @param startTime run start timestamp
 * @param endTime run end timestamp (null if still running)
 * @param requestId correlation request ID
 * @param requestSource source of the request
 * @param recordsSeen total records seen during ingestion
 * @param recordsInserted new records inserted
 * @param recordsUpdated existing records updated
 * @param rejectCount rejected records count
 */
public record IngestionRunDetailResponse(
    Long runId,
    String methodName,
    String windowKey,
    String status,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    String requestId,
    String requestSource,
    Integer recordsSeen,
    Integer recordsInserted,
    Integer recordsUpdated,
    Integer rejectCount
) {}

