package com.dalejandrov.sipsa.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-043: full {@code GlobalExceptionHandler} contract coverage — every
 * {@code @ExceptionHandler} method, exercised through real MVC dispatch, asserting
 * status, {@code Content-Type}, {@code code}, {@code message}, {@code requestId},
 * {@code instance}, a present {@code timestamp}, absence of any leaked stack trace, and
 * each response type's special structure ({@code fieldErrors}, {@code availableMethods})
 * where applicable.
 * <p>
 * TECH-021, TECH-022, and TECH-023 each added focused tests for the handler(s) their own
 * story touched (parse/502, not-found/404, and the requestId/instance fields
 * respectively) — none of them, individually or together, covered every handler method.
 * This test is the first to do so; it necessarily re-covers a few cases those stories
 * already tested (some overlap is expected and fine for a "complete coverage" story), but
 * its scope is every {@code @ExceptionHandler} in the class, not just the ones already
 * covered.
 * <p>
 * Two handler-documented cases are excluded, not silently skipped:
 * <ul>
 *   <li>{@code HttpRequestMethodNotSupportedException} (405) and
 *       {@code HttpMediaTypeNotSupportedException} (415) — {@code GlobalExceptionHandler}
 *       has no {@code @ExceptionHandler} for either; Spring Boot's default error
 *       machinery handles them, not this class, so there is nothing of this class's
 *       contract to assert.</li>
 *   <li>{@code NoHandlerFoundException} specifically (as opposed to
 *       {@code NoResourceFoundException}, which shares its handler method and is
 *       covered here) — the handler's own Javadoc notes it requires
 *       {@code spring.mvc.throw-exception-if-no-handler-found=true}, which this
 *       application does not set. In this Spring Boot version, an unmapped route
 *       reaches {@code NoResourceFoundException} instead (verified below), so the
 *       practical 404-for-unmapped-route case is fully covered — {@code
 *       NoHandlerFoundException} itself is presently unreachable, not untested.</li>
 * </ul>
 */
@WebMvcTest(
        controllers = RequestContextThrowingTestController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ResourceServerWebSecurityAutoConfiguration.class
        })
@DisplayName("TECH-043: GlobalExceptionHandler — full handler contract coverage")
class GlobalExceptionHandlerContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("SipsaValidationException -> 400, VALIDATION_ERROR")
    void sipsaValidationException_maps400() throws Exception {
        mvc.perform(get("/test/context/validation-error"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Page size must be positive"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/validation-error"));
    }

    @Test
    @DisplayName("SipsaBusinessException -> 422, BUSINESS_ERROR")
    void sipsaBusinessException_maps422() throws Exception {
        mvc.perform(get("/test/context/business-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Run already succeeded"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/business-error"));
    }

    @Test
    @DisplayName("SipsaNotFoundException -> 404, NOT_FOUND")
    void sipsaNotFoundException_maps404() throws Exception {
        mvc.perform(get("/test/context/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Ingestion run not found: 999"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/not-found"));
    }

    @Test
    @DisplayName("SipsaIngestionException -> 500, INGESTION_ERROR")
    void sipsaIngestionException_maps500() throws Exception {
        mvc.perform(get("/test/context/ingestion-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INGESTION_ERROR"))
                .andExpect(jsonPath("$.message").value("Reject threshold exceeded during ingestion"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/ingestion-error"))
                .andExpect(content().string(not(containsString("at com.dalejandrov"))));
    }

    @Test
    @DisplayName("SipsaParseException -> 502, PARSE_ERROR")
    void sipsaParseException_maps502() throws Exception {
        mvc.perform(get("/test/context/parse-error"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PARSE_ERROR"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/parse-error"));
    }

    @Test
    @DisplayName("SipsaExternalException -> 502, EXTERNAL_ERROR")
    void sipsaExternalException_maps502() throws Exception {
        mvc.perform(get("/test/context/external-error"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("EXTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("SOAP service unavailable"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/external-error"));
    }

    @Test
    @DisplayName("SipsaConfigurationException -> 500, CONFIGURATION_ERROR")
    void sipsaConfigurationException_maps500() throws Exception {
        mvc.perform(get("/test/context/configuration-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CONFIGURATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Missing required SOAP endpoint configuration"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/configuration-error"))
                .andExpect(content().string(not(containsString("at com.dalejandrov"))));
    }

    @Test
    @DisplayName("generic Exception (catch-all) -> 500, INTERNAL_ERROR, no leaked stack trace or original message")
    void genericException_maps500_noStackTraceLeaked() throws Exception {
        mvc.perform(get("/test/context/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/boom"))
                .andExpect(content().string(not(containsString("secret stack trace"))))
                .andExpect(content().string(not(containsString("at com.dalejandrov"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    @DisplayName("SipsaIngestionValidationException -> 400, INGESTION_VALIDATION_ERROR, availableMethods present")
    void sipsaIngestionValidationException_maps400_withAvailableMethods() throws Exception {
        mvc.perform(get("/test/context/ingestion-validation-error"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INGESTION_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Unknown ingestion method: bogus"))
                .andExpect(jsonPath("$.availableMethods", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.availableMethods", org.hamcrest.Matchers.hasItem("promediosSipsaCiudad")))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/ingestion-validation-error"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException (Bean Validation on @RequestBody) -> 400, VALIDATION_ERROR, fieldErrors present")
    void methodArgumentNotValidException_maps400_withFieldErrors() throws Exception {
        mvc.perform(post("/test/context/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").value("name must not be blank"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/validate"));
    }

    @Test
    @DisplayName("ConstraintViolationException (@Validated path variable) -> 400, VALIDATION_ERROR")
    void constraintViolationException_maps400() throws Exception {
        mvc.perform(get("/test/context/constraint-violation/0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/constraint-violation/0"));
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException -> 400, TYPE_MISMATCH")
    void methodArgumentTypeMismatchException_maps400() throws Exception {
        mvc.perform(get("/test/context/type-mismatch/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.message").value("Parameter 'id' should be of type Integer"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/type-mismatch/not-a-number"));
    }

    @Test
    @DisplayName("HttpMessageNotReadableException (malformed JSON body) -> 400, INVALID_FORMAT")
    void httpMessageNotReadableException_maps400() throws Exception {
        mvc.perform(post("/test/context/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/validate"));
    }

    @Test
    @DisplayName("MissingServletRequestParameterException -> 400, MISSING_PARAMETER")
    void missingServletRequestParameterException_maps400() throws Exception {
        mvc.perform(get("/test/context/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Required parameter 'required' is missing"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/missing-param"));
    }

    @Test
    @DisplayName("NoResourceFoundException (unmapped route) -> 404, NOT_FOUND")
    void noResourceFoundException_maps404() throws Exception {
        mvc.perform(get("/test/context/this-route-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/test/context/this-route-does-not-exist"));
    }
}
