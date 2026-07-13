package com.dalejandrov.sipsa.application.command;

import com.dalejandrov.sipsa.domain.entity.RequestSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link IngestionRequest}, {@link CreateRunRequest}, and
 * {@link AuditEventRequest} can still be constructed and used the same way after their
 * move from {@code api.dto.request} to {@code application.command} (TECH-090).
 */
class InternalIngestionCommandsTest {

    @Test
    void ingestionRequest_manual_setsExpectedFields() {
        IngestionRequest request = IngestionRequest.manual("promediosSipsaCiudad", "req-1");

        assertThat(request.methodName()).isEqualTo("promediosSipsaCiudad");
        assertThat(request.force()).isFalse();
        assertThat(request.requestId()).isEqualTo("req-1");
        assertThat(request.requestSource()).isEqualTo(RequestSource.MANUAL);
    }

    @Test
    void ingestionRequest_manualForced_setsForceTrue() {
        IngestionRequest request = IngestionRequest.manualForced("promediosSipsaCiudad", "req-2");

        assertThat(request.force()).isTrue();
        assertThat(request.requestSource()).isEqualTo(RequestSource.MANUAL);
    }

    @Test
    void ingestionRequest_scheduled_setsScheduledSource() {
        IngestionRequest request = IngestionRequest.scheduled("promediosSipsaParcial", "req-3");

        assertThat(request.force()).isFalse();
        assertThat(request.requestSource()).isEqualTo(RequestSource.SCHEDULED);
    }

    @Test
    void createRunRequest_from_copiesIngestionRequestFields() {
        IngestionRequest ingestionRequest = IngestionRequest.manual("promediosSipsaCiudad", "req-4");

        CreateRunRequest createRunRequest = CreateRunRequest.from(ingestionRequest, "2026-07-13");

        assertThat(createRunRequest.methodName()).isEqualTo(ingestionRequest.methodName());
        assertThat(createRunRequest.windowKey()).isEqualTo("2026-07-13");
        assertThat(createRunRequest.requestId()).isEqualTo(ingestionRequest.requestId());
        assertThat(createRunRequest.requestSource()).isEqualTo(ingestionRequest.requestSource());
        assertThat(createRunRequest.force()).isEqualTo(ingestionRequest.force());
    }

    @Test
    void auditEventRequest_ingestionStarted_buildsExpectedEvent() {
        IngestionRequest ingestionRequest = IngestionRequest.manual("promediosSipsaCiudad", "req-5");

        AuditEventRequest event = AuditEventRequest.ingestionStarted(ingestionRequest, 42L, "2026-07-13");

        assertThat(event.requestId()).isEqualTo("req-5");
        assertThat(event.runId()).isEqualTo(42L);
        assertThat(event.requestSource()).isEqualTo(RequestSource.MANUAL);
        assertThat(event.message()).contains("promediosSipsaCiudad", "2026-07-13");
    }

    @Test
    void auditEventRequest_requestReceived_hasNullRunId() {
        AuditEventRequest event = AuditEventRequest.requestReceived(
                "req-6", RequestSource.MANUAL, "promediosSipsaCiudad", false);

        assertThat(event.runId()).isNull();
        assertThat(event.requestId()).isEqualTo("req-6");
    }
}
