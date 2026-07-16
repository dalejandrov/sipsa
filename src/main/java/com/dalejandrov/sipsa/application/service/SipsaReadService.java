package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.response.*;
import com.dalejandrov.sipsa.api.dto.request.*;
import com.dalejandrov.sipsa.api.mapper.SipsaAbastecimientosMensualMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaCiudadMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaMayoristasMensualMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaMayoristasSemanalMapper;
import com.dalejandrov.sipsa.api.mapper.SipsaParcialMapper;
import com.dalejandrov.sipsa.domain.entity.*;
import com.dalejandrov.sipsa.infrastructure.config.PaginationConfig;
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

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Application service for reading SIPSA data with filtering and pagination.
 * <p>
 * This service encapsulates all business logic for querying SIPSA data,
 * providing a clean interface for controllers without exposing infrastructure details.
 * <p>
 * <b>Responsibilities:</b>
 * <ul>
 *   <li>Validate business rules (IDs, pagination limits)</li>
 *   <li>Build query specifications from request DTOs</li>
 *   <li>Coordinate with repositories for data access</li>
 *   <li>Map entities to response DTOs</li>
 *   <li>Handle pagination logic internally</li>
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
    private final SipsaCiudadMapper ciudadMapper;
    private final SipsaMayoristasMensualMapper mensualMapper;
    private final SipsaParcialMapper parcialMapper;
    private final SipsaMayoristasSemanalMapper semanalMapper;
    private final SipsaAbastecimientosMensualMapper abasMapper;
    private final PaginationConfig paginationConfig;

    @Value("${sipsa.timezone:America/Bogota}")
    private String timezone;

    /**
     * Retrieves city-level pricing data with optional filtering and pagination.
     * <p>
     * This method encapsulates all business logic including:
     * <ul>
     *   <li>Pagination parameters validation and construction</li>
     *   <li>Business rules validation (positive IDs)</li>
     *   <li>Query specification building</li>
     *   <li>Entity to DTO mapping</li>
     * </ul>
     *
     * @param request the query request containing filters and pagination parameters
     * @return paginated list of city pricing response DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaCiudadResponse> getCiudad(CiudadQueryRequest request) {
        Pageable pageable = paginationConfig.buildPageable(request.page(), request.size(), request.sort());

        return executeQuery(
                pageable,
                ciudadRepository,
                ciudadMapper::toDto,
                () -> {
                    paginationConfig.validateIds(request.artiId(), request.fuenId());
                    return SpecificationBuilder.<SipsaCiudad>builder(timezone)
                            .withAttribute("ciudad", request.ciudad())
                            .withAttribute("producto", request.producto())
                            .withAttribute("codProducto", request.artiId())
                            .withDateOrRange("fechaCaptura", request.fecha(), request.startDate(), request.endDate())
                            .build();
                }
        );
    }

    /**
     * Retrieves monthly wholesale market data with optional filtering and pagination.
     *
     * @param request the query request containing filters and pagination parameters
     * @return paginated list of monthly wholesale response DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaMayoristasMensualResponse> getMayoristasMensual(MayoristasMensualQueryRequest request) {
        Pageable pageable = paginationConfig.buildPageable(request.page(), request.size(), request.sort());

        return executeQuery(
                pageable,
                mensualRepository,
                mensualMapper::toDto,
                () -> {
                    paginationConfig.validateIds(request.artiId());
                    return SpecificationBuilder.<SipsaMayoristasMensual>builder(timezone)
                            .withDateOrRange("fechaMesIni", request.fechaMes(), request.startDate(), request.endDate())
                            .withAttribute("artiId", request.artiId())
                            .withAttribute("artiNombre", request.artiNombre())
                            .withAttribute("fuenNombre", request.fuenNombre())
                            .build();
                }
        );
    }

    /**
     * Retrieves partial market data by municipality with optional filtering and pagination.
     *
     * @param request the query request containing filters and pagination parameters
     * @return paginated list of partial market response DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaParcialResponse> getParcial(ParcialQueryRequest request) {
        Pageable pageable = paginationConfig.buildPageable(request.page(), request.size(), request.sort());

        return executeQuery(
                pageable,
                parcialRepository,
                parcialMapper::toDto,
                () -> {
                    /* TECH-113: muniId is TEXT (DIVIPOLA codes keep leading zeros) and the
                     * article filter targets the real entity attribute `idArtiSemana`
                     * (`artiId` is a validated compatibility alias, resolved in the DTO). */
                    paginationConfig.validateIds(request.fuenId(), request.idArtiSemana(), request.artiId());
                    return SpecificationBuilder.<SipsaParcial>builder(timezone)
                            .withDateOrRange("enmaFecha", request.fechaEncuesta(), request.startDate(), request.endDate())
                            .withAttribute("muniId", request.validatedMuniId())
                            .withAttribute("fuenId", request.fuenId())
                            .withAttribute("idArtiSemana", request.effectiveArticleId())
                            .withAttribute("muniNombre", request.muniNombre())
                            .withAttribute("deptNombre", request.deptNombre())
                            .withAttribute("fuenNombre", request.fuenNombre())
                            .withAttribute("artiNombre", request.artiNombre())
                            .withAttribute("grupNombre", request.grupNombre())
                            .build();
                }
        );
    }

    /**
     * Retrieves weekly wholesale market data with optional filtering and pagination.
     *
     * @param request the query request containing filters and pagination parameters
     * @return paginated list of weekly wholesale response DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaMayoristasSemanalResponse> getMayoristasSemanal(MayoristasSemanalQueryRequest request) {
        Pageable pageable = paginationConfig.buildPageable(request.page(), request.size(), request.sort());

        return executeQuery(
                pageable,
                semanalRepository,
                semanalMapper::toDto,
                () -> {
                    paginationConfig.validateIds(request.artiId(), request.fuenId());
                    return SpecificationBuilder.<SipsaMayoristasSemanal>builder(timezone)
                            .withDateOrRange("fechaIni", request.fechaIni(), request.startDate(), request.endDate())
                            .withAttribute("artiId", request.artiId())
                            .withAttribute("fuenId", request.fuenId())
                            .withAttribute("artiNombre", request.artiNombre())
                            .withAttribute("fuenNombre", request.fuenNombre())
                            .build();
                }
        );
    }

    /**
     * Retrieves monthly supply data with optional filtering and pagination.
     *
     * @param request the query request containing filters and pagination parameters
     * @return paginated list of monthly supply response DTOs
     */
    @Transactional(readOnly = true)
    public Page<SipsaAbastecimientosMensualResponse> getAbastecimientosMensual(AbastecimientosMensualQueryRequest request) {
        Pageable pageable = paginationConfig.buildPageable(request.page(), request.size(), request.sort());

        return executeQuery(
                pageable,
                abasRepository,
                abasMapper::toDto,
                () -> {
                    paginationConfig.validateIds(request.artiId(), request.fuenId());
                    return SpecificationBuilder.<SipsaAbastecimientosMensual>builder(timezone)
                            .withDateOrRange("fechaMesIni", request.fechaMes(), request.startDate(), request.endDate())
                            .withAttribute("artiId", request.artiId())
                            .withAttribute("fuenId", request.fuenId())
                            .withAttribute("artiNombre", request.artiNombre())
                            .withAttribute("fuenNombre", request.fuenNombre())
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
