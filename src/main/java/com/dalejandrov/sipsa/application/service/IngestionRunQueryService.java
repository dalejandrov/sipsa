package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.request.IngestionRunQueryRequest;
import com.dalejandrov.sipsa.api.dto.response.IngestionMethodsResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunResponse;
import com.dalejandrov.sipsa.api.mapper.IngestionAuditMapper;
import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import com.dalejandrov.sipsa.infrastructure.config.PaginationConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final PaginationConfig paginationConfig;

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
     * Retrieves ingestion runs with full details, paginated (TECH-054).
     * <p>
     * {@code page}/{@code size} follow the same {@link PaginationConfig} convention as
     * every other paginated endpoint (1-based page, size clamped to {@code [1, 100]} by
     * {@link IngestionRunQueryRequest} itself, matching {@code maxUserPageSize}). The
     * sort order is fixed —
     * {@code startTime DESC, runId DESC} — not client-configurable: {@code runId} is
     * the deterministic tie-breaker for runs whose {@code startTime} collides (e.g. the
     * daily window's three sequential-but-fast dispatches), which a single-column sort
     * cannot guarantee. {@link PaginationConfig#buildPageable} only supports a single
     * sort field, so it is used here just for its existing page/size defaulting and
     * validation — the actual {@link Pageable} passed to the repository is rebuilt with
     * the two-column {@link Sort} on top of that same page/size.
     *
     * @param request page/size parameters (already defaulted/clamped by the DTO itself)
     * @return a page of detailed run responses
     */
    @Transactional(readOnly = true)
    public Page<IngestionRunDetailResponse> getAllRuns(IngestionRunQueryRequest request) {
        log.debug("Retrieving ingestion runs: page={}, size={}", request.page(), request.size());

        Pageable unsorted = paginationConfig.buildPageable(request.page(), request.size(), null);
        paginationConfig.validatePageable(unsorted);
        Pageable pageable = PageRequest.of(unsorted.getPageNumber(), unsorted.getPageSize(),
                Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("runId")));

        Page<IngestionRun> runs = controlService.findAllRuns(pageable);
        return runs.map(mapper::toDetailDto);
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

