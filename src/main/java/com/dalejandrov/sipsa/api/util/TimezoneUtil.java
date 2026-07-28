package com.dalejandrov.sipsa.api.util;

import java.time.Instant;
import java.time.LocalDate;
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

    /**
     * Fixed business zone (ADR-008) used to resolve calendar-date fields sourced from
     * DANE (survey/period-start dates). Deliberately not {@code ZoneId.systemDefault()}
     * and not the request's {@code X-Timezone} — these fields are Colombia dates by
     * definition, so the day they represent must never depend on the server host's zone
     * or on what a client happens to request.
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

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

    /**
     * Resolves the calendar date of a stored {@link Instant} that represents a
     * period-start/survey date from DANE (e.g. {@code fechaCaptura}, {@code fechaMesIni},
     * {@code fechaIni}, {@code enmaFecha}), never an instant a client should see converted
     * to their own zone. Always uses the fixed business zone, ignoring both the request's
     * {@code X-Timezone} and UTC — this is what makes those fields immune to date-shifting
     * by construction (ADR-008 F1), rather than by the convention previously enforced only
     * through {@link #convertToOffsetDateTime}'s {@code isSystemGenerated=false} default.
     *
     * @param instant the stored instant backing a calendar-date field
     * @return the calendar date in {@code America/Bogota}, or {@code null} if the instant is null
     */
    public static LocalDate toBusinessLocalDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(BUSINESS_ZONE).toLocalDate();
    }
}
