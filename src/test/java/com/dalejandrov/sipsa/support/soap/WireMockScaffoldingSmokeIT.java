package com.dalejandrov.sipsa.support.soap;

import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient;
import com.dalejandrov.sipsa.infrastructure.soap.config.SoapProperties;
import com.dalejandrov.sipsa.infrastructure.soap.gateway.SoapGatewayImpl;
import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaCiudadRecord;
import com.dalejandrov.sipsa.infrastructure.soap.parser.CiudadStaxParser;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TECH-150 scaffolding proof: Maven Failsafe actually runs {@code *IT} classes
 * (separately from the Surefire unit run), and {@link SoapWireMockSupport} actually
 * serves a fixture through the real {@link SoapGatewayImpl} -&gt; {@link
 * SoapStreamingClient} -&gt; {@link CiudadStaxParser} chain. No Spring context, no
 * database — this is intentionally the minimum needed to prove the wiring.
 * <p>
 * <b>Throwaway:</b> deleted once TECH-151 lands the real {@code CiudadIngestionHandlerIT}
 * (which exercises this exact chain, plus the repository and idempotency).
 */
class WireMockScaffoldingSmokeIT {

    @RegisterExtension
    static final SoapWireMockSupport SOAP = new SoapWireMockSupport();

    @Test
    @DisplayName("SoapGatewayImpl -> SoapStreamingClient -> CiudadStaxParser parses a WireMock-served fixture")
    void ciudadFixture_parsesIntoExpectedRecords() throws Exception {
        SOAP.stubFixture("CiudadIngestionHandler", "two-records.xml");

        SoapProperties properties = new SoapProperties();
        properties.setEndpoint(SOAP.endpoint());
        properties.setNamespace("http://servicios.sipsa.co.gov.dane/");
        properties.setConnectTimeoutMs(2000);
        properties.setReadTimeoutMs(5000);
        properties.setMaxRetries(0);
        properties.setRetryBackoffMs(1);
        properties.setLoggingEnabled(false);
        properties.setLoggingLimitBytes(0);
        properties.setMaxChildElements(0);

        IngestionMetrics metrics = mock(IngestionMetrics.class);
        when(metrics.startSoapCall()).thenReturn(Timer.start());
        SoapStreamingClient client = new SoapStreamingClient(properties, metrics);
        SoapGatewayImpl gateway = new SoapGatewayImpl(client, properties);

        List<SipsaCiudadRecord> records = new ArrayList<>();
        try (InputStream stream = gateway.getCiudadData()) {
            CiudadStaxParser parser = new CiudadStaxParser(stream);
            while (parser.hasNext()) {
                records.add(parser.next());
            }
        }

        assertThat(records).hasSize(2);
        assertThat(records.get(0).regId()).isEqualTo(100001L);
        assertThat(records.get(0).ciudad()).isEqualTo("BOGOTA, D.C.");
        assertThat(records.get(0).codProducto()).isEqualTo(101L);
        assertThat(records.get(0).precioPromedio()).isEqualTo(new BigDecimal("2500.00"));
        assertThat(records.get(1).regId()).isEqualTo(100002L);
        assertThat(records.get(1).ciudad()).isEqualTo("MEDELLIN");
    }

    @Test
    @DisplayName("An HTTP 500 from WireMock surfaces as a failure, not a silent empty result")
    void httpFailure_doesNotSilentlySucceed() {
        SOAP.stubHttpStatus(500);

        SoapProperties properties = new SoapProperties();
        properties.setEndpoint(SOAP.endpoint());
        properties.setNamespace("http://servicios.sipsa.co.gov.dane/");
        properties.setConnectTimeoutMs(2000);
        properties.setReadTimeoutMs(5000);
        properties.setMaxRetries(0);
        properties.setRetryBackoffMs(1);
        properties.setLoggingEnabled(false);
        properties.setLoggingLimitBytes(0);
        properties.setMaxChildElements(0);

        IngestionMetrics metrics = mock(IngestionMetrics.class);
        when(metrics.startSoapCall()).thenReturn(Timer.start());
        SoapStreamingClient client = new SoapStreamingClient(properties, metrics);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> client.stream("promediosSipsaCiudad", "<payload/>"))
                .isInstanceOf(com.dalejandrov.sipsa.domain.exception.SipsaExternalException.class);
    }
}
