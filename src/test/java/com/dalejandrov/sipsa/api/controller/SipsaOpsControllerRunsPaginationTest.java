package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.application.service.AuditTrailService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionRunQueryService;
import com.dalejandrov.sipsa.application.service.IngestionTriggerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-054: {@code GET /api/internal/ingestion/runs} pagination contract — real MVC
 * dispatch, same {@code @SpringBootTest + @AutoConfigureMockMvc} pattern as
 * {@code InternalControllerRouteMappingTest}. {@link IngestionRunQueryService} is
 * mocked here (its own logic — {@code Pageable} construction, fixed sort, entity→DTO
 * mapping — is covered by {@code IngestionRunQueryServiceGetAllRunsTest}); this class
 * asserts the HTTP-layer contract: query param binding, the {@code ApiResponse}
 * envelope shape, and the error contract for a genuinely malformed parameter.
 * <p>
 * {@code page}/{@code size} out-of-range values (negative page, {@code size=0},
 * {@code size} above the maximum) are <b>clamped, not rejected</b> — this matches
 * {@link com.dalejandrov.sipsa.api.dto.request.CiudadQueryRequest} and every other
 * paginated request DTO in this codebase (the clamping happens in the record's compact
 * constructor, before Bean Validation would ever see an out-of-range value): this
 * story does not introduce a new, inconsistent validation behavior just for this one
 * endpoint. A genuinely malformed value (non-numeric) still produces the existing 400
 * error contract, exercised below.
 * <p>
 * The full 401/403/200-with-correct-scope security matrix for this exact endpoint
 * already exists in {@code InternalEndpointSecurityTest} — not duplicated here beyond
 * one minimal case of each, to confirm security still gates the new paginated handler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("TECH-054: GET /api/internal/ingestion/runs — pagination contract")
class SipsaOpsControllerRunsPaginationTest {

    private static final String RUNS_URL = "/api/internal/ingestion/runs";

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

    private static JwtRequestPostProcessor readToken() {
        return tokenWithScope("sipsa/ingestion.read");
    }

    private static IngestionRunDetailResponse detail(long runId) {
        return new IngestionRunDetailResponse(runId, "promediosSipsaCiudad", "2026-07-20",
                "SUCCEEDED", null, null, "req-" + runId, "MANUAL", 10, 10, 0, 0);
    }

    @Test
    @DisplayName("defaults (no query params): page=1, size=20 forwarded to the service")
    void defaults_noParams() throws Exception {
        when(runQueryService.getAllRuns(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get(RUNS_URL).with(readToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.pages").value(0));

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.dalejandrov.sipsa.api.dto.request.IngestionRunQueryRequest.class);
        verify(runQueryService).getAllRuns(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().page()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("page=1&size=20 explicit: identical result to defaults")
    void explicitPageAndSize() throws Exception {
        when(runQueryService.getAllRuns(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get(RUNS_URL).param("page", "1").param("size", "20").with(readToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    @DisplayName("page with content: results reflect the service's page content")
    void pageWithContent() throws Exception {
        Page<IngestionRunDetailResponse> page = new PageImpl<>(
                List.of(detail(3L), detail(2L)), PageRequest.of(0, 20), 2);
        when(runQueryService.getAllRuns(any())).thenReturn(page);

        mvc.perform(get(RUNS_URL).with(readToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.results[0].runId").value(3))
                .andExpect(jsonPath("$.results[1].runId").value(2))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("empty page: 200, empty results, zero count/pages, not an error")
    void emptyPage() throws Exception {
        when(runQueryService.getAllRuns(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get(RUNS_URL).with(readToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", org.hamcrest.Matchers.hasSize(0)))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.pages").value(0));
    }

    @Test
    @DisplayName("negative page: clamped to 1, not rejected (matches every other paginated endpoint)")
    void negativePage_clampedNotRejected() throws Exception {
        when(runQueryService.getAllRuns(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get(RUNS_URL).param("page", "-1").with(readToken()))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.dalejandrov.sipsa.api.dto.request.IngestionRunQueryRequest.class);
        verify(runQueryService).getAllRuns(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().page()).isEqualTo(1);
    }

    @Test
    @DisplayName("size=0: clamped to the default (20), not rejected")
    void sizeZero_clampedToDefault() throws Exception {
        when(runQueryService.getAllRuns(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get(RUNS_URL).param("size", "0").with(readToken()))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.dalejandrov.sipsa.api.dto.request.IngestionRunQueryRequest.class);
        verify(runQueryService).getAllRuns(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().size()).isEqualTo(20);
    }

    @Test
    @DisplayName("size above the maximum (100): clamped to 100, not rejected")
    void sizeAboveMaximum_clampedTo100() throws Exception {
        when(runQueryService.getAllRuns(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mvc.perform(get(RUNS_URL).param("size", "99999").with(readToken()))
                .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.dalejandrov.sipsa.api.dto.request.IngestionRunQueryRequest.class);
        verify(runQueryService).getAllRuns(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().size()).isEqualTo(100);
    }

    @Test
    @DisplayName("no token: 401")
    void noToken_401() throws Exception {
        mvc.perform(get(RUNS_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("wrong scope: 403")
    void wrongScope_403() throws Exception {
        mvc.perform(get(RUNS_URL).with(tokenWithScope("sipsa/audit.read")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("malformed size (non-numeric): 400 with the existing error contract - requestId, instance, fieldErrors, no stack trace")
    void malformedSize_400WithErrorContract() throws Exception {
        // IngestionRunQueryRequest is bound as a query-backed object (like every other
        // *QueryRequest in this codebase), so a value that can't convert to Integer is a
        // WebDataBinder failure -> MethodArgumentNotValidException -> the existing
        // VALIDATION_ERROR handler with a per-field fieldErrors map (not
        // MethodArgumentTypeMismatchException/TYPE_MISMATCH, which applies to simple
        // @RequestParam/@PathVariable scalars, not object binding).
        mvc.perform(get(RUNS_URL).param("size", "not-a-number").with(readToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.size").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(RUNS_URL))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at com.dalejandrov"))));
    }

    @Test
    @DisplayName("JSON envelope: exactly the standard ApiResponse pagination contract (count, pages, results; next/prev omitted when null)")
    void jsonEnvelope_matchesApiResponseContract() throws Exception {
        when(runQueryService.getAllRuns(any()))
                .thenReturn(new PageImpl<>(List.of(detail(1L)), PageRequest.of(0, 20), 1));

        mvc.perform(get(RUNS_URL).with(readToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.pages").value(1))
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.next").doesNotExist())
                .andExpect(jsonPath("$.prev").doesNotExist());
    }
}
