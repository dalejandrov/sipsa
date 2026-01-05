package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generic implementation of {@link IngestionJob} that delegates to registered handlers.
 * <p>
 * This class provides a concrete implementation of the abstract {@link IngestionJob}
 * by delegating the actual ingestion work to method-specific handlers registered
 * in {@link IngestionService}.
 * <p>
 * The delegation pattern allows for:
 * <ul>
 *   <li>Easy addition of new ingestion methods without modifying this class</li>
 *   <li>Clear separation between orchestration (IngestionJob) and execution (handlers)</li>
 *   <li>Reusable ingestion workflow for all methods</li>
 * </ul>
 * <p>
 * This job is injected wherever ingestion needs to be triggered (e.g., controllers,
 * schedulers) and handles all common concerns like window validation, run management,
 * metrics tracking, and error handling.
 *
 * @see IngestionJob
 * @see IngestionService
 * @see IngestionHandler
 */
@Service
public class GenericIngestionJob extends IngestionJob {

    private final IngestionService ingestionService;

    /**
     * Creates the generic ingestion job with all required dependencies.
     * <p>
     * All parameters are injected by Spring and configured via application properties.
     *
     * @param ingestionService service that manages and dispatches to specific handlers
     * @param windowPolicy policy for validating execution time windows
     * @param controlService service for managing run state and lifecycle
     * @param auditService service for logging audit events
     * @param maxRejectRate maximum allowed rejection rate (default: 0.01 = 1%)
     * @param maxRejectCount maximum absolute number of rejections allowed (default: 5000)
     */
    public GenericIngestionJob(IngestionService ingestionService, WindowPolicy windowPolicy,
                               IngestionControlService controlService, IngestionAuditService auditService,
                               @Value("${sipsa.ingestion.max-reject-rate:0.01}") double maxRejectRate,
                               @Value("${sipsa.ingestion.max-reject-count:5000}") int maxRejectCount) {
        super(windowPolicy, controlService, auditService, maxRejectRate, maxRejectCount);
        this.ingestionService = ingestionService;
    }

    /**
     * Delegates ingestion execution to the appropriate handler.
     * <p>
     * The method name from the context is used to look up the registered handler,
     * which then performs the actual data extraction and processing.
     *
     * @param context the ingestion context containing method name and metrics
     * @throws Exception if handler lookup fails or handler execution fails
     */
    @Override
    protected void runIngestion(IngestionContext context) throws Exception {
        ingestionService.execute(context.getMethodName(), context);
    }
}
