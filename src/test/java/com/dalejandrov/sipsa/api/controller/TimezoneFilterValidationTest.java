package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.filter.TimezoneFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-008 F4: {@link TimezoneFilter} must reject a PRESENT-but-invalid {@code X-Timezone}
 * header with {@code 400 SIPSA_INVALID_TIMEZONE} instead of degrading to UTC unnoticed. An
 * ABSENT header is a different case — that is the intended international-API default and
 * must keep working exactly as before (silently UTC, request proceeds normally).
 * <p>
 * Uses the same {@code @WebMvcTest} + {@code RequestContextThrowingTestController} fixture
 * as {@link RequestIdFilterValidationTest} — {@code /test/context/not-found} is a vehicle
 * to reach a real 404 through the full filter chain when the header is valid or absent.
 */
@WebMvcTest(
        controllers = RequestContextThrowingTestController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@DisplayName("ADR-008 F4: TimezoneFilter rejects an invalid X-Timezone header with 400")
class TimezoneFilterValidationTest {

    private static final String HEADER_NAME = "X-Timezone";
    private static final String ENDPOINT = "/test/context/not-found";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("a valid IANA timezone header lets the request reach the controller")
    void validHeader_requestProceeds() throws Exception {
        mvc.perform(get(ENDPOINT).header(HEADER_NAME, "America/Bogota"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("no header at all still falls back to UTC silently — request proceeds normally")
    void absentHeader_stillProceeds() throws Exception {
        mvc.perform(get(ENDPOINT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("an invalid timezone header short-circuits with 400 SIPSA_INVALID_TIMEZONE, never reaching the controller")
    void invalidHeader_shortCircuitsWith400() throws Exception {
        mvc.perform(get(ENDPOINT).header(HEADER_NAME, "Not/AZone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIPSA_INVALID_TIMEZONE"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.instance").value(ENDPOINT));
    }

    @Test
    @DisplayName("a blank timezone header is treated as absent — UTC fallback, request proceeds")
    void blankHeader_treatedAsAbsent() throws Exception {
        mvc.perform(get(ENDPOINT).header(HEADER_NAME, "   "))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
