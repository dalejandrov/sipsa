package com.dalejandrov.sipsa.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Data Transfer Object for city-level agricultural pricing data.
 * <p>
 * This record represents price information collected at city level,
 * including average prices per product and timestamps for data capture and ingestion.
 *
 * @param regId          unique registration identifier from source system
 * @param ciudad         city name where data was collected
 * @param codProducto    product code identifier
 * @param producto       product name/description
 * @param fechaCaptura   timestamp when the price was captured/recorded (external, in UTC)
 * @param fechaCreacion  timestamp when the record was created in source system (external, in UTC)
 * @param precioPromedio average price for the product (in local currency per unit)
 * @param enviado        amount sent/dispatched (specific to source system)
 * @param fechaIngestion timestamp when the record was ingested into this system (system, converted to client timezone)
 */
public record SipsaCiudadDto(
        Long regId,
        String ciudad,
        Long codProducto,
        String producto,
        OffsetDateTime fechaCaptura,
        OffsetDateTime fechaCreacion,
        BigDecimal precioPromedio,
        BigDecimal enviado,
        OffsetDateTime fechaIngestion) {
}
