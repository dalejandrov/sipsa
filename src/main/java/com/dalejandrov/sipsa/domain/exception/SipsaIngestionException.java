package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception for ingestion process failures.
 * <p>
 * Thrown when the data ingestion process fails, such as:
 * <ul>
 *   <li>Quality threshold exceeded (too many rejections)</li>
 *   <li>Data processing logic errors</li>
 *   <li>Batch operation failures</li>
 *   <li>Mapping or transformation errors</li>
 * </ul>
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 500 (Internal Server Error).
 */
public class SipsaIngestionException extends RuntimeException {

    public SipsaIngestionException(String message) {
        super(message);
    }

    public SipsaIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
