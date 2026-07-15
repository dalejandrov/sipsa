package com.dalejandrov.sipsa.infrastructure.config.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Writes security error responses (401/403) as JSON matching the shape of the API's
 * {@code ErrorResponse} contract ({@code timestamp}, {@code status}, {@code error},
 * {@code code}, {@code message}).
 * <p>
 * The body is built by hand on purpose: security filters run before the MVC layer, and
 * this keeps the infrastructure layer free of imports from {@code api.*} (ADR-007
 * package boundaries) and independent of the Jackson version. All interpolated values
 * are fixed constants or an ISO timestamp — no user-controlled input is echoed, so no
 * JSON escaping is required. Messages are deliberately generic: they never reveal whether
 * the failure was a missing token, a bad signature, a wrong issuer, a disallowed
 * {@code client_id}, or which scope was missing.
 */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    static void writeForbidden(HttpServletResponse response) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied");
    }

    private static void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{"
                + "\"timestamp\":\"" + LocalDateTime.now() + "\","
                + "\"status\":" + status.value() + ","
                + "\"error\":\"" + status.getReasonPhrase() + "\","
                + "\"code\":\"" + code + "\","
                + "\"message\":\"" + message + "\""
                + "}");
    }
}
