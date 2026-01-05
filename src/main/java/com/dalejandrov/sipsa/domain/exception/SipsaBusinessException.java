package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception for business logic violations in SIPSA operations.
 * <p>
 * This runtime exception is thrown when business rules or constraints are violated,
 * such as:
 * <ul>
 *   <li>Attempting to restart a succeeded run without force flag</li>
 *   <li>Invalid method names or configurations</li>
 *   <li>Duplicate run detection</li>
 *   <li>Business validation failures</li>
 * </ul>
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 422 (Unprocessable Entity).
 */
public class SipsaBusinessException extends RuntimeException {

    public SipsaBusinessException(String message) {
        super(message);
    }

    public SipsaBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
