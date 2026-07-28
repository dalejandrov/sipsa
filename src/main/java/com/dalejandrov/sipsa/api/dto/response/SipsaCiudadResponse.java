package com.dalejandrov.sipsa.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for city-level agricultural pricing data.
 * <p>
 * This record represents price information collected at city level,
 * including average prices per product and timestamps for data capture and ingestion.
 *
 * @param regId          unique registration identifier from source system
 * @param ciudad         city name where data was collected
 * @param codProducto    product code identifier
 * @param producto       product name/description
 * @param fechaCaptura   calendar date the price was captured (DANE survey date, Colombia — not an instant, never shifted by timezone; ADR-008)
 * @param fechaCreacion  timestamp when the record was created in source system (external, in UTC)
 * @param precioPromedio average price for the product (in local currency per unit)
 * @param enviado        amount sent/dispatched (specific to source system)
 * @param fechaSincronizacion timestamp when the record was ingested into this system (system, converted to client timezone)
 */
public record SipsaCiudadResponse(
        Long regId,
        String ciudad,
        Long codProducto,
        String producto,
        LocalDate fechaCaptura,
        OffsetDateTime fechaCreacion,
        BigDecimal precioPromedio,
        BigDecimal enviado,
        OffsetDateTime fechaSincronizacion) {
}
