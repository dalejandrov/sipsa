package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaMayoristasSemanalResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaMayoristasSemanal entities to API Response DTOs.
 * <p>
 * Handles weekly wholesale market data mappings.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaMayoristasSemanalMapper {

    /**
     * Converts weekly wholesale entity to response DTO.
     *
     * @param entity the source entity from database
     * @return mapped response DTO for API
     */
    SipsaMayoristasSemanalResponse toDto(SipsaMayoristasSemanal entity);
}
