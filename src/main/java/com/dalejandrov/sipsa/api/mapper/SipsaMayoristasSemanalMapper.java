package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaMayoristasSemanalResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaMayoristasSemanal entities to API Response DTOs.
 * <p>
 * Handles weekly wholesale market data mappings with timezone-aware timestamp conversion.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaMayoristasSemanalMapper {

    /**
     * Converts weekly wholesale entity to response DTO.
     * <p>
     * Includes last update timestamp for client-side change tracking
     * and cache invalidation.
     *
     * @param entity the source entity from database
     * @return mapped response DTO for API
     */
    @Mapping(target = "fechaSincronizacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaSincronizacion(), true))")
    SipsaMayoristasSemanalResponse toDto(SipsaMayoristasSemanal entity);
}
