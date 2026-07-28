package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaMayoristasMensualResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaMayoristasMensual entities to API Response DTOs.
 * <p>
 * Handles monthly wholesale market data mappings with timezone-aware timestamp conversion.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaMayoristasMensualMapper {

    /**
     * Converts monthly wholesale entity to response DTO.
     * <p>
     * Includes last update timestamp for client-side change tracking
     * and cache invalidation.
     *
     * @param entity the source entity from database
     * @return mapped response DTO for API
     */
    @Mapping(target = "fechaMesIni", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.toBusinessLocalDate(entity.getFechaMesIni()))")
    @Mapping(target = "fechaSincronizacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaSincronizacion(), true))")
    SipsaMayoristasMensualResponse toDto(SipsaMayoristasMensual entity);
}
