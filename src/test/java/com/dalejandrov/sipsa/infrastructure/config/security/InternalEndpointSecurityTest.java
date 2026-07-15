package com.dalejandrov.sipsa.infrastructure.config.security;

import com.dalejandrov.sipsa.application.service.AuditTrailService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionRunQueryService;
import com.dalejandrov.sipsa.application.service.IngestionTriggerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the application security layer (ADR-002, TECH-001/TECH-002):
 * scoped JWT authorization on {@code /api/internal/**}, public functional API, actuator
 * policy, stateless behavior, and the JSON 401/403 contract.
 * <p>
 * Authentication is injected with spring-security-test's {@code jwt()} post-processor
 * (no real tokens, no issuer contact). The {@code JwtDecoder} is mocked so the
 * invalid-token path deterministically exercises the 401 flow; the decoder's real
 * validators (issuer, {@code token_use}, client allowlist) are unit-tested in
 * {@link SipsaJwtValidatorsTest}. Controller services are mocked: this class asserts
 * security semantics, not business behavior.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Internal endpoint security (scoped JWT, ADR-002)")
class InternalEndpointSecurityTest {

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

    // -----------------------------------------------------------------------
    // Public surface
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("GET /api/sipsa/ciudad without any token -> 200 (API key metering lives in API Gateway)")
        void functionalEndpoint_noToken_ok() throws Exception {
            mvc.perform(get("/api/sipsa/ciudad"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /actuator/health without any token -> not 401/403 (container healthchecks)")
        void health_noToken_accessible() throws Exception {
            int status = mvc.perform(get("/actuator/health"))
                    .andReturn().getResponse().getStatus();

            org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
        }
    }

    // -----------------------------------------------------------------------
    // 401 — missing or invalid credentials
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("401 Unauthorized")
    class Unauthorized {

        @Test
        @DisplayName("internal endpoint without token -> 401 JSON per the error contract, no HTML, no cookie")
        void internal_noToken_401Json() throws Exception {
            mvc.perform(post("/api/internal/ingestion/run").param("method", "promediosSipsaCiudad"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(content().string(not(containsString("<html"))))
                    .andExpect(content().string(not(containsString("Exception"))));
        }

        @Test
        @DisplayName("internal endpoint with an invalid bearer token -> 401, same generic body")
        void internal_invalidToken_401() throws Exception {
            when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("simulated invalid token"));

            mvc.perform(get("/api/internal/ingestion/runs")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                    .andExpect(content().string(not(containsString("simulated"))));
        }

        @Test
        @DisplayName("audit endpoint without token -> 401")
        void audit_noToken_401() throws Exception {
            mvc.perform(get("/api/internal/audit/recent"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("undeclared route -> denied (default deny), 401 for anonymous")
        void undeclaredRoute_denied() throws Exception {
            mvc.perform(get("/not-a-declared-route"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("actuator metrics without token -> 401")
        void actuatorMetrics_noToken_401() throws Exception {
            mvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // -----------------------------------------------------------------------
    // 403 — valid token, missing scope
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("403 Forbidden (valid token, wrong scope)")
    class Forbidden {

        @Test
        @DisplayName("run with a read-only scope -> 403 JSON, scope name not revealed")
        void run_withReadScope_403() throws Exception {
            mvc.perform(post("/api/internal/ingestion/run")
                            .param("method", "promediosSipsaCiudad")
                            .with(tokenWithScope("sipsa/ingestion.read")))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith("application/json"))
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                    .andExpect(content().string(not(containsString("sipsa/ingestion.execute"))));
        }

        @Test
        @DisplayName("cancel with the execute scope -> 403 (cancel requires its own scope)")
        void cancel_withExecuteScope_403() throws Exception {
            mvc.perform(post("/api/internal/ingestion/cancel/5")
                            .with(tokenWithScope("sipsa/ingestion.execute")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("audit with an ingestion scope -> 403 (audit requires sipsa/audit.read)")
        void audit_withIngestionScope_403() throws Exception {
            mvc.perform(get("/api/internal/audit/recent")
                            .with(tokenWithScope("sipsa/ingestion.read")))
                    .andExpect(status().isForbidden());
        }
    }

    // -----------------------------------------------------------------------
    // 2xx — correct scope per operation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Authorized access with the required scope")
    class Authorized {

        @Test
        @DisplayName("run with sipsa/ingestion.execute -> 202, stateless (no Set-Cookie)")
        void run_withExecuteScope_202() throws Exception {
            mvc.perform(post("/api/internal/ingestion/run")
                            .param("method", "promediosSipsaCiudad")
                            .with(tokenWithScope("sipsa/ingestion.execute")))
                    .andExpect(status().isAccepted())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }

        @Test
        @DisplayName("cancel with sipsa/ingestion.cancel -> 200")
        void cancel_withCancelScope_200() throws Exception {
            mvc.perform(post("/api/internal/ingestion/cancel/5")
                            .with(tokenWithScope("sipsa/ingestion.cancel")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("runs listing with sipsa/ingestion.read -> 200")
        void runs_withReadScope_200() throws Exception {
            mvc.perform(get("/api/internal/ingestion/runs")
                            .with(tokenWithScope("sipsa/ingestion.read")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("audit with sipsa/audit.read -> 200")
        void audit_withAuditScope_200() throws Exception {
            mvc.perform(get("/api/internal/audit/recent")
                            .with(tokenWithScope("sipsa/audit.read")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("actuator metrics with any valid access token -> 200")
        void actuatorMetrics_withToken_200() throws Exception {
            mvc.perform(get("/actuator/metrics").with(jwt()))
                    .andExpect(status().isOk());
        }
    }
}
