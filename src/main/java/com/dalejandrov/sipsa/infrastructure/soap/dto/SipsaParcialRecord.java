package com.dalejandrov.sipsa.infrastructure.soap.dto;

import java.math.BigDecimal;

/**
 * Immutable DTO for partial municipal market data parsed from SOAP XML responses.
 * <p>
 * Represents partial market pricing data by municipality, extracted directly
 * from SOAP service before domain entity mapping. This data includes municipal-level
 * pricing information for specific products during survey periods.
 * <p>
 * This record is used as an intermediate format between XML parsing and
 * entity persistence, allowing for immutability and type safety.
 *
 * @param muniId municipality ID
 * @param muniNombre municipality name
 * @param deptNombre department (state) name
 * @param fuenId market source ID
 * @param fuenNombre market source name
 * @param futiId market type ID
 * @param artiId article/product ID
 * @param artiNombre article/product name
 * @param grupNombre product group name
 * @param fechaEncuestaText survey date as text
 * @param idArtiSemana weekly article ID
 * @param enmaFecha survey date formatted
 * @param promedioKg average price per kg
 * @param maximoKg maximum price per kg
 * @param minimoKg minimum price per kg
 * @see com.dalejandrov.sipsa.infrastructure.soap.parser.ParcialStaxParser
 * @see com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper
 */
public record SipsaParcialRecord(
        String muniId,
        String muniNombre,
        String deptNombre,
        Long fuenId,
        String fuenNombre,
        Long futiId,
        Long artiId,
        String artiNombre,
        String grupNombre,
        String fechaEncuestaText,
        Long idArtiSemana,
        String enmaFecha,
        BigDecimal promedioKg,
        BigDecimal maximoKg,
        BigDecimal minimoKg) {
}
