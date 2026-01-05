package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.infrastructure.config.PaginationConfig;
import com.dalejandrov.sipsa.api.dto.*;
import com.dalejandrov.sipsa.api.util.PaginationUtils;
import com.dalejandrov.sipsa.application.service.SipsaReadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for querying SIPSA (Sistema de Información de Precios y Abastecimiento del Sector Agropecuario) data.
 * <p>
 * This controller provides read-only access to agricultural price and supply data
 * from different sources and aggregation levels:
 * <ul>
 *   <li><b>Ciudad:</b> City-level pricing data</li>
 *   <li><b>Parcial:</b> Partial market data by municipality</li>
 *   <li><b>Mayoristas Semanal:</b> Weekly wholesale market data</li>
 *   <li><b>Mayoristas Mensual:</b> Monthly wholesale market data</li>
 *   <li><b>Abastecimientos Mensual:</b> Monthly supply data</li>
 * </ul>
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Standardized response format with pagination metadata</li>*
 *   <li>Date range filtering (single date or from/to)</li>
 *   <li>Multiple filter combinations (product, source, municipality)</li>
 *   <li>Configurable pagination (page size 1-100)</li>
 *   <li>Proper HTTP status codes via ResponseEntity</li>
 *   <li>Input validation with Jakarta Validation</li>
 * </ul>
 * <p>
 * <b>Response Format:</b>
 * <pre>
 * {
 *   "count": 150,
 *   "next": "<a href="http://api/endpoint?page=2">...</a>",
 *   "prev": null,
 *   "pages": 15,
 *   "results": [...]
 * }
 * </pre>
 *
 * @see SipsaReadService
 * @see ApiResponse
 * @see PaginationUtils
 * @see PaginationConfig
 */
@RestController
@RequestMapping("/api/sipsa")
@RequiredArgsConstructor
@Validated
public class SipsaRestController {

    private final SipsaReadService readService;
    private final PaginationConfig paginationConfig;

    /**
     * Root endpoint that lists all available API endpoints.
     * <p>
     * Provides autodiscoverable documentation of available resources with full URLs.
     *
     * @return list of available endpoints with their metadata and full URLs
     */
    @GetMapping
    public ResponseEntity<java.util.Map<String, List<EndpointInfo>>> getApiRoot() {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/sipsa")
                .build()
                .toUriString();

        List<EndpointInfo> endpoints = List.of(
                EndpointInfo.builder()
                        .name("ciudad")
                        .description("City-level pricing data with product and source filters")
                        .path(baseUrl + "/ciudad")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfo.builder()
                        .name("mayoristas-mensual")
                        .description("Monthly wholesale market data with price statistics")
                        .path(baseUrl + "/mayoristas/mensual")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfo.builder()
                        .name("mayoristas-semanal")
                        .description("Weekly wholesale market data with price ranges")
                        .path(baseUrl + "/mayoristas/semanal")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfo.builder()
                        .name("parcial")
                        .description("Partial market data by municipality with detailed pricing")
                        .path(baseUrl + "/parcial")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfo.builder()
                        .name("abastecimientos-mensual")
                        .description("Monthly supply data to wholesale markets")
                        .path(baseUrl + "/abastecimientos/mensual")
                        .methods(new String[]{"GET"})
                        .build()
        );

        return ResponseEntity.ok(java.util.Map.of("endpoints", endpoints));
    }

