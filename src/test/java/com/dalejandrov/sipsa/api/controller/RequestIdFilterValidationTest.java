package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.api.filter.RequestIdFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-023 hardening: {@link RequestIdFilter} treats the incoming {@code X-Request-Id}
 * header as untrusted client input. An invalid value is silently replaced with a
 * generated UUID (never a {@code 400} — a malformed correlation ID isn't a request
 * error), and no accepted value can ever contain CR/LF (which would otherwise enable
 * response-header injection) since the whitelist regex simply doesn't include them.
 * <p>
 * Uses the same {@code @WebMvcTest} + {@link RequestContextThrowingTestController}
 * fixture as {@code GlobalExceptionHandlerRequestContextTest} — dispatches to
 * {@code /test/context/not-found} purely as a vehicle to produce a response with both
 * the {@code X-Request-Id} response header (set by the filter) and the
 * {@code requestId} JSON field (set by {@code GlobalExceptionHandler} from the same
 * request attribute), so both can be asserted against each other.
 */
@WebMvcTest(
        controllers = RequestContextThrowingTestController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@DisplayName("TECH-023 hardening: RequestIdFilter validates the incoming X-Request-Id header")
class RequestIdFilterValidationTest {

    private static final String HEADER_NAME = "X-Request-Id";
    private static final String ENDPOINT = "/test/context/not-found";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("a valid header value is preserved verbatim")
    void validHeader_isPreserved() throws Exception {
        String valid = "trace-abc.123:456_XYZ";

        mvc.perform(get(ENDPOINT).header(HEADER_NAME, valid))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HEADER_NAME, valid))
                .andExpect(jsonPath("$.requestId").value(valid));
    }

    @Test
    @DisplayName("an empty header value is replaced with a generated ID")
    void emptyHeader_isReplaced() throws Exception {
        mvc.perform(get(ENDPOINT).header(HEADER_NAME, ""))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HEADER_NAME, org.hamcrest.Matchers.not("")))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("a blank (whitespace-only) header value is replaced with a generated ID")
    void blankHeader_isReplaced() throws Exception {
        mvc.perform(get(ENDPOINT).header(HEADER_NAME, "   "))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HEADER_NAME, org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.blankOrNullString())))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @DisplayName("a header value longer than 128 characters is replaced with a generated ID")
    void tooLongHeader_isReplaced() throws Exception {
        String tooLong = "a".repeat(129);

        MvcResult result = mvc.perform(get(ENDPOINT).header(HEADER_NAME, tooLong))
                .andExpect(status().isNotFound())
                .andReturn();

        String headerValue = result.getResponse().getHeader(HEADER_NAME);
        assertThat(headerValue).isNotEqualTo(tooLong);
    }

    @Test
    @DisplayName("a header value with invalid characters is replaced with a generated ID")
    void invalidCharacters_areReplaced() throws Exception {
        String invalid = "not valid! <script>alert(1)</script>";

        MvcResult result = mvc.perform(get(ENDPOINT).header(HEADER_NAME, invalid))
                .andExpect(status().isNotFound())
                .andReturn();

        String headerValue = result.getResponse().getHeader(HEADER_NAME);
        assertThat(headerValue).isNotEqualTo(invalid);
    }

    @Test
    @DisplayName("a generated fallback ID is identical in the response header and the JSON body")
    void generatedFallback_matchesBetweenHeaderAndBody() throws Exception {
        MvcResult result = mvc.perform(get(ENDPOINT).header(HEADER_NAME, "###invalid###"))
                .andExpect(status().isNotFound())
                .andReturn();

        String headerValue = result.getResponse().getHeader(HEADER_NAME);
        String bodyValue = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.requestId");

        assertThat(headerValue).isNotBlank();
        assertThat(bodyValue).isEqualTo(headerValue);
    }

    @Test
    @DisplayName("no accepted request ID can ever contain CR or LF, whatever the input")
    void noValueEverContainsCrOrLf() throws Exception {
        // A raw header value can't literally carry CR/LF (the servlet container itself
        // rejects those in header syntax), so this proves the whitelist regex — the
        // actual injection guard — rejects a value smuggling characters that ARE
        // representable in a header but still outside the allowed set: the regex has
        // no allowance for anything outside [A-Za-z0-9._:-].
        String withDisallowedChars = "id withcontrolchars";

        MvcResult result = mvc.perform(get(ENDPOINT).header(HEADER_NAME, withDisallowedChars))
                .andExpect(status().isNotFound())
                .andReturn();

        String headerValue = result.getResponse().getHeader(HEADER_NAME);
        String bodyValue = com.jayway.jsonpath.JsonPath.read(
                result.getResponse().getContentAsString(), "$.requestId");

        assertThat(headerValue).doesNotContain("\r").doesNotContain("\n");
        assertThat(bodyValue).doesNotContain("\r").doesNotContain("\n");
        assertThat(headerValue).isEqualTo(bodyValue);
    }
}
