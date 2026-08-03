package com.dalejandrov.sipsa.e2e;

import com.dalejandrov.sipsa.api.dto.response.ApiResponse;
import com.dalejandrov.sipsa.api.dto.response.AuditTrailResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionRunDetailResponse;
import com.dalejandrov.sipsa.api.dto.response.IngestionTriggerResponse;
import com.dalejandrov.sipsa.support.soap.SoapWireMockSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * TECH-160 (resolves TECH-044/ADR-011): the E2E suite. Deliberately narrow scope - one
 * golden path, one failure path, both driving the real, running application over real
 * HTTP (no mocked Spring beans beyond the WireMock SOAP endpoint and the mock OIDC
 * issuer) - not a second copy of the per-handler integration tests (TECH-151..155),
 * which already prove each handler's parsing/persistence contract. This proves the
 * *wiring* between the HTTP trigger, the async dispatch, the ingestion pipeline, and the
 * audit/query side effects, end to end, once.
 * <p>
 * Flow proven: {@code POST /api/internal/ingestion/run} -&gt; {@code 202} -&gt; poll
 * {@code GET /api/internal/audit/request/{requestId}} (the endpoint that includes the
 * pre-run {@code REQUEST_RECEIVED}/{@code REQUEST_ACCEPTED} events too, unlike
 * {@code GET /audit/run/{runId}}, which only sees events already carrying a {@code
 * run_id}) until the run reaches a terminal state -&gt; {@code GET
 * /api/internal/ingestion/runs/{runId}} confirms the status -&gt; {@code GET
 * /api/sipsa/ciudad} (golden path only) confirms the persisted rows.
 * <p>
 * Uses {@code force=true} on the trigger so the test is not dependent on the real
 * wall-clock daily ingestion window (14:20 COT) - that window's own logic is already
 * exhaustively covered by {@code WindowPolicyTest}; this suite exists to prove the
 * wiring, not re-prove the window.
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-160: Ciudad ingestion, end to end, over real HTTP")
class CiudadIngestionE2ETest {

    private static final String METHOD = "promediosSipsaCiudad";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    private static WireMockServer soap;
    private static HttpServer oidcServer;
    private static RSAKey signingKey;
    private static String issuer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        soap = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        soap.start();
        registry.add("sipsa.soap.endpoint", () -> SoapWireMockSupport.endpointOf(soap));

