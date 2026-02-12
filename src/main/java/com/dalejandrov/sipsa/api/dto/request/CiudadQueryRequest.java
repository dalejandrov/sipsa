package com.dalejandrov.sipsa.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Request DTO for city-level pricing data queries.
 * <p>
 * Encapsulates all filtering parameters for city pricing queries,
 * providing better type safety and validation.
 */
public record CiudadQueryRequest(
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate fecha,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate startDate,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate endDate,

    @Positive(message = "artiId must be a positive number")
    Long artiId,

    @Positive(message = "fuenId must be a positive number")
    Long fuenId,

    String ciudad,

    String producto,

    @Min(value = 1, message = "page must be >= 1")
    Integer page,

    @Min(value = 1, message = "size must be >= 1")
    @Max(value = 100, message = "size must be <= 100")
    Integer size,

    String sort
) {
    public CiudadQueryRequest {
        // Provide defaults for null values
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;
        if (size > 100) size = 100;
        if (sort == null || sort.trim().isEmpty()) sort = "fechaCaptura,desc";
    }
}
