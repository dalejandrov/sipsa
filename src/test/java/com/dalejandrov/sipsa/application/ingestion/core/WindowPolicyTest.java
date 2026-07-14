package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.domain.exception.WindowViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WindowPolicy}, using an injected fixed {@link Clock} for every
 * scenario — no test depends on {@code LocalDateTime.now()}/{@code Instant.now()}/
 * {@code ZonedDateTime.now()} or the system's default timezone.
 * <p>
 * Two configurations are used throughout, both constructed directly (no Spring context):
 * <ul>
 *   <li><b>DANE-documented boundary</b> ({@code dailyStart=14:00}): isolates the raw
 *       2:00 p.m. rule from DANE's 2020 documentation, independent of the operational
 *       buffer actually deployed.</li>
 *   <li><b>Production configuration</b> ({@code dailyStart=14:20}, {@code monthlyStart=14:00},
 *       {@code monthlyRunDays=8,10}): the exact values resolved from
 *       {@code application.yaml}'s defaults, proving the deployed configuration behaves as
 *       configured.</li>
 * </ul>
 * <p>
 * <b>TECH-111:</b> the monthly rules are per-method — {@code promediosSipsaMesMadr} is
 * bound to days 8/9 and {@code promedioAbasSipsaMesMadr} to days 10/11, with the
 * {@code monthlyStart} time gate applying to principal and grace days alike. The
 * cross-method acceptance and grace-day time bypass originally found by TECH-110
 * (F-WP-01/F-WP-03, {@code docs/architecture/scheduled-ingestion-validation.md}) are fixed
 * and pinned by the {@code MonthlyWindowMesMadr}/{@code MonthlyWindowAbasMes} matrices below.
 */
