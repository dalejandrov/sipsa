package com.dalejandrov.sipsa.domain.entity;

/**
 * Enumeration of request sources for ingestion operations.
 * <p>
 * This enum identifies the origin of an ingestion request, allowing the system
 * to differentiate between manual API calls, scheduled jobs, and system-initiated
 * processes. This information is crucial for:
 * <ul>
 *   <li>Audit trails and compliance reporting</li>
 *   <li>Performance analysis by request type</li>
 *   <li>Filtering and querying historical data</li>
 *   <li>Monitoring and alerting (different thresholds per source)</li>
 * </ul>
 * <p>
 * The request source is:
 * <ul>
 *   <li>Stored in both {@link IngestionRun} and {@link IngestionAudit} tables</li>
 *   <li>Indexed for efficient querying</li>
 *   <li>Included in all audit events for the request</li>
 *   <li>Visible in API responses and logs</li>
 * </ul>
 * <p>
 * <b>Usage Examples:</b>
 * <ul>
 *   <li>Manual API call: {@code POST /internal/ingestion/run} → MANUAL</li>
 *   <li>Scheduled cron job: {@code @Scheduled} methods → SCHEDULED</li>
 *   <li>Internal trigger: Recovery jobs, maintenance → SYSTEM</li>
 * </ul>
 *
 * @see IngestionRun
 * @see IngestionAudit
 * @see com.dalejandrov.sipsa.application.ingestion.core.IngestionContext
 */
public enum RequestSource {
    /**
     * Request initiated manually via REST API.
     * <p>
     * This source indicates the ingestion was triggered by:
     * <ul>
     *   <li>Human operator via API call</li>
     *   <li>External system integration</li>
     *   <li>DevOps tooling (curl, Postman, etc.)</li>
     *   <li>CI/CD pipeline</li>
     * </ul>
     * <p>
     * Manual requests typically include {@code force=true} to bypass
     * window and duplicate checks.
     */
    MANUAL,

    /**
     * Request initiated automatically by the scheduler.
     * <p>
     * This source indicates the ingestion was triggered by:
     * <ul>
     *   <li>Spring {@code @Scheduled} cron jobs</li>
     *   <li>Daily window at 14:20 (Ciudad, Parcial, Semana)</li>
     *   <li>Monthly runs on day 8 (MesMadr) and day 10 (AbasMes)</li>
     * </ul>
     * <p>
     * Scheduled requests always use {@code force=false} and respect
     * window validation rules.
     */
    SCHEDULED,

    /**
     * Request initiated by internal system processes.
     * <p>
     * This source indicates the ingestion was triggered by:
     * <ul>
     *   <li>Recovery jobs after failures</li>
     *   <li>Data backfill operations</li>
     *   <li>Maintenance and cleanup tasks</li>
     *   <li>Internal system events</li>
     * </ul>
     * <p>
     * System requests may use either {@code force=true} or {@code force=false}
     * depending on the specific use case.
     */
    SYSTEM
}

