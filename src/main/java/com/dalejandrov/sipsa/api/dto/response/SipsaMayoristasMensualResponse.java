package com.dalejandrov.sipsa.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for monthly wholesale market pricing data.
 * <p>
 * This record represents aggregated wholesale market prices on a monthly basis,
 * including price statistics per product and market source.
 *
 * @param artiId product/article identifier
 * @param artiNombre product/article name
 * @param fuenId source identifier (wholesale market ID)
 * @param fuenNombre source name (wholesale market name)
 * @param fechaMesIni month start date (DANE calendar date, Colombia — not an instant, never shifted by timezone; ADR-008)
 * @param minimoKg minimum price per kilogram for the month
 * @param maximoKg maximum price per kilogram for the month
 * @param promedioKg average price per kilogram for the month
 * @param fechaSincronizacion timestamp of last update in this system (system, converted to client timezone)
 */
public record SipsaMayoristasMensualResponse(
        Long artiId,
        String artiNombre,
        Long fuenId,
        String fuenNombre,
        LocalDate fechaMesIni,
        BigDecimal minimoKg,
        BigDecimal maximoKg,
        BigDecimal promedioKg,
        OffsetDateTime fechaSincronizacion) {
}
