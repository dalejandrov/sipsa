package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRejectRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TECH-022 / SIPSA-F4-21: {@link IngestionControlService#cancelRun(long)} distinguishes two
 * previously conflated cases, both formerly {@link SipsaBusinessException} (422):
 * <ul>
 *   <li>the run doesn't exist at all → now {@link SipsaNotFoundException} (404)</li>
 *   <li>the run exists but isn't STARTED/RUNNING → still {@link SipsaBusinessException} (422),
 *       unchanged — this is a genuine business-rule violation, not an absent resource</li>
 * </ul>
 * <p>
 * <b>Adapted for SIPSA-F4-21 (necessary regression fix, not a behavior change):</b> {@code
 * cancelRun} no longer calls {@code findById} then {@code save} on the success path — it calls
 * the atomic {@link IngestionRunRepository#cancelIfActive} once. {@code findById} is now only
 * reached on the zero-rows-affected path, to build the 404/422 message. These tests are
 * adapted to mock that new call shape; the external contract (exceptions, messages, HTTP
 * mapping) asserted here is byte-for-byte unchanged, which is exactly what
 * {@link com.dalejandrov.sipsa.api.controller.SipsaOpsControllerNotFoundTest} (a controller
 * test, out of this task's scope, left untouched) continues to verify end-to-end.
 */
@DisplayName("IngestionControlService.cancelRun — not-found vs. not-active")
class IngestionControlServiceCancelRunTest {

    private final IngestionRunRepository runRepository = mock(IngestionRunRepository.class);
    private final IngestionRejectRepository rejectRepository = mock(IngestionRejectRepository.class);
    private final IngestionControlService service =
            new IngestionControlService(runRepository, rejectRepository);

    @Test
    @DisplayName("a non-existent run throws SipsaNotFoundException (404)")
    void missingRun_throwsSipsaNotFoundException() {
        when(runRepository.cancelIfActive(anyLong(), any())).thenReturn(0);
        when(runRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SipsaNotFoundException.class)
                .isThrownBy(() -> service.cancelRun(999L))
                .withMessage("Run not found: 999");

        verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("an existing but inactive run still throws SipsaBusinessException (422) — unchanged")
    void inactiveRun_stillThrowsSipsaBusinessException() {
        when(runRepository.cancelIfActive(anyLong(), any())).thenReturn(0);
        IngestionRun run = IngestionRun.builder()
                .runId(42L)
                .status(IngestionRunStatus.SUCCEEDED)
                .build();
        when(runRepository.findById(42L)).thenReturn(Optional.of(run));

        assertThatExceptionOfType(SipsaBusinessException.class)
                .isThrownBy(() -> service.cancelRun(42L))
                .withMessage("Run is not active (status: SUCCEEDED)");

        verify(runRepository, never()).save(any());
    }

    @Test
    @DisplayName("an active run (STARTED) is canceled successfully via the atomic conditional UPDATE")
    void activeRun_isCanceled() {
        when(runRepository.cancelIfActive(anyLong(), any())).thenReturn(1);

        service.cancelRun(7L);

        verify(runRepository).cancelIfActive(org.mockito.ArgumentMatchers.eq(7L), any());
        verify(runRepository, never()).findById(anyLong());
        verify(runRepository, never()).save(any());
    }
}
