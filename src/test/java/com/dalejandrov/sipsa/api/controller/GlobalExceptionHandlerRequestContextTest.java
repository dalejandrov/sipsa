package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-023: {@code requestId} and {@code instance} are now present on every
 * {@code GlobalExceptionHandler}-produced error body, additively — every status, error
 * {@code code}, message, and existing field from TECH-021/TECH-022 is unchanged.
 * <p>
 * {@code requestId} comes from {@link com.dalejandrov.sipsa.api.filter.RequestIdFilter},
 * which {@code @WebMvcTest} picks up automatically (like {@code TimezoneFilter} already
 * did for TECH-020/021's slices) — real MVC dispatch through the actual filter, not a
 * stub. {@code instance} is asserted against the exact request path
 * ({@code HttpServletRequest#getRequestURI()}).
 */
@WebMvcTest(
        controllers = RequestContextThrowingTestController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@DisplayName("TECH-023: GlobalExceptionHandler — requestId and instance on every error body")
class GlobalExceptionHandlerRequestContextTest {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("SipsaParseException: 502, requestId present, instance correct")
    void parseException_hasRequestIdAndInstance() throws Exception {
        mvc.perform(get("/test/context/parse-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/parse-error"));
    }

    @Test
    @DisplayName("SipsaNotFoundException: 404, requestId present, instance correct")
    void notFoundException_hasRequestIdAndInstance() throws Exception {
        mvc.perform(get("/test/context/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ingestion run not found: 999"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/not-found"));
    }

    @Test
    @DisplayName("SipsaBusinessException: 422, new fields present, prior contract preserved")
    void businessException_hasNewFieldsAndPreservesPriorContract() throws Exception {
        mvc.perform(get("/test/context/business-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Content"))
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Run already succeeded"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/business-error"));
    }

    @Test
    @DisplayName("Bean Validation error: new fields present, prior fieldErrors detail preserved")
    void validationError_hasNewFieldsAndPreservesFieldErrors() throws Exception {
        mvc.perform(post("/test/context/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.name").value("name must not be blank"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/validate"));
    }

    @Test
    @DisplayName("generic exception: new fields present, no stack trace leaked")
    void genericException_hasNewFields_noStackTraceLeaked() throws Exception {
        mvc.perform(get("/test/context/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/boom"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret stack trace"))));
    }

    @Test
    @DisplayName("correlation: an incoming X-Request-Id header is echoed verbatim in the error body and response header")
    void correlation_incomingHeaderPropagatesToBodyAndResponseHeader() throws Exception {
        String clientRequestId = "client-supplied-correlation-id-123";

        mvc.perform(get("/test/context/not-found").header(REQUEST_ID_HEADER, clientRequestId))
                .andExpect(status().isNotFound())
                .andExpect(header().string(REQUEST_ID_HEADER, clientRequestId))
                .andExpect(jsonPath("$.requestId").value(clientRequestId));
    }

    @Test
    @DisplayName("correlation: without an incoming header, a non-blank ID is generated — same value in header and body")
    void correlation_fallbackGeneratesConsistentNonBlankId() throws Exception {
        MvcResult result = mvc.perform(get("/test/context/not-found"))
                .andExpect(status().isNotFound())
                .andReturn();

        String headerValue = result.getResponse().getHeader(REQUEST_ID_HEADER);
        String bodyValue = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.requestId");

        assertThat(headerValue).isNotNull().isNotBlank();
        assertThat(bodyValue).isNotNull().isNotBlank();
        // Same filter-set value must reach both places — not two independently
        // generated IDs for the same request.
        assertThat(bodyValue).isEqualTo(headerValue);
    }

    @Test
    @DisplayName("correlation: a blank incoming header is not trusted verbatim — falls back to a generated ID")
    void correlation_blankIncomingHeaderFallsBackToGenerated() throws Exception {
        mvc.perform(get("/test/context/not-found").header(REQUEST_ID_HEADER, "   "))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().string(REQUEST_ID_HEADER, org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.blankOrNullString())));
    }

    @Test
    @DisplayName("regression: SipsaParseException's 502 mapping from TECH-021 is unaffected by this story")
    void regression_parseExceptionStatusUnchanged() throws Exception {
        mvc.perform(get("/test/context/parse-error"))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("regression: SipsaNotFoundException's 404 mapping from TECH-022 is unaffected by this story")
    void regression_notFoundExceptionStatusUnchanged() throws Exception {
        mvc.perform(get("/test/context/not-found"))
                .andExpect(status().isNotFound());
    }
}
