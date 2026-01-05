package com.dalejandrov.sipsa.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Data Transfer Object for monthly supply data to wholesale markets.
 * <p>
 * This record represents information about product supply volumes
 * to wholesale markets on a monthly basis, measured in tons.
 *
 * @param artiId         product/article identifier
 * @param artiNombre     product/article name
 * @param fuenId         source identifier (wholesale market ID)
 * @param fuenNombre     source name (wholesale market name)
 * @param fechaMesIni    month start date (external, in UTC)
 * @param fechaCreacion  timestamp when the record was created in source system (external, in UTC)
 * @param cantidadTon    quantity supplied in tons for the month
 * @param enviado        amount sent/dispatched (specific to source system)
 * @param fechaIngestion timestamp when the record was ingested into this system (system, converted to client timezone)
 */
public record SipsaAbastecimientosMensualDto(
        Long artiId,
        String artiNombre,
        Long fuenId,
        String fuenNombre,
        OffsetDateTime fechaMesIni,
        OffsetDateTime fechaCreacion,
        BigDecimal cantidadTon,
        BigDecimal enviado,
        OffsetDateTime fechaIngestion) {
}
