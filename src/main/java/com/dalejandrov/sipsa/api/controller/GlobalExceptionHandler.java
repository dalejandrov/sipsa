package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Global exception handler for SIPSA REST API.
 * <p>
 * This controller advice intercepts exceptions thrown by REST controllers
 * and transforms them into standardized HTTP error responses.
 * <p>
 * <b>Application Exception Hierarchy:</b>
 * <ul>
 *   <li>{@link SipsaValidationException} → 400 Bad Request</li>
 *   <li>{@link SipsaIngestionValidationException} → 400 Bad Request (with available methods)</li>
 *   <li>{@link SipsaBusinessException} → 422 Unprocessable Entity</li>
 *   <li>{@link SipsaParseException} → 400 Bad Request</li>
 *   <li>{@link SipsaExternalException} → 502 Bad Gateway</li>
 *   <li>{@link SipsaIngestionException} → 500 Internal Server Error</li>
 *   <li>{@link SipsaConfigurationException} → 500 Internal Server Error</li>
 * </ul>
 * <p>
 * <b>Spring Framework Exceptions:</b>
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} → 400 Bad Request (with field errors)</li>
 *   <li>{@link ConstraintViolationException} → 400 Bad Request</li>
 *   <li>{@link MethodArgumentTypeMismatchException} → 400 Bad Request</li>
 *   <li>{@link HttpMessageNotReadableException} → 400 Bad Request</li>
 *   <li>{@link MissingServletRequestParameterException} → 400 Bad Request</li>
 *   <li>{@link NoHandlerFoundException} → 404 Not Found</li>
 *   <li>{@link NoResourceFoundException} → 404 Not Found</li>
 * </ul>
 * <p>
 * <b>Fallback Handler:</b>
 * <ul>
 *   <li>{@link Exception} (catch-all) → 500 Internal Server Error</li>
 * </ul>
 * <p>
 * All error responses follow a consistent JSON structure with timestamp,
 * status code, error code, and message. This ensures that clients never
 * receive unhandled 500 errors with stack traces.
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
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_ERROR", ex.getMessage());
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
     * Handles Bean Validation errors on request DTOs.
     * <p>
     * Triggered when @Valid or @Validated fails on @RequestBody parameters.
     * Returns detailed field-level validation errors.
     *
     * @param ex the method argument validation exception
     * @return HTTP 400 response with field-specific errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation error on request parameters: {}", errors);
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "VALIDATION_ERROR",
                "Request validation failed",
                errors
        ));
    }

    /**
     * Handles constraint violations on method parameters.
     * <p>
     * Triggered when @Validated constraints fail on controller method parameters.
     * Returns detailed constraint violation messages.
     *
     * @param ex the constraint violation exception
     * @return HTTP 400 response with constraint violations
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    /**
     * Handles type mismatch errors (e.g., passing string where number is expected).
     *
     * @param ex the type mismatch exception
     * @return HTTP 400 response with error details
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' should be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        log.warn("Type mismatch error: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", message);
    }

    /**
     * Handles malformed JSON or request body parsing errors.
     *
     * @param ex the message not readable exception
     * @return HTTP 400 response with error details
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Malformed JSON request";
        if (ex.getCause() != null) {
            message = "Invalid request format: " + ex.getCause().getMessage();
        }

        log.warn("Request parsing error: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_FORMAT", message);
    }

    /**
     * Handles missing required request parameters.
     *
     * @param ex the missing parameter exception
     * @return HTTP 400 response with error details
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());

        log.warn("Missing parameter error: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", message);
    }

    /**
     * Handles requests to non-existent endpoints (404 Not Found).
     * <p>
     * Note: Requires spring.mvc.throw-exception-if-no-handler-found=true
     * and spring.web.resources.add-mappings=false in application.yaml
     *
     * @param ex the no handler found exception
     * @return HTTP 404 response with error details
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex) {
        String message = "The requested resource was not found";

        log.debug("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
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

    /**
     * Validation error response with field-level details.
     * <p>
     * This record provides detailed validation errors for each field
     * that failed validation.
     *
     * @param timestamp when the error occurred
     * @param status HTTP status code (400)
     * @param error HTTP status reason phrase ("Bad Request")
     * @param code application-specific error code ("VALIDATION_ERROR")
     * @param message general error message
     * @param fieldErrors map of field names to error messages
     */
    public record ValidationErrorResponse(
            LocalDateTime timestamp,
            int status,
            String error,
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {}
}
