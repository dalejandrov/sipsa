package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TECH-021 test fixture: a minimal controller that throws the exceptions
 * {@link GlobalExceptionHandlerParseExceptionTest} needs to dispatch through real MVC handling.
 * <p>
 * Kept as a top-level class rather than nested inside the test: {@code @WebMvcTest}'s
 * component scan did not pick up a {@code static} nested {@code @RestController} in this
 * Spring Boot 4 project — no bean was registered and requests fell through to the static
 * resource handler (404). A top-level class is the reliable pattern.
 */
@RestController
class ParseExceptionThrowingTestController {

    @GetMapping("/test/parse-error")
    String throwParseException() {
        throw new SipsaParseException("Malformed XML from DANE SOAP response", null);
    }

    @GetMapping("/test/business-error")
    String throwBusinessException() {
        throw new SipsaBusinessException("Run already succeeded");
    }
}
