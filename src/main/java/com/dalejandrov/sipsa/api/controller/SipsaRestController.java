package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.dto.request.*;
import com.dalejandrov.sipsa.api.dto.response.*;
import com.dalejandrov.sipsa.api.util.PaginationUtils;
import com.dalejandrov.sipsa.application.service.SipsaReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * REST controller for querying SIPSA (Sistema de Información de Precios y Abastecimiento del Sector Agropecuario) data.
 * <p>
 * <b>Controller Responsibilities (HTTP Layer):</b>
 * <ul>
 *   <li>Handle HTTP requests and responses</li>
 *   <li>Map request parameters to DTOs (via Spring binding)</li>
 *   <li>Delegate ALL business logic to service layer</li>
 *   <li>Convert service responses to HTTP responses</li>
 *   <li>Always return ResponseEntity.ok() - exceptions handled by GlobalExceptionHandler</li>
 * </ul>
 * <p>
 * <b>NOT Responsible For:</b>
 * <ul>
 *   <li>❌ Business logic or validation</li>
 *   <li>❌ Data transformation or mapping</li>
 *   <li>❌ Pagination logic construction</li>
 *   <li>❌ Query specification building</li>
 * </ul>
 * <p>
 * All business logic is delegated to {@link SipsaReadService}.
 *
 * @see SipsaReadService
 * @see ApiResponse
 */
@RestController
@RequestMapping("/api/sipsa")
@RequiredArgsConstructor
@Validated
public class SipsaRestController {

    private final SipsaReadService readService;

    /**
     * Root endpoint that lists all available API endpoints.
     * <p>
     * Provides autodiscoverable documentation of available resources with full URLs.
     *
     * @return list of available endpoints with their metadata and full URLs
     */
    @GetMapping
    public ResponseEntity<java.util.Map<String, List<EndpointInfoResponse>>> getApiRoot() {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/sipsa")
                .build()
                .toUriString();

        List<EndpointInfoResponse> endpoints = List.of(
                EndpointInfoResponse.builder()
                        .name("ciudad")
                        .description("City-level pricing data with product and source filters")
                        .path(baseUrl + "/ciudad")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfoResponse.builder()
                        .name("mayoristas-mensual")
                        .description("Monthly wholesale market data with price statistics")
                        .path(baseUrl + "/mayoristas/mensual")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfoResponse.builder()
                        .name("mayoristas-semanal")
                        .description("Weekly wholesale market data with price ranges")
                        .path(baseUrl + "/mayoristas/semanal")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfoResponse.builder()
                        .name("parcial")
                        .description("Partial market data by municipality with detailed pricing")
                        .path(baseUrl + "/parcial")
                        .methods(new String[]{"GET"})
                        .build(),
                EndpointInfoResponse.builder()
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
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     *
     * @param request the query parameters encapsulated in a DTO
     * @return standardized response with paginated city price records
     */
    @GetMapping("/ciudad")
    public ResponseEntity<ApiResponse<SipsaCiudadResponse>> getCiudad(CiudadQueryRequest request) {
        Page<SipsaCiudadResponse> resultPage = readService.getCiudad(request);
        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves monthly wholesale market data.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     *
     * @param request the query parameters encapsulated in a DTO
     * @return standardized response with paginated monthly wholesale market records
     */
    @GetMapping("/mayoristas/mensual")
    public ResponseEntity<ApiResponse<SipsaMayoristasMensualResponse>> getMayoristasMensual(
            MayoristasMensualQueryRequest request) {

        Page<SipsaMayoristasMensualResponse> resultPage = readService.getMayoristasMensual(request);
        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves partial market data by municipality.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     *
     * @param request the query parameters encapsulated in a DTO
     * @return standardized response with paginated partial market records
     */
    @GetMapping("/parcial")
    public ResponseEntity<ApiResponse<SipsaParcialResponse>> getParcial(ParcialQueryRequest request) {
        Page<SipsaParcialResponse> resultPage = readService.getParcial(request);
        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves weekly wholesale market data.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     *
     * @param request the query parameters encapsulated in a DTO
     * @return standardized response with paginated weekly wholesale market records
     */
    @GetMapping("/mayoristas/semanal")
    public ResponseEntity<ApiResponse<SipsaMayoristasSemanalResponse>> getMayoristasSemanal(
            MayoristasSemanalQueryRequest request) {

        Page<SipsaMayoristasSemanalResponse> resultPage = readService.getMayoristasSemanal(request);
        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }

    /**
     * Retrieves monthly supply data.
     * <p>
     * <b>Controller Role:</b> Receive HTTP request, delegate to service, return HTTP 200.
     *
     * @param request the query parameters encapsulated in a DTO
     * @return standardized response with paginated monthly supply records
     */
    @GetMapping("/abastecimientos/mensual")
    public ResponseEntity<ApiResponse<SipsaAbastecimientosMensualResponse>> getAbastecimientosMensual(
            AbastecimientosMensualQueryRequest request) {

        Page<SipsaAbastecimientosMensualResponse> resultPage = readService.getAbastecimientosMensual(request);
        return ResponseEntity.ok(PaginationUtils.toApiResponse(resultPage));
    }
}
