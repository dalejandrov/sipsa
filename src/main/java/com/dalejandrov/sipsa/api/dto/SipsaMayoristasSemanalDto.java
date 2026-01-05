package com.dalejandrov.sipsa.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Data Transfer Object for weekly wholesale market pricing data.
 * <p>
 * This record represents aggregated wholesale market prices on a weekly basis,
 * including price statistics per product and market source.
 *
 * @param artiId product/article identifier
 * @param artiNombre product/article name
 * @param fuenId source identifier (wholesale market ID)
 * @param fuenNombre source name (wholesale market name)
 * @param fechaIni week start date (external, in UTC)
 * @param minimoKg minimum price per kilogram for the week
 * @param maximoKg maximum price per kilogram for the week
 * @param promedioKg average price per kilogram for the week
 * @param lastUpdated timestamp of last update in this system (system, converted to client timezone)
 */
public record SipsaMayoristasSemanalDto(
        Long artiId,
        String artiNombre,
        Long fuenId,
        String fuenNombre,
        OffsetDateTime fechaIni,
        BigDecimal minimoKg,
        BigDecimal maximoKg,
        BigDecimal promedioKg,
        OffsetDateTime lastUpdated) {
}
