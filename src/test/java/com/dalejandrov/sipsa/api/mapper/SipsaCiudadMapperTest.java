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
 * ADR-008 F1 / TECH-104: {@code fechaCaptura} is a DANE calendar date, not an instant.
 * Since TECH-104 retyped the entity/DB column itself to {@link LocalDate}, the mapper is a
 * plain passthrough — no zone conversion happens here at all (that now happens once, at
 * ingestion time, in {@code SipsaIngestionMapper}). {@code fechaCreacion} is a genuine
 * instant and keeps its prior {@link OffsetDateTime} (UTC) behavior — unchanged by this
 * story. {@code fechaSincronizacion} is an internal audit timestamp and is not exposed on
 * {@link SipsaCiudadResponse} at all.
 */
class SipsaCiudadMapperTest {

    private final SipsaCiudadMapper mapper = Mappers.getMapper(SipsaCiudadMapper.class);

    @AfterEach
    void clearRequestTimezone() {
        TimezoneUtil.clearRequestTimezone();
    }

    @Test
    @DisplayName("fechaCaptura maps straight across as a LocalDate, unaffected by the request timezone")
    void fechaCaptura_mapsAsPlainLocalDate() {
        LocalDate fechaCaptura = LocalDate.of(2026, 7, 15);
        Instant fechaCreacion = Instant.parse("2026-07-16T01:00:00Z");
        TimezoneUtil.setRequestTimezone(ZoneId.of("Asia/Tokyo"));

        SipsaCiudad entity = SipsaCiudad.builder()
                .regId(1L)
                .ciudad("Bogota")
                .codProducto(10L)
                .producto("Papa")
                .fechaCaptura(fechaCaptura)
                .fechaCreacion(fechaCreacion)
                .precioPromedio(new BigDecimal("1500.00"))
                .build();

        SipsaCiudadResponse response = mapper.toDto(entity);

        assertThat(response.fechaCaptura()).isEqualTo(fechaCaptura);
        assertThat(response.fechaCreacion()).isEqualTo(fechaCreacion.atOffset(ZoneOffset.UTC));
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
                .build();

        SipsaCiudadResponse response = mapper.toDto(entity);

        assertThat(response.fechaCaptura()).isNull();
    }
}
