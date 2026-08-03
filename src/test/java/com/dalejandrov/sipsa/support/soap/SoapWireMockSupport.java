package com.dalejandrov.sipsa.support.soap;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Shared WireMock scaffolding for SOAP integration tests (TECH-150, resolves
 * TECH-044/ADR-011). Starts a fresh {@link WireMockServer} per test method, on a
 * dynamic port, and exposes helpers to stub the single POST endpoint every
 * {@code SoapStreamingClient} call hits ({@code soapProperties.getEndpoint()} -
 * see {@code SoapStreamingClient.executeCall}, one fixed URL, no per-method path).
 * <p>
 * Fixture convention: {@code src/test/resources/fixtures/soap/<HandlerName>/<file>.xml},
 * one directory per handler class.
 * <p>
 * Register as {@code @RegisterExtension static final SoapWireMockSupport SOAP = new
 * SoapWireMockSupport();} — {@link BeforeEachCallback}/{@link AfterEachCallback} run
 * per test method regardless of the field being static, so every test gets an
 * isolated server and isolated stubs.
 */
public final class SoapWireMockSupport implements BeforeEachCallback, AfterEachCallback {

    private static final String SOAP_PATH = "/soap";

    private WireMockServer server;

    @Override
    public void beforeEach(ExtensionContext context) {
        server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        server.start();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (server != null) {
            server.stop();
        }
    }

    /** The endpoint {@code SoapProperties.setEndpoint(...)} should point at. */
    public String endpoint() {
        return "http://localhost:" + server.port() + SOAP_PATH;
    }

    /**
     * Stubs a {@code 200} SOAP response by reading a fixture file from
     * {@code fixtures/soap/<handlerName>/<fixtureFileName>} on the test classpath.
     */
    public void stubFixture(String handlerName, String fixtureFileName) {
        String body = readFixture(handlerName, fixtureFileName);
        server.stubFor(post(urlEqualTo(SOAP_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/soap+xml; charset=utf-8")
                        .withBody(body)));
    }

    /** Stubs an HTTP-level failure (e.g. 500) with an empty body, for failure-path tests. */
    public void stubHttpStatus(int status) {
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
