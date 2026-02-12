package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaCiudadResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaCiudad entities to API Response DTOs.
 * <p>
 * Handles city-level pricing data mappings with timezone-aware timestamp conversion.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaCiudadMapper {

    /**
     * Converts city price entity to response DTO.
     * <p>
     * Converts timestamps appropriately: external dates to UTC OffsetDateTime,
     * system ingestion date to client timezone OffsetDateTime.
     *
     * @param entity the source entity
     * @return mapped response DTO for API
     */
    @Mapping(target = "fechaCaptura", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaCaptura(), false))")
    @Mapping(target = "fechaCreacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaCreacion(), false))")
    @Mapping(target = "fechaSincronizacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaSincronizacion(), true))")
    SipsaCiudadResponse toDto(SipsaCiudad entity);
}
