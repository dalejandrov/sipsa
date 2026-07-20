package com.dalejandrov.sipsa.api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Establishes a single correlation ID for the current request, exposed to downstream
 * code — notably {@link com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler} —
 * as the {@value #REQUEST_ID_ATTRIBUTE} request attribute.
 * <p>
 * TECH-023: no existing per-HTTP-request correlation ID source was found in this
 * repository before this filter. The only prior "requestId" concept was the
 * ingestion-domain business correlation ID ({@code UUID.randomUUID()} in
 * {@code IngestionTriggerService} / {@code SipsaIngestionScheduler}, generated only for
 * ingestion-trigger operations, not every HTTP request), and MDC (via
 * {@code IngestionJob}) is populated only deep inside the async ingestion pipeline,
 * never on the synchronous request-handling thread — so neither covers, say, a
 * validation error on an unrelated endpoint. This filter is the minimal new
 * infrastructure needed: it honors an incoming {@value #HEADER_NAME} header if the
 * caller already provides one (normalized here, in one place, rather than read ad hoc
 * by individual handlers), otherwise generates a UUID — exactly once per request, set
 * as a request attribute before the rest of the filter chain runs so it survives even
 * when a downstream handler throws. Echoed back on the response header of the same
 * name so callers that didn't supply one can still learn it.
 * <p>
 * TECH-023 follow-up (hardening): the incoming header is untrusted client input, so it
 * is validated against {@link #VALID_REQUEST_ID}, not reused verbatim. An invalid value
 * (missing, blank, longer than {@value #MAX_LENGTH} characters, or containing anything
 * outside the allowed character set — which also rules out CR/LF, so it can never cause
 * response-header injection or a JSON-breaking value) is silently replaced with a
 * generated UUID rather than rejected: a malformed correlation ID is not the caller's
 * fault to fix with a {@code 400}, it just isn't trustworthy enough to echo back or log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final String HEADER_NAME = "X-Request-Id";
    private static final int MAX_LENGTH = 128;
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER_NAME);
        String trimmed = incoming != null ? incoming.trim() : null;
        String requestId = (trimmed != null && VALID_REQUEST_ID.matcher(trimmed).matches())
                ? trimmed
                : UUID.randomUUID().toString();

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);

        filterChain.doFilter(request, response);
    }
}
