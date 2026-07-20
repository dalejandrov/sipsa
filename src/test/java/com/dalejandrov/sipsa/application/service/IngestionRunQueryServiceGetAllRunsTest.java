package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.request.IngestionRunQueryRequest;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.api.mapper.IngestionAuditMapper;
import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import com.dalejandrov.sipsa.infrastructure.config.PaginationConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TECH-054: {@link IngestionRunQueryService#getAllRuns(IngestionRunQueryRequest)} —
 * end-to-end paginated flow (no {@code findAll()} then in-memory sublist anywhere).
 * Real MVC dispatch and a real PostgreSQL query are covered separately
 * ({@code SipsaOpsControllerRunsPaginationTest},
 * {@code IngestionRunRepositoryPaginationTest}); this class isolates the service
 * layer's own responsibility — building the right {@link Pageable} (fixed
 * {@code startTime DESC, runId DESC} sort, not client-configurable) and mapping the
 * returned {@link Page} of entities to a {@link Page} of DTOs — with
 * {@link IngestionControlService} mocked.
 */
@DisplayName("IngestionRunQueryService.getAllRuns — paginated flow")
class IngestionRunQueryServiceGetAllRunsTest {

    private final IngestionControlService controlService = mock(IngestionControlService.class);
    private final IngestionService ingestionService = mock(IngestionService.class);
    private final IngestionAuditMapper mapper = mock(IngestionAuditMapper.class);
    private final PaginationConfig paginationConfig = new PaginationConfig();
    private final IngestionRunQueryService service =
            new IngestionRunQueryService(controlService, ingestionService, mapper, paginationConfig);

    private static IngestionRun run(long id) {
        return IngestionRun.builder().runId(id).methodName("promediosSipsaCiudad").build();
    }

    private static IngestionRunDetailResponse dtoFor(IngestionRun run) {
        return new IngestionRunDetailResponse(run.getRunId(), run.getMethodName(), "2026-07-20",
                "SUCCEEDED", null, null, "req-1", "MANUAL", 10, 10, 0, 0);
    }

    @Test
    @DisplayName("empty page: no error, empty content, zero totals")
    void emptyPage() {
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        Page<IngestionRunDetailResponse> result = service.getAllRuns(new IngestionRunQueryRequest(1, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    @DisplayName("first page: page=1 maps to Pageable page index 0")
    void firstPage() {
        IngestionRun run1 = run(1L);
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run1), PageRequest.of(0, 20), 45));
        when(mapper.toDetailDto(run1)).thenReturn(dtoFor(run1));

        service.getAllRuns(new IngestionRunQueryRequest(1, 20));

        var captor = forClass(Pageable.class);
        verify(controlService).findAllRuns(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("intermediate page: page=3 maps to Pageable page index 2")
    void intermediatePage() {
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 10), 45));

        service.getAllRuns(new IngestionRunQueryRequest(3, 10));

        var captor = forClass(Pageable.class);
        verify(controlService).findAllRuns(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("last page: fewer results than page size, still valid")
    void lastPage() {
        IngestionRun run1 = run(41L);
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run1), PageRequest.of(4, 10), 41));
        when(mapper.toDetailDto(run1)).thenReturn(dtoFor(run1));

        Page<IngestionRunDetailResponse> result = service.getAllRuns(new IngestionRunQueryRequest(5, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(41);
        assertThat(result.getTotalPages()).isEqualTo(5);
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("stable order: Pageable always carries startTime DESC, runId DESC, never client-configurable")
    void stableOrder() {
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.getAllRuns(new IngestionRunQueryRequest(1, 20));

        var captor = forClass(Pageable.class);
        verify(controlService).findAllRuns(captor.capture());
        Sort sort = captor.getValue().getSort();
        assertThat(sort.getOrderFor("startTime")).isNotNull();
        assertThat(sort.getOrderFor("startTime").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("runId")).isNotNull();
        assertThat(sort.getOrderFor("runId").getDirection()).isEqualTo(Sort.Direction.DESC);
        // startTime must be evaluated before runId - the tie-breaker only matters
        // when startTime values collide.
        assertThat(sort.stream().map(Sort.Order::getProperty).toList())
                .containsExactly("startTime", "runId");
    }

    @Test
    @DisplayName("entity to DTO mapping: each entity in the page is mapped via IngestionAuditMapper")
    void entityToDtoMapping() {
        IngestionRun run1 = run(1L);
        IngestionRun run2 = run(2L);
        IngestionRunDetailResponse dto1 = dtoFor(run1);
        IngestionRunDetailResponse dto2 = dtoFor(run2);
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(run1, run2), PageRequest.of(0, 20), 2));
        when(mapper.toDetailDto(run1)).thenReturn(dto1);
        when(mapper.toDetailDto(run2)).thenReturn(dto2);

        Page<IngestionRunDetailResponse> result = service.getAllRuns(new IngestionRunQueryRequest(1, 20));

        assertThat(result.getContent()).containsExactly(dto1, dto2);
    }

    @Test
    @DisplayName("totalElements is propagated unchanged from the repository page")
    void totalElementsPropagated() {
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 137));

        Page<IngestionRunDetailResponse> result = service.getAllRuns(new IngestionRunQueryRequest(1, 20));

        assertThat(result.getTotalElements()).isEqualTo(137);
    }

    @Test
    @DisplayName("totalPages is propagated unchanged from the repository page")
    void totalPagesPropagated() {
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 137));

        Page<IngestionRunDetailResponse> result = service.getAllRuns(new IngestionRunQueryRequest(1, 20));

        assertThat(result.getTotalPages()).isEqualTo(7); // ceil(137 / 20)
    }

    @Test
    @DisplayName("page/size from the request are propagated into the Pageable passed to the repository")
    void pageSizePropagated() {
        when(controlService.findAllRuns(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(6, 5), 0));

        service.getAllRuns(new IngestionRunQueryRequest(7, 5));

        var captor = forClass(Pageable.class);
        verify(controlService).findAllRuns(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(6);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    @DisplayName("IngestionControlService no longer has a no-arg findAllRuns() overload (TECH-054 removed it, no callers left)")
    void noArgFindAllRunsWasRemoved() {
        boolean stillExists = java.util.Arrays.stream(IngestionControlService.class.getDeclaredMethods())
                .anyMatch(m -> m.getName().equals("findAllRuns") && m.getParameterCount() == 0);
        assertThat(stillExists).as("the unpaged, unbounded findAllRuns() must be gone").isFalse();
    }

    @Test
    @DisplayName("size above the configured maximum is rejected with the existing validation contract")
    void sizeAboveMaximum_rejected() {
        // The DTO itself clamps to 100, so this exercises validatePageable's own guard
        // via a directly-constructed oversized Pageable-equivalent request at the
        // boundary the DTO allows through (100 is the DTO's own ceiling; anything
        // above it never reaches the service - covered at the MVC layer instead).
        assertThatExceptionOfType(SipsaValidationException.class).isThrownBy(() ->
                paginationConfig.validatePageable(PageRequest.of(0, 5000)));
    }
}
