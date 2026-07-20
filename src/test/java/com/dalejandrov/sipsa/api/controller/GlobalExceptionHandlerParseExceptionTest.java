package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-021: {@link SipsaParseException} maps to {@code 502 Bad Gateway}, not
 * {@code 400 Bad Request} — the malformed XML that triggers it comes from DANE's SOAP
 * response (an upstream dependency failure), never from the API caller.
 * <p>
 * {@code @WebMvcTest(GlobalExceptionHandler.class)} with a minimal throwing test
 * controller ({@link ParseExceptionThrowingTestController}), per the pattern
 * {@code docs/architecture/testing-strategy.md} already documents for this handler
 * class — real MVC dispatch, so content negotiation and response headers are genuine,
 * not just the raw {@code ResponseEntity} a direct method call would return. Scoped to
 * exactly this story: one exception type, plus one regression check that
 * {@link SipsaBusinessException}'s mapping is untouched. Full coverage of every handler
 * method is TECH-043.
 * <p>
 * OAuth2 resource-server autoconfiguration is excluded: it needs an {@code HttpSecurity}
 * bean this narrow slice doesn't provide (the app's real {@code SecurityConfig} is out
 * of scope for an exception-mapping test), and would otherwise fail context startup.
 */
@WebMvcTest(
        controllers = ParseExceptionThrowingTestController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@DisplayName("TECH-021: GlobalExceptionHandler — SipsaParseException → 502 Bad Gateway")
class GlobalExceptionHandlerParseExceptionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("SipsaParseException maps to 502 Bad Gateway (was 400 Bad Request)")
    void parseException_mapsTo502() throws Exception {
        mvc.perform(get("/test/parse-error"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"));
    }

    @Test
    @DisplayName("the error code stays PARSE_ERROR — only the HTTP status changed")
    void parseException_errorCodeUnchanged() throws Exception {
        mvc.perform(get("/test/parse-error"))
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"));
    }

    @Test
    @DisplayName("the exception message is preserved verbatim in the response body")
    void parseException_messagePreserved() throws Exception {
        mvc.perform(get("/test/parse-error"))
                .andExpect(jsonPath("$.message").value("Malformed XML from DANE SOAP response"));
    }

    @Test
    @DisplayName("the response body still carries a timestamp — body structure unchanged")
    void parseException_timestampPresent() throws Exception {
        mvc.perform(get("/test/parse-error"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("content type is application/json, matching every other GlobalExceptionHandler response")
    void parseException_contentTypeIsJson() throws Exception {
        mvc.perform(get("/test/parse-error"))
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    @DisplayName("regression: SipsaBusinessException's mapping (422) is untouched by this change")
    void businessException_stillMapsTo422() throws Exception {
        mvc.perform(get("/test/business-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Run already succeeded"));
    }
}
