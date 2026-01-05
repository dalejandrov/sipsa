package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception for external service failures.
 * <p>
 * Thrown when communication with external systems fails, such as:
 * <ul>
 *   <li>SOAP service unavailable or timeout</li>
 *   <li>Network connectivity issues</li>
 *   <li>SOAP fault responses</li>
 *   <li>HTTP errors from external services</li>
 *   <li>Authentication/authorization failures</li>
 * </ul>
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 502 (Bad Gateway).
 */
public class SipsaExternalException extends RuntimeException {

    public SipsaExternalException(String message) {
        super(message);
    }

    public SipsaExternalException(String message, Throwable cause) {
        super(message, cause);
    }
}
