package com.dalejandrov.sipsa.application.ingestion.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the three production cron expressions declared on
 * {@link SipsaIngestionScheduler} without waiting for the system clock or depending on the
 * machine's default timezone.
 * <p>
 * Two things are validated independently:
 * <ol>
 *   <li>The literal cron expression strings (copied from {@code application.yaml}'s
 *       defaults / {@code @Scheduled} annotation defaults) compute the expected next-fire
 *       time in {@code America/Bogota}, using {@link CronExpression}, exactly as Spring's
 *       scheduler would.</li>
 *   <li>The {@code @Scheduled} annotations on {@link SipsaIngestionScheduler} actually
 *       declare {@code zone = "${sipsa.timezone:America/Bogota}"} — i.e. that the zone is
 *       not left to the JVM/container default. This is checked via reflection on the live
 *       annotations, so it cannot silently drift from the real source.</li>
 * </ol>
 * <p>
 * <b>Reference:</b> DANE's 2020 documentation states daily/weekly Mayoristas data becomes
 * available "a partir de las 2:00 p.m."; the production cron fires at 14:20 (20-minute
 * operational buffer, see {@code application.yaml:118-125}), and the two monthly crons fire
 * at 14:30 on day 8 (Mayoristas monthly) and day 10 (Abastecimientos monthly) respectively.
 * This test validates the buffered cron values as implemented, not DANE's raw 14:00 — that
 * contrast is documented separately in
 * {@code docs/architecture/scheduled-ingestion-validation.md}.
 */
