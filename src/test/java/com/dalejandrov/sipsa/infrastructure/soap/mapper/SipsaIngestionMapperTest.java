package com.dalejandrov.sipsa.infrastructure.soap.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TECH-102: timezone-conversion coverage for {@link SipsaIngestionMapper}'s two epoch-millis
 * qualifiers. TECH-104 moved calendar-date resolution from the API read path
 * (per-request, {@code X-Timezone}-influenced) to here — ingestion time, once per record,
 * always in the fixed {@code America/Bogota} business zone. This is now the one place a
 * date-shift bug could actually happen, so it gets the direct, boundary-focused unit
 * coverage: exact midnight, late-evening-crossing-the-UTC-day-boundary (the scenario ADR-008
 * F1 originally flagged as the latent risk), and confirmation that {@code America/Bogota}
 * never observes DST (unlike the US zones TECH-102 also asks to cover for the still-Instant
 * fields, exercised in {@link com.dalejandrov.sipsa.api.util.TimezoneUtilTest}).
 */
class SipsaIngestionMapperTest {

    private final SipsaIngestionMapper mapper = new SipsaIngestionMapperImpl();

    @Test
    @DisplayName("millisToBusinessLocalDate: null millis maps to null date")
    void millisToBusinessLocalDate_null_mapsToNull() {
        assertThat(mapper.millisToBusinessLocalDate(null)).isNull();
    }

    @Test
    @DisplayName("millisToBusinessLocalDate: exact Bogota midnight resolves to that calendar day")
    void millisToBusinessLocalDate_exactMidnightBogota() {
        // 2026-07-15T00:00:00-05:00 == 2026-07-15T05:00:00Z
        long millis = Instant.parse("2026-07-15T05:00:00Z").toEpochMilli();

        assertThat(mapper.millisToBusinessLocalDate(millis)).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("millisToBusinessLocalDate: late-evening Bogota instant does not shift to the next UTC day")
    void millisToBusinessLocalDate_lateEveningBogota_staysOnBogotaDay() {
        // 2026-07-15T20:00:00-05:00 == 2026-07-16T01:00:00Z — UTC has already rolled to the
        // 16th, but the Bogota-observed day is still the 15th. Exactly the shift ADR-008 F1
        // flagged as a latent risk under the old UTC-pinned mapping convention.
        long millis = Instant.parse("2026-07-16T01:00:00Z").toEpochMilli();

        assertThat(mapper.millisToBusinessLocalDate(millis)).isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("millisToBusinessLocalDate: just-before-midnight Bogota still resolves to the earlier day")
    void millisToBusinessLocalDate_justBeforeMidnightBogota() {
        // 2026-07-14T23:59:59-05:00 == 2026-07-15T04:59:59Z
        long millis = Instant.parse("2026-07-15T04:59:59Z").toEpochMilli();

        assertThat(mapper.millisToBusinessLocalDate(millis)).isEqualTo(LocalDate.of(2026, 7, 14));
    }

    @ParameterizedTest(name = "{0} -> Bogota offset is always -05:00, no DST (millis {1})")
    @DisplayName("America/Bogota never observes DST: the same wall-clock midnight is always -05:00, year-round")
    @CsvSource({
            "2026-01-15T05:00:00Z, 2026-01-15", // southern-hemisphere-adjacent January, no US DST in effect
            "2026-07-15T05:00:00Z, 2026-07-15", // July, US DST in effect elsewhere - irrelevant to Bogota
            "2026-03-08T05:00:00Z, 2026-03-08", // US spring-forward date - Bogota unaffected
            "2026-11-01T05:00:00Z, 2026-11-01", // US fall-back date - Bogota unaffected
    })
    void millisToBusinessLocalDate_neverObservesDst(String instantText, String expectedDate) {
        long millis = Instant.parse(instantText).toEpochMilli();

        assertThat(mapper.millisToBusinessLocalDate(millis)).isEqualTo(LocalDate.parse(expectedDate));
        assertThat(ZoneId.of("America/Bogota").getRules().getOffset(Instant.parse(instantText)))
                .as("Bogota's UTC offset is fixed year-round")
                .isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    @DisplayName("millisToInstant: unaffected by TECH-104, still a plain epoch-millis passthrough")
    void millisToInstant_stillPlainPassthrough() {
        long millis = Instant.parse("2026-07-15T12:34:56Z").toEpochMilli();

        assertThat(mapper.millisToInstant(millis)).isEqualTo(Instant.ofEpochMilli(millis));
        assertThat(mapper.millisToInstant(null)).isNull();
    }
}
