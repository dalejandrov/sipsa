package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception for data parsing errors.
 * <p>
 * Thrown when incoming data cannot be parsed correctly, such as:
 * <ul>
 *   <li>Malformed XML from SOAP service</li>
 *   <li>Invalid date/time formats</li>
 *   <li>Corrupt or truncated data streams</li>
 *   <li>Unexpected data structures</li>
 * </ul>
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 400 (Bad Request).
 */
public class SipsaParseException extends RuntimeException {

    public SipsaParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
