package com.dalejandrov.sipsa.infrastructure.soap.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

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
 * <p>
 * <b>Validation (TECH-070):</b> every constraint below mirrors a condition the SOAP
 * client already required to function correctly — most were previously enforced only
 * imperatively, and only partially, inside {@link SipsaSoapClientConfig}'s {@code
 * @Bean} factory method (which still carries those checks; they are now unreachable in
 * practice because {@code @Validated} binding-time validation fails first, but removing
 * them is out of this story's scope). Two gaps this closes that were never checked
 * anywhere before: {@code maxRetries} and {@code retryBackoffMs} could previously be
 * negative — a negative retry count silently degrades the retry loop into a no-op, and
 * a negative backoff multiplies out to a negative sleep duration that throws
 * {@code IllegalArgumentException} deep inside {@link
 * com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient}'s retry loop —
 * and {@code namespace}, which {@link
 * com.dalejandrov.sipsa.infrastructure.soap.gateway.SoapGatewayImpl} uses verbatim to
 * build every SOAP operation's {@code QName}.
 * <p>
 * {@code maxChildElements} and {@code loggingLimitBytes} deliberately keep {@code
 * @Min(0)}, not {@code @Positive}: {@code 0} is a real, documented value for both — for
 * {@code maxChildElements} it means "unlimited" ({@link SipsaSoapClientConfig}
 * substitutes {@code Integer.MAX_VALUE}), and {@code loggingLimitBytes} legitimately
 * truncates to nothing when {@code 0}. {@code endpoint} is unconditionally required —
 * this repository has no real "SOAP disabled" flag to gate it on, and the SOAP client
 * bean is always constructed.
 *
 * @see SipsaSoapClientConfig
 * @see com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient
 */
@Component
@ConfigurationProperties(prefix = "sipsa.soap")
@Validated
@Data
public class SoapProperties {

    /** SOAP service endpoint URL — always required, the client is always constructed. */
    @NotBlank(message = "sipsa.soap.endpoint must not be blank")
    private String endpoint;

    /** Connection timeout in milliseconds (time to establish connection) */
    @Positive(message = "sipsa.soap.connect-timeout-ms must be > 0")
    private int connectTimeoutMs;

    /** Read timeout in milliseconds (time to wait for response) */
    @Positive(message = "sipsa.soap.read-timeout-ms must be > 0")
    private int readTimeoutMs;

    /** Maximum number of retry attempts for transient failures (0 = no retries) */
    @Min(value = 0, message = "sipsa.soap.max-retries must be >= 0")
    private int maxRetries;

    /** Base backoff time in milliseconds for exponential retry strategy (0 = no backoff) */
    @Min(value = 0, message = "sipsa.soap.retry-backoff-ms must be >= 0")
    private long retryBackoffMs;

    /** Whether to enable detailed SOAP message logging (dev/debug only) */
    private boolean loggingEnabled;

    /** Maximum bytes to log per SOAP message (prevents log explosion; 0 = no body logged) */
    @Min(value = 0, message = "sipsa.soap.logging-limit-bytes must be >= 0")
    private int loggingLimitBytes;

    /** Maximum child elements to parse per parent (prevents XML bomb attacks; 0 = unlimited) */
    @Min(value = 0, message = "sipsa.soap.max-child-elements must be >= 0")
    private int maxChildElements;

    /** XML namespace for SOAP service methods — used to build every operation's QName */
    @NotBlank(message = "sipsa.soap.namespace must not be blank")
    private String namespace;
}
