package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRejectRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SIPSA-F4-21: {@link IngestionControlService#updateStatus} unit coverage for the
 * predecessor-derivation guard and the terminal/non-terminal {@code endTime} split - the parts
 * that don't need a real database, complementing the Testcontainers-backed race coverage in
 * {@link IngestionControlServiceLifecycleConcurrentTest}.
 */
@DisplayName("IngestionControlService.updateStatus — predecessor derivation and endTime")
class IngestionControlServiceUpdateStatusTest {

    private final IngestionRunRepository runRepository = mock(IngestionRunRepository.class);
    private final IngestionRejectRepository rejectRepository = mock(IngestionRejectRepository.class);
    private final IngestionControlService service =
            new IngestionControlService(runRepository, rejectRepository);

    @Test
    @DisplayName("to=STARTED has no defined predecessor: rejected with IllegalArgumentException, no query issued")
    void toStarted_throwsIllegalArgumentException() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> service.updateStatus(1L, IngestionRunStatus.STARTED));
    }

    @Test
    @DisplayName("to=CANCELED has no defined predecessor here: rejected with IllegalArgumentException — cancelRun owns that transition")
    void toCanceled_throwsIllegalArgumentException() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> service.updateStatus(1L, IngestionRunStatus.CANCELED));
    }

    @Test
    @DisplayName("to=RUNNING requires expectedFrom=STARTED and passes a null endTime (non-terminal)")
    void toRunning_requiresStartedPredecessor_nullEndTime() {
        when(runRepository.transitionStatusIfCurrentIs(eq(5L), eq(IngestionRunStatus.STARTED),
                eq(IngestionRunStatus.RUNNING), isNull())).thenReturn(1);

        boolean result = service.updateStatus(5L, IngestionRunStatus.RUNNING);

        assertThat(result).isTrue();
        verify(runRepository).transitionStatusIfCurrentIs(5L, IngestionRunStatus.STARTED,
                IngestionRunStatus.RUNNING, null);
    }

    @Test
    @DisplayName("to=SUCCEEDED requires expectedFrom=RUNNING and passes a non-null endTime (terminal)")
    void toSucceeded_requiresRunningPredecessor_nonNullEndTime() {
        when(runRepository.transitionStatusIfCurrentIs(eq(6L), eq(IngestionRunStatus.RUNNING),
                eq(IngestionRunStatus.SUCCEEDED), any(Instant.class))).thenReturn(1);

        boolean result = service.updateStatus(6L, IngestionRunStatus.SUCCEEDED);

        assertThat(result).isTrue();
        verify(runRepository).transitionStatusIfCurrentIs(eq(6L), eq(IngestionRunStatus.RUNNING),
                eq(IngestionRunStatus.SUCCEEDED), any(Instant.class));
    }

    @Test
    @DisplayName("to=FAILED requires expectedFrom=RUNNING; zero rows affected surfaces as false, not an exception")
    void toFailed_zeroRowsAffected_returnsFalse() {
        when(runRepository.transitionStatusIfCurrentIs(eq(7L), eq(IngestionRunStatus.RUNNING),
                eq(IngestionRunStatus.FAILED), any(Instant.class))).thenReturn(0);

        boolean result = service.updateStatus(7L, IngestionRunStatus.FAILED);

        assertThat(result).as("a lost race must surface as false, never throw").isFalse();
    }
}
