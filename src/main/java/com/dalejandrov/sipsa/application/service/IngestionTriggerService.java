package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.application.command.AuditEventRequest;
import com.dalejandrov.sipsa.application.command.IngestionRequest;
import com.dalejandrov.sipsa.api.dto.request.IngestionTriggerRequest;
import com.dalejandrov.sipsa.api.dto.response.IngestionTriggerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application service for handling ingestion trigger operations.
 * <p>
 * This service orchestrates the complete ingestion trigger workflow,
 * including validation, audit logging, and async execution.
 * It follows the Application Service pattern, coordinating domain services.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IngestionTriggerService {

    private final IngestionService ingestionService;
    private final IngestionAuditService auditService;
    private final AsyncIngestionService asyncIngestionService;

    /**
     * Triggers an ingestion process asynchronously.
     * <p>
     * This method handles the complete workflow:
     * <ol>
     *   <li>Generates a unique request ID</li>
     *   <li>Logs the request reception</li>
     *   <li>Validates the request parameters</li>
     *   <li>Logs request acceptance</li>
     *   <li>Triggers async execution</li>
     * </ol>
     *
     * @param request the ingestion trigger request
     * @return response with request details
     */
    public IngestionTriggerResponse triggerIngestion(IngestionTriggerRequest request) {
        String requestId = UUID.randomUUID().toString();

        log.info("Ingestion request received requestId={} method={} force={}",
                requestId, request.method(), request.force());

        // Log request reception
        auditService.logEventSync(AuditEventRequest.requestReceived(
            requestId, request.requestSource(), request.method(), request.force()));

        // Validate request
        ingestionService.validateTriggerRequest(request.method());

        // Log request acceptance
        auditService.logEvent(AuditEventRequest.requestAccepted(
            requestId, request.requestSource(), request.method(), request.force()));

        // Create ingestion request and execute async
        IngestionRequest ingestionRequest = request.force() ?
            IngestionRequest.manualForced(request.method(), requestId) :
            IngestionRequest.manual(request.method(), requestId);

        asyncIngestionService.executeAsync(ingestionRequest);

        return new IngestionTriggerResponse(
            requestId,
            "ACCEPTED",
            request.method(),
            request.force()
        );
    }
}
