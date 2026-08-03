package com.dalejandrov.sipsa.support.soap;

import com.github.tomakehurst.wiremock.WireMockServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Shared WireMock stubbing helpers for SOAP integration tests (TECH-150/151, resolves
 * TECH-044/ADR-011). Stubs the single POST endpoint every {@code SoapStreamingClient}
 * call hits ({@code soapProperties.getEndpoint()} - see
 * {@code SoapStreamingClient.executeCall}, one fixed URL, no per-method path).
 * <p>
 * Fixture convention: {@code src/test/resources/fixtures/soap/<HandlerName>/<file>.xml},
 * one directory per handler class.
 * <p>
 * The server itself is caller-managed, not owned by this class: Spring-context handler
 * ITs (e.g. {@code CiudadIngestionHandlerIT}, TECH-151) need the endpoint published via
 * {@code @DynamicPropertySource} <em>before</em> the context is created, which means the
 * {@link WireMockServer} has to be started once, statically, ahead of that - a JUnit 5
 * {@code @RegisterExtension}-managed lifecycle would run too late for that use case, so
 * there is deliberately no such extension here; start/stop the server yourself and call
 * these helpers against it (see {@code CiudadIngestionHandlerIT} for the full pattern:
 * static field, {@code @DynamicPropertySource}, {@code @AfterAll} to stop,
 * {@code resetAll()} in {@code @BeforeEach} to isolate stubs between tests).
 */
public final class SoapWireMockSupport {

    private static final String SOAP_PATH = "/soap";

    private SoapWireMockSupport() {
    }

    /** The endpoint {@code SoapProperties.setEndpoint(...)} (or {@code sipsa.soap.endpoint}) should point at. */
    public static String endpointOf(WireMockServer server) {
        return "http://localhost:" + server.port() + SOAP_PATH;
    }

    /**
     * Stubs a {@code 200} SOAP response by reading a fixture file from
     * {@code fixtures/soap/<handlerName>/<fixtureFileName>} on the test classpath.
     */
    public static void stubFixture(WireMockServer server, String handlerName, String fixtureFileName) {
        String body = readFixture(handlerName, fixtureFileName);
        server.stubFor(post(urlEqualTo(SOAP_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/soap+xml; charset=utf-8")
                        .withBody(body)));
    }

    /** Stubs an HTTP-level failure (e.g. 500) with an empty body, for failure-path tests. */
    public static void stubHttpStatus(WireMockServer server, int status) {
        server.stubFor(post(urlEqualTo(SOAP_PATH))
                .willReturn(aResponse().withStatus(status)));
    }

    private static String readFixture(String handlerName, String fixtureFileName) {
        String resourcePath = "fixtures/soap/" + handlerName + "/" + fixtureFileName;
        try (InputStream in = SoapWireMockSupport.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read fixture: " + resourcePath, e);
        }
    }
}