        signingKey = new RSAKeyGenerator(2048)
                .keyID("e2e-key-1")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();
        oidcServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        issuer = "http://localhost:" + oidcServer.getAddress().getPort();
        oidcServer.createContext("/.well-known/openid-configuration", exchange -> respondJson(exchange, """
                {"issuer":"%s","jwks_uri":"%s/.well-known/jwks.json"}
                """.formatted(issuer, issuer)));
        oidcServer.createContext("/.well-known/jwks.json", exchange -> respondJson(exchange,
                "{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}"));
        oidcServer.start();

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> issuer);
    }

    private static void respondJson(HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterAll
    static void stopServers() {
        if (soap != null) {
            soap.stop();
        }
        if (oidcServer != null) {
            oidcServer.stop(0);
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        soap.resetAll();
        // FK-safe order: audit/reject rows reference ingestion_runs.run_id with no
        // ON DELETE CASCADE. Deleting (not just resetting) the run row for this method
        // guarantees each test gets a fresh run_id and a clean audit history - createRun
        // (IngestionControlService) would otherwise reuse/restart the same row for a
        // repeated (method_name, window_key), per its own documented restart logic.
        jdbc.update("DELETE FROM ingestion_audit WHERE run_id IN "
                + "(SELECT run_id FROM ingestion_runs WHERE method_name = ?)", METHOD);
        jdbc.update("DELETE FROM ingestion_rejects WHERE run_id IN "
                + "(SELECT run_id FROM ingestion_runs WHERE method_name = ?)", METHOD);
        jdbc.update("DELETE FROM ingestion_runs WHERE method_name = ?", METHOD);
        jdbc.update("DELETE FROM sipsa_ciudad");
    }

    private String accessToken() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("e2e-test-client")
                .issuer(issuer)
                .claim("token_use", "access")
                .claim("client_id", "e2e-test-client")
                .claim("scope", "sipsa/ingestion.execute sipsa/ingestion.read sipsa/audit.read")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private HttpEntity<Void> authenticated() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        return new HttpEntity<>(headers);
    }

    private AuditTrailResponse fetchAuditTrail(String requestId) throws Exception {
        return restTemplate.exchange("/api/internal/audit/request/" + requestId, HttpMethod.GET,
                authenticated(), AuditTrailResponse.class).getBody();
    }

    private boolean auditTrailContains(String requestId, String eventType) {
        try {
            AuditTrailResponse trail = fetchAuditTrail(requestId);
            return trail != null && trail.events().stream().anyMatch(e -> eventType.equals(e.eventType()));
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @DisplayName("golden path: trigger -> async ingestion -> audit trail -> run status -> persisted rows queryable")
    void goldenPath_endToEnd() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "CiudadIngestionHandler", "two-records.xml");

        ResponseEntity<IngestionTriggerResponse> triggerResponse = restTemplate.exchange(
                "/api/internal/ingestion/run?method=" + METHOD + "&force=true", HttpMethod.POST,
                authenticated(), IngestionTriggerResponse.class);

        assertThat(triggerResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String requestId = triggerResponse.getBody().requestId();
        assertThat(requestId).isNotBlank();

        // METRICS_UPDATED is the last event of the sequence (finally block, TECH-032/
        // testing-strategy.md's own documented async-audit-assertion rule) - polling for
        // it, not for INGESTION_SUCCEEDED, avoids racing the last audit commit.
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .until(() -> auditTrailContains(requestId, "METRICS_UPDATED"));

        AuditTrailResponse trail = fetchAuditTrail(requestId);
        assertThat(trail.events()).extracting(AuditTrailResponse.AuditEventResponse::eventType)
                .containsExactly("REQUEST_RECEIVED", "REQUEST_ACCEPTED", "INGESTION_STARTED",
                        "INGESTION_RUNNING", "INGESTION_SUCCEEDED", "METRICS_UPDATED");

        Long runId = trail.events().stream()
                .map(AuditTrailResponse.AuditEventResponse::runId)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new AssertionError("No event carried a runId"));

        ResponseEntity<IngestionRunDetailResponse> runResponse = restTemplate.exchange(
                "/api/internal/ingestion/runs/" + runId, HttpMethod.GET,
                authenticated(), IngestionRunDetailResponse.class);
        assertThat(runResponse.getBody().status()).isEqualTo("SUCCEEDED");
        assertThat(runResponse.getBody().recordsInserted()).isEqualTo(2);

        // /api/sipsa/** is permitAll (public) - no auth header needed here.
        ResponseEntity<ApiResponse> ciudadResponse = restTemplate.getForEntity("/api/sipsa/ciudad", ApiResponse.class);
        assertThat(ciudadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ciudadResponse.getBody().getCount()).isEqualTo(2L);
        assertThat(ciudadResponse.getBody().getResults()).hasSize(2);
    }

    @Test
    @DisplayName("failure path: SOAP 500 -> run ends FAILED, audit trail intact through METRICS_UPDATED")
    void failurePath_endToEnd() throws Exception {
        SoapWireMockSupport.stubHttpStatus(soap, 500);

        ResponseEntity<IngestionTriggerResponse> triggerResponse = restTemplate.exchange(
                "/api/internal/ingestion/run?method=" + METHOD + "&force=true", HttpMethod.POST,
                authenticated(), IngestionTriggerResponse.class);

        assertThat(triggerResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String requestId = triggerResponse.getBody().requestId();

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200))
                .until(() -> auditTrailContains(requestId, "METRICS_UPDATED"));

        AuditTrailResponse trail = fetchAuditTrail(requestId);
        assertThat(trail.events()).extracting(AuditTrailResponse.AuditEventResponse::eventType)
                .containsExactly("REQUEST_RECEIVED", "REQUEST_ACCEPTED", "INGESTION_STARTED",
                        "INGESTION_RUNNING", "INGESTION_FAILED", "METRICS_UPDATED");

        Long runId = trail.events().stream()
                .map(AuditTrailResponse.AuditEventResponse::runId)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElseThrow(() -> new AssertionError("No event carried a runId"));

        ResponseEntity<IngestionRunDetailResponse> runResponse = restTemplate.exchange(
                "/api/internal/ingestion/runs/" + runId, HttpMethod.GET,
                authenticated(), IngestionRunDetailResponse.class);
        assertThat(runResponse.getBody().status()).isEqualTo("FAILED");

        List<?> rows = restTemplate.getForEntity("/api/sipsa/ciudad", ApiResponse.class).getBody().getResults();
        assertThat(rows).isEmpty();
    }
}
