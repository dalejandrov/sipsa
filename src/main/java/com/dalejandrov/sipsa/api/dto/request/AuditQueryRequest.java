package com.dalejandrov.sipsa.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Request DTO for audit event queries.
 * <p>
 * Encapsulates all filtering parameters for audit event queries,
 * providing better type safety and validation.
 */
public record AuditQueryRequest(
    String requestId,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate fecha,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate startDate,

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate endDate,

    @Min(value = 1, message = "page must be >= 1")
    Integer page,

    @Min(value = 1, message = "size must be >= 1")
    @Max(value = 100, message = "size must be <= 100")
    Integer size,

    String sort
) {
    public AuditQueryRequest {
        if (page == null || page < 1) page = 1;
        if (size == null || size < 1) size = 20;
        if (size > 100) size = 100;
        if (sort == null || sort.trim().isEmpty()) sort = "occurredAt,desc";
    }
}
