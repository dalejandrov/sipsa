package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.application.service.AuditTrailService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionRunQueryService;
import com.dalejandrov.sipsa.application.service.IngestionTriggerService;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-022: real MVC dispatch (via {@code SipsaOpsController}) confirming the HTTP
 * contract for {@code GET /api/internal/ingestion/runs/{runId}} and
 * {@code POST /api/internal/ingestion/cancel/{runId}} — a genuinely missing run now
 * returns 404, not 422, while every other status this controller already produced is
 * unchanged. Uses the same {@code @SpringBootTest + @AutoConfigureMockMvc} pattern as
 * {@link InternalControllerRouteMappingTest} (real security filter chain, mocked
 * {@link JwtDecoder}) rather than {@code @WebMvcTest}, since a real security config is
 * exercised here (scope-gated endpoints), unlike the narrower slice test used for
 * TECH-021's exception-mapping-only coverage.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("TECH-022: SipsaOpsController — missing run returns 404, not 422")
class SipsaOpsControllerNotFoundTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private IngestionTriggerService triggerService;

    @MockitoBean
    private IngestionControlService controlService;

    @MockitoBean
    private IngestionRunQueryService runQueryService;

    @MockitoBean
    private AuditTrailService auditTrailService;

    private static JwtRequestPostProcessor tokenWithScope(String scope) {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    @Test
    @DisplayName("GET .../runs/{runId} for a missing run returns 404, code NOT_FOUND")
    void getRunStatus_missingRun_returns404() throws Exception {
        when(runQueryService.getRunStatus(999L))
                .thenThrow(new SipsaNotFoundException("Ingestion run not found: 999"));

        mvc.perform(get("/api/internal/ingestion/runs/999")
                        .with(tokenWithScope("sipsa/ingestion.read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ingestion run not found: 999"));
    }

    @Test
    @DisplayName("GET .../runs/{runId} for an existing run returns 200 — unchanged behavior")
    void getRunStatus_existingRun_returns200() throws Exception {
        IngestionRunDetailResponse response = new IngestionRunDetailResponse(
                42L, "promediosSipsaParcial", "2026-07-19", "SUCCEEDED",
                null, null, "req-1", "MANUAL", 100, 100, 0, 0);
        when(runQueryService.getRunStatus(42L)).thenReturn(response);

        mvc.perform(get("/api/internal/ingestion/runs/42")
                        .with(tokenWithScope("sipsa/ingestion.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(42))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    @DisplayName("GET .../runs/{runId} regression: a parse failure downstream still maps to 502 (TECH-021 untouched)")
    void getRunStatus_parseException_stillReturns502() throws Exception {
        when(runQueryService.getRunStatus(502L))
                .thenThrow(new SipsaParseException("Malformed XML from DANE SOAP response", null));

        mvc.perform(get("/api/internal/ingestion/runs/502")
                        .with(tokenWithScope("sipsa/ingestion.read")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"));
    }

    @Test
    @DisplayName("POST .../cancel/{runId} for a missing run returns 404, code NOT_FOUND")
    void cancelRun_missingRun_returns404() throws Exception {
        doThrow(new SipsaNotFoundException("Run not found: 999"))
                .when(controlService).cancelRun(999L);

        mvc.perform(post("/api/internal/ingestion/cancel/999")
                        .with(tokenWithScope("sipsa/ingestion.cancel")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Run not found: 999"));
    }

    @Test
    @DisplayName("POST .../cancel/{runId} regression: an existing but inactive run still returns 422 (business rule, not not-found)")
    void cancelRun_inactiveRun_stillReturns422() throws Exception {
        doThrow(new SipsaBusinessException("Run is not active (status: SUCCEEDED)"))
                .when(controlService).cancelRun(42L);

        mvc.perform(post("/api/internal/ingestion/cancel/42")
                        .with(tokenWithScope("sipsa/ingestion.cancel")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Run is not active (status: SUCCEEDED)"));
    }

    @Test
    @DisplayName("POST .../cancel/{runId} for an active run returns 200 — unchanged behavior")
    void cancelRun_activeRun_returns200() throws Exception {
        doNothing().when(controlService).cancelRun(7L);

        mvc.perform(post("/api/internal/ingestion/cancel/7")
                        .with(tokenWithScope("sipsa/ingestion.cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(7))
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }
}
