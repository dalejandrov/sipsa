package com.dalejandrov.sipsa.infrastructure.config.security;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the Cognito-specific JWT validators and the fail-fast configuration
 * properties (ADR-002). These cover the validator logic directly because the MockMvc
 * integration tests replace the {@code JwtDecoder} (and therefore bypass validators).
 */
@DisplayName("JWT validators and security properties")
class SipsaJwtValidatorsTest {

    private static Jwt jwtWithClaims(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("test-subject")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Nested
    @DisplayName("TokenUseValidator (token_use must be \"access\")")
    class TokenUse {

        private final TokenUseValidator validator = new TokenUseValidator();

        @Test
        @DisplayName("token_use=access -> valid")
        void accessToken_valid() {
            assertThat(validator.validate(jwtWithClaims(Map.of("token_use", "access"))).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("token_use=id (Cognito ID token) -> rejected")
        void idToken_rejected() {
            assertThat(validator.validate(jwtWithClaims(Map.of("token_use", "id"))).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("missing token_use claim -> rejected")
        void missingClaim_rejected() {
            assertThat(validator.validate(jwtWithClaims(Map.of())).hasErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("AllowedClientIdsValidator (optional client_id allowlist)")
    class AllowedClientIds {

        @Test
        @DisplayName("client_id in the allowlist -> valid")
        void allowedClient_valid() {
            AllowedClientIdsValidator validator =
                    new AllowedClientIdsValidator(List.of("client-a", "client-b"));

            assertThat(validator.validate(jwtWithClaims(Map.of("client_id", "client-b"))).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("client_id not in the allowlist -> rejected")
        void unknownClient_rejected() {
            AllowedClientIdsValidator validator = new AllowedClientIdsValidator(List.of("client-a"));

            assertThat(validator.validate(jwtWithClaims(Map.of("client_id", "intruder"))).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("missing client_id claim while the allowlist is active -> rejected")
        void missingClaim_rejected() {
            AllowedClientIdsValidator validator = new AllowedClientIdsValidator(List.of("client-a"));

            assertThat(validator.validate(jwtWithClaims(Map.of())).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("registering the validator with an empty list is a programming error")
        void emptyList_rejectedAtConstruction() {
            assertThatThrownBy(() -> new AllowedClientIdsValidator(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("SipsaJwtProperties (fail-fast configuration)")
    class Properties {

        @Test
        @DisplayName("missing issuer -> SipsaConfigurationException at startup")
        void missingIssuer_failsFast() {
            assertThatThrownBy(() -> new SipsaJwtProperties("", ""))
                    .isInstanceOf(SipsaConfigurationException.class)
                    .hasMessageContaining("SIPSA_JWT_ISSUER_URI");
        }

        @Test
        @DisplayName("empty allowlist -> no client restriction (empty list)")
        void emptyAllowlist_parsedAsEmpty() {
            SipsaJwtProperties props = new SipsaJwtProperties("https://issuer.example", "");

            assertThat(props.allowedClientIds()).isEmpty();
        }

        @Test
        @DisplayName("CSV allowlist with whitespace -> trimmed entries")
        void csvAllowlist_parsedAndTrimmed() {
            SipsaJwtProperties props = new SipsaJwtProperties("https://issuer.example", " client-a , client-b ");

            assertThat(props.allowedClientIds()).containsExactly("client-a", "client-b");
        }

        @Test
        @DisplayName("malformed allowlist (blank entry) -> SipsaConfigurationException")
        void malformedAllowlist_failsFast() {
            assertThatThrownBy(() -> new SipsaJwtProperties("https://issuer.example", "client-a,,client-b"))
                    .isInstanceOf(SipsaConfigurationException.class)
                    .hasMessageContaining("SIPSA_JWT_ALLOWED_CLIENT_IDS");
        }
    }
}
