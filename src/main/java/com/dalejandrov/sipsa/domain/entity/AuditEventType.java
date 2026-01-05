package com.dalejandrov.sipsa.domain.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumeration of audit event types that can occur during SIPSA ingestion processes.
 * <p>
 * These events represent key milestones and state transitions in the ingestion lifecycle,
 * from initial request receipt through final completion or failure. They enable:
 * <ul>
 *   <li>Complete request traceability</li>
 *   <li>Debugging of failed ingestions</li>
 *   <li>Performance monitoring and analysis</li>
 *   <li>Compliance and operational transparency</li>
 * </ul>
 * <p>
 * Events are categorized into:
 * <ul>
 *   <li><b>Request Lifecycle:</b> HTTP request handling and validation</li>
 *   <li><b>Ingestion Lifecycle:</b> Core processing states</li>
 *   <li><b>Data Processing:</b> Record-level operations</li>
 *   <li><b>Errors:</b> Different failure types</li>
 *   <li><b>System:</b> Administrative and operational events</li>
 * </ul>
 * <p>
 * Each event includes a human-readable description that can be used in
 * logs, dashboards, and audit reports.
 *
 * @see IngestionAudit
 * @see com.dalejandrov.sipsa.application.service.IngestionAuditService
 */
@Getter
@RequiredArgsConstructor
public enum AuditEventType {

    /** Initial HTTP request received from API endpoint */
    REQUEST_RECEIVED("Request received from API"),

    /** Request passed validation and accepted for async processing */
    REQUEST_ACCEPTED("Request accepted for async processing"),

    /** Request rejected due to validation failures (invalid method, missing params, etc.) */
    REQUEST_REJECTED("Request rejected due to validation"),

    /** Ingestion run created and process initiated */
    INGESTION_STARTED("Ingestion process started"),

    /** Ingestion actively processing data */
    INGESTION_RUNNING("Ingestion process running"),

    /** Ingestion completed successfully with all data processed */
    INGESTION_SUCCEEDED("Ingestion completed successfully"),

    /** Ingestion failed due to error (see error message for details) */
    INGESTION_FAILED("Ingestion failed with error"),

    /** Ingestion skipped because execution is outside allowed time window */
    INGESTION_SKIPPED_WINDOW("Ingestion skipped - outside window"),

    /** Ingestion skipped because data already successfully ingested for this window */
    INGESTION_SKIPPED_DUPLICATE("Ingestion skipped - already completed"),

    /** Batch of records successfully processed (milestone marker) */
    RECORDS_PROCESSED("Batch of records processed"),

    /** Individual record inserted into database */
    RECORD_INSERTED("Record inserted"),

    /** Existing record updated with new data */
    RECORD_UPDATED("Record updated"),

    /** Record rejected due to validation failure */
    RECORD_REJECTED("Record rejected"),

    /** Validation error occurred (business rules violated) */
    ERROR_VALIDATION("Validation error"),

    /** Parsing error occurred (malformed XML or data) */
    ERROR_PARSE("Parse error"),

    /** Database operation failed (connection, constraint, etc.) */
    ERROR_DATABASE("Database error"),

    /** SOAP service call failed (network, timeout, fault) */
    ERROR_SOAP("SOAP service error"),

    /** Quality threshold exceeded (too many rejections) */
    ERROR_THRESHOLD("Threshold exceeded"),

    /** Existing run was forced to restart (force=true parameter used) */
    FORCE_RESTART("Forced restart of existing run"),

    /** Final metrics updated for the run (seen, inserted, updated, rejected) */
    METRICS_UPDATED("Metrics updated");

    /** Human-readable description of this audit event type */
    private final String description;
}

