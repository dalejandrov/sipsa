package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaAbastecimientosMensualResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaAbastecimientosMensual;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaAbastecimientosMensual entities to API Response DTOs.
 * <p>
 * Handles monthly supply data mappings with timezone-aware timestamp conversion.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaAbastecimientosMensualMapper {

    /**
     * Converts monthly supply entity to response DTO.
     *
     * @param entity the source entity from database
     * @return mapped response DTO for API
     */
    @Mapping(target = "fechaCreacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaCreacion(), false))")
    SipsaAbastecimientosMensualResponse toDto(SipsaAbastecimientosMensual entity);
}
