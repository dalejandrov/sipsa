package com.dalejandrov.sipsa.infrastructure.config.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TECH-142: exercises {@link SecurityConfig#jwtDecoder(SipsaJwtProperties)} end to end
 * against realistically-shaped, locally-signed Cognito access/ID tokens — issuer
 * discovery, JWKS signature verification, {@code token_use}, and the optional
 * {@code client_id} allowlist together, the way {@link SipsaJwtValidatorsTest} (which
 * hand-builds {@link Jwt} objects, bypassing signature/issuer verification) and
 * {@link InternalEndpointSecurityTest} (which mocks {@code JwtDecoder} entirely and
 * injects authorities directly, bypassing the decoder and the scope-to-authority
 * conversion) do not. No real Cognito account is contacted — a local JDK
 * {@link HttpServer} (loopback-only, no new test dependency — this repository does not
 * have a working WireMock HTTP-server extension on its classpath yet, see
 * {@code SoapStreamingClientMetricsTest}) serves the OIDC discovery document and JWKS,
 * mirroring the mechanism the repository's own {@code docker/mock-oidc-config.json}
 * service provides for manual e2e validation, reproduced here as an automated,
 * in-process fixture.
 */
@DisplayName("Cognito-shaped JWT contract (decoder + validators + scope conversion)")
class CognitoJwtDecoderContractTest {

    private static final String M2M_CLIENT_ID = "test-m2m-client-id";
    private static final String HUMAN_CLIENT_ID = "test-human-client-id";

    private static HttpServer server;
    private static RSAKey signingKey;
    private static String issuer;

    @BeforeAll
    static void startMockIssuer() throws Exception {
        signingKey = new RSAKeyGenerator(2048)
                .keyID("test-key-1")
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .generate();

        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        issuer = "http://localhost:" + server.getAddress().getPort();

        server.createContext("/.well-known/openid-configuration", exchange -> {
            String body = """
                    {"issuer":"%s","jwks_uri":"%s/.well-known/jwks.json"}
                    """.formatted(issuer, issuer);
            respondJson(exchange, body);
        });
        server.createContext("/.well-known/jwks.json", exchange -> {
            String body = "{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}";
            respondJson(exchange, body);
        });
        server.start();
    }

    private static void respondJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterAll
    static void stopMockIssuer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static String sign(String tokenIssuer, String tokenUse, String clientId, String scope) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("cognito-subject")
                .issuer(tokenIssuer)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
        if (tokenUse != null) {
            claims.claim("token_use", tokenUse);
        }
        if (clientId != null) {
            claims.claim("client_id", clientId);
        }
        if (scope != null) {
            claims.claim("scope", scope);
        }

        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static JwtDecoder decoderWithAllowlist(String allowedClientIdsCsv) {
        SipsaJwtProperties properties = new SipsaJwtProperties(issuer, allowedClientIdsCsv);
        return new SecurityConfig().jwtDecoder(properties);
    }

    /**
     * Only {@code SCOPE_*} authorities — the only ones {@link SecurityConfig}'s
     * {@code hasAuthority(...)} matchers ever check. Spring Security's default
     * {@link JwtAuthenticationConverter} also adds a {@code FACTOR_BEARER} authentication-
     * factor authority to every bearer-token authentication (confirmed by running this
     * test, not assumed) — filtered out here since it plays no role in this
     * application's per-operation authorization, which is scope-based only.
     */
    private static Set<String> authoritiesOf(Jwt jwt) {
        return new JwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(authority -> authority.startsWith("SCOPE_"))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("1: valid M2M access token (client_credentials) -> decodes, scopes convert to SCOPE_ authorities")
    void m2mAccessToken_valid() throws Exception {
        String token = sign(issuer, "access", M2M_CLIENT_ID,
                "sipsa/ingestion.execute sipsa/ingestion.read");
        JwtDecoder decoder = decoderWithAllowlist(M2M_CLIENT_ID + "," + HUMAN_CLIENT_ID);

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getClaimAsString("client_id")).isEqualTo(M2M_CLIENT_ID);
        assertThat(authoritiesOf(jwt)).containsExactlyInAnyOrder(
                "SCOPE_sipsa/ingestion.execute", "SCOPE_sipsa/ingestion.read");
    }

    @Test
    @DisplayName("2: valid human access token (Authorization Code) -> decodes when the human client is allowlisted")
    void humanAccessToken_valid() throws Exception {
        String token = sign(issuer, "access", HUMAN_CLIENT_ID, "sipsa/audit.read");
        JwtDecoder decoder = decoderWithAllowlist(M2M_CLIENT_ID + "," + HUMAN_CLIENT_ID);

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getClaimAsString("client_id")).isEqualTo(HUMAN_CLIENT_ID);
        assertThat(authoritiesOf(jwt)).containsExactly("SCOPE_sipsa/audit.read");
    }

    @Test
    @DisplayName("3: ID token (token_use=id) -> rejected, even with an otherwise-valid signature/issuer/client_id")
    void idToken_rejected() throws Exception {
        String token = sign(issuer, "id", M2M_CLIENT_ID, "sipsa/ingestion.execute");
        JwtDecoder decoder = decoderWithAllowlist(M2M_CLIENT_ID);

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class)
                .satisfies(ex -> assertThat(errorDescription(ex)).contains("token_use"));
    }

    @Test
    @DisplayName("4: client_id not in the allowlist -> rejected")
    void disallowedClientId_rejected() throws Exception {
        String token = sign(issuer, "access", "intruder-client-id", "sipsa/ingestion.execute");
        JwtDecoder decoder = decoderWithAllowlist(M2M_CLIENT_ID + "," + HUMAN_CLIENT_ID);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("5: missing scope claim -> decodes (decoder does not enforce scope), but zero SCOPE_ authorities "
            + "are derived, so SecurityConfig's per-operation hasAuthority(...) matchers deny it downstream "
            + "(403 path already covered by InternalEndpointSecurityTest)")
    void missingScope_decodesButGrantsNoAuthorities() throws Exception {
        String token = sign(issuer, "access", M2M_CLIENT_ID, null);
        JwtDecoder decoder = decoderWithAllowlist(M2M_CLIENT_ID);

        Jwt jwt = decoder.decode(token);

        assertThat(authoritiesOf(jwt)).isEmpty();
    }

    @Test
    @DisplayName("6: wrong issuer claim -> rejected, even with a signature the configured JWKS accepts")
    void wrongIssuer_rejected() throws Exception {
        String token = sign("https://not-the-configured-issuer.invalid", "access", M2M_CLIENT_ID,
                "sipsa/ingestion.execute");
        JwtDecoder decoder = decoderWithAllowlist(M2M_CLIENT_ID);

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static String errorDescription(Throwable ex) {
        if (ex instanceof OAuth2AuthenticationException authEx) {
            OAuth2Error error = authEx.getError();
            return error.getDescription() == null ? "" : error.getDescription();
        }
        return ex.getMessage() == null ? "" : ex.getMessage();
    }
}
