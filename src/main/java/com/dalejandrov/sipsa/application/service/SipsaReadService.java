package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.infrastructure.config.PaginationConfig;
import com.dalejandrov.sipsa.api.dto.*;
import com.dalejandrov.sipsa.api.mapper.SipsaApiMapper;
import com.dalejandrov.sipsa.domain.entity.*;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.*;
import com.dalejandrov.sipsa.infrastructure.specification.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Service for reading SIPSA data with filtering and pagination support.
 * <p>
 * Provides read-only access to all SIPSA data tables with:
 * <ul>
 *   <li>Dynamic filtering by date, product, source, and municipality</li>
 *   <li>Configurable pagination (max 1000 records)</li>
 *   <li>Timezone-aware date range queries</li>
 * </ul>
 * <p>
 * Uses <b>Template Method Pattern</b> via {@link #executeQuery} to eliminate duplication.
 *
 * @see SpecificationBuilder
 * @see PaginationConfig
 */
@Service
@RequiredArgsConstructor
public class SipsaReadService {

    private final SipsaCiudadRepository ciudadRepository;
    private final SipsaMayoristasMensualRepository mensualRepository;
    private final SipsaParcialRepository parcialRepository;
    private final SipsaMayoristasSemanalRepository semanalRepository;
    private final SipsaAbastecimientosMensualRepository abasRepository;
    private final SipsaApiMapper mapper;
    private final PaginationConfig paginationConfig;

    @Value("${sipsa.timezone:America/Bogota}")
    private String timezone;

    /**
     * Retrieves city-level pricing data with optional filtering.
     * <p>
     * Filters are combined with AND logic. Null filters are ignored.
     * <p>
     * <b>Date Filtering:</b>
     * <ul>
     *   <li>If {@code fecha} is provided, filters for exact date (full day)</li>
     *   <li>If {@code startDate} and {@code endDate} are provided, filters date range</li>
     *   <li>If both {@code fecha} and date range are provided, {@code fecha} takes precedence</li>
     * </ul>
     *
     * @param fecha     optional filter by exact capture date (full day in configured timezone)
     * @param startDate optional filter by date range start (inclusive)
     * @param endDate   optional filter by date range end (inclusive)
     * @param artiId    optional filter by product ID
     * @param fuenId    optional filter by source ID
     * @param pageable  pagination parameters (page number, size, sorting)
     * @return paginated list of city pricing DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaCiudadDto> getCiudad(LocalDate fecha, LocalDate startDate, LocalDate endDate,
                                          Long artiId, Long fuenId, Pageable pageable) {
        return executeQuery(
                pageable,
                ciudadRepository,
                mapper::toDto,
                () -> {
                    paginationConfig.validateIds(artiId, fuenId);
                    return SpecificationBuilder.<SipsaCiudad>builder(timezone)
                            .withDateOrRange("fechaCaptura", fecha, startDate, endDate)
                            .withAttribute("artiId", artiId)
                            .withAttribute("fuenId", fuenId)
                            .build();
                }
        );
    }

    /**
     * Retrieves monthly wholesale market data with optional filtering.
     *
     * @param fechaMes  optional filter by exact month start date
     * @param startDate optional filter by date range start
     * @param endDate   optional filter by date range end
     * @param artiId    optional filter by product ID
     * @param pageable  pagination parameters
     * @return paginated list of monthly wholesale DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaMayoristasMensualDto> getMayoristasMensual(LocalDate fechaMes, LocalDate startDate,
                                                                LocalDate endDate, Long artiId, Pageable pageable) {
        return executeQuery(
                pageable,
                mensualRepository,
                mapper::toDto,
                () -> {
                    paginationConfig.validateIds(artiId);
                    return SpecificationBuilder.<SipsaMayoristasMensual>builder(timezone)
                            .withDateOrRange("fechaMesIni", fechaMes, startDate, endDate)
                            .withAttribute("artiId", artiId)
                            .build();
                }
        );
    }

    /**
     * Retrieves partial market data by municipality with optional filtering.
     *
     * @param fechaEncuesta optional filter by exact survey date
     * @param startDate     optional filter by date range start
     * @param endDate       optional filter by date range end
     * @param muniId        optional filter by municipality ID
     * @param fuenId        optional filter by source ID
     * @param artiId        optional filter by product ID
     * @param pageable      pagination parameters
     * @return paginated list of partial market DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaParcialDto> getParcial(LocalDate fechaEncuesta, LocalDate startDate, LocalDate endDate,
                                            Long muniId, Long fuenId, Long artiId, Pageable pageable) {
        return executeQuery(
                pageable,
                parcialRepository,
                mapper::toDto,
                () -> {
                    paginationConfig.validateIds(artiId, fuenId, muniId);
                    return SpecificationBuilder.<SipsaParcial>builder(timezone)
                            .withDateOrRange("enmaFecha", fechaEncuesta, startDate, endDate)
                            .withAttribute("muniId", muniId)
                            .withAttribute("fuenId", fuenId)
                            .withAttribute("artiId", artiId)
                            .build();
                }
        );
    }

    /**
     * Retrieves weekly wholesale market data with optional filtering.
     *
     * @param fechaIni  optional filter by exact week start date
     * @param startDate optional filter by date range start
     * @param endDate   optional filter by date range end
     * @param artiId    optional filter by product ID
     * @param fuenId    optional filter by source ID
     * @param pageable  pagination parameters
     * @return paginated list of weekly wholesale DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaMayoristasSemanalDto> getMayoristasSemanal(LocalDate fechaIni, LocalDate startDate,
                                                                LocalDate endDate, Long artiId, Long fuenId,
                                                                Pageable pageable) {
        return executeQuery(
                pageable,
                semanalRepository,
                mapper::toDto,
                () -> {
                    paginationConfig.validateIds(artiId, fuenId);
                    return SpecificationBuilder.<SipsaMayoristasSemanal>builder(timezone)
                            .withDateOrRange("fechaIni", fechaIni, startDate, endDate)
                            .withAttribute("artiId", artiId)
                            .withAttribute("fuenId", fuenId)
                            .build();
                }
        );
    }

    /**
     * Retrieves monthly supply data with optional filtering.
     *
     * @param fechaMes  optional filter by exact month start date
     * @param startDate optional filter by date range start
     * @param endDate   optional filter by date range end
     * @param artiId    optional filter by product ID
     * @param fuenId    optional filter by source ID
     * @param pageable  pagination parameters
     * @return paginated list of monthly supply DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaAbastecimientosMensualDto> getAbastecimientosMensual(LocalDate fechaMes, LocalDate startDate,
                                                                          LocalDate endDate, Long artiId,
                                                                          Long fuenId, Pageable pageable) {
        return executeQuery(
                pageable,
                abasRepository,
                mapper::toDto,
                () -> {
                    paginationConfig.validateIds(artiId, fuenId);
                    return SpecificationBuilder.<SipsaAbastecimientosMensual>builder(timezone)
                            .withDateOrRange("fechaMesIni", fechaMes, startDate, endDate)
                            .withAttribute("artiId", artiId)
                            .withAttribute("fuenId", fuenId)
                            .build();
                }
        );
    }

    /**
     * Template method that executes a query with common logic.
     * <p>
     * Eliminates code duplication by handling validation, query execution,
     * and DTO mapping in one place.
     *
     * @param pageable     pagination parameters
     * @param repository   JPA repository to query
     * @param mapper       entity to DTO converter
     * @param specSupplier builds the query specification
     * @return paginated results as DTOs
     */
    private <E, D> Page<D> executeQuery(
            Pageable pageable,
            JpaSpecificationExecutor<E> repository,
            Function<E, D> mapper,
            Supplier<Specification<E>> specSupplier) {

        paginationConfig.validatePageable(pageable);
        Specification<E> spec = specSupplier.get();
        return repository.findAll(spec, pageable).map(mapper);
    }
}
