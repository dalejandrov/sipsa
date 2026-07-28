package com.dalejandrov.sipsa.api.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-008 F1: {@link TimezoneUtil#toBusinessLocalDate(Instant)} must resolve the calendar
 * date of DANE period-start/survey fields (e.g. {@code fechaCaptura}, {@code fechaMesIni},
 * {@code fechaIni}, {@code enmaFecha}) in the fixed business zone ({@code America/Bogota}),
 * never the request's {@code X-Timezone} nor UTC — that is what makes those fields immune
 * to date-shifting by construction rather than by the {@code isSystemGenerated=false}
 * convention {@link #convertToOffsetDateTime} relies on for genuine instants.
 */
class TimezoneUtilTest {

    @AfterEach
    void clearRequestTimezone() {
        TimezoneUtil.clearRequestTimezone();
    }

    @Test
    @DisplayName("null instant maps to null date")
    void nullInstant_mapsToNull() {
        assertThat(TimezoneUtil.toBusinessLocalDate(null)).isNull();
    }

    @Test
    @DisplayName("midnight Bogota, stored as its UTC instant, resolves back to the same calendar day")
    void midnightBogota_resolvesToSameDay() {
        // 2026-07-15T00:00:00-05:00 == 2026-07-15T05:00:00Z
        Instant instant = Instant.parse("2026-07-15T05:00:00Z");

        assertThat(TimezoneUtil.toBusinessLocalDate(instant)).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("late-evening Bogota instant near the UTC day boundary still resolves to the Bogota day, not the UTC day")
    void lateEveningBogota_doesNotShiftToNextUtcDay() {
        // 2026-07-15T20:00:00-05:00 == 2026-07-16T01:00:00Z — UTC has already rolled to the
        // 16th, but the Bogota-observed day is still the 15th. This is exactly the shift
        // ADR-008 F1 flags as a latent risk under the old UTC-pinned convention.
        Instant instant = Instant.parse("2026-07-16T01:00:00Z");

        assertThat(TimezoneUtil.toBusinessLocalDate(instant)).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("ignores the request's X-Timezone entirely — always resolves in the fixed business zone")
    void ignoresRequestTimezone() {
        Instant instant = Instant.parse("2026-07-16T01:00:00Z");
        TimezoneUtil.setRequestTimezone(ZoneId.of("Asia/Tokyo"));

        // In Asia/Tokyo (+09:00) this instant would already be 2026-07-16 — proving the
        // result does NOT follow the request timezone.
        assertThat(TimezoneUtil.toBusinessLocalDate(instant)).isEqualTo(LocalDate.of(2026, 7, 15));
    }
}
