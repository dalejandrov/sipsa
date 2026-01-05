package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.*;
import com.dalejandrov.sipsa.domain.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * MapStruct mapper for converting between SIPSA domain entities and API DTOs.
 * <p>
 * This interface is automatically implemented by MapStruct at compile time,
 * generating efficient mapping code without reflection.
 * <p>
 * The mapper handles bidirectional conversions:
 * <ul>
 *   <li>Entity → DTO: For API responses (read operations)</li>
 *   <li>DTO → Entity: For data ingestion (less common, mainly internal use)</li>
 * </ul>
 * <p>
 * Unmapped fields are ignored to allow partial updates and avoid mapping errors
 * for internal/system fields that shouldn't be set from external input.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SipsaApiMapper {

    /**
     * Converts city price DTO to entity.
     * <p>
     * Ignores system-managed fields (id, ingestionRunId) which are set internally
     * during the ingestion process.
     *
     * @param dto the source DTO
     * @return mapped entity ready for persistence
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ingestionRunId", ignore = true)
    SipsaCiudad toEntity(SipsaCiudadDto dto);

    /**
     * Converts monthly wholesale DTO to entity.
     * <p>
     * Ignores internal fields that are populated during the ingestion process,
     * including temporary IDs, timestamps, and audit fields.
     *
     * @param dto the source DTO
     * @return mapped entity ready for persistence
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tmpMayoMesId", ignore = true)
    @Mapping(target = "futiId", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "enviado", ignore = true)
    @Mapping(target = "ingestionRunId", ignore = true)
    SipsaMayoristasMensual toEntity(SipsaMayoristasMensualDto dto);

    /**
     * Converts partial market DTO to entity.
     * <p>
     * Ignores system-managed fields (id, ingestionRunId).
     *
     * @param dto the source DTO
     * @return mapped entity ready for persistence
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ingestionRunId", ignore = true)
    SipsaParcial toEntity(SipsaParcialDto dto);

    /**
     * Converts weekly wholesale DTO to entity.
     * <p>
     * Ignores internal fields that are populated during the ingestion process,
     * including temporary IDs, timestamps, and audit fields.
     *
     * @param dto the source DTO
     * @return mapped entity ready for persistence
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tmpMayoSemId", ignore = true)
    @Mapping(target = "futiId", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "enviado", ignore = true)
    @Mapping(target = "ingestionRunId", ignore = true)
    SipsaMayoristasSemanal toEntity(SipsaMayoristasSemanalDto dto);

    /**
     * Converts monthly supply DTO to entity.
     * <p>
     * Ignores internal fields that are populated during the ingestion process,
     * including temporary IDs and ingestion timestamps.
     *
     * @param dto the source DTO
     * @return mapped entity ready for persistence
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tmpAbasMesId", ignore = true)
    @Mapping(target = "futiId", ignore = true)
    @Mapping(target = "ingestionRunId", ignore = true)
    @Mapping(target = "fechaIngestion", ignore = true)
    SipsaAbastecimientosMensual toEntity(SipsaAbastecimientosMensualDto dto);

    /**
     * Converts monthly wholesale entity to DTO for API responses.
     * <p>
     * Includes last update timestamp for client-side change tracking
     * and cache invalidation.
     *
     * @param entity the source entity from database
     * @return mapped DTO for API response
     */
    @Mapping(target = "fechaMesIni", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaMesIni(), true))")
    @Mapping(target = "lastUpdated", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getLastUpdated(), true))")
    SipsaMayoristasMensualDto toDto(SipsaMayoristasMensual entity);

    /**
     * Converts partial market entity to DTO for API responses.
     * <p>
     * Includes last update timestamp for client-side change tracking
     * and cache invalidation.
     *
     * @param entity the source entity from database
     * @return mapped DTO for API response
     */
    @Mapping(target = "enmaFecha", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getEnmaFecha(), true))")
    @Mapping(target = "lastUpdated", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getLastUpdated(), true))")
    SipsaParcialDto toDto(SipsaParcial entity);

    /**
     * Converts weekly wholesale entity to DTO for API responses.
     * <p>
     * Includes last update timestamp for client-side change tracking
     * and cache invalidation.
     *
     * @param entity the source entity from database
     * @return mapped DTO for API response
     */
    @Mapping(target = "fechaIni", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaIni(), true))")
    @Mapping(target = "lastUpdated", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getLastUpdated(), true))")
    SipsaMayoristasSemanalDto toDto(SipsaMayoristasSemanal entity);

    /**
     * Converts monthly supply entity to DTO for API responses.
     * <p>
     * Includes ingestion timestamp to allow API clients to track data freshness
     * and determine when the data was last updated.
     *
     * @param entity the source entity from database
     * @return mapped DTO for API response
     */
    @Mapping(target = "fechaMesIni", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaMesIni(), true))")
    @Mapping(target = "fechaCreacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaCreacion(), true))")
    @Mapping(target = "fechaIngestion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaIngestion(), true))")
    SipsaAbastecimientosMensualDto toDto(SipsaAbastecimientosMensual entity);

    /**
     * Converts city entity to DTO for API responses.
     * <p>
     * Converts timestamps appropriately: external dates to UTC OffsetDateTime,
     * system ingestion date to client timezone OffsetDateTime.
     *
     * @param entity the source entity
     * @return mapped DTO for API response
     */
    @Mapping(target = "fechaCaptura", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaCaptura(), true))")
    @Mapping(target = "fechaCreacion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaCreacion(), true))")
    @Mapping(target = "fechaIngestion", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getFechaIngestion(), true))")
    SipsaCiudadDto toDto(SipsaCiudad entity);

    /**
     * Maps OffsetDateTime to Instant for entity conversion.
     *
     * @param offsetDateTime the OffsetDateTime to convert
     * @return the equivalent Instant
     */
    default Instant map(OffsetDateTime offsetDateTime) {
        return offsetDateTime != null ? offsetDateTime.toInstant() : null;
    }
}
