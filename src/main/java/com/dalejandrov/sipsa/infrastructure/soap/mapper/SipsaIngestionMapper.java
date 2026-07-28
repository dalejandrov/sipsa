package com.dalejandrov.sipsa.infrastructure.soap.mapper;

import com.dalejandrov.sipsa.domain.entity.*;
import com.dalejandrov.sipsa.infrastructure.soap.dto.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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
     * Fixed business zone (ADR-008/TECH-104) used to resolve the 4 DANE calendar-date
     * fields from their raw epoch-millis wire representation. Never
     * {@code ZoneId.systemDefault()} — these are Colombia dates by definition.
     */
    ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

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
    @Mapping(target = "fechaCaptura", source = "record.fechaCaptura", qualifiedByName = "millisToBusinessLocalDate")
    @Mapping(target = "fechaCreacion", source = "record.fechaCreacion", qualifiedByName = "millisToInstant")
    SipsaCiudad toEntity(SipsaCiudadRecord record, Long runId);

    /**
     * Converts a partial municipal market record to a JPA entity.
     * <p>
     * Maps SOAP DTO fields to entity fields with custom hash and survey date.
     * The hash is used for deduplication across ingestion runs.
     *
     * @param record the parsed partial market record from SOAP
     * @param fechaEncuesta parsed survey calendar date (TECH-104 — resolved by the caller
     *     in the fixed business zone, no longer an Instant)
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
    SipsaParcial toEntity(SipsaParcialRecord record, LocalDate fechaEncuesta, Long runId);

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
    @Mapping(target = "fechaIni", source = "record.fechaIni", qualifiedByName = "millisToBusinessLocalDate")
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
    @Mapping(target = "fechaMesIni", source = "record.fechaMesIni", qualifiedByName = "millisToBusinessLocalDate")
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
    @Mapping(target = "fechaMesIni", source = "record.fechaMes", qualifiedByName = "millisToBusinessLocalDate")
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
     * Converts epoch milliseconds to the calendar date they represent in the fixed
     * business zone (ADR-008/TECH-104), for the 4 DANE calendar-date fields
     * ({@code fechaCaptura}, {@code fechaMesIni}, {@code fechaIni}) — never an
     * {@link Instant}, and never converted in any other zone.
     * <p>
     * Handles null values gracefully, returning null for null input.
     *
     * @param millis epoch milliseconds from SOAP response (nullable)
     * @return the calendar date in {@link #BUSINESS_ZONE}, or null if input is null
     */
    @Named("millisToBusinessLocalDate")
    default LocalDate millisToBusinessLocalDate(Long millis) {
        return millis != null ? Instant.ofEpochMilli(millis).atZone(BUSINESS_ZONE).toLocalDate() : null;
    }

    /**
     * Computes the deterministic business-key hash for a SipsaParcial record (ADR-001).
     * <p>
     * Same business inputs always produce the same hash, enabling the skip-first
     * deduplication in {@code SipsaParcialRepository.batchUpsert()}. The caller must
     * have rejected records with missing key fields or an unparseable survey date.
     * <p>
     * <b>TECH-104:</b> {@code fechaEncuesta} changed from {@code Instant} to
     * {@code LocalDate}, which changed {@link ParcialKeyHash}'s hash payload (v1 → v2,
     * see its Javadoc) — key hashes computed before this change do not match key hashes
     * computed after it for the same logical record. Accepted, documented risk; see the
     * V5 migration's header comment.
     *
     * @param record the parsed partial market record
     * @param fechaEncuesta the parsed survey date (never null at this point)
     * @return lowercase hex SHA-256 of the natural key, 64 characters
     */
    default String computeKeyHash(SipsaParcialRecord record, LocalDate fechaEncuesta) {
        return ParcialKeyHash.compute(
                record.muniId(), record.fuenId(), record.futiId(), record.idArtiSemana(), fechaEncuesta);
    }
}
