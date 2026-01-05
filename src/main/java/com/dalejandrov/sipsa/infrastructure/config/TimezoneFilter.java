package com.dalejandrov.sipsa.infrastructure.config;

import com.dalejandrov.sipsa.api.util.TimezoneUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Filter to set the request timezone based on headers or user preferences.
 * <p>
 * Resolves the client's timezone in this order:
 * 1. X-Timezone header (IANA timezone ID)
 * 2. UTC as fallback
 */
@Component
@Slf4j
public class TimezoneFilter extends OncePerRequestFilter {

    private static final String TIMEZONE_HEADER = "X-Timezone";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            ZoneId zoneId = resolveTimezone(request);
            TimezoneUtil.setRequestTimezone(zoneId);
            log.debug("Set request timezone to: {}", zoneId);
        } catch (Exception e) {
            log.warn("Failed to resolve timezone, using UTC: {}", e.getMessage());
            TimezoneUtil.setRequestTimezone(ZoneOffset.UTC);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TimezoneUtil.clearRequestTimezone();
        }
    }

    /**
     * Resolves the client's timezone from the request.
     * <p>
     * Checks the X-Timezone header first, then falls back to UTC.
     *
     * @param request the HTTP servlet request
     * @return the resolved ZoneId, never null
     */
    private ZoneId resolveTimezone(HttpServletRequest request) {
        String timezoneHeader = request.getHeader(TIMEZONE_HEADER);
        if (timezoneHeader != null && !timezoneHeader.trim().isEmpty()) {
            try {
                return ZoneId.of(timezoneHeader.trim());
            } catch (Exception e) {
                log.warn("Invalid timezone header '{}': {}", timezoneHeader, e.getMessage());
            }
        }

        return ZoneOffset.UTC;
    }
}
