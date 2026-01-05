package com.dalejandrov.sipsa.infrastructure.soap.dto;

import java.math.BigDecimal;

/**
 * Immutable DTO for monthly supply/provisioning data parsed from SOAP XML responses.
 * <p>
 * Represents monthly supply quantities (abastecimientos) for products, indicating
 * the volume of products supplied to markets. Unlike pricing records, this focuses
 * on quantity (tons) rather than prices.
 *
 * @param tmpAbasMesId temporary monthly supply ID (nullable, for deduplication)
 * @param artiId article/product ID
 * @param artiNombre article/product name
 * @param fuenId market source ID
 * @param fuenNombre market source name
 * @param futiId market type ID
 * @param fechaMes month start date (epoch millis)
 * @param fechaCreacion creation date (epoch millis)
 * @param cantidadTon quantity supplied in tons
 * @param enviado amount sent/dispatched
 * @see com.dalejandrov.sipsa.infrastructure.soap.parser.AbasStaxParser
 * @see com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper
 */
public record SipsaAbasRecord(
                Long tmpAbasMesId,
                Long artiId,
                String artiNombre,
                Long fuenId,
                String fuenNombre,
                Long futiId,
                Long fechaMes,
                Long fechaCreacion,
                BigDecimal cantidadTon,
                BigDecimal enviado) {
}
