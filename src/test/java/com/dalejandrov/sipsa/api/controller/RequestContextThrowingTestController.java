package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionValidationException;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Shared test fixture: a minimal controller that throws (or triggers) exactly the
 * exception/error types {@code GlobalExceptionHandler}'s tests need to dispatch through
 * real MVC handling — used by TECH-023's {@link GlobalExceptionHandlerRequestContextTest}
 * and {@link RequestIdFilterValidationTest}, and TECH-043's
 * {@link GlobalExceptionHandlerContractTest} (full handler-contract coverage). Kept
 * top-level, not nested, per the lesson from TECH-021's
 * {@code ParseExceptionThrowingTestController} — a {@code static} nested
 * {@code @RestController} was not picked up by {@code @WebMvcTest}'s component scan in
 * this Spring Boot 4 project. Consolidated into one fixture rather than adding another
 * near-duplicate controller (TECH-043 explicitly asked for reuse, not a redundant one).
 */
@RestController
@Validated
class RequestContextThrowingTestController {

    @GetMapping("/test/context/parse-error")
    String throwParseException() {
        throw new SipsaParseException("Malformed XML from DANE SOAP response", null);
    }

    @GetMapping("/test/context/not-found")
    String throwNotFoundException() {
        throw new SipsaNotFoundException("Ingestion run not found: 999");
    }

    @GetMapping("/test/context/business-error")
    String throwBusinessException() {
        throw new SipsaBusinessException("Run already succeeded");
    }

    @GetMapping("/test/context/boom")
    String throwGenericException() {
        throw new RuntimeException("boom: something unexpected with a secret stack trace");
    }

    @PostMapping("/test/context/validate")
    String validate(@Valid @RequestBody ValidatedBody body) {
        return "ok";
    }

    @GetMapping("/test/context/validation-error")
    String throwValidationException() {
        throw new SipsaValidationException("Page size must be positive");
    }

    @GetMapping("/test/context/ingestion-error")
    String throwIngestionException() {
        throw new SipsaIngestionException("Reject threshold exceeded during ingestion");
    }

    @GetMapping("/test/context/external-error")
    String throwExternalException() {
        throw new SipsaExternalException("SOAP service unavailable");
    }

    @GetMapping("/test/context/configuration-error")
    String throwConfigurationException() {
        throw new SipsaConfigurationException("Missing required SOAP endpoint configuration");
    }

    @GetMapping("/test/context/ingestion-validation-error")
    String throwIngestionValidationException() {
        throw new SipsaIngestionValidationException(
                "Unknown ingestion method: bogus", Set.of("promediosSipsaCiudad", "promediosSipsaParcial"));
    }

    @GetMapping("/test/context/type-mismatch/{id}")
    String triggerTypeMismatch(@PathVariable Integer id) {
        return "id=" + id;
    }

    @GetMapping("/test/context/missing-param")
    String triggerMissingParameter(@RequestParam String required) {
        return "required=" + required;
    }

    @GetMapping("/test/context/constraint-violation/{value}")
    String triggerConstraintViolation(@PathVariable @Min(1) int value) {
        return "value=" + value;
    }

    record ValidatedBody(@NotBlank(message = "name must not be blank") String name) {}
}
