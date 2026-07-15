package com.dalejandrov.sipsa.infrastructure.config.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects any JWT whose {@code token_use} claim is not exactly {@code "access"}.
 * <p>
 * Cognito stamps {@code token_use} on every token it issues: {@code "access"} on access
 * tokens and {@code "id"} on ID tokens. ID tokens carry user identity for the client and
 * must never be accepted as API credentials — without this check, a leaked ID token
 * (which has no scopes) could still pass issuer/signature validation. A token missing the
 * claim entirely is also rejected: the local mock OIDC server is configured to stamp it
 * (see {@code docker/mock-oidc-config.json}).
 */
final class TokenUseValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error NOT_AN_ACCESS_TOKEN = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The token is not an access token (token_use != access)", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object tokenUse = jwt.getClaims().get("token_use");
        if ("access".equals(tokenUse)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(NOT_AN_ACCESS_TOKEN);
    }
}
