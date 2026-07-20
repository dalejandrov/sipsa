package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception for requests referencing a SIPSA resource that does not exist.
 * <p>
 * Distinct from {@link SipsaBusinessException}: a business exception means the resource
 * exists but the requested operation on it is invalid (e.g. canceling an already-finished
 * run); this exception means the resource itself was never there (e.g. the run ID doesn't
 * exist at all).
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 404 (Not Found).
 */
public class SipsaNotFoundException extends RuntimeException {

    public SipsaNotFoundException(String message) {
        super(message);
    }

    public SipsaNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
