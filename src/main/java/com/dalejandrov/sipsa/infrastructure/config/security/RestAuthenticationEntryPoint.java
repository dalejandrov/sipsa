package com.dalejandrov.sipsa.infrastructure.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns {@code 401 Unauthorized} as JSON for any unauthenticated request — missing
 * token, malformed token, bad signature, wrong issuer, expired, wrong {@code token_use},
 * or disallowed {@code client_id}. The body is identical in every case (the specific
 * cause goes to the server log only), there is no HTML, no stack trace, and no session
 * or cookie is created. A {@code WWW-Authenticate: Bearer} header is included per
 * RFC 6750.
 */
@Component
@Slf4j
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("Rejected unauthenticated request to {} {}: {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());
        SecurityErrorWriter.writeUnauthorized(response);
    }
}
