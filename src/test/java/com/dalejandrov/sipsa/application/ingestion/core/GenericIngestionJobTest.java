package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionService;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TECH-158: {@link GenericIngestionJob#runIngestion} - found during the ADR-011 review
 * to have no dedicated unit test. It is covered only transitively today, through
 * {@code ScheduledIngestionDispatcherTest} and {@code ParcialConcurrentIngestionAppTest},
 * both of which use a real {@code GenericIngestionJob} for other reasons, not to prove
 * this class's own one-line contract: delegate to {@link IngestionService#execute}
 * with the context's method name, unmodified.
 * <p>
 * Calls {@code runIngestion} directly (package-private test, same package as the
 * {@code protected} method) rather than going through the full {@code IngestionJob}
 * orchestration ({@code execute()}, window validation, run/audit persistence, metrics) -
 * that orchestration is {@code IngestionJob}'s own contract, already covered by
 * {@code IngestionJobContractTest} et al. against a minimal {@code ScriptedIngestionJob}
 * subclass. This test isolates exactly the one line {@code GenericIngestionJob} itself
 * adds on top of that.
 */
class GenericIngestionJobTest {

    private IngestionService ingestionService;
    private GenericIngestionJob job;

    @BeforeEach
    void setUp() {
        ingestionService = mock(IngestionService.class);
        IngestionProperties properties = mock(IngestionProperties.class);
        when(properties.getMaxRejectRate()).thenReturn(0.01);
        when(properties.getMaxRejectCount()).thenReturn(5000);

        job = new GenericIngestionJob(
                ingestionService,
                mock(WindowPolicy.class),
                mock(IngestionControlService.class),
                mock(IngestionAuditService.class),
                properties,
                mock(IngestionMetrics.class));
    }

    @Test
    @DisplayName("runIngestion delegates to IngestionService.execute with the context's own method name, unmodified")
    void runIngestion_delegatesToIngestionService() throws Exception {
        IngestionContext context = new IngestionContext(1L, "promediosSipsaCiudad", "window-1", "req-1", RequestSource.MANUAL);

        job.runIngestion(context);

        verify(ingestionService, times(1)).execute("promediosSipsaCiudad", context);
    }

    @Test
    @DisplayName("an exception from IngestionService.execute propagates out of runIngestion unmodified")
    void runIngestion_propagatesHandlerException() throws Exception {
        IngestionContext context = new IngestionContext(2L, "promediosSipsaParcial", "window-2", "req-2", RequestSource.SCHEDULED);
        SipsaBusinessException failure = new SipsaBusinessException("No handler found for method: promediosSipsaParcial");
        doThrow(failure).when(ingestionService).execute("promediosSipsaParcial", context);

        assertThatThrownBy(() -> job.runIngestion(context)).isSameAs(failure);
    }
}
