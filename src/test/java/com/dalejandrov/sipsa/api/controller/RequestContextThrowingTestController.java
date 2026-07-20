package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaNotFoundException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * TECH-023 test fixture: a minimal controller that throws (or triggers) exactly the
 * exception types {@link GlobalExceptionHandlerRequestContextTest} needs to dispatch
 * through real MVC handling. Kept top-level, not nested, per the lesson from TECH-021's
 * {@code ParseExceptionThrowingTestController} — a {@code static} nested
 * {@code @RestController} was not picked up by {@code @WebMvcTest}'s component scan in
 * this Spring Boot 4 project.
 */
@RestController
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

    record ValidatedBody(@NotBlank(message = "name must not be blank") String name) {}
}
