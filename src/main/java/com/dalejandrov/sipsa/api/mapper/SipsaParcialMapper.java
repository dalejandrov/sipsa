package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.SipsaParcialResponse;
import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting SipsaParcial entities to API Response DTOs.
 * <p>
 * Handles partial market data by municipality mappings.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaParcialMapper {

    /**
     * Converts partial market entity to response DTO.
     *
     * @param entity the source entity from database
     * @return mapped response DTO for API
     */
    SipsaParcialResponse toDto(SipsaParcial entity);
}
