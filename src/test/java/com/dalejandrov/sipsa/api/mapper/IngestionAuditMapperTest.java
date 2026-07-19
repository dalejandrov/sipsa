package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.AuditTrailResponse;
import com.dalejandrov.sipsa.domain.entity.IngestionAudit;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral coverage of {@link IngestionAuditMapper}'s entity-to-response mapping
 * (TECH-051): asserts field values, not the method's identifier. {@code
 * toAuditEventResponse} was renamed from {@code toAuditEventRequest} to match what it
 * actually returns — {@link AuditTrailResponse.AuditEventResponse}, never a
 * {@code *Request} type — with no change to the mapping behavior this test pins.
 */
@DisplayName("IngestionAuditMapper — IngestionAudit to AuditEventResponse")
class IngestionAuditMapperTest {

    private final IngestionAuditMapper mapper = Mappers.getMapper(IngestionAuditMapper.class);

    @Test
    @DisplayName("maps every field, converting requestSource to its name and occurredAt to an OffsetDateTime")
    void mapsAllFields() {
        Instant occurredAt = Instant.parse("2026-07-19T14:30:00Z");
        IngestionAudit entity = IngestionAudit.builder()
                .auditId(42L)
                .runId(7L)
                .requestId("req-123")
                .requestSource(RequestSource.SCHEDULED)
                .eventType("INGESTION_STARTED")
                .message("Ingestion started for promediosSipsaParcial")
                .occurredAt(occurredAt)
                .build();

        AuditTrailResponse.AuditEventResponse response = mapper.toAuditEventResponse(entity);

        assertThat(response.auditId()).isEqualTo(42L);
        assertThat(response.runId()).isEqualTo(7L);
        assertThat(response.requestSource()).isEqualTo("SCHEDULED");
        assertThat(response.eventType()).isEqualTo("INGESTION_STARTED");
        assertThat(response.message()).isEqualTo("Ingestion started for promediosSipsaParcial");
        // No request-timezone context set (plain unit test) -> the mapper's expression
        // falls back to UTC, per TimezoneUtil.getRequestTimezone()'s documented default.
        assertThat(response.occurredAt()).isEqualTo(occurredAt.atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("a null requestSource maps to a null string, not an NPE")
    void nullRequestSourceMapsToNull() {
        IngestionAudit entity = IngestionAudit.builder()
                .auditId(1L)
                .eventType("REQUEST_RECEIVED")
                .message("Request received")
                .requestSource(null)
                .occurredAt(Instant.parse("2026-07-19T00:00:00Z"))
                .build();

        AuditTrailResponse.AuditEventResponse response = mapper.toAuditEventResponse(entity);

        assertThat(response.requestSource()).isNull();
    }

    @Test
    @DisplayName("a null runId maps to a null runId (pre-run events)")
    void nullRunIdMapsToNull() {
        IngestionAudit entity = IngestionAudit.builder()
                .auditId(2L)
                .runId(null)
                .requestSource(RequestSource.MANUAL)
                .eventType("REQUEST_RECEIVED")
                .message("Request received")
                .occurredAt(Instant.parse("2026-07-19T00:00:00Z"))
                .build();

        AuditTrailResponse.AuditEventResponse response = mapper.toAuditEventResponse(entity);

        assertThat(response.runId()).isNull();
    }
}
