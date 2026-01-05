package com.dalejandrov.sipsa.infrastructure.soap.dto;

import java.math.BigDecimal;

/**
 * Immutable DTO for city-level pricing data parsed from SOAP XML responses.
 * <p>
 * This record represents raw data extracted from the SOAP service before
 * mapping to domain entities. Using a record ensures immutability and
 * reduces boilerplate for StAX parsing results.
 * <p>
 * Timestamps are represented as Long (epoch milliseconds) as they come
 * from the SOAP service, and are converted to Instant during mapping.
 *
 * @param regId registration ID from source system
 * @param ciudad city name
 * @param codProducto product code
 * @param producto product name/description
 * @param fechaCaptura capture date (epoch millis)
 * @param fechaCreacion creation date (epoch millis)
 * @param precioPromedio average price
 * @param enviado amount sent/dispatched
 * @see com.dalejandrov.sipsa.infrastructure.soap.parser.CiudadStaxParser
 * @see com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper
 */
public record SipsaCiudadRecord(
                Long regId,
                String ciudad,
                Long codProducto,
                String producto,
                Long fechaCaptura,
                Long fechaCreacion,
                BigDecimal precioPromedio,
                BigDecimal enviado) {
}
