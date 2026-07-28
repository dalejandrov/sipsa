package com.dalejandrov.sipsa.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for monthly supply data to wholesale markets.
 * <p>
 * This record represents information about product supply volumes
 * to wholesale markets on a monthly basis, measured in tons.
 *
 * @param artiId         product/article identifier
 * @param artiNombre     product/article name
 * @param fuenId         source identifier (wholesale market ID)
 * @param fuenNombre     source name (wholesale market name)
 * @param futiId         source type identifier
 * @param fechaMesIni    month start date (DANE calendar date, Colombia — not an instant, never shifted by timezone; ADR-008)
 * @param fechaCreacion  timestamp when the record was created in source system (external, in UTC)
 * @param cantidadTon    quantity supplied in tons for the month
 * @param enviado        amount sent/dispatched (specific to source system)
 * @param fechaSincronizacion timestamp when the record was ingested into this system (system, converted to client timezone)
 */
public record SipsaAbastecimientosMensualResponse(
        Long artiId,
        String artiNombre,
        Long fuenId,
        String fuenNombre,
        Long futiId,
        LocalDate fechaMesIni,
        OffsetDateTime fechaCreacion,
        BigDecimal cantidadTon,
        BigDecimal enviado,
        OffsetDateTime fechaSincronizacion) {
}
