package com.dalejandrov.sipsa.infrastructure.config.security;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * JWT validation settings for the OAuth 2.0 Resource Server (ADR-002).
 * <p>
 * Both values are environment-driven and contain no secrets: the issuer URI and its
 * published JWKS are public information, and the allowed-client-ids list holds opaque
 * client identifiers, never client secrets.
 * <ul>
 *   <li>{@code spring.security.oauth2.resourceserver.jwt.issuer-uri}
 *       ({@code SIPSA_JWT_ISSUER_URI}) — required. The application fails fast at startup
 *       when it is missing, instead of starting with unverifiable tokens.</li>
 *   <li>{@code sipsa.security.jwt.allowed-client-ids}
 *       ({@code SIPSA_JWT_ALLOWED_CLIENT_IDS}) — optional CSV. When empty, any client of
 *       the trusted issuer is accepted (issuer + {@code token_use} + scopes still apply).
 *       When set, the token's {@code client_id} claim must be in the list. A malformed
 *       list (blank entries) fails at startup.</li>
 * </ul>
 */
@Component
public class SipsaJwtProperties {

    private final String issuerUri;
    private final List<String> allowedClientIds;

    public SipsaJwtProperties(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${sipsa.security.jwt.allowed-client-ids:}") String allowedClientIdsCsv) {

        if (issuerUri == null || issuerUri.isBlank()) {
            throw new SipsaConfigurationException(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri is required. "
                            + "Set the SIPSA_JWT_ISSUER_URI environment variable to the OIDC issuer "
                            + "(Cognito user pool URL in AWS, the local mock OIDC server in dev/docker). "
                            + "Refusing to start without a trusted token issuer.");
        }
        this.issuerUri = issuerUri.trim();
        this.allowedClientIds = parseCsv(allowedClientIdsCsv);
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String part : csv.split(",", -1)) {
            String id = part.trim();
            if (id.isEmpty()) {
                throw new SipsaConfigurationException(
                        "sipsa.security.jwt.allowed-client-ids (SIPSA_JWT_ALLOWED_CLIENT_IDS) is malformed: "
                                + "blank entry in '" + csv + "'. Provide a comma-separated list of client ids, "
                                + "or leave it empty to accept any client of the trusted issuer.");
            }
            ids.add(id);
        }
        return List.copyOf(ids);
    }

    public String issuerUri() {
        return issuerUri;
    }

    public List<String> allowedClientIds() {
        return allowedClientIds;
    }
}
