package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;

/**
 * Global exception handler for SIPSA REST API.
 * <p>
 * This controller advice intercepts exceptions thrown by REST controllers
 * and transforms them into standardized HTTP error responses.
 * <p>
 * Exception handling hierarchy:
 * <ul>
 *   <li>{@link SipsaValidationException} → 400 Bad Request</li>
 *   <li>{@link SipsaBusinessException} → 422 Unprocessable Entity</li>
 *   <li>{@link SipsaIngestionException} → 500 Internal Server Error</li>
 *   <li>{@link SipsaParseException} → 400 Bad Request</li>
 *   <li>{@link SipsaExternalException} → 502 Bad Gateway</li>
 *   <li>{@link SipsaConfigurationException} → 500 Internal Server Error</li>
 *   <li>{@link NoResourceFoundException} → 404 Not Found (suppressed logging)</li>
 *   <li>{@link Exception} (fallback) → 500 Internal Server Error</li>
 * </ul>
 *
 * @see org.springframework.web.bind.annotation.ControllerAdvice
 * @see org.springframework.web.bind.annotation.ExceptionHandler
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles validation exceptions (invalid input data).
     *
     * @param ex the validation exception
     * @return HTTP 400 response with error details
     */
    @ExceptionHandler(SipsaValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(SipsaValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    /**
     * Handles business logic exceptions (business rules violated).
     *
     * @param ex the business exception
     * @return HTTP 422 response with error details
     */
    @ExceptionHandler(SipsaBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(SipsaBusinessException ex) {
        log.error("Business logic error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_ERROR", ex.getMessage());
    }

    /**
     * Handles ingestion process exceptions (data ingestion failures).
     *
     * @param ex the ingestion exception
     * @return HTTP 500 response with error details
     */
    @ExceptionHandler(SipsaIngestionException.class)
    public ResponseEntity<ErrorResponse> handleIngestionException(SipsaIngestionException ex) {
        log.error("Ingestion error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INGESTION_ERROR", ex.getMessage());
    }

    /**
     * Handles parsing exceptions (malformed data).
     *
     * @param ex the parse exception
     * @return HTTP 400 response with error details
     */
    @ExceptionHandler(SipsaParseException.class)
    public ResponseEntity<ErrorResponse> handleParseException(SipsaParseException ex) {
        log.warn("Parse error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "PARSE_ERROR", ex.getMessage());
    }

    /**
     * Handles external service exceptions (SOAP service failures).
     *
     * @param ex the external service exception
     * @return HTTP 502 response with error details
     */
    @ExceptionHandler(SipsaExternalException.class)
    public ResponseEntity<ErrorResponse> handleExternalException(SipsaExternalException ex) {
        log.error("External service error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, "EXTERNAL_ERROR", ex.getMessage());
    }

    /**
     * Handles configuration exceptions (invalid application configuration).
     *
     * @param ex the configuration exception
     * @return HTTP 500 response with error details
     */
    @ExceptionHandler(SipsaConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleConfigurationException(SipsaConfigurationException ex) {
        log.error("Configuration error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "CONFIGURATION_ERROR", ex.getMessage());
    }

    /**
     * Handles missing static resource exceptions (e.g., favicon.ico).
     * <p>
     * Browsers automatically request favicon.ico. This handler prevents
     * error logs for expected missing resources and returns 404 quietly.
     *
     * @param ex the no resource found exception
     * @return HTTP 404 response without logging error
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        // Don't log as error - this is expected for favicon.ico and other static resources
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
    }

    /**
     * Handles all other unexpected exceptions (fallback handler).
     * <p>
     * This handler catches any exception not handled by more specific handlers,
     * ensuring that all errors are properly logged and return a safe response.
     *
     * @param ex the unexpected exception
     * @return HTTP 500 response with generic error message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    /**
     * Builds a standardized error response.
     *
     * @param status the HTTP status
     * @param errorCode the application-specific error code
     * @param message the error message
     * @return response entity with error details
     */
    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String errorCode, String message) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                errorCode,
                message
        );
        return new ResponseEntity<>(errorResponse, status);
    }

    /**
     * Standardized error response structure.
     * <p>
     * This record provides a consistent format for all error responses
     * returned by the API.
     *
     * @param timestamp when the error occurred
     * @param status HTTP status code (e.g., 400, 500)
     * @param error HTTP status reason phrase (e.g., "Bad Request")
     * @param code application-specific error code (e.g., "VALIDATION_ERROR")
     * @param message detailed error message
     */
    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String code,
            String message
    ) {}
}
