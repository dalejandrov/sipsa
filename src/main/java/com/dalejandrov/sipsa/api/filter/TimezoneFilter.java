package com.dalejandrov.sipsa.api.filter;

import com.dalejandrov.sipsa.api.controller.GlobalExceptionHandler;
import com.dalejandrov.sipsa.api.util.TimezoneUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Filter to set the request timezone based on headers or user preferences.
 * <p>
 * Resolves the client's timezone in this order:
 * 1. X-Timezone header (IANA timezone ID)
 * 2. UTC as fallback
 * <p>
 * ADR-008 F4: an <em>absent</em> header still falls back to UTC silently — that is the
 * intended default for an international API. An header that IS present but not a valid
 * IANA zone ID is different: it is caller error, not absence, so it short-circuits the
 * chain with {@code 400 SIPSA_INVALID_TIMEZONE} instead of degrading to UTC unnoticed.
 */
@Component
@Slf4j
public class TimezoneFilter extends OncePerRequestFilter {

    private static final String TIMEZONE_HEADER = "X-Timezone";
    static final String INVALID_TIMEZONE_CODE = "SIPSA_INVALID_TIMEZONE";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String timezoneHeader = request.getHeader(TIMEZONE_HEADER);
        if (timezoneHeader != null && !timezoneHeader.trim().isEmpty()) {
            try {
                ZoneId zoneId = ZoneId.of(timezoneHeader.trim());
                TimezoneUtil.setRequestTimezone(zoneId);
                log.debug("Set request timezone to: {}", zoneId);
            } catch (Exception e) {
                log.debug("Invalid timezone header '{}': {}", timezoneHeader, e.getMessage());
                writeInvalidTimezoneResponse(request, response, timezoneHeader);
                return;
            }
        } else {
            TimezoneUtil.setRequestTimezone(ZoneOffset.UTC);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TimezoneUtil.clearRequestTimezone();
        }
    }

    /**
     * Writes a {@code 400} response for an {@code X-Timezone} header that is present but
     * not a valid IANA zone ID, in the same {@link GlobalExceptionHandler.ErrorResponse}
     * shape every other API error uses. Runs before {@code DispatcherServlet}, so this
     * cannot go through {@code @ControllerAdvice} — the body is assembled and written
     * directly, deliberately without a Jackson {@code ObjectMapper}: this filter runs in
     * every servlet context (including narrow test slices that don't always expose one),
     * and the fixed, seven-field shape below doesn't need a general-purpose serializer.
     */
    private void writeInvalidTimezoneResponse(HttpServletRequest request, HttpServletResponse response, String headerValue)
            throws IOException {
        Object requestIdAttribute = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        String requestId = requestIdAttribute != null ? requestIdAttribute.toString() : "";

        GlobalExceptionHandler.ErrorResponse body = new GlobalExceptionHandler.ErrorResponse(
                TimezoneUtil.convertToOffsetDateTime(Instant.now(), true),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                INVALID_TIMEZONE_CODE,
                "Invalid X-Timezone header value '" + headerValue.trim()
                        + "': expected an IANA timezone ID, e.g. America/Bogota",
                requestId,
                request.getRequestURI()
        );

        String json = "{"
                + "\"timestamp\":\"" + jsonEscape(body.timestamp().toString()) + "\","
                + "\"status\":" + body.status() + ","
                + "\"error\":\"" + jsonEscape(body.error()) + "\","
                + "\"code\":\"" + jsonEscape(body.code()) + "\","
                + "\"message\":\"" + jsonEscape(body.message()) + "\","
                + "\"requestId\":\"" + jsonEscape(body.requestId()) + "\","
                + "\"instance\":\"" + jsonEscape(body.instance()) + "\""
                + "}";

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }

    /**
     * Minimal JSON string escaping. The header value flows into {@code message}
     * unvalidated (it's caller input, by definition — that's why the request was
     * rejected), so quotes/backslashes/control characters must not be able to break the
     * response's JSON structure.
     */
    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
