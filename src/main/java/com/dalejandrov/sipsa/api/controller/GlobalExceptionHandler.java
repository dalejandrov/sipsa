package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.Set;

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
     * Handles ingestion validation exceptions with available methods.
     *
     * @param ex the ingestion validation exception
     * @return HTTP 400 response with error details and available methods
     */
    @ExceptionHandler(SipsaIngestionValidationException.class)
    public ResponseEntity<IngestionValidationErrorResponse> handleIngestionValidationException(SipsaIngestionValidationException ex) {
        log.warn("Ingestion validation error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new IngestionValidationErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "INGESTION_VALIDATION_ERROR",
                ex.getMessage(),
                ex.getAvailableMethods()
        ));
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

    /**
     * Ingestion validation error response structure.
     * <p>
     * This record extends the standard error response with additional
     * information for ingestion validation errors.
     *
     * @param timestamp when the error occurred
     * @param status HTTP status code (e.g., 400)
     * @param error HTTP status reason phrase (e.g., "Bad Request")
     * @param code application-specific error code (e.g., "INGESTION_VALIDATION_ERROR")
     * @param message detailed error message
     * @param availableMethods set of available methods for the request
     */
    public record IngestionValidationErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String code,
            String message,
            Set<String> availableMethods
    ) {}
}
