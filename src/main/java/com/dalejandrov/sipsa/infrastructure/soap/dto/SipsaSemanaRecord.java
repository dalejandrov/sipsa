package com.dalejandrov.sipsa.infrastructure.soap.dto;

import java.math.BigDecimal;

/**
 * Immutable DTO for weekly wholesale market data parsed from SOAP XML responses.
 * <p>
 * Represents weekly pricing data from wholesale markets (mayoristas), extracted
 * from SOAP service before domain entity mapping. Includes temporary IDs when
 * available from source system for better duplicate detection.
 * <p>
 * Timestamps are epoch milliseconds as received from SOAP service and are
 * converted to Instant during entity mapping.
 *
 * @param tmpMayoSemId temporary weekly wholesale ID (nullable, for deduplication)
 * @param artiId article/product ID
 * @param artiNombre article/product name
 * @param fuenId market source ID
 * @param fuenNombre market source name
 * @param futiId market type ID
 * @param fechaIni week start date (epoch millis)
 * @param fechaCreacion creation date (epoch millis)
 * @param minimoKg minimum price per kg for the week
 * @param maximoKg maximum price per kg for the week
 * @param promedioKg average price per kg for the week
 * @param enviado amount sent/dispatched
 * @see com.dalejandrov.sipsa.infrastructure.soap.parser.SemanaStaxParser
 * @see com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper
 */
public record SipsaSemanaRecord(
        Long tmpMayoSemId,
        Long artiId,
        String artiNombre,
        Long fuenId,
        String fuenNombre,
        Long futiId,
        Long fechaIni,
        Long fechaCreacion,
        BigDecimal minimoKg,
        BigDecimal maximoKg,
        BigDecimal promedioKg,
        BigDecimal enviado) {
}