@DisplayName("WindowPolicy")
class WindowPolicyTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static Clock fixedBogota(String isoInstant) {
        return Clock.fixed(Instant.parse(isoInstant), BOGOTA);
    }

    /** Mirrors application.yaml's resolved defaults: 14:20-23:59 daily, 8/10 monthly, 14:00 monthly start. */
    private static WindowPolicy productionPolicy() {
        return new WindowPolicy("14:20", "23:59", "8,10", "14:00", "America/Bogota");
    }

    /** Isolates DANE's raw documented 2:00 p.m. boundary, without the deployed 20-minute buffer. */
    private static WindowPolicy daneDocumentedBoundaryPolicy() {
        return new WindowPolicy("14:00", "23:59", "8,10", "14:00", "America/Bogota");
    }

    // ---------------------------------------------------------------------
    // monthly-run-days startup sanity check (TECH-111): the property no longer
    // participates in per-run validation; it must simply be compatible with the
    // contractual per-method rules fixed in code.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("monthly-run-days startup sanity check")
    class MonthlyRunDaysSanityCheck {

        @Test
        @DisplayName("configured set missing a contractual principal day (8 or 10) -> fails at construction")
        void missingPrincipalDay_failsFast() {
            assertThatThrownBy(() -> new WindowPolicy("14:20", "23:59", "8", "14:00", "America/Bogota"))
                    .isInstanceOf(SipsaConfigurationException.class)
                    .hasMessageContaining("monthly-run-days");

            assertThatThrownBy(() -> new WindowPolicy("14:20", "23:59", "10,11", "14:00", "America/Bogota"))
                    .isInstanceOf(SipsaConfigurationException.class)
                    .hasMessageContaining("monthly-run-days");
        }

        @Test
        @DisplayName("exact contractual set (8,10) and supersets are accepted")
        void contractualSetAndSupersets_accepted() {
            new WindowPolicy("14:20", "23:59", "8,10", "14:00", "America/Bogota");
            new WindowPolicy("14:20", "23:59", "8,9,10,11", "14:00", "America/Bogota");
        }
    }

    // ---------------------------------------------------------------------
    // Daily / weekly window (Ciudad, Parcial, Semana — promediosSipsaSemanaMadr
    // is classified as daily by WindowPolicy.isMonthlyMethod, confirmed correct
    // per the DANE contrast in the validation report)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Daily window — DANE-documented 2:00 p.m. boundary")
    class DailyWindowDaneBoundary {

        @Test
        @DisplayName("13:59:59 -> rejected")
        void beforeTwoPm_rejected() {
            WindowPolicy policy = daneDocumentedBoundaryPolicy();
            policy.setClock(fixedBogota("2026-06-15T18:59:59Z")); // 13:59:59 America/Bogota (UTC-5)

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaCiudad", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("14:00:00 -> allowed")
        void exactlyTwoPm_allowed() {
            WindowPolicy policy = daneDocumentedBoundaryPolicy();
            policy.setClock(fixedBogota("2026-06-15T19:00:00Z")); // 14:00:00 America/Bogota

            String key = policy.validateAndGetKey("promediosSipsaCiudad", false);

            assertThat(key).isEqualTo("2026-06-15");
        }

        @Test
        @DisplayName("14:00:01 -> allowed")
        void oneSecondAfterTwoPm_allowed() {
            WindowPolicy policy = daneDocumentedBoundaryPolicy();
            policy.setClock(fixedBogota("2026-06-15T19:00:01Z")); // 14:00:01 America/Bogota

            String key = policy.validateAndGetKey("promediosSipsaCiudad", false);

            assertThat(key).isEqualTo("2026-06-15");
        }
    }

    @Nested
    @DisplayName("Daily window — production configuration (14:20 buffer)")
    class DailyWindowProductionConfig {

        @Test
        @DisplayName("14:19:59 -> rejected (before the deployed 14:20 buffer)")
        void beforeBufferedStart_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-15T19:19:59Z")); // 14:19:59 America/Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaParcial", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("14:20:00 -> allowed")
        void atBufferedStart_allowed() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-15T19:20:00Z")); // 14:20:00 America/Bogota

            assertThat(policy.validateAndGetKey("promediosSipsaSemanaMadr", false)).isEqualTo("2026-06-15");
        }

        @Test
        @DisplayName("23:59:00 -> allowed (end of window, inclusive)")
        void atWindowEnd_allowed() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-16T04:59:00Z")); // 23:59:00 America/Bogota on the 15th

            assertThat(policy.validateAndGetKey("promediosSipsaCiudad", false)).isEqualTo("2026-06-15");
        }

        @Test
        @DisplayName("day rolls over at midnight America/Bogota -> new windowKey")
        void nextDay_newWindowKey() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-06-15T19:20:00Z")); // day 15, 14:20 Bogota
            String day15Key = policy.validateAndGetKey("promediosSipsaCiudad", false);

            policy.setClock(fixedBogota("2026-06-16T19:20:00Z")); // day 16, 14:20 Bogota
            String day16Key = policy.validateAndGetKey("promediosSipsaCiudad", false);

            assertThat(day15Key).isEqualTo("2026-06-15");
            assertThat(day16Key).isEqualTo("2026-06-16");
            assertThat(day15Key).isNotEqualTo(day16Key);
        }

        @Test
        @DisplayName("uses the configured America/Bogota calendar date, not UTC or the server/container default zone")
        void usesBogotaCalendarDate_notUtcOrServerDefault() {
            WindowPolicy policy = productionPolicy();
            // 2026-06-09T03:00:00Z is already June 9 in UTC, but only 22:00 on June 8 in
            // America/Bogota (UTC-5, no DST). A server/container running in UTC that
            // naively used its own default zone would compute June 9 here; WindowPolicy
            // must compute June 8, since it always builds its Clock from the configured
            // "sipsa.timezone", never from the JVM/container default.
            policy.setClock(fixedBogota("2026-06-09T03:00:00Z"));

            // Outside the daily window (22:00 > 23:59? no, 22:00 is within 14:20-23:59),
            // so this call succeeds and its key proves which calendar day was used.
            String key = policy.validateAndGetKey("promediosSipsaCiudad", true);

            assertThat(key).isEqualTo("2026-06-08");
        }
    }

    // ---------------------------------------------------------------------
    // Monthly window — per-method rules (TECH-111): each method is bound to
    // its own DANE publication day (MesMadr: 8/9, AbasMes: 10/11), and the
    // monthlyStart time gate applies to the principal day AND the grace day.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Monthly window — promediosSipsaMesMadr (principal day 8, grace day 9)")
    class MonthlyWindowMesMadr {

        @Test
        @DisplayName("day 7 -> rejected")
        void day7_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-07T19:00:00Z")); // day 7, 14:00 Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 8, before 14:00 -> rejected")
        void day8BeforeStart_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T18:59:59Z")); // day 8, 13:59:59 Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 8, at/after 14:00 -> accepted")
        void day8AtOrAfterStart_accepted() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T19:00:00Z")); // day 8, 14:00 Bogota

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", false)).isEqualTo("2026-06-08");
        }

        @Test
        @DisplayName("day 9 (grace), before 14:00 -> rejected — the time gate applies to the grace day too")
        void day9BeforeStart_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-09T05:00:00Z")); // day 9, 00:00:00 Bogota (midnight)

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 9 (grace), at/after 14:00 -> accepted")
        void day9AtOrAfterStart_accepted() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-09T19:00:00Z")); // day 9, 14:00 Bogota

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", false)).isEqualTo("2026-06-09");
        }

        @Test
        @DisplayName("day 10, any time -> rejected — day 10 belongs to AbasMes, not MesMadr")
        void day10_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-10T19:00:00Z")); // day 10, 14:00 Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 11, any time -> rejected")
        void day11_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-11T19:00:00Z")); // day 11, 14:00 Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 12 -> rejected")
        void day12_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-12T19:00:00Z"));

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }
    }

    @Nested
    @DisplayName("Monthly window — promedioAbasSipsaMesMadr (principal day 10, grace day 11)")
    class MonthlyWindowAbasMes {

        @Test
        @DisplayName("day 8, any time -> rejected — day 8 belongs to MesMadr, not AbasMes")
        void day8_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T19:00:00Z")); // day 8, 14:00 Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 9, any time -> rejected")
        void day9_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-09T19:00:00Z")); // day 9, 14:00 Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 10, before 14:00 -> rejected")
        void day10BeforeStart_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-10T18:59:59Z")); // day 10, 13:59:59 Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 10, at/after 14:00 -> accepted")
        void day10AtOrAfterStart_accepted() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-10T19:00:00Z")); // day 10, 14:00 Bogota

            assertThat(policy.validateAndGetKey("promedioAbasSipsaMesMadr", false)).isEqualTo("2026-06-10");
        }

        @Test
        @DisplayName("day 11 (grace), before 14:00 -> rejected — the time gate applies to the grace day too")
        void day11BeforeStart_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-11T05:00:00Z")); // day 11, 00:00:00 Bogota (midnight)

            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("day 11 (grace), at/after 14:00 -> accepted")
        void day11AtOrAfterStart_accepted() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-11T19:00:00Z")); // day 11, 14:00 Bogota

            assertThat(policy.validateAndGetKey("promedioAbasSipsaMesMadr", false)).isEqualTo("2026-06-11");
        }

        @Test
        @DisplayName("day 12 -> rejected")
        void day12_rejected() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-12T19:00:00Z"));

            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("force=true bypasses the window on any day, for both methods")
        void forceTrue_bypassesWindow_forBothMethods() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-01T05:00:00Z")); // day 1 -- normally always rejected

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", true)).isEqualTo("2026-06-01");
            assertThat(policy.validateAndGetKey("promedioAbasSipsaMesMadr", true)).isEqualTo("2026-06-01");
        }
    }

    // ---------------------------------------------------------------------
    // Desired post-fix behavior originally specified by TECH-110's @Disabled
    // tests (re-enabled by TECH-111's commit 3)
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Monthly window — cross-method day acceptance is fixed (TECH-111)")
    class MonthlyWindowConfirmedBugDemonstration {

        @Test
        @Disabled("Documents the DESIRED behavior once TECH-111 binds the allowed day to the "
                + "specific method. Currently fails: WindowPolicy.validateMonthly() does not "
                + "receive or use the method name, so day 8 is accepted for AbasMes too. "
                + "Re-enable this test as part of TECH-111's acceptance criteria.")
        @DisplayName("DESIRED (post-fix): promedioAbasSipsaMesMadr should be rejected on day 8")
        void abastecimientosMensual_diaOcho_deberiaSerRechazadoTrasElFix() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T19:00:00Z"));

            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @Disabled("Documents the DESIRED behavior once TECH-111 binds the allowed day to the "
                + "specific method. Currently fails: WindowPolicy.validateMonthly() does not "
                + "receive or use the method name, so day 10 is accepted for MesMadr too. "
                + "Re-enable this test as part of TECH-111's acceptance criteria.")
        @DisplayName("DESIRED (post-fix): promediosSipsaMesMadr should be rejected on day 10")
        void mayoristasMensual_diaDiez_deberiaSerRechazadoTrasElFix() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-10T19:00:00Z"));

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }
    }

    // ---------------------------------------------------------------------
    // windowKey semantics
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("windowKey semantics")
    class WindowKeySemantics {

        @Test
        @DisplayName("same method, same simulated instant -> same windowKey")
        void samePeriodSameMethod_sameKey() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T19:00:00Z"));

            String first = policy.validateAndGetKey("promediosSipsaMesMadr", false);
            String second = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            assertThat(first).isEqualTo(second).isEqualTo("2026-06-08");
        }

        @Test
        @DisplayName("CONFIRMED BUG (idempotency): a retry on the grace day (day 9) produces a DIFFERENT windowKey than day 8, for the same logical monthly period")
        void retryOnGraceDay_producesDifferentWindowKey_forSameLogicalPeriod() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-06-08T19:00:00Z")); // day 8 run
            String day8Key = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            policy.setClock(fixedBogota("2026-06-09T19:00:00Z")); // day 9 retry (14:00 Bogota), same logical month
            String day9Key = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            // WindowPolicy's own Javadoc documents monthly keys as "YYYY-MM-M8"/"YYYY-MM-M10"
            // (stable per logical period). The actual implementation uses the raw run date,
            // so these two keys differ even though both belong to June's MesMadr period.
            // This breaks the (method_name, window_key) uniqueness guarantee across retries
            // on the grace day. Documented here, not fixed -- see TECH-111 (commit 2).
            assertThat(day8Key).isEqualTo("2026-06-08");
            assertThat(day9Key).isEqualTo("2026-06-09");
            assertThat(day8Key).isNotEqualTo(day9Key);
        }

        @Test
        @DisplayName("MesMadr and AbasMes, each validated on its own day, produce keys derived from their own run dates")
        void differentMethodsOwnDays_produceKeysFromOwnRunDates() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-06-08T19:00:00Z")); // MesMadr's principal day
            String mesKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            policy.setClock(fixedBogota("2026-06-10T19:00:00Z")); // AbasMes' principal day
            String abasKey = policy.validateAndGetKey("promedioAbasSipsaMesMadr", false);

            // Raw-run-date keys (replaced by the stable YYYY-MM-M8/M10 markers in
            // TECH-111's commit 2). Uniqueness in the DB is scoped by
            // (method_name, window_key), not by the key string alone.
            assertThat(mesKey).isEqualTo("2026-06-08");
            assertThat(abasKey).isEqualTo("2026-06-10");
        }

        @Test
        @DisplayName("year rollover: December 9 (MesMadr grace day) to January 8 next year -> new, correctly-formatted windowKey")
        void yearRollover_decemberToJanuary_newCorrectWindowKey() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-12-09T19:00:00Z")); // grace day, 14:00 Bogota
            String decemberKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            policy.setClock(fixedBogota("2027-01-08T19:00:00Z"));
            String januaryKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            assertThat(decemberKey).isEqualTo("2026-12-09");
            assertThat(januaryKey).isEqualTo("2027-01-08");
        }
    }
}
