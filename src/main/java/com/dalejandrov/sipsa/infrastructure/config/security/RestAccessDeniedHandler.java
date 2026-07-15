package com.dalejandrov.sipsa.infrastructure.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns {@code 403 Forbidden} as JSON when an authenticated caller lacks the required
 * scope. The body never reveals which scope was missing — that detail goes to the server
 * log only. No HTML, no stack trace.
 */
@Component
@Slf4j
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.debug("Denied request to {} {}: {}",
                request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());
        SecurityErrorWriter.writeForbidden(response);
    }
}
