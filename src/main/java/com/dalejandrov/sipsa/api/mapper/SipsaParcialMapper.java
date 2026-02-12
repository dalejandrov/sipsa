package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaParcialResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaParcial entities to API Response DTOs.
 * <p>
 * Handles partial market data by municipality mappings with timezone-aware timestamp conversion.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaParcialMapper {

    /**
     * Converts partial market entity to response DTO.
     * <p>
     * Includes last update timestamp for client-side change tracking
     * and cache invalidation.
     *
     * @param entity the source entity from database
     * @return mapped response DTO for API
     */
    @Mapping(target = "enmaFecha", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getEnmaFecha(), false))")
    @Mapping(target = "fechaSincronizacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaSincronizacion(), true))")
    SipsaParcialResponse toDto(SipsaParcial entity);
}
