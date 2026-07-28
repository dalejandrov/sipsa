package com.dalejandrov.sipsa.api.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TECH-102: timezone-conversion coverage for {@link TimezoneUtil#convertToOffsetDateTime}
 * across {@code America/Bogota} (fixed offset, never DST), {@code America/New_York} and
 * {@code America/Los_Angeles} (both observe US DST), and {@code UTC} — including the exact
 * 2026 US DST transition instants. TECH-104 narrowed this method's remaining scope to
 * genuine instants only ({@code fechaCreacion}, error-response timestamps —
 * {@code fechaSincronizacion} is an internal audit timestamp, never exposed on a response
 * DTO, so it is no longer converted here either); the 4 DANE calendar-date fields moved to
 * {@code SipsaIngestionMapper.millisToBusinessLocalDate}, covered separately in
 * {@link com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapperTest}.
 */
class TimezoneUtilTest {

    @AfterEach
    void clearRequestTimezone() {
        TimezoneUtil.clearRequestTimezone();
    }

    @Test
    @DisplayName("null instant maps to null OffsetDateTime")
    void nullInstant_mapsToNull() {
        assertThat(TimezoneUtil.convertToOffsetDateTime(null, true)).isNull();
        assertThat(TimezoneUtil.convertToOffsetDateTime(null, false)).isNull();
    }

    @Test
    @DisplayName("isSystemGenerated=false always renders in UTC, regardless of the request timezone")
    void isSystemGeneratedFalse_alwaysUtc() {
        Instant instant = Instant.parse("2026-07-15T12:00:00Z");
        TimezoneUtil.setRequestTimezone(ZoneId.of("America/New_York"));

        assertThat(TimezoneUtil.convertToOffsetDateTime(instant, false))
                .isEqualTo(instant.atOffset(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("isSystemGenerated=true with no request timezone set defaults to UTC")
    void isSystemGeneratedTrue_noRequestTimezone_defaultsToUtc() {
        Instant instant = Instant.parse("2026-07-15T12:00:00Z");

        assertThat(TimezoneUtil.convertToOffsetDateTime(instant, true))
                .isEqualTo(instant.atOffset(ZoneOffset.UTC));
    }

    @ParameterizedTest(name = "{0}: {1} -> offset {2}")
    @DisplayName("isSystemGenerated=true converts to the request timezone's correct offset, across zones and DST")
    @CsvSource({
            // zoneId,              instant (UTC),          expected offset  | note
            "America/Bogota,        2026-01-15T12:00:00Z,   -05:00",  // no DST, ever
            "America/Bogota,        2026-07-15T12:00:00Z,   -05:00",  // no DST, ever - same offset in July
            "America/New_York,      2026-01-15T12:00:00Z,   -05:00",  // EST (winter, before spring-forward)
            "America/New_York,      2026-07-15T12:00:00Z,   -04:00",  // EDT (summer, DST active)
            "America/Los_Angeles,   2026-01-15T12:00:00Z,   -08:00",  // PST (winter)
            "America/Los_Angeles,   2026-07-15T12:00:00Z,   -07:00",  // PDT (summer, DST active)
            "UTC,                   2026-07-15T12:00:00Z,   Z",
    })
    void convertToOffsetDateTime_acrossZonesAndSeasons(String zoneId, String instantText, String expectedOffset) {
        Instant instant = Instant.parse(instantText);
        TimezoneUtil.setRequestTimezone(ZoneId.of(zoneId));

        OffsetDateTime result = TimezoneUtil.convertToOffsetDateTime(instant, true);

        assertThat(result).isEqualTo(instant.atZone(ZoneId.of(zoneId)).toOffsetDateTime());
        assertThat(result.getOffset().getId()).isEqualTo(expectedOffset);
    }

    @Test
    @DisplayName("US spring-forward 2026 (America/New_York, 2026-03-08 02:00 local -> 03:00): offset flips exactly at the transition")
    void usSpringForward2026_offsetFlipsAtTransition() {
        ZoneId newYork = ZoneId.of("America/New_York");
        TimezoneUtil.setRequestTimezone(newYork);

        // One second before 2:00 AM local (still EST, -05:00): 2026-03-08T06:59:59Z.
        Instant justBefore = Instant.parse("2026-03-08T06:59:59Z");
        // The transition instant itself: 2:00 AM local is skipped, clocks jump to 3:00 AM
        // EDT (-04:00) - 2026-03-08T07:00:00Z is already past the gap.
        Instant atTransition = Instant.parse("2026-03-08T07:00:00Z");

        assertThat(TimezoneUtil.convertToOffsetDateTime(justBefore, true).getOffset())
                .isEqualTo(ZoneOffset.ofHours(-5));
        assertThat(TimezoneUtil.convertToOffsetDateTime(atTransition, true).getOffset())
                .isEqualTo(ZoneOffset.ofHours(-4));
    }

    @Test
    @DisplayName("US fall-back 2026 (America/New_York, 2026-11-01 02:00 local -> 01:00): offset flips exactly at the transition")
    void usFallBack2026_offsetFlipsAtTransition() {
        ZoneId newYork = ZoneId.of("America/New_York");
        TimezoneUtil.setRequestTimezone(newYork);

        // One second before 2:00 AM EDT (-04:00): 2026-11-01T05:59:59Z.
        Instant justBefore = Instant.parse("2026-11-01T05:59:59Z");
        // The transition instant: clocks fall back to 1:00 AM EST (-05:00).
        Instant atTransition = Instant.parse("2026-11-01T06:00:00Z");

        assertThat(TimezoneUtil.convertToOffsetDateTime(justBefore, true).getOffset())
                .isEqualTo(ZoneOffset.ofHours(-4));
        assertThat(TimezoneUtil.convertToOffsetDateTime(atTransition, true).getOffset())
                .isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    @DisplayName("America/Bogota crosses both US DST transition instants with no offset change at all")
    void bogota_unaffectedByUsDstTransitions() {
        TimezoneUtil.setRequestTimezone(ZoneId.of("America/Bogota"));

        Instant springForward = Instant.parse("2026-03-08T07:00:00Z");
        Instant fallBack = Instant.parse("2026-11-01T06:00:00Z");

        assertThat(TimezoneUtil.convertToOffsetDateTime(springForward, true).getOffset())
                .isEqualTo(ZoneOffset.ofHours(-5));
        assertThat(TimezoneUtil.convertToOffsetDateTime(fallBack, true).getOffset())
                .isEqualTo(ZoneOffset.ofHours(-5));
    }
}
