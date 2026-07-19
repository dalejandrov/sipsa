package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRejectRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TECH-052: {@link IngestionControlService#getRun(long)} makes run absence an explicit
 * {@link Optional#empty()} instead of a nullable return — the repository already returns
 * {@code Optional} ({@code JpaRepository.findById}), and the service now propagates it
 * directly instead of unwrapping it with {@code .orElse(null)}.
 */
@DisplayName("IngestionControlService.getRun — explicit Optional contract")
class IngestionControlServiceGetRunTest {

    private final IngestionRunRepository runRepository = mock(IngestionRunRepository.class);
    private final IngestionRejectRepository rejectRepository = mock(IngestionRejectRepository.class);
    private final IngestionControlService service =
            new IngestionControlService(runRepository, rejectRepository);

    @Test
    @DisplayName("an existing run is returned as Optional.of(run)")
    void existingRun_returnsOptionalOfRun() {
        IngestionRun run = IngestionRun.builder().runId(42L).build();
        when(runRepository.findById(42L)).thenReturn(Optional.of(run));

        Optional<IngestionRun> result = service.getRun(42L);

        assertThat(result).isPresent().contains(run);
    }

    @Test
    @DisplayName("a non-existent run is returned as Optional.empty() — never null, never a bare null-wrapped Optional")
    void missingRun_returnsOptionalEmpty() {
        when(runRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<IngestionRun> result = service.getRun(999L);

        assertThat(result).isEmpty();
    }
}
