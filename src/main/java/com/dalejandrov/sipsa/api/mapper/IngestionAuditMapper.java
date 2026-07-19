package com.dalejandrov.sipsa.api.mapper;

import com.dalejandrov.sipsa.api.dto.response.*;
import com.dalejandrov.sipsa.domain.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting ingestion audit and run entities to API Response DTOs.
 * <p>
 * This mapper handles conversions for operational monitoring entities:
 * <ul>
 *   <li>Ingestion run summaries (IngestionRun → IngestionRunResponse)</li>
 *   <li>Ingestion run details (IngestionRun → IngestionRunDetailResponse)</li>
 *   <li>Audit events (IngestionAudit → AuditEventResponse)</li>
 * </ul>
 * <p>
 * All mappings include timezone-aware timestamp conversion for API responses.
 *
 * @see org.mapstruct.Mapper
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IngestionAuditMapper {

    /**
     * Converts IngestionAudit entity to AuditEventResponse for responses.
     * <p>
     * Maps the audit event with timezone-aware timestamp conversion.
     *
     * @param entity the IngestionAudit entity
     * @return the corresponding AuditEventResponse
     */
    @Mapping(target = "requestSource", expression = "java(entity.getRequestSource() != null ? entity.getRequestSource().name() : null)")
    @Mapping(target = "occurredAt", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getOccurredAt(), true))")
    AuditTrailResponse.AuditEventResponse toAuditEventResponse(IngestionAudit entity);

    /**
     * Converts IngestionRun entity to response DTO for API.
     * <p>
     * Maps the run with timezone-aware timestamp conversion for API responses.
     *
     * @param entity the IngestionRun entity
     * @return the corresponding response DTO
     */
    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    @Mapping(target = "startTime", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getStartTime(), true))")
    IngestionRunResponse toDto(IngestionRun entity);

    /**
     * Converts IngestionRun entity to detailed response DTO for API.
     * <p>
     * Maps all fields including metrics with timezone-aware timestamp conversion.
     *
     * @param entity the IngestionRun entity
     * @return the corresponding detailed response DTO
     */
    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    @Mapping(target = "requestSource", expression = "java(entity.getRequestSource() != null ? entity.getRequestSource().name() : null)")
    @Mapping(target = "startTime", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getStartTime(), true))")
    @Mapping(target = "endTime", expression = "java(com.dalejandrov.sipsa.api.util.TimezoneUtil.convertToOffsetDateTime(entity.getEndTime(), true))")
    IngestionRunDetailResponse toDetailDto(IngestionRun entity);
}
