package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaMayoristasMensualResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaMayoristasMensual entities to API Response DTOs.
 * <p>
 * Handles monthly wholesale market data mappings.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaMayoristasMensualMapper {

    /**
     * Converts monthly wholesale entity to response DTO.
     *
     * @param entity the source entity from database
     * @return mapped response DTO for API
     */
    SipsaMayoristasMensualResponse toDto(SipsaMayoristasMensual entity);
}
