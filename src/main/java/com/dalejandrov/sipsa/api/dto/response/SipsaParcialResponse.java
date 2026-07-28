package com.dalejandrov.sipsa.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Response DTO for partial market data by municipality.
 * <p>
 * This record represents detailed market information at municipality level,
 * including price ranges and product availability across different sources.
 *
 * @param muniId municipality identifier code
 * @param muniNombre municipality name
 * @param deptNombre department (state/province) name
 * @param fuenId source identifier (market/collection point ID)
 * @param fuenNombre source name (market/collection point name)
 * @param futiId source type identifier
 * @param idArtiSemana weekly product article identifier
 * @param artiNombre product/article name
 * @param grupNombre product group/category name
 * @param enmaFecha survey/data collection date (DANE calendar date, Colombia — not an instant, never shifted by timezone; ADR-008)
 * @param promedioKg average price per kilogram
 * @param maximoKg maximum price per kilogram
 * @param minimoKg minimum price per kilogram
 * @param fechaSincronizacion timestamp of last update in this system (system, converted to client timezone)
 */
public record SipsaParcialResponse(
        String muniId,
        String muniNombre,
        String deptNombre,
        Long fuenId,
        String fuenNombre,
        Long futiId,
        Long idArtiSemana,
        String artiNombre,
        String grupNombre,
        LocalDate enmaFecha,
        BigDecimal promedioKg,
        BigDecimal maximoKg,
        BigDecimal minimoKg,
        OffsetDateTime fechaSincronizacion) {
}
