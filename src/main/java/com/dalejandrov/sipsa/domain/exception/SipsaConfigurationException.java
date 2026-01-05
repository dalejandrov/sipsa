package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception for configuration and initialization errors.
 * <p>
 * Thrown when application configuration is invalid or missing, such as:
 * <ul>
 *   <li>Invalid or missing timezone configuration</li>
 *   <li>Invalid SOAP client timeouts (negative or zero values)</li>
 *   <li>Missing required configuration properties</li>
 *   <li>Invalid configuration value ranges</li>
 * </ul>
 * <p>
 * These errors typically prevent application startup and should be
 * fixed in configuration files (application.yaml, environment variables).
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 500 (Internal Server Error).
 */
public class SipsaConfigurationException extends RuntimeException {

    public SipsaConfigurationException(String message) {
        super(message);
    }

    public SipsaConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

