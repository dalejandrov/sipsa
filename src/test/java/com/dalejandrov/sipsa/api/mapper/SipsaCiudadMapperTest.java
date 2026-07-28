package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaCiudadResponse;
import com.dalejandrov.sipsa.api.util.TimezoneUtil;
import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-008 F1: {@code fechaCaptura} is a DANE calendar date, not an instant — it must map
 * to {@link LocalDate} via {@link TimezoneUtil#toBusinessLocalDate}, unaffected by the
 * request's {@code X-Timezone}. {@code fechaCreacion} and {@code fechaSincronizacion} are
 * genuine instants and keep their prior {@link OffsetDateTime} behavior (UTC and
 * request-timezone respectively) — unchanged by this story.
 */
class SipsaCiudadMapperTest {

    private final SipsaCiudadMapper mapper = Mappers.getMapper(SipsaCiudadMapper.class);

    @AfterEach
    void clearRequestTimezone() {
        TimezoneUtil.clearRequestTimezone();
    }

    @Test
    @DisplayName("fechaCaptura maps to the Bogota calendar date, ignoring the request timezone")
    void fechaCaptura_mapsToBusinessLocalDate() {
        // 2026-07-16T01:00:00Z is already 2026-07-16 in UTC, but still 2026-07-15 in Bogota.
        Instant fechaCaptura = Instant.parse("2026-07-16T01:00:00Z");
        Instant fechaCreacion = Instant.parse("2026-07-16T01:00:00Z");
        Instant fechaSincronizacion = Instant.parse("2026-07-16T01:00:00Z");
        TimezoneUtil.setRequestTimezone(ZoneId.of("Asia/Tokyo"));

        SipsaCiudad entity = SipsaCiudad.builder()
                .regId(1L)
                .ciudad("Bogota")
                .codProducto(10L)
                .producto("Papa")
                .fechaCaptura(fechaCaptura)
                .fechaCreacion(fechaCreacion)
                .precioPromedio(new BigDecimal("1500.00"))
                .fechaSincronizacion(fechaSincronizacion)
                .build();

        SipsaCiudadResponse response = mapper.toDto(entity);

        assertThat(response.fechaCaptura()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(response.fechaCreacion()).isEqualTo(fechaCreacion.atOffset(ZoneOffset.UTC));
        assertThat(response.fechaSincronizacion()).isEqualTo(fechaSincronizacion.atZone(ZoneId.of("Asia/Tokyo")).toOffsetDateTime());
    }

    @Test
    @DisplayName("null fechaCaptura maps to null, not an exception")
    void nullFechaCaptura_mapsToNull() {
        SipsaCiudad entity = SipsaCiudad.builder()
                .regId(1L)
                .ciudad("Bogota")
                .codProducto(10L)
                .producto("Papa")
                .fechaCaptura(null)
                .fechaCreacion(Instant.parse("2026-07-15T00:00:00Z"))
                .precioPromedio(new BigDecimal("1500.00"))
                .fechaSincronizacion(Instant.parse("2026-07-15T00:00:00Z"))
                .build();

        SipsaCiudadResponse response = mapper.toDto(entity);

        assertThat(response.fechaCaptura()).isNull();
    }
}
