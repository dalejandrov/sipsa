package com.dalejandrov.sipsa.domain.exception;

import lombok.Getter;

import java.util.Set;

/**
 * Exception for invalid ingestion trigger requests.
 * <p>
 * Thrown when parameters for triggering an ingestion process are invalid.
 * Includes the set of available methods for better error messages.
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 400 (Bad Request) with available methods.
 */
@Getter
public class SipsaIngestionValidationException extends SipsaValidationException {

    private final Set<String> availableMethods;

    public SipsaIngestionValidationException(String message, Set<String> availableMethods) {
        super(message);
        this.availableMethods = availableMethods;
    }

}
