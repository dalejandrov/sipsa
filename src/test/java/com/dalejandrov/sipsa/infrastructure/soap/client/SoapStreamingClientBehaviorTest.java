package com.dalejandrov.sipsa.infrastructure.soap.client;

import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.infrastructure.observability.IngestionMetrics;
import com.dalejandrov.sipsa.infrastructure.soap.config.SoapProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TECH-156: {@link SoapStreamingClient}'s retry/backoff/GZIP behavior, against a real
 * local {@link HttpServer} (same fixture style as {@code SoapStreamingClientMetricsTest},
 * TECH-032 — still no WireMock dependency needed here, this is exercising the client in
 * isolation, not a SOAP-shaped response). Complements
 * {@code SoapStreamingClientMetricsTest}, which already proves the *metrics* emitted per
 * attempt/retry, by proving the *behavioral* contract testing-strategy.md described but
 * never implemented: 4xx doesn't retry, 5xx does (with real exponential-backoff timing,
 * not just a call count), and GZIP responses decompress transparently.
 */
class SoapStreamingClientBehaviorTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private SoapProperties propertiesFor(int port, int maxRetries, long backoffMs) {
        SoapProperties properties = new SoapProperties();
        properties.setEndpoint("http://localhost:" + port + "/soap");
        properties.setNamespace("http://servicios.sipsa.co.gov.dane/");
        properties.setConnectTimeoutMs(2000);
        properties.setReadTimeoutMs(2000);
        properties.setMaxRetries(maxRetries);
        properties.setRetryBackoffMs(backoffMs);
        properties.setLoggingEnabled(false);
        properties.setLoggingLimitBytes(0);
        properties.setMaxChildElements(0);
        return properties;
    }

    private IngestionMetrics metricsStub() {
        IngestionMetrics metrics = mock(IngestionMetrics.class);
        when(metrics.startSoapCall()).thenReturn(Timer.start());
        return metrics;
    }

    @Test
    @DisplayName("4xx: exactly one attempt, no retry, SipsaExternalException carries the real status")
    void clientError_noRetry_singleAttempt() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
        });
        server.start();

        SoapStreamingClient client = new SoapStreamingClient(
                propertiesFor(server.getAddress().getPort(), 3, 20), metricsStub());

        assertThatThrownBy(() -> client.stream("promediosSipsaCiudad", "<payload/>"))
                .isInstanceOf(SipsaExternalException.class)
                .satisfies(ex -> assertThat(((SipsaExternalException) ex).getHttpStatus()).isEqualTo(400));

        assertThat(requestCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("5xx exhausting all retries: exactly maxRetries+1 attempts, real exponential backoff elapsed")
    void serverError_retriesExhausted_exponentialBackoffTiming() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            requestCount.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        long backoffMs = 80;
        int maxRetries = 2;
        SoapStreamingClient client = new SoapStreamingClient(
                propertiesFor(server.getAddress().getPort(), maxRetries, backoffMs), metricsStub());

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.stream("promediosSipsaCiudad", "<payload/>"))
                .isInstanceOf(SipsaExternalException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // attempt 0 (immediate) + 2 retries = 3 real HTTP calls, not just 1.
        assertThat(requestCount.get()).isEqualTo(maxRetries + 1);
        // Exponential backoff: backoffMs * 2^0 before attempt 1, backoffMs * 2^1 before
        // attempt 2 -> minimum real elapsed time is the sum, not just "some delay".
        long expectedMinimumSleepMs = backoffMs + (backoffMs * 2);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(expectedMinimumSleepMs);
    }

    @Test
    @DisplayName("5xx then success: retries, then returns the successful response body")
    void serverError_thenSuccess_retriesAndRecovers() throws IOException {
        AtomicInteger requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            if (requestCount.incrementAndGet() <= 2) {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
                return;
            }
            byte[] body = "<response>ok-after-retries</response>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        SoapStreamingClient client = new SoapStreamingClient(
                propertiesFor(server.getAddress().getPort(), 3, 10), metricsStub());

        InputStream result = client.stream("promediosSipsaCiudad", "<payload/>");

        assertThat(new String(result.readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("<response>ok-after-retries</response>");
        assertThat(requestCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("GZIP: a gzip-encoded response is transparently decompressed before the caller sees it")
    void gzipResponse_transparentlyDecompressed() throws IOException {
        String plaintext = "<response><return><regId>1</regId></return></response>";
        byte[] gzipped = gzip(plaintext);

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/soap", exchange -> {
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, gzipped.length);
            exchange.getResponseBody().write(gzipped);
            exchange.close();
        });
        server.start();

        SoapStreamingClient client = new SoapStreamingClient(
                propertiesFor(server.getAddress().getPort(), 0, 10), metricsStub());

        InputStream result = client.stream("promediosSipsaCiudad", "<payload/>");

        assertThat(new String(result.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(plaintext);
    }

    private static byte[] gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
