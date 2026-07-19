package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.dto.request.IngestionTriggerRequest;
import com.dalejandrov.sipsa.api.dto.response.IngestionCancelResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionMethodsResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionTriggerResponse;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionRunQueryService;
import com.dalejandrov.sipsa.application.service.IngestionTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for internal operational actions related to SIPSA ingestion.
 * <p>
 * <b>Controller Responsibilities (HTTP Layer):</b>
 * <ul>
 *   <li>Handle HTTP requests and responses</li>
 *   <li>Validate request DTOs with Jakarta Validation</li>
 *   <li>Delegate operations to service layer</li>
 *   <li>Return appropriate HTTP status codes</li>
 * </ul>
 * <p>
 * Security: every endpoint here requires a Cognito access token with the matching
 * {@code sipsa/ingestion.*} scope, enforced by
 * {@code infrastructure.config.security.SecurityConfig} (ADR-002, TECH-001).
 */
@RestController
@RequestMapping("/api/internal/ingestion")
@Slf4j
@RequiredArgsConstructor
public class SipsaOpsController {

    private final IngestionTriggerService triggerService;
    private final IngestionControlService controlService;
    private final IngestionRunQueryService runQueryService;

    /**
     * Triggers an ingestion job asynchronously.
     * <p>
     * <b>Controller Role:</b> Validate parameters, build request DTO, delegate to service, return HTTP 202.
     * <p>
     * This endpoint accepts query parameters for easier manual testing and scheduled job integration.
     * Use this for operational triggers (DevOps, scheduled tasks).
     *
     * @param method the ingestion method name (e.g., "promediosSipsaCiudad")
     * @param force whether to force ingestion (bypasses window checks), default false
     * @return HTTP 202 if the job is accepted, HTTP 400 for invalid requests
     */
    @PostMapping("/run")
    public ResponseEntity<IngestionTriggerResponse> triggerIngestion(
            @RequestParam String method,
            @RequestParam(defaultValue = "false") boolean force) {

        IngestionTriggerRequest request = new IngestionTriggerRequest(
                method,
                force,
                com.dalejandrov.sipsa.domain.entity.RequestSource.MANUAL
        );

        IngestionTriggerResponse response = triggerService.triggerIngestion(request);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Lists all available ingestion methods.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP response.
     *
     * @return list of available method names
     */
    @GetMapping("/methods")
    public ResponseEntity<IngestionMethodsResponse> getAvailableMethods() {
        IngestionMethodsResponse response = runQueryService.getAvailableMethods();
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels an active ingestion run.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP response.
     *
     * @param runId the run identifier to cancel
     * @return HTTP 200 if canceled
     */
    @PostMapping("/cancel/{runId}")
    public ResponseEntity<IngestionCancelResponse> cancelRun(@PathVariable Long runId) {
        controlService.cancelRun(runId);
        return ResponseEntity.ok(new IngestionCancelResponse(runId, "CANCELED"));
    }

    /**
     * Lists all currently active ingestion runs.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP response.
     *
     * @return list of active runs
     */
    @GetMapping("/running")
    public ResponseEntity<List<IngestionRunResponse>> getActiveRuns() {
        List<IngestionRunResponse> response = runQueryService.getActiveRuns();
        return ResponseEntity.ok(response);
    }

    /**
     * Lists all ingestion runs.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP response.
     *
     * @return list of all runs with full details
     */
    @GetMapping("/runs")
    public ResponseEntity<List<IngestionRunDetailResponse>> getAllRuns() {
        List<IngestionRunDetailResponse> response = runQueryService.getAllRuns();
        return ResponseEntity.ok(response);
    }

    /**
     * Gets the status of a specific ingestion run.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     * Service throws exception if not found (handled by GlobalExceptionHandler).
     *
     * @param runId the run identifier
     * @return HTTP 200 with run details
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<IngestionRunDetailResponse> getRunStatus(@PathVariable Long runId) {
        IngestionRunDetailResponse response = runQueryService.getRunStatus(runId);
        return ResponseEntity.ok(response);
    }
}
