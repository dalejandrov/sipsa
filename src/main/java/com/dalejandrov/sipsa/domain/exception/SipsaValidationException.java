package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception for input validation errors.
 * <p>
 * Thrown when user-provided input fails validation, such as:
 * <ul>
 *   <li>Invalid pagination parameters (negative page, size > 1000)</li>
 *   <li>Invalid ID values (zero or negative)</li>
 *   <li>Missing or malformed required parameters</li>
 *   <li>Data format violations</li>
 * </ul>
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 400 (Bad Request).
 */
public class SipsaValidationException extends RuntimeException {

    public SipsaValidationException(String message) {
        super(message);
    }
}
