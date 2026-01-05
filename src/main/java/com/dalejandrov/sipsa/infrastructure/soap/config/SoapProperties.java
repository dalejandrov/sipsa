package com.dalejandrov.sipsa.infrastructure.soap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for SIPSA SOAP web service client.
 * <p>
 * Binds to {@code sipsa.soap.*} properties in application.yaml, providing
 * centralized configuration for SOAP client behavior including timeouts,
 * retry policies, and logging.
 * <p>
 * <b>Example Configuration:</b>
 * <pre>{@code
 * sipsa:
 *   soap:
 *     endpoint: https://www.datos.gov.co/...
 *     connect-timeout-ms: 30000
 *     read-timeout-ms: 120000
 *     max-retries: 3
 *     retry-backoff-ms: 1000
 *     logging-enabled: true
 *     logging-limit-bytes: 1000
 *     max-child-elements: 10000
 *     namespace: http://ws.sia.gov.co/
 * }</pre>
 * <p>
 * <b>Timeout Configuration:</b>
 * <ul>
 *   <li>connectTimeoutMs: Time to establish connection (default: 30s)</li>
 *   <li>readTimeoutMs: Time to read response (default: 120s for large datasets)</li>
 * </ul>
 * <p>
 * <b>Retry Configuration:</b>
 * <ul>
 *   <li>maxRetries: Number of retry attempts (default: 3)</li>
 *   <li>retryBackoffMs: Base backoff time for exponential retry (default: 1000ms)</li>
 * </ul>
 *
 * @see SipsaSoapClientConfig
 * @see com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient
 */
@Component
@ConfigurationProperties(prefix = "sipsa.soap")
@Data
public class SoapProperties {

    /** SOAP service endpoint URL */
    private String endpoint;

    /** Connection timeout in milliseconds (time to establish connection) */
    private int connectTimeoutMs;

    /** Read timeout in milliseconds (time to wait for response) */
    private int readTimeoutMs;

    /** Maximum number of retry attempts for transient failures */
    private int maxRetries;

    /** Base backoff time in milliseconds for exponential retry strategy */
    private long retryBackoffMs;

    /** Whether to enable detailed SOAP message logging (dev/debug only) */
    private boolean loggingEnabled;

    /** Maximum bytes to log per SOAP message (prevents log explosion) */
    private int loggingLimitBytes;

    /** Maximum child elements to parse per parent (prevents XML bomb attacks) */
    private int maxChildElements;

    /** XML namespace for SOAP service methods */
    private String namespace;
}
