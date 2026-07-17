package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.domain.exception.WindowViolationException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
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

    /**
     * Builds the typed properties WindowPolicy consumes since TECH-133 — a plain
     * validated POJO, so no Spring context is ever needed in this test class.
     */
    private static IngestionProperties props(String monthlyStartHHmm) {
        IngestionProperties properties = new IngestionProperties();
        properties.setMonthlyWindowStart(LocalTime.parse(monthlyStartHHmm));
        return properties;
    }

    /** Mirrors application.yaml's resolved defaults: 14:20-23:59 daily, 8/10 monthly, 14:00 monthly start. */
    private static WindowPolicy productionPolicy() {
        return new WindowPolicy("14:20", "23:59", "8,10", props("14:00"), "America/Bogota");
    }

    /** Isolates DANE's raw documented 2:00 p.m. boundary, without the deployed 20-minute buffer. */
    private static WindowPolicy daneDocumentedBoundaryPolicy() {
        return new WindowPolicy("14:00", "23:59", "8,10", props("14:00"), "America/Bogota");
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
            assertThatThrownBy(() -> new WindowPolicy("14:20", "23:59", "8", props("14:00"), "America/Bogota"))
                    .isInstanceOf(SipsaConfigurationException.class)
                    .hasMessageContaining("monthly-run-days");

            assertThatThrownBy(() -> new WindowPolicy("14:20", "23:59", "10,11", props("14:00"), "America/Bogota"))
                    .isInstanceOf(SipsaConfigurationException.class)
                    .hasMessageContaining("monthly-run-days");
        }

        @Test
        @DisplayName("exact contractual set (8,10) and supersets are accepted")
        void contractualSetAndSupersets_accepted() {
            new WindowPolicy("14:20", "23:59", "8,10", props("14:00"), "America/Bogota");
            new WindowPolicy("14:20", "23:59", "8,9,10,11", props("14:00"), "America/Bogota");
        }
    }

    // ---------------------------------------------------------------------
    // Monthly window start from typed IngestionProperties (TECH-133): the
    // authorization gate is centrally configured; the old @Value fallback
    // (06:00, never effective) is gone. All boundaries tested with Clock.fixed
    // so results are identical on any machine, JVM default zone, or container.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Monthly window start — typed configuration (TECH-133)")
    class MonthlyWindowStartFromProperties {

        /** Same policy as production but with the gate overridden to 10:30. */
        private WindowPolicy overriddenPolicy() {
            return new WindowPolicy("14:20", "23:59", "8,10", props("10:30"), "America/Bogota");
        }

        @Test
        @DisplayName("plain IngestionProperties default is the canonical 14:00 — never 06:00")
        void canonicalDefaultIs1400() {
            assertThat(new IngestionProperties().getMonthlyWindowStart())
                    .isEqualTo(IngestionProperties.DEFAULT_MONTHLY_WINDOW_START)
                    .isEqualTo(LocalTime.of(14, 0));
        }

        @Test
        @DisplayName("overridden gate 10:30 — one minute before (10:29) the run is rejected")
        void overriddenGate_oneMinuteBefore_rejected() {
            WindowPolicy policy = overriddenPolicy();
            policy.setClock(fixedBogota("2026-06-08T15:29:00Z")); // day 8, 10:29 America/Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class)
                    .hasMessageContaining("10:30");
        }

        @Test
        @DisplayName("overridden gate 10:30 — exactly at 10:30:00 the run is authorized")
        void overriddenGate_exactStart_authorized() {
            WindowPolicy policy = overriddenPolicy();
            policy.setClock(fixedBogota("2026-06-08T15:30:00Z")); // day 8, 10:30:00 America/Bogota

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", false)).isEqualTo("2026-06-M8");
        }

        @Test
        @DisplayName("overridden gate 10:30 — one minute after (10:31) the run is authorized")
        void overriddenGate_oneMinuteAfter_authorized() {
            WindowPolicy policy = overriddenPolicy();
            policy.setClock(fixedBogota("2026-06-08T15:31:00Z")); // day 8, 10:31 America/Bogota

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", false)).isEqualTo("2026-06-M8");
        }

        @Test
        @DisplayName("an early gate never authorizes the wrong day — day 7 rejected even after 10:30")
        void overriddenGate_wrongDayStillRejected() {
            WindowPolicy policy = overriddenPolicy();
            policy.setClock(fixedBogota("2026-06-07T17:00:00Z")); // day 7, 12:00 America/Bogota

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("the configured zone decides the outcome — the same instant is authorized in UTC but rejected in Bogota")
        void explicitZone_decidesOutcome_notTheMachine() {
            // 2026-06-08T15:00:00Z is 15:00 in UTC (>= 10:30 -> authorized) but only
            // 10:00 in America/Bogota (< 10:30 -> rejected). Whatever zone the JVM,
            // container or CI machine runs in, each policy answers from its own
            // configured zone, never from ZoneId.systemDefault().
            Instant instant = Instant.parse("2026-06-08T15:00:00Z");

            WindowPolicy utcPolicy = new WindowPolicy("14:20", "23:59", "8,10", props("10:30"), "UTC");
            utcPolicy.setClock(Clock.fixed(instant, ZoneId.of("UTC")));
            assertThat(utcPolicy.validateAndGetKey("promediosSipsaMesMadr", false)).isEqualTo("2026-06-M8");

            WindowPolicy bogotaPolicy = overriddenPolicy();
            bogotaPolicy.setClock(Clock.fixed(instant, BOGOTA));
            assertThatThrownBy(() -> bogotaPolicy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("force=true bypasses the gate before the window start but still returns the stable period key")
        void force_bypassesGate_keepsStableKey() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T10:00:00Z")); // day 8, 05:00 America/Bogota

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", true)).isEqualTo("2026-06-M8");
        }

        @Test
        @DisplayName("an invalid timezone fails at construction, before any run is attempted")
        void invalidZone_failsAtConstruction() {
            assertThatThrownBy(() -> new WindowPolicy("14:20", "23:59", "8,10", props("14:00"), "America/Bogotaa"))
                    .isInstanceOf(java.time.DateTimeException.class);
        }

        @Test
        @DisplayName("a null monthly window start fails at construction with the property name")
        void nullMonthlyStart_failsAtConstruction() {
            IngestionProperties broken = new IngestionProperties();
            broken.setMonthlyWindowStart(null);

            assertThatThrownBy(() -> new WindowPolicy("14:20", "23:59", "8,10", broken, "America/Bogota"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sipsa.ingestion.monthly-window-start");
        }
    }

    // ---------------------------------------------------------------------
    // Daily / weekly window (Ciudad, Parcial, Semana — promediosSipsaSemanaMadr
    // resolves no monthly rule and is therefore classified as daily, confirmed
    // correct per the DANE contrast in the validation report)
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

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", false)).isEqualTo("2026-06-M8");
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

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", false)).isEqualTo("2026-06-M8");
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

            assertThat(policy.validateAndGetKey("promedioAbasSipsaMesMadr", false)).isEqualTo("2026-06-M10");
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

            assertThat(policy.validateAndGetKey("promedioAbasSipsaMesMadr", false)).isEqualTo("2026-06-M10");
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
        @DisplayName("force=true bypasses the window on any day, for both methods, and returns the correct period key, not the forced-on date")
        void forceTrue_bypassesWindow_forBothMethods() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-01T05:00:00Z")); // day 1 -- normally always rejected

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", true)).isEqualTo("2026-06-M8");
            assertThat(policy.validateAndGetKey("promedioAbasSipsaMesMadr", true)).isEqualTo("2026-06-M10");
        }
    }

    // ---------------------------------------------------------------------
    // TECH-111 acceptance: the two tests TECH-110 left @Disabled, re-enabled.
    // They are the canonical cross-method rejection cases (F-WP-01) and
    // complete the per-method matrices above — Abas day 8 and MesMadr day 10
    // are asserted here, not duplicated in the matrix classes.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Monthly window — cross-method day acceptance is fixed (TECH-111)")
    class MonthlyWindowCrossMethodRejection {

        @Test
        @DisplayName("promedioAbasSipsaMesMadr is rejected on day 8 (MesMadr's day), any time")
        void abastecimientosMensual_diaOcho_deberiaSerRechazadoTrasElFix() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T19:00:00Z"));

            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("promediosSipsaMesMadr is rejected on day 10 (AbasMes' day), any time")
        void mayoristasMensual_diaDiez_deberiaSerRechazadoTrasElFix() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-10T19:00:00Z"));

            assertThatThrownBy(() -> policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }
    }

    // ---------------------------------------------------------------------
    // Rule resolution order: "promedioAbasSipsaMesMadr" contains BOTH the
    // "abas" and "mesmadr" fragments — resolution must check "abas" first,
    // or the Abastecimientos method would silently receive MesMadr's
    // day-8/M8 rule.
    // ---------------------------------------------------------------------

    @Nested
    @DisplayName("Monthly rule resolution — abas takes precedence over mesmadr")
    class MonthlyRuleResolutionOrder {

        @Test
        @DisplayName("promedioAbasSipsaMesMadr resolves to the AbasMes rule (day 10, key M10), not MesMadr's")
        void abasMethodContainingBothFragments_getsAbasRule() {
            WindowPolicy policy = productionPolicy();

            // Accepted on AbasMes' own day with AbasMes' key marker...
            policy.setClock(fixedBogota("2026-06-10T19:00:00Z"));
            assertThat(policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isEqualTo("2026-06-M10");

            // ...and rejected on MesMadr's day — proving it did NOT get the M8 rule
            // despite its name also containing "mesmadr".
            policy.setClock(fixedBogota("2026-06-08T19:00:00Z"));
            assertThatThrownBy(() -> policy.validateAndGetKey("promedioAbasSipsaMesMadr", false))
                    .isInstanceOf(WindowViolationException.class);
        }

        @Test
        @DisplayName("promediosSipsaMesMadr (mesmadr fragment only) resolves to the MesMadr rule (day 8, key M8)")
        void mesMadrMethod_getsMesMadrRule() {
            WindowPolicy policy = productionPolicy();
            policy.setClock(fixedBogota("2026-06-08T19:00:00Z"));

            assertThat(policy.validateAndGetKey("promediosSipsaMesMadr", false))
                    .isEqualTo("2026-06-M8");
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

            assertThat(first).isEqualTo(second).isEqualTo("2026-06-M8");
        }

        @Test
        @DisplayName("idempotency (F-WP-02 fixed): a retry on the grace day (day 9) reuses the SAME windowKey as day 8, for the same logical monthly period")
        void retryOnGraceDay_reusesSameWindowKey_forSameLogicalPeriod() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-06-08T19:00:00Z")); // day 8 run
            String day8Key = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            policy.setClock(fixedBogota("2026-06-09T19:00:00Z")); // day 9 retry (14:00 Bogota), same logical month
            String day9Key = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            // The key is the stable per-period marker, not the raw run date, so a
            // principal-day run and its grace-day retry resolve to the same
            // (method_name, window_key) row — the idempotency guarantee holds.
            assertThat(day8Key).isEqualTo(day9Key).isEqualTo("2026-06-M8");
        }

        @Test
        @DisplayName("idempotency (F-WP-02 fixed): an AbasMes retry on the grace day (day 11) reuses the SAME windowKey as day 10")
        void abasRetryOnGraceDay_reusesSameWindowKey_forSameLogicalPeriod() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-06-10T19:00:00Z")); // day 10 run
            String day10Key = policy.validateAndGetKey("promedioAbasSipsaMesMadr", false);

            policy.setClock(fixedBogota("2026-06-11T19:00:00Z")); // day 11 retry, same logical month
            String day11Key = policy.validateAndGetKey("promedioAbasSipsaMesMadr", false);

            assertThat(day10Key).isEqualTo(day11Key).isEqualTo("2026-06-M10");
        }

        @Test
        @DisplayName("MesMadr and AbasMes in the same month produce DIFFERENT keys (M8 vs M10)")
        void differentMethodsSameMonth_produceDifferentKeys() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-06-08T19:00:00Z")); // MesMadr's principal day
            String mesKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            policy.setClock(fixedBogota("2026-06-10T19:00:00Z")); // AbasMes' principal day
            String abasKey = policy.validateAndGetKey("promedioAbasSipsaMesMadr", false);

            assertThat(mesKey).isEqualTo("2026-06-M8");
            assertThat(abasKey).isEqualTo("2026-06-M10");
            assertThat(mesKey).isNotEqualTo(abasKey);
        }

        @Test
        @DisplayName("same method, month changes -> different keys")
        void sameMethodDifferentMonth_differentKeys() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-06-08T19:00:00Z"));
            String juneKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            policy.setClock(fixedBogota("2026-07-08T19:00:00Z"));
            String julyKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            assertThat(juneKey).isEqualTo("2026-06-M8");
            assertThat(julyKey).isEqualTo("2026-07-M8");
        }

        @Test
        @DisplayName("year rollover: December 9 (MesMadr grace day) to January 8 next year -> new, correctly-formatted windowKey")
        void yearRollover_decemberToJanuary_newCorrectWindowKey() {
            WindowPolicy policy = productionPolicy();

            policy.setClock(fixedBogota("2026-12-09T19:00:00Z")); // grace day, 14:00 Bogota
            String decemberKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            policy.setClock(fixedBogota("2027-01-08T19:00:00Z"));
            String januaryKey = policy.validateAndGetKey("promediosSipsaMesMadr", false);

            assertThat(decemberKey).isEqualTo("2026-12-M8");
            assertThat(januaryKey).isEqualTo("2027-01-M8");
        }
    }
}
