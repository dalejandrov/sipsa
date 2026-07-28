package com.dalejandrov.sipsa.api.util;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Utility class for timezone-aware date conversions.
 * <p>
 * This class provides methods to convert timestamps to the client's timezone
 * for system-generated records, while keeping historical/external records in UTC.
 */
public class TimezoneUtil {

    private static final ThreadLocal<ZoneId> REQUEST_TIMEZONE = new ThreadLocal<>();

    /**
     * Sets the timezone for the current request.
     *
     * @param zoneId the client's timezone
     */
    public static void setRequestTimezone(ZoneId zoneId) {
        REQUEST_TIMEZONE.set(zoneId);
    }

    /**
     * Gets the timezone for the current request, defaulting to UTC.
     *
     * @return the client's timezone or UTC
     */
    public static ZoneId getRequestTimezone() {
        ZoneId zone = REQUEST_TIMEZONE.get();
        return zone != null ? zone : ZoneOffset.UTC;
    }

    /**
     * Clears the timezone for the current request.
     */
    public static void clearRequestTimezone() {
        REQUEST_TIMEZONE.remove();
    }

    /**
     * Converts an Instant to OffsetDateTime using the request timezone if the record is system-generated,
     * otherwise keeps it in UTC.
     *
     * @param instant the timestamp to convert
     * @param isSystemGenerated whether the record was created/updated by the system
     * @return OffsetDateTime in request timezone if system-generated, else in UTC
     */
    public static OffsetDateTime convertToOffsetDateTime(Instant instant, boolean isSystemGenerated) {
        if (instant == null) {
            return null;
        }
        ZoneId zone = isSystemGenerated ? getRequestTimezone() : ZoneOffset.UTC;
        return instant.atZone(zone).toOffsetDateTime();
    }
}
