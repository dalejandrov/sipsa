package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.api.mapper.IngestionAuditMapper;
import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TECH-052: {@link IngestionRunQueryService#getRunStatus(Long)} is the sole caller of
 * {@link IngestionControlService#getRun(long)}. It must handle the now-explicit
 * {@code Optional<IngestionRun>} without ever calling {@code .get()} unguarded, and its
 * observable behavior — a mapped response when the run exists, the same
 * {@link SipsaBusinessException} (unchanged message and type — the HTTP 404 story,
 * TECH-022, is out of scope here) when it doesn't — must stay exactly as it was before
 * the rename.
 */
@DisplayName("IngestionRunQueryService.getRunStatus — Optional-based absence handling")
class IngestionRunQueryServiceGetRunStatusTest {

    private final IngestionControlService controlService = mock(IngestionControlService.class);
    private final IngestionService ingestionService = mock(IngestionService.class);
    private final IngestionAuditMapper mapper = mock(IngestionAuditMapper.class);
    private final IngestionRunQueryService service =
            new IngestionRunQueryService(controlService, ingestionService, mapper);

    @Test
    @DisplayName("an existing run is mapped to its detail response")
    void existingRun_returnsMappedDetail() {
        IngestionRun run = IngestionRun.builder().runId(42L).build();
        IngestionRunDetailResponse expected = new IngestionRunDetailResponse(
                42L, "promediosSipsaParcial", "2026-07-19", "SUCCEEDED",
                null, null, "req-1", "MANUAL", 100, 100, 0, 0);
        when(controlService.getRun(42L)).thenReturn(Optional.of(run));
        when(mapper.toDetailDto(run)).thenReturn(expected);

        IngestionRunDetailResponse result = service.getRunStatus(42L);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("a non-existent run throws SipsaBusinessException with the unchanged message — no NPE, no unguarded .get()")
    void missingRun_throwsSipsaBusinessException() {
        when(controlService.getRun(999L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SipsaBusinessException.class)
                .isThrownBy(() -> service.getRunStatus(999L))
                .withMessage("Ingestion run not found: 999");

        // The mapper must never be invoked when the run is absent.
        verifyNoInteractions(mapper);
    }
}
