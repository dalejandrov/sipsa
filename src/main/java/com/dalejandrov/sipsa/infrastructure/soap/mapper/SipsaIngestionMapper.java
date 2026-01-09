package com.dalejandrov.sipsa.infrastructure.soap.mapper;

import com.dalejandrov.sipsa.domain.entity.*;
import com.dalejandrov.sipsa.infrastructure.soap.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;

/**
 * MapStruct mapper for converting SOAP DTOs to domain entities.
 * <p>
 * This mapper handles the transformation of immutable record DTOs (parsed from
 * SOAP XML) into JPA entity objects ready for persistence. It provides:
 * <ul>
 *   <li>Type-safe mapping with compile-time checking</li>
 *   <li>Custom field transformations (timestamps, hashes, etc.)</li>
 *   <li>Automatic null handling</li>
 *   <li>Source tracking via runId</li>
 * </ul>
 * <p>
 * <b>Key Transformations:</b>
 * <ul>
 *   <li>Epoch millis (Long) → Instant for timestamp fields</li>
 *   <li>Record fields → Entity fields with proper naming</li>
 *   <li>Injection of ingestion metadata (runId, timestamps)</li>
 * </ul>
 * <p>
 * <b>Usage Pattern:</b>
 * <pre>{@code
 * @Autowired
 * private SipsaIngestionMapper mapper;
 *
 * SipsaCiudad entity = mapper.toEntity(record, runId);
 * repository.save(entity);
 * }</pre>
 * <p>
 * MapStruct generates the implementation at compile-time for optimal performance.
 *
 * @see com.dalejandrov.sipsa.infrastructure.soap.dto
 * @see com.dalejandrov.sipsa.domain.entity
 */
@Mapper(componentModel = "spring")
public interface SipsaIngestionMapper {

    /**
     * Converts a city pricing record to a JPA entity.
     * <p>
     * Maps SOAP DTO fields to entity fields, converting epoch milliseconds
     * to Instant timestamps. Auto-generated fields (id, fechaIngestion) are
     * handled by JPA on persist.
     *
     * @param record the parsed city pricing record from SOAP
     * @param runId the ingestion run identifier for tracking
     * @return mapped SipsaCiudad entity ready for persistence
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ingestionRunId", source = "runId")
    @Mapping(target = "fechaSincronizacion", ignore = true)
    @Mapping(target = "fechaCaptura", source = "record.fechaCaptura", qualifiedByName = "millisToInstant")
    @Mapping(target = "fechaCreacion", source = "record.fechaCreacion", qualifiedByName = "millisToInstant")
    SipsaCiudad toEntity(SipsaCiudadRecord record, Long runId);

    /**
     * Converts a partial municipal market record to a JPA entity.
     * <p>
     * Maps SOAP DTO fields to entity fields with custom hash and survey date.
     * The hash is used for deduplication across ingestion runs.
     *
     * @param record the parsed partial market record from SOAP
     * @param fechaEncuesta parsed survey date as Instant
     * @param runId the ingestion run identifier for tracking
     * @return mapped SipsaParcial entity ready for persistence
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ingestionRunId", source = "runId")
    @Mapping(target = "enmaFecha", source = "fechaEncuesta")
    @Mapping(target = "deptNombre", source = "record.deptNombre")
    @Mapping(target = "grupNombre", source = "record.grupNombre")
    @Mapping(target = "artiNombre", source = "record.artiNombre")
    @Mapping(target = "fechaSincronizacion", ignore = true)
    @Mapping(target = "keyHash", expression = "java(computeKeyHash(record, fechaEncuesta))")
    SipsaParcial toEntity(SipsaParcialRecord record, Instant fechaEncuesta, Long runId);

    /**
     * Converts a weekly wholesale market record to a JPA entity.
     * <p>
     * Maps SOAP DTO fields to entity fields, converting epoch milliseconds
     * to Instant timestamps. Handles optional tmpMayoSemId for deduplication.
     *
     * @param record the parsed weekly wholesale record from SOAP
     * @param runId the ingestion run identifier for tracking
     * @return mapped SipsaMayoristasSemanal entity ready for persistence
     */
    @Mapping(target = "ingestionRunId", source = "runId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaIni", source = "record.fechaIni", qualifiedByName = "millisToInstant")
    @Mapping(target = "fechaCreacion", source = "record.fechaCreacion", qualifiedByName = "millisToInstant")
    @Mapping(target = "fechaSincronizacion", ignore = true)
    SipsaMayoristasSemanal toEntity(SipsaSemanaRecord record, Long runId);

    /**
     * Converts a monthly wholesale market record to a JPA entity.
     * <p>
     * Maps SOAP DTO fields to entity fields, converting epoch milliseconds
     * to Instant timestamps. Handles optional tmpMayoMesId for deduplication.
     *
     * @param record the parsed monthly wholesale record from SOAP
     * @param runId the ingestion run identifier for tracking
     * @return mapped SipsaMayoristasMensual entity ready for persistence
     */
    @Mapping(target = "ingestionRunId", source = "runId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaMesIni", source = "record.fechaMesIni", qualifiedByName = "millisToInstant")
    @Mapping(target = "fechaCreacion", source = "record.fechaCreacion", qualifiedByName = "millisToInstant")
    @Mapping(target = "fechaSincronizacion", ignore = true)
    SipsaMayoristasMensual toEntity(SipsaMayoristasMensualRecord record, Long runId);

    /**
     * Converts a monthly supply record to a JPA entity.
     * <p>
     * Maps SOAP DTO fields to entity fields, converting epoch milliseconds
     * to Instant timestamps. Supply data focuses on quantity (tons) rather
     * than pricing.
     *
     * @param record the parsed monthly supply record from SOAP
     * @param runId the ingestion run identifier for tracking
     * @return mapped SipsaAbastecimientosMensual entity ready for persistence
     */
    @Mapping(target = "ingestionRunId", source = "runId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaSincronizacion", ignore = true)
    @Mapping(target = "fechaMesIni", source = "record.fechaMes", qualifiedByName = "millisToInstant")
    @Mapping(target = "fechaCreacion", source = "record.fechaCreacion", qualifiedByName = "millisToInstant")
    SipsaAbastecimientosMensual toEntity(SipsaAbasRecord record, Long runId);

    /**
     * Converts epoch milliseconds to Instant timestamp.
     * <p>
     * This is a custom conversion method used by MapStruct when the
     * {@code @Named("millisToInstant")} qualifier is specified in mappings.
     * <p>
     * Handles null values gracefully, returning null for null input.
     *
     * @param millis epoch milliseconds from SOAP response (nullable)
     * @return Instant timestamp, or null if input is null
     */
    @Named("millisToInstant")
    default Instant millisToInstant(Long millis) {
        return millis != null ? Instant.ofEpochMilli(millis) : null;
    }

    /**
     * Computes a hash for the business key of SipsaParcial records.
     * <p>
     * The hash is based on (muniId, fuenId, futiId, idArtiSemana, enmaFecha, artiNombre)
     * to ensure idempotent processing and prevent duplicates.
     *
     * @param record the parsed partial market record
     * @param fechaEncuesta the survey date
     * @return hash code as String
     */
    default String computeKeyHash(SipsaParcialRecord record, Instant fechaEncuesta) {
        String key = record.muniId() + "|" + record.fuenId() + "|" + record.futiId() + "|" +
                     record.idArtiSemana() + "|" + fechaEncuesta + "|" + record.artiNombre();
        return key;
    }
}