    /**
     * Retrieves city-level pricing data with optional filtering.
     * <p>
     * This endpoint returns price information collected at city level,
     * including average prices per product and capture date.
     * <p>
     * <b>Filtering Options:</b>
     * <ul>
     *   <li><b>Single date:</b> {@code fecha=2024-01-15}</li>
     *   <li><b>Date range:</b> {@code startDate=2024-01-01&endDate=2024-01-31}</li>
     *   <li><b>Product:</b> {@code artiId=123}</li>
     *   <li><b>Source:</b> {@code fuenId=45}</li>
     * </ul>
     * <p>
     * <b>Validation Rules:</b>
     * <ul>
     *   <li>Page must be ≥ 1</li>
     *   <li>Size must be between 1 and 100</li>
     *   <li>Product/Source IDs must be positive</li>
     *   <li>Dates must be in ISO format (YYYY-MM-DD)</li>
     *   <li>If using date range, endDate must be ≥ startDate</li>
     * </ul>
     *
     * @param fecha     optional filter by exact capture date (YYYY-MM-DD)
     * @param startDate optional filter by date range start (YYYY-MM-DD)
     * @param endDate   optional filter by date range end (YYYY-MM-DD)
     * @param artiId    optional filter by product ID (must be positive)
     * @param fuenId    optional filter by source ID (must be positive)
     * @param page      page number (1-based, default: 1)
     * @param size      page size (1-100, default: 20)
     * @param sort      optional sort field (e.g., "fechaCaptura,desc")
     * @return standardized response with paginated city price records
     */
    @GetMapping("/ciudad")
    public ResponseEntity<ApiResponse<SipsaCiudadDto>> getCiudad(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fecha,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            @Positive(message = "artiId must be a positive number")
            Long artiId,

            @RequestParam(required = false)
            @Positive(message = "fuenId must be a positive number")
            Long fuenId,

            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page must be >= 1")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size,

            @RequestParam(required = false)
            String sort) {

        Pageable pageable = paginationConfig.buildPageable(page, size, sort);
        Page<SipsaCiudadDto> resultPage = readService.getCiudad(
                fecha, startDate, endDate, artiId, fuenId, pageable
        );

        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves monthly wholesale market data.
     * <p>
     * This endpoint provides aggregated wholesale market prices on a monthly basis,
     * including minimum, maximum, and average prices per kilogram.
     * <p>
     * <b>Filtering Options:</b>
     * <ul>
     *   <li><b>Single month:</b> {@code fechaMes=2024-01-01}</li>
     *   <li><b>Date range:</b> {@code startDate=2024-01-01&endDate=2024-06-01}</li>
     *   <li><b>Product:</b> {@code artiId=123}</li>
     * </ul>
     *
     * @param fechaMes  optional filter by month start date (YYYY-MM-DD)
     * @param startDate optional filter by date range start
     * @param endDate   optional filter by date range end
     * @param artiId    optional filter by product ID
     * @param page      page number (1-based, default: 1)
     * @param size      page size (1-100, default: 20)
     * @param sort      optional sort field
     * @return standardized response with paginated monthly wholesale market records
     */
    @GetMapping("/mayoristas/mensual")
    public ResponseEntity<ApiResponse<SipsaMayoristasMensualDto>> getMayoristasMensual(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaMes,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            @Positive(message = "artiId must be a positive number")
            Long artiId,

            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page must be >= 1")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size,

            @RequestParam(required = false)
            String sort) {

        Pageable pageable = paginationConfig.buildPageable(page, size, sort);
        Page<SipsaMayoristasMensualDto> resultPage = readService.getMayoristasMensual(
                fechaMes, startDate, endDate, artiId, pageable
        );

        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves partial market data by municipality.
     * <p>
     * This endpoint provides detailed market information at municipality level,
     * including price ranges and product availability.
     * <p>
     * <b>Filtering Options:</b>
     * <ul>
     *   <li><b>Single date:</b> {@code fechaEncuesta=2024-01-15}</li>
     *   <li><b>Date range:</b> {@code startDate=2024-01-01&endDate=2024-01-31}</li>
     *   <li><b>Municipality:</b> {@code muniId=11001}</li>
     *   <li><b>Source:</b> {@code fuenId=45}</li>
     *   <li><b>Product:</b> {@code artiId=123}</li>
     * </ul>
     *
     * @param fechaEncuesta optional filter by survey date (YYYY-MM-DD)
     * @param startDate     optional filter by date range start
     * @param endDate       optional filter by date range end
     * @param muniId        optional filter by municipality ID
     * @param fuenId        optional filter by source ID
     * @param artiId        optional filter by product ID
     * @param page          page number (1-based, default: 1)
     * @param size          page size (1-100, default: 20)
     * @param sort          optional sort field
     * @return standardized response with paginated partial market records
     */
    @GetMapping("/parcial")
    public ResponseEntity<ApiResponse<SipsaParcialDto>> getParcial(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaEncuesta,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            @Positive(message = "muniId must be a positive number")
            Long muniId,

            @RequestParam(required = false)
            @Positive(message = "fuenId must be a positive number")
            Long fuenId,

            @RequestParam(required = false)
            @Positive(message = "artiId must be a positive number")
            Long artiId,

            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page must be >= 1")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size,

            @RequestParam(required = false)
            String sort) {

        Pageable pageable = paginationConfig.buildPageable(page, size, sort);
        Page<SipsaParcialDto> resultPage = readService.getParcial(
                fechaEncuesta, startDate, endDate, muniId, fuenId, artiId, pageable
        );

        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves weekly wholesale market data.
     * <p>
     * This endpoint provides wholesale market prices aggregated by week,
     * including price statistics per product and market.
     * <p>
     * <b>Filtering Options:</b>
     * <ul>
     *   <li><b>Single week:</b> {@code fechaIni=2024-01-01}</li>
     *   <li><b>Date range:</b> {@code startDate=2024-01-01&endDate=2024-01-31}</li>
     *   <li><b>Product:</b> {@code artiId=123}</li>
     *   <li><b>Source:</b> {@code fuenId=45}</li>
     * </ul>
     *
     * @param fechaIni  optional filter by week start date (YYYY-MM-DD)
     * @param startDate optional filter by date range start
     * @param endDate   optional filter by date range end
     * @param artiId    optional filter by product ID
     * @param fuenId    optional filter by source ID
     * @param page      page number (1-based, default: 1)
     * @param size      page size (1-100, default: 20)
     * @param sort      optional sort field
     * @return standardized response with paginated weekly wholesale market records
     */
    @GetMapping("/mayoristas/semanal")
    public ResponseEntity<ApiResponse<SipsaMayoristasSemanalDto>> getMayoristasSemanal(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaIni,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            @Positive(message = "artiId must be a positive number")
            Long artiId,

            @RequestParam(required = false)
            @Positive(message = "fuenId must be a positive number")
            Long fuenId,

            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page must be >= 1")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size,

            @RequestParam(required = false)
            String sort) {

        Pageable pageable = paginationConfig.buildPageable(page, size, sort);
        Page<SipsaMayoristasSemanalDto> resultPage = readService.getMayoristasSemanal(
                fechaIni, startDate, endDate, artiId, fuenId, pageable
        );

        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves monthly supply data.
     * <p>
     * This endpoint provides information about product supply volumes
     * to wholesale markets on a monthly basis.
     * <p>
     * <b>Filtering Options:</b>
     * <ul>
     *   <li><b>Single month:</b> {@code fechaMes=2024-01-01}</li>
     *   <li><b>Date range:</b> {@code startDate=2024-01-01&endDate=2024-06-01}</li>
     *   <li><b>Product:</b> {@code artiId=123}</li>
     *   <li><b>Source:</b> {@code fuenId=45}</li>
     * </ul>
     *
     * @param fechaMes  optional filter by month start date (YYYY-MM-DD)
     * @param startDate optional filter by date range start
     * @param endDate   optional filter by date range end
     * @param artiId    optional filter by product ID
     * @param fuenId    optional filter by source ID
     * @param page      page number (1-based, default: 1)
     * @param size      page size (1-100, default: 20)
     * @param sort      optional sort field
     * @return standardized response with paginated monthly supply records
     */
    @GetMapping("/abastecimientos/mensual")
    public ResponseEntity<ApiResponse<SipsaAbastecimientosMensualDto>> getAbastecimientosMensual(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fechaMes,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false)
            @Positive(message = "artiId must be a positive number")
            Long artiId,

            @RequestParam(required = false)
            @Positive(message = "fuenId must be a positive number")
            Long fuenId,

            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page must be >= 1")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be >= 1")
            @Max(value = 100, message = "size must be <= 100")
            int size,

            @RequestParam(required = false)
            String sort) {

        Pageable pageable = paginationConfig.buildPageable(page, size, sort);
        Page<SipsaAbastecimientosMensualDto> resultPage = readService.getAbastecimientosMensual(
                fechaMes, startDate, endDate, artiId, fuenId, pageable
        );

        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }
}
