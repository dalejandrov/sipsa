package com.dalejandrov.sipsa.infrastructure.soap.client;

import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import com.dalejandrov.sipsa.infrastructure.soap.config.SoapProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TECH-032: {@link SoapStreamingClient#stream} calls {@link IngestionMetrics} exactly
 * once per outcome (never once per internal HTTP attempt), with retries recorded
 * separately — verified against a real local {@link HttpServer} (JDK built-in,
 * loopback-only, no new test dependency and no WireMock, which this repository doesn't
 * have yet — see TECH-044) rather than mocking the client's internals.
 * <p>
 * Complements {@code IngestionMetricsTest} (meter values against a real registry) by
 * verifying the production call semantics: exactly one {@code startSoapCall}/
 * {@code recordSoapCallCompleted} pair per {@code stream()} invocation, and one
 * {@code recordSoapRetry} per retry attempt.
 */
class SoapStreamingClientMetricsTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private SoapProperties propertiesFor(int port, int maxRetries) {
        SoapProperties properties = new SoapProperties();
        properties.setEndpoint("http://localhost:" + port + "/soap");
        properties.setNamespace("http://servicios.sipsa.co.gov.dane/");
        properties.setConnectTimeoutMs(500);
        properties.setReadTimeoutMs(1000);
        properties.setMaxRetries(maxRetries);
        properties.setRetryBackoffMs(1); // keep retry backoff test-fast
        properties.setLoggingEnabled(false);
        properties.setLoggingLimitBytes(0);
        properties.setMaxChildElements(0);
        return properties;
    }

    @Test
    @DisplayName("a successful call: exactly one recordSoapCallCompleted(success=true), zero retries")
    void successfulCall_recordsSuccessOnce() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            byte[] body = "<response>ok</response>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        IngestionMetrics metrics = mock(IngestionMetrics.class);
        when(metrics.startSoapCall()).thenReturn(Timer.start());
        SoapStreamingClient client = new SoapStreamingClient(propertiesFor(server.getAddress().getPort(), 3), metrics);

        InputStream result = client.stream("promediosSipsaCiudad", "<payload/>");

        assertThat(result).isNotNull();
        verify(metrics, times(1)).startSoapCall();
        verify(metrics, times(1)).recordSoapCallCompleted(org.mockito.ArgumentMatchers.any(), eq("promediosSipsaCiudad"), eq(true));
        verify(metrics, never()).recordSoapRetry(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a non-retryable failure (HTTP 400): zero retries, exactly one recordSoapCallCompleted(success=false)")
    void nonRetryableFailure_recordsFailureOnce_noRetries() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();

        IngestionMetrics metrics = mock(IngestionMetrics.class);
        when(metrics.startSoapCall()).thenReturn(Timer.start());
        SoapStreamingClient client = new SoapStreamingClient(propertiesFor(server.getAddress().getPort(), 3), metrics);

        assertThatExceptionOfType(SipsaExternalException.class)
                .isThrownBy(() -> client.stream("promediosSipsaParcial", "<payload/>"));

        verify(metrics, times(1)).startSoapCall();
        verify(metrics, times(1)).recordSoapCallCompleted(org.mockito.ArgumentMatchers.any(), eq("promediosSipsaParcial"), eq(false));
        verify(metrics, never()).recordSoapRetry(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a retryable failure (HTTP 500) exhausting all retries: exactly maxRetries retry events, one final failure")
    void retryableFailure_recordsOneRetryPerAttempt_thenOneFailure() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();

        IngestionMetrics metrics = mock(IngestionMetrics.class);
        when(metrics.startSoapCall()).thenReturn(Timer.start());
        int maxRetries = 2;
        SoapStreamingClient client = new SoapStreamingClient(propertiesFor(server.getAddress().getPort(), maxRetries), metrics);

        assertThatExceptionOfType(SipsaExternalException.class)
                .isThrownBy(() -> client.stream("promediosSipsaSemanaMadr", "<payload/>"));

        verify(metrics, times(1)).startSoapCall();
        verify(metrics, times(maxRetries)).recordSoapRetry("promediosSipsaSemanaMadr");
        verify(metrics, times(1)).recordSoapCallCompleted(org.mockito.ArgumentMatchers.any(), eq("promediosSipsaSemanaMadr"), eq(false));
    }
}
