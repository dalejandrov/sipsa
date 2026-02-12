package com.dalejandrov.sipsa.domain.exception;

import lombok.Getter;

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
 * This exception captures additional context about the failure:
 * <ul>
 *   <li><b>httpStatus:</b> HTTP status code from the failed request (if applicable)</li>
 *   <li><b>soapFaultCode:</b> SOAP fault code from the service (if applicable)</li>
 * </ul>
 * <p>
 * Handled by {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler}
 * and returns HTTP 502 (Bad Gateway).
 */
@Getter
public class SipsaExternalException extends RuntimeException {

    /**
     * HTTP status code from the external service.
     * {@code null} if the error is not HTTP-related.
     */
    private final Integer httpStatus;

    /**
     * SOAP fault code from the external service.
     * {@code null} if the error is not a SOAP fault.
     */
    private final String soapFaultCode;

    public SipsaExternalException(String message) {
        super(message);
        this.httpStatus = null;
        this.soapFaultCode = null;
    }

    public SipsaExternalException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
        this.soapFaultCode = null;
    }

    /**
     * Constructor with HTTP status code.
     *
     * @param message error message
     * @param httpStatus HTTP status code from the external service
     */
    public SipsaExternalException(String message, Integer httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.soapFaultCode = null;
    }

    /**
     * Constructor with HTTP status code and cause.
     *
     * @param message error message
     * @param httpStatus HTTP status code from the external service
     * @param cause the underlying cause
     */
    public SipsaExternalException(String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.soapFaultCode = null;
    }

    /**
     * Constructor with SOAP fault code.
     *
     * @param message error message
     * @param soapFaultCode SOAP fault code from the service
     */
    public SipsaExternalException(String message, String soapFaultCode) {
        super(message);
        this.httpStatus = null;
        this.soapFaultCode = soapFaultCode;
    }

    /**
     * Constructor with both HTTP status and SOAP fault code.
     *
     * @param message error message
     * @param httpStatus HTTP status code from the external service
     * @param soapFaultCode SOAP fault code from the service
     */
    public SipsaExternalException(String message, Integer httpStatus, String soapFaultCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.soapFaultCode = soapFaultCode;
    }

    /**
     * Constructor with HTTP status, SOAP fault code, and cause.
     *
     * @param message error message
     * @param httpStatus HTTP status code from the external service
     * @param soapFaultCode SOAP fault code from the service
     * @param cause the underlying cause
     */
    public SipsaExternalException(String message, Integer httpStatus, String soapFaultCode, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.soapFaultCode = soapFaultCode;
    }
}