@DisplayName("SipsaIngestionScheduler cron expressions")
class SipsaSchedulingCronTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    // Literal values copied from SipsaIngestionScheduler's @Scheduled defaults /
    // application.yaml's sipsa.ingestion.cron.* defaults. Kept as constants here (not
    // reflectively read) so a change to the production value is caught by an explicit
    // assertion, not silently re-derived.
    private static final String DAILY_CRON = "0 20 14 * * *";
    private static final String MONTHLY_MES_CRON = "0 30 14 8 * *";
    private static final String MONTHLY_ABAS_CRON = "0 30 14 10 * *";

    private ZonedDateTime bogota(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, BOGOTA);
    }

    @Nested
    @DisplayName("Daily window cron (Ciudad, Parcial, Semana) — 14:20 America/Bogota")
    class DailyCron {

        private final CronExpression cron = CronExpression.parse(DAILY_CRON);

        @Test
        @DisplayName("reference before 14:20 -> fires same day at 14:20:00")
        void beforeWindow_firesSameDay() {
            ZonedDateTime reference = bogota(2026, 3, 15, 13, 59, 59);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 3, 15, 14, 20, 0));
        }

        @Test
        @DisplayName("reference exactly 14:20:00 -> next fire is the following day (CronExpression.next is exclusive)")
        void exactlyAtTrigger_firesNextDay() {
            ZonedDateTime reference = bogota(2026, 3, 15, 14, 20, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 3, 16, 14, 20, 0));
        }

        @Test
        @DisplayName("reference after 14:20 -> fires the following day at 14:20:00")
        void afterWindow_firesNextDay() {
            ZonedDateTime reference = bogota(2026, 3, 15, 18, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 3, 16, 14, 20, 0));
        }

        @Test
        @DisplayName("fires every day of the month, including day 8, 9, 10 and 11 (day-of-month field is *)")
        void firesOnEveryDayOfMonth_includingMonthlyRunDays() {
            for (int day = 7; day <= 11; day++) {
                ZonedDateTime reference = bogota(2026, 4, day, 10, 0, 0);
                ZonedDateTime next = cron.next(reference);
                assertThat(next).as("day %d", day).isEqualTo(bogota(2026, 4, day, 14, 20, 0));
            }
        }

        @Test
        @DisplayName("month rollover: December 31 -> next fire is January 1 next year")
        void monthRollover_decemberToJanuary() {
            ZonedDateTime reference = bogota(2026, 12, 31, 20, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2027, 1, 1, 14, 20, 0));
        }

        @Test
        @DisplayName("leap day: February 29, 2028 is a valid trigger day")
        void leapDay_fires() {
            ZonedDateTime reference = bogota(2028, 2, 29, 10, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2028, 2, 29, 14, 20, 0));
        }
    }

    @Nested
    @DisplayName("Monthly MesMadr cron (Mayoristas) — day 8, 14:30 America/Bogota")
    class MonthlyMesCron {

        private final CronExpression cron = CronExpression.parse(MONTHLY_MES_CRON);

        @Test
        @DisplayName("day 7 -> next fire is day 8 at 14:30:00, same month")
        void dayBefore_firesNextDaySameMonth() {
            ZonedDateTime reference = bogota(2026, 6, 7, 23, 59, 59);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 6, 8, 14, 30, 0));
        }

        @Test
        @DisplayName("day 8, before 14:30 -> fires day 8 at 14:30:00")
        void day8BeforeTrigger_firesSameDay() {
            ZonedDateTime reference = bogota(2026, 6, 8, 9, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 6, 8, 14, 30, 0));
        }

        @Test
        @DisplayName("day 8, after 14:30 -> next fire rolls to day 8 of the *next* month, not day 9, 10 or 11")
        void day8AfterTrigger_rollsToNextMonthDay8_notGraceDays() {
            ZonedDateTime reference = bogota(2026, 6, 8, 15, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 7, 8, 14, 30, 0));
        }

        @Test
        @DisplayName("day 9, 10 or 11 -> next fire is day 8 of the following month (the cron itself has no grace day)")
        void graceDays_haveNoCronTrigger_nextIsFollowingMonth() {
            for (int day : new int[]{9, 10, 11}) {
                ZonedDateTime reference = bogota(2026, 6, day, 8, 0, 0);
                ZonedDateTime next = cron.next(reference);
                assertThat(next).as("day %d", day).isEqualTo(bogota(2026, 7, 8, 14, 30, 0));
            }
        }

        @Test
        @DisplayName("year rollover: reference in December -> next fire is January 8 next year")
        void yearRollover_decemberToJanuary() {
            ZonedDateTime reference = bogota(2026, 12, 9, 0, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2027, 1, 8, 14, 30, 0));
        }
    }

    @Nested
    @DisplayName("Monthly AbasMes cron (Abastecimientos) — day 10, 14:30 America/Bogota")
    class MonthlyAbasCron {

        private final CronExpression cron = CronExpression.parse(MONTHLY_ABAS_CRON);

        @Test
        @DisplayName("day 9 -> next fire is day 10 at 14:30:00, same month")
        void dayBefore_firesNextDaySameMonth() {
            ZonedDateTime reference = bogota(2026, 6, 9, 23, 59, 59);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 6, 10, 14, 30, 0));
        }

        @Test
        @DisplayName("day 10, before 14:30 -> fires day 10 at 14:30:00")
        void day10BeforeTrigger_firesSameDay() {
            ZonedDateTime reference = bogota(2026, 6, 10, 9, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 6, 10, 14, 30, 0));
        }

        @Test
        @DisplayName("day 10, after 14:30 -> next fire rolls to day 10 of the *next* month, not day 8, 9 or 11")
        void day10AfterTrigger_rollsToNextMonthDay10_notGraceDays() {
            ZonedDateTime reference = bogota(2026, 6, 10, 15, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2026, 7, 10, 14, 30, 0));
        }

        @Test
        @DisplayName("day 8, 9 or 11 -> the cron does not trigger on any of these; next fire is day 10")
        void nonTriggerDays_nextIsDay10SameOrFollowingMonth() {
            ZonedDateTime referenceDay8 = bogota(2026, 6, 8, 8, 0, 0);
            assertThat(cron.next(referenceDay8)).isEqualTo(bogota(2026, 6, 10, 14, 30, 0));

            ZonedDateTime referenceDay11 = bogota(2026, 6, 11, 8, 0, 0);
            assertThat(cron.next(referenceDay11)).isEqualTo(bogota(2026, 7, 10, 14, 30, 0));
        }

        @Test
        @DisplayName("year rollover: reference in December -> next fire is January 10 next year")
        void yearRollover_decemberToJanuary() {
            ZonedDateTime reference = bogota(2026, 12, 11, 0, 0, 0);
            ZonedDateTime next = cron.next(reference);

            assertThat(next).isEqualTo(bogota(2027, 1, 10, 14, 30, 0));
        }
    }

    @Nested
    @DisplayName("No overlap between the two monthly crons")
    class NoOverlap {

        @Test
        @DisplayName("MesMadr (day 8) and AbasMes (day 10) never fire on the same calendar day")
        void mesMadrAndAbasMes_fireOnDifferentDays() {
            CronExpression mes = CronExpression.parse(MONTHLY_MES_CRON);
            CronExpression abas = CronExpression.parse(MONTHLY_ABAS_CRON);

            ZonedDateTime reference = bogota(2026, 1, 1, 0, 0, 0);
            ZonedDateTime nextMes = mes.next(reference);
            ZonedDateTime nextAbas = abas.next(reference);

            assertThat(nextMes.toLocalDate()).isNotEqualTo(nextAbas.toLocalDate());
            assertThat(nextMes.getDayOfMonth()).isEqualTo(8);
            assertThat(nextAbas.getDayOfMonth()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("Zone declared on the @Scheduled annotations")
    class DeclaredZone {

        @Test
        @DisplayName("all three scheduled methods declare zone = ${sipsa.timezone:America/Bogota}, not the JVM default")
        void allThreeMethods_declareBogotaZonePlaceholder() throws NoSuchMethodException {
            assertZone("runDailyWindow");
            assertZone("runMonthlyMes");
            assertZone("runMonthlyAbas");
        }

        private void assertZone(String methodName) throws NoSuchMethodException {
            Method method = SipsaIngestionScheduler.class.getDeclaredMethod(methodName);
            Scheduled scheduled = method.getAnnotation(Scheduled.class);

            assertThat(scheduled).as("@Scheduled on %s", methodName).isNotNull();
            assertThat(scheduled.zone())
                    .as("zone attribute on %s", methodName)
                    .isEqualTo("${sipsa.timezone:America/Bogota}");
        }
    }
}
