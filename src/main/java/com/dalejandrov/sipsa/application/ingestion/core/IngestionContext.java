package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.domain.entity.RequestSource;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable context object tracking the state and metrics of a single ingestion run.
 * <p>
 * This class serves as a data container that is passed through the ingestion
 * pipeline, accumulating metrics and rejected records as processing progresses.
 * It provides a lightweight way to share state without requiring excessive
 * method parameters.
 * <p>
 * <b>Immutable fields (set at creation):</b>
 * <ul>
 *   <li>{@code runId} - Database identifier for this run</li>
 *   <li>{@code methodName} - Ingestion method being executed</li>
 *   <li>{@code windowKey} - Time window key (e.g., "2026-01-02")</li>
 *   <li>{@code requestId} - Correlation ID from the original request</li>
 *   <li>{@code requestSource} - Origin of the request (MANUAL/SCHEDULED/SYSTEM)</li>
 * </ul>
 * <p>
 * <b>Mutable fields (updated during processing):</b>
 * <ul>
 *   <li>Record counters (seen, inserted, updated, rejected)</li>
 *   <li>List of rejected records with reasons</li>
 * </ul>
 * <p>
 * <b>Thread Safety:</b> This class is NOT thread-safe. It's designed to be used
 * within a single ingestion execution thread.
 *
 * @see IngestionJob
 */
@Data
@RequiredArgsConstructor
public class IngestionContext {
    // Immutable context fields
    private final long runId;
    private final String methodName;
    private final String windowKey;
    private final String requestId;
    private final RequestSource requestSource;

    // Mutable metric counters
    private int recordsSeen = 0;
    private int recordsInserted = 0;
    private int recordsUpdated = 0;
    private int rejectCount = 0;
    private int parseErrors = 0;

    // Collection of rejected records with details
    private final List<RejectedRecord> rejectedRecords = new ArrayList<>();

    /**
     * Increments the count of records seen during processing.
     * <p>
     * This should be called for every record encountered, regardless
     * of whether it was successfully processed or rejected.
     */
    public void incrementSeen() {
        recordsSeen++;
    }

    /**
     * Increments the count of records inserted into the database.
     * <p>
     * This should be called when a new record is created in the database.
     */
    public void incrementInserted() {
        recordsInserted++;
    }

    /**
     * Increments the count of existing records updated in the database.
     * <p>
     * This should be called when an existing record is modified with new data.
     */
    public void incrementUpdated() {
        recordsUpdated++;
    }

    /**
     * Increments the count of rejected records.
     * <p>
     * This is automatically called by {@link #addRejectedRecord}, so it should
     * only be called directly if you're not storing the rejection details.
     */
    public void incrementReject() {
        rejectCount++;
    }

    /**
     * Increments the count of parse errors encountered.
     * <p>
     * Parse errors are a subset of rejections caused by malformed data
     * that couldn't be parsed into the expected format.
     */
    public void incrementParseError() {
        parseErrors++;
    }

    /**
     * Records a rejected record with its raw data and reason for rejection.
     * <p>
     * This method stores the complete rejection details so they can be
     * persisted to the {@code ingestion_rejects} table later. It automatically
     * increments the {@code rejectCount}.
     * <p>
     * The rejection information is essential for:
     * <ul>
     *   <li>Debugging data quality issues</li>
     *   <li>Understanding why records were excluded</li>
     *   <li>Identifying patterns in bad data</li>
     *   <li>Reprocessing after fixes</li>
     * </ul>
     *
     * @param rawData the raw data that was rejected (e.g., XML fragment, JSON)
     * @param reason human-readable explanation of why it was rejected
     * @param isParseError true if rejection was due to parsing failure, false for validation failure
     */
    public void addRejectedRecord(String rawData, String reason, boolean isParseError) {
        rejectedRecords.add(new RejectedRecord(rawData, reason, isParseError));
        rejectCount++;
    }

    /**
     * Records a rejected record due to validation failure (not a parse error).
     * <p>
     * This is a convenience method equivalent to calling
     * {@link #addRejectedRecord(String, String, boolean)} with {@code isParseError=false}.
     * <p>
     * Use this for records that parsed successfully but failed business validation,
     * such as missing required fields or invalid value ranges.
     *
     * @param rawData the raw data that was rejected
     * @param reason human-readable explanation of the validation failure
     */
    public void addRejectedRecord(String rawData, String reason) {
        addRejectedRecord(rawData, reason, false);
    }

    /**
     * Generates a concise summary of ingestion metrics for logging.
     * <p>
     * This method avoids including the full {@code rejectedRecords} list
     * to prevent log bloat when there are many rejections. The rejected
     * records are stored in the database and can be queried separately.
     *
     * @return Summary string with key metrics only
     */
    public String toLogSummary() {
        return String.format(
            "IngestionContext(runId=%d, methodName=%s, windowKey=%s, requestId=%s, " +
            "requestSource=%s, recordsSeen=%d, recordsInserted=%d, recordsUpdated=%d, " +
            "rejectCount=%d, parseErrors=%d)",
            runId, methodName, windowKey, requestId, requestSource,
            recordsSeen, recordsInserted, recordsUpdated, rejectCount, parseErrors
        );
    }

    /**
     * Immutable record representing a rejected record with its details.
     * <p>
     * This record is stored in memory during ingestion and persisted
     * to the {@code ingestion_rejects} table at the end of the run.
     *
     * @param rawData the raw data that failed processing
     * @param reason explanation of why it was rejected
     * @param isParseError whether this was a parsing error vs validation error
     */
    public record RejectedRecord(String rawData, String reason, boolean isParseError) {
    }
}

