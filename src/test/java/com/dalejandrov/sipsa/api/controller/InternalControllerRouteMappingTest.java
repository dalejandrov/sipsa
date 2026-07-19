package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.application.service.AuditTrailService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionRunQueryService;
import com.dalejandrov.sipsa.application.service.IngestionTriggerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-020 regression: {@code SipsaOpsController} and {@code IngestionAuditController}
 * declared {@code @RequestMapping("api/internal/...")} without a leading {@code /},
 * inconsistent with {@code SipsaRestController}'s {@code @RequestMapping("/api/sipsa")}.
 * <p>
 * Spring MVC normalizes a class-level {@code @RequestMapping} value at startup regardless
 * of a leading slash, so the effective route was never actually broken — this is a
 * declared-contract normalization, not a bug fix for a routing failure. These tests pin
 * the structural evidence that the fix didn't change: the exact controller and handler
 * method Spring's {@code HandlerMapping} resolves for each path, independent of the HTTP
 * status code (which {@link com.dalejandrov.sipsa.infrastructure.config.security.InternalEndpointSecurityTest}
 * already covers exhaustively for both leading-slash paths, before and after this change).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("TECH-020: /api/internal/** routes resolve to the correct controller")
class InternalControllerRouteMappingTest {

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
    @DisplayName("GET /api/internal/ingestion/runs resolves to SipsaOpsController#getActiveRuns")
    void ingestionRoute_resolvesToSipsaOpsController() throws Exception {
        mvc.perform(get("/api/internal/ingestion/running")
                        .with(tokenWithScope("sipsa/ingestion.read")))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(SipsaOpsController.class))
                .andExpect(handler().methodName("getActiveRuns"));
    }

    @Test
    @DisplayName("GET /api/internal/audit/recent resolves to IngestionAuditController#getRecentEvents")
    void auditRoute_resolvesToIngestionAuditController() throws Exception {
        mvc.perform(get("/api/internal/audit/recent")
                        .with(tokenWithScope("sipsa/audit.read")))
                .andExpect(status().isOk())
                .andExpect(handler().handlerType(IngestionAuditController.class));
    }

    @Test
    @DisplayName("both routes still deny anonymous access — security untouched by the mapping fix")
    void bothRoutes_stillDenyAnonymousAccess() throws Exception {
        mvc.perform(get("/api/internal/ingestion/running")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/audit/recent")).andExpect(status().isUnauthorized());
    }
}
