package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.dto.AuditEventRequest;
import com.dalejandrov.sipsa.api.dto.IngestionRequest;
import com.dalejandrov.sipsa.application.service.AsyncIngestionService;
import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionService;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * REST controller for internal operational actions related to SIPSA ingestion.
 * <p>
 * This controller exposes endpoints intended for internal use only
 * (e.g. DevOps, scheduled jobs, or operational tooling).
 * <p>
 * IMPORTANT:
 * This controller MUST be protected in production environments
 * (e.g. Spring Security, IP allowlist, internal network only).
 */
@RestController
@RequestMapping("/internal/ingestion")
@Slf4j
@RequiredArgsConstructor
public class SipsaOpsController {

    private final AsyncIngestionService asyncIngestionService;
    private final IngestionService ingestionService;
    private final IngestionAuditService auditService;

    /**
     * Triggers an ingestion job asynchronously.
     * <p>
     * The request returns immediately with HTTP 202 (Accepted),
     * while the ingestion process is executed in the background.
     * <p>
     * Use the {@code force} parameter to control whether scheduler
     * checks should be bypassed during execution.
     *
     * @param method the ingestion method identifier to execute
     *               (must be one of the available methods)
     * @param force  whether to bypass scheduler checks and force execution
     * @return HTTP 202 if the job is accepted, HTTP 400 for invalid requests
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> triggerIngestion(
            @RequestParam String method,
            @RequestParam(defaultValue = "false") boolean force) {

        String requestId = UUID.randomUUID().toString();

        log.info(
                "Ingestion request received requestId={} method={} force={}",
                requestId, method, force
        );

        auditService.logEventSync(AuditEventRequest.requestReceived(requestId, RequestSource.MANUAL, method, force));

        if (method == null || method.isBlank()) {
            auditService.logEventSync(AuditEventRequest.requestRejected(requestId, RequestSource.MANUAL,
                    "Method parameter is required and cannot be blank"));
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Method parameter is required and cannot be blank",
                    "availableMethods", ingestionService.getAvailableMethodNames(),
                    "requestId", requestId
            ));
        }

        if (!ingestionService.isValidMethod(method)) {
            auditService.logEventSync(AuditEventRequest.requestRejected(requestId, RequestSource.MANUAL,
                    "Invalid method: " + method));
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid method: " + method,
                    "availableMethods", ingestionService.getAvailableMethodNames(),
                    "requestId", requestId
            ));
        }

        auditService.logEvent(AuditEventRequest.requestAccepted(requestId, RequestSource.MANUAL, method, force));

        IngestionRequest ingestionRequest = force ?
            IngestionRequest.manualForced(method, requestId) :
            IngestionRequest.manual(method, requestId);
        asyncIngestionService.executeAsync(ingestionRequest);

        return ResponseEntity.accepted().body(Map.of(
                "requestId", requestId,
                "status", "ACCEPTED",
                "method", method,
                "force", force
        ));
    }

    /**
     * Lists all available ingestion methods.
     *
     * @return list of available method names
     */
    @GetMapping("/methods")
    public ResponseEntity<Map<String, Object>> getAvailableMethods() {
        Set<String> methods = ingestionService.getAvailableMethodNames();

        return ResponseEntity.ok(Map.of(
                "methods", methods,
                "count", methods.size()
        ));
    }
}
