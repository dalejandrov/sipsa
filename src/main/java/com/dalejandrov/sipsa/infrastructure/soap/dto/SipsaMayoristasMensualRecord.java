package com.dalejandrov.sipsa.infrastructure.soap.dto;

import java.math.BigDecimal;

/**
 * Immutable DTO for monthly wholesale market data parsed from SOAP XML responses.
 * <p>
 * Represents monthly aggregated pricing data from wholesale markets (mayoristas),
 * extracted from SOAP service before domain entity mapping. Similar to weekly data
 * but aggregated by month instead of week.
 *
 * @param tmpMayoMesId temporary monthly wholesale ID (nullable, for deduplication)
 * @param artiId article/product ID
 * @param artiNombre article/product name
 * @param fuenId market source ID
 * @param fuenNombre market source name
 * @param futiId market type ID
 * @param fechaMesIni month start date (epoch millis)
 * @param fechaCreacion creation date (epoch millis)
 * @param minimoKg minimum price per kg for the month
 * @param maximoKg maximum price per kg for the month
 * @param promedioKg average price per kg for the month
 * @param enviado amount sent/dispatched
 * @see com.dalejandrov.sipsa.infrastructure.soap.parser.MesStaxParser
 * @see com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper
 */
public record SipsaMayoristasMensualRecord(
        Long tmpMayoMesId,
        Long artiId,
        String artiNombre,
        Long fuenId,
        String fuenNombre,
        Long futiId,
        Long fechaMesIni,
        Long fechaCreacion,
        BigDecimal minimoKg,
        BigDecimal maximoKg,
        BigDecimal promedioKg,
        BigDecimal enviado) {
}
