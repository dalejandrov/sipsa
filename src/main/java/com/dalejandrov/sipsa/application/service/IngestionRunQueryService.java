package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.response.IngestionMethodsResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunResponse;
import com.dalejandrov.sipsa.api.mapper.IngestionAuditMapper;
import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for managing and querying ingestion runs.
 * <p>
 * <b>Service Responsibilities:</b>
 * <ul>
 *   <li>Coordinate with IngestionControlService for run operations</li>
 *   <li>Coordinate with IngestionService for method queries</li>
 *   <li>Map entities to response DTOs</li>
 *   <li>Throw business exceptions when data not found</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionRunQueryService {

    private final IngestionControlService controlService;
    private final IngestionService ingestionService;
    private final IngestionAuditMapper mapper;

    /**
     * Retrieves all available ingestion method names.
     *
     * @return response with method names and count
     */
    @Transactional(readOnly = true)
    public IngestionMethodsResponse getAvailableMethods() {
        log.debug("Retrieving available ingestion methods");
        var methods = ingestionService.getAvailableMethodNames();
        return new IngestionMethodsResponse(methods, methods.size());
    }

    /**
     * Retrieves all currently active ingestion runs.
     *
     * @return list of active run responses
     */
    @Transactional(readOnly = true)
    public List<IngestionRunResponse> getActiveRuns() {
        log.debug("Retrieving active ingestion runs");
        List<IngestionRun> runs = controlService.findActiveRuns();
        return runs.stream()
                .map(mapper::toDto)
                .toList();
    }

    /**
     * Retrieves all ingestion runs with full details.
     *
     * @return list of detailed run responses
     */
    @Transactional(readOnly = true)
    public List<IngestionRunDetailResponse> getAllRuns() {
        log.debug("Retrieving all ingestion runs");
        List<IngestionRun> runs = controlService.findAllRuns();
        return runs.stream()
                .map(mapper::toDetailDto)
                .toList();
    }

    /**
     * Retrieves the status and details of a specific ingestion run.
     *
     * @param runId the run identifier
     * @return run detail response
     * @throws SipsaNotFoundException if run not found
     */
    @Transactional(readOnly = true)
    public IngestionRunDetailResponse getRunStatus(Long runId) {
        log.debug("Retrieving status for runId={}", runId);
        return controlService.getRun(runId)
                .map(mapper::toDetailDto)
                .orElseThrow(() -> {
                    log.warn("Run not found: runId={}", runId);
                    return new SipsaNotFoundException("Ingestion run not found: " + runId);
                });
    }
}

