package com.dalejandrov.sipsa.infrastructure.soap.config;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.infrastructure.soap.client.SrvSipsaUpraBeanService;
import com.dalejandrov.sipsa.infrastructure.soap.client.SrvSipsaUpraService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.ext.logging.LoggingFeature;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Spring configuration for the SIPSA SOAP client.
 *
 * <p>This configuration class sets up the Apache CXF SOAP client for communicating with the SIPSA web service.
 * It handles endpoint configuration, HTTP timeouts, XML parsing limits, and optional logging.
 *
 * <p>Configuration properties are loaded from application properties with prefix "sipsa.soap".
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class SipsaSoapClientConfig {

    private final SoapProperties soapProperties;

    /**
     * Creates and configures the SIPSA SOAP client bean.
     *
     * <p>This method initializes the JAX-WS proxy client with all necessary configurations
     * including endpoint, timeouts, XML limits, and optional logging.
     *
     * @return the configured SOAP service port
     */
    @Bean
    public SrvSipsaUpraService sipsaSoapClient() {
        validateConfiguration();

        log.info("Initializing SIPSA SOAP client for endpoint {}", soapProperties.getEndpoint());

        SrvSipsaUpraBeanService svc = createServiceWithOptionalLogging();
        SrvSipsaUpraService port = svc.getSrvSipsaUpraPort();

        Client client = ClientProxy.getClient(port);

        configureEndpoint(client);
        configureHttpClientPolicy(client);
        configureCxfXmlLimits(client);

        log.info("SOAP client successfully configured");
        return port;
    }

    /**
     * Validates the configuration properties.
     *
     * @throws SipsaConfigurationException if any configuration value is invalid
     */
    private void validateConfiguration() {
        requireNonNull(soapProperties.getEndpoint(), "sipsa.soap.endpoint must not be null");
        if (soapProperties.getConnectTimeoutMs() < 1) {
            throw new SipsaConfigurationException("sipsa.soap.connect-timeout-ms must be >= 1");
        }
        if (soapProperties.getReadTimeoutMs() < 1) {
            throw new SipsaConfigurationException("sipsa.soap.read-timeout-ms must be >= 1");
        }
        if (soapProperties.getLoggingLimitBytes() < 0) {
            throw new SipsaConfigurationException("sipsa.soap.logging-limit-bytes must be >= 0");
        }
        if (soapProperties.getMaxChildElements() < 0) {
            throw new SipsaConfigurationException("sipsa.soap.max-child-elements must be >= 0");
        }
    }

    /**
     * Creates the JAX-WS service instance with optional logging feature.
     * <p>
     * When logging is enabled ({@code sipsa.soap.logging-enabled=true}), attaches
     * Apache CXF's LoggingFeature which logs:
     * <ul>
     *   <li>SOAP request envelopes</li>
     *   <li>SOAP response envelopes</li>
     *   <li>HTTP headers</li>
     *   <li>Truncated to {@code loggingLimitBytes} to prevent log explosion</li>
     * </ul>
     * <p>
     * <b>Warning:</b> Logging should be disabled in production as it:
     * <ul>
     *   <li>Impacts performance</li>
     *   <li>Creates large log files</li>
     *   <li>May log sensitive data</li>
     * </ul>
     *
     * @return service instance with logging configured if enabled
     */
    private SrvSipsaUpraBeanService createServiceWithOptionalLogging() {
        if (!soapProperties.isLoggingEnabled()) {
            return new SrvSipsaUpraBeanService();
        }
        LoggingFeature logging = new LoggingFeature();
        logging.setPrettyLogging(true);
        logging.setLimit(soapProperties.getLoggingLimitBytes());

        log.warn("SOAP logging ENABLED (limit={} bytes)", soapProperties.getLoggingLimitBytes());
        return new SrvSipsaUpraBeanService(logging);
    }

    /**
     * Configures the SOAP endpoint address from properties.
     * <p>
     * Sets the target endpoint URL for SOAP requests. This overrides the
     * endpoint specified in the WSDL file, allowing environment-specific
     * configuration through {@code sipsa.soap.endpoint} property.
     *
     * @param client the CXF client to configure
     */
    private void configureEndpoint(Client client) {
        client.getRequestContext().put(Message.ENDPOINT_ADDRESS, soapProperties.getEndpoint());
    }

    /**
     * Configures HTTP client policy including timeouts and chunking.
     *
     * @param client the CXF client to configure
     */
    private void configureHttpClientPolicy(Client client) {
        HTTPConduit conduit = (HTTPConduit) client.getConduit();
        HTTPClientPolicy policy = conduit.getClient();
        if (policy == null) {
            policy = new HTTPClientPolicy();
            conduit.setClient(policy);
        }

        policy.setConnectionTimeout(soapProperties.getConnectTimeoutMs());
        policy.setReceiveTimeout(soapProperties.getReadTimeoutMs());

        policy.setAllowChunking(true);
        policy.setChunkingThreshold(4096);

        policy.setAutoRedirect(false);

        log.debug(
                "Configured HTTP timeouts: connect={}ms, read={}ms",
                soapProperties.getConnectTimeoutMs(), soapProperties.getReadTimeoutMs()
        );
    }

    /**
     * Configures CXF XML parsing limits to handle large SOAP responses safely.
     * <p>
     * Sets the maximum number of child elements allowed per parent element to
     * protect against XML bomb attacks while still handling legitimate large
     * SIPSA responses (which can have 10K+ elements).
     * <p>
     * <b>Security Note:</b><br>
     * This prevents malicious XML like:
     * <pre>{@code
     * <parent>
     *   <child>...</child> <!-- repeated millions of times -->
     * </parent>
     * }</pre>
     * <p>
     * A value of 0 in configuration means unlimited (Integer.MAX_VALUE).
     *
     * @param client the CXF client to configure
     */
    private void configureCxfXmlLimits(Client client) {
        int effectiveMaxChildren = (soapProperties.getMaxChildElements() == 0) ? Integer.MAX_VALUE : soapProperties.getMaxChildElements();

        Map<String, Object> staxProps = Map.of(
                "org.apache.cxf.stax.maxChildElements", effectiveMaxChildren,
                "org.apache.cxf.stax.maxElementDepth", Integer.MAX_VALUE,
                "org.apache.cxf.stax.maxAttributeSize", Integer.MAX_VALUE,
                "org.apache.cxf.stax.maxTextLength", Integer.MAX_VALUE
        );

        client.getRequestContext().putAll(staxProps);

        Bus bus = client.getBus();
        staxProps.forEach(bus::setProperty);

        log.debug(
                "Applied CXF XML limits (maxChildElements={})",
                effectiveMaxChildren == Integer.MAX_VALUE ? "unlimited" : effectiveMaxChildren
        );
    }
}
