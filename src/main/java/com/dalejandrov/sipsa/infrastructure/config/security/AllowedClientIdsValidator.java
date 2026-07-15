package com.dalejandrov.sipsa.infrastructure.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Optional defense-in-depth allowlist: when active, the token's {@code client_id} claim
 * must match one of the configured ids.
 * <p>
 * Cognito access tokens issued via {@code client_credentials} carry no {@code aud} claim,
 * so the standard audience validator cannot pin which app clients this API trusts —
 * {@code client_id} is the claim that identifies the caller. This validator is only
 * registered when {@code sipsa.security.jwt.allowed-client-ids} is non-empty (see
 * {@link SipsaJwtProperties}); a token without the claim is rejected while the allowlist
 * is active.
 */
final class AllowedClientIdsValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error CLIENT_NOT_ALLOWED = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The token's client is not allowed for this API", null);

    private final List<String> allowedClientIds;

    AllowedClientIdsValidator(List<String> allowedClientIds) {
        if (allowedClientIds == null || allowedClientIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "AllowedClientIdsValidator must not be registered with an empty allowlist");
        }
        this.allowedClientIds = List.copyOf(allowedClientIds);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object clientId = jwt.getClaims().get("client_id");
        if (clientId instanceof String id && allowedClientIds.contains(id)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(CLIENT_NOT_ALLOWED);
    }
}
