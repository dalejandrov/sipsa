package com.dalejandrov.sipsa.infrastructure.observability;

import com.dalejandrov.sipsa.application.ingestion.core.WindowPolicy;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.infrastructure.config.SipsaHealthProperties;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavior gate for {@link SipsaHealthIndicator}'s staleness thresholds (TECH-031):
 * pins that externalizing them to {@link SipsaHealthProperties} preserved the exact
 * prior comparison (strictly-greater-than the threshold, in whole hours) for both the
 * "daily" method group and every other ("monthly") method, at both the canonical
 * defaults and under overrides. The clock is fixed via the package-private test seam —
 * no dependency on wall-clock time.
 * <p>
 * {@link WindowPolicy} is mocked here (TECH-056) — this class no longer maintains its own
 * daily/monthly method list, so these cases stub {@code isMonthlyMethod} directly rather
 * than relying on real method names. The consolidation itself (real dependency on
 * {@code WindowPolicy}, no reintroduced hardcoded list) is verified separately in
 * {@code SipsaHealthIndicatorClassificationConsistencyTest}.
 */
@DisplayName("SipsaHealthIndicator — configurable staleness thresholds")
class SipsaHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    private final IngestionRunRepository runRepository = mock(IngestionRunRepository.class);
    private final WindowPolicy windowPolicy = mock(WindowPolicy.class);

    private SipsaHealthIndicator indicatorWith(Duration dailyThreshold, Duration monthlyThreshold) {
        SipsaHealthProperties properties = new SipsaHealthProperties();
        properties.setDailyStalenessThreshold(dailyThreshold);
        properties.setMonthlyStalenessThreshold(monthlyThreshold);
        SipsaHealthIndicator indicator = new SipsaHealthIndicator(runRepository, properties, windowPolicy);
        indicator.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        return indicator;
    }

    private void lastRunAgeHours(String method, long ageHours) {
        when(runRepository.findLastRunPerMethodByStatus(IngestionRunStatus.SUCCEEDED))
                .thenReturn(List.<Object[]>of(new Object[]{method, NOW.minus(Duration.ofHours(ageHours))}));
    }

    @Test
    @DisplayName("daily method exactly at the default 36h threshold stays UP (strict >, not >=)")
    void dailyMethod_exactlyAtDefaultThreshold_staysUp() {
        lastRunAgeHours("promediosSipsaCiudad", 36);
        when(windowPolicy.isMonthlyMethod("promediosSipsaCiudad")).thenReturn(false);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("daily method one hour past the default 36h threshold goes DOWN and is marked STALE")
    void dailyMethod_pastDefaultThreshold_goesDown() {
        lastRunAgeHours("promediosSipsaCiudad", 37);
        when(windowPolicy.isMonthlyMethod("promediosSipsaCiudad")).thenReturn(false);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("promediosSipsaCiudad_status", "STALE");
    }

    @Test
    @DisplayName("monthly method exactly at the default 840h (35d) threshold stays UP")
    void monthlyMethod_exactlyAtDefaultThreshold_staysUp() {
        lastRunAgeHours("promediosSipsaMesMadr", 840);
        when(windowPolicy.isMonthlyMethod("promediosSipsaMesMadr")).thenReturn(true);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("monthly method one hour past the default 840h threshold goes DOWN")
    void monthlyMethod_pastDefaultThreshold_goesDown() {
        lastRunAgeHours("promediosSipsaMesMadr", 841);
        when(windowPolicy.isMonthlyMethod("promediosSipsaMesMadr")).thenReturn(true);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("promediosSipsaMesMadr_status", "STALE");
    }

    @Test
    @DisplayName("a configured override changes the effective threshold for the daily group")
    void configuredOverride_changesDailyThreshold() {
        // Age 20h would be fine under the 36h default but breaches a 12h override.
        lastRunAgeHours("promediosSipsaParcial", 20);
        when(windowPolicy.isMonthlyMethod("promediosSipsaParcial")).thenReturn(false);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(12), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("SipsaHealthIndicator genuinely depends on WindowPolicy: the same method/age gets a different verdict purely from WindowPolicy's answer")
    void healthIndicator_dependsOnWindowPolicyClassification_notAHardcodedList() {
        // Age 40h: past the 36h daily threshold, but well within the 840h monthly one.
        // The exact same method name and age must flip DOWN<->UP purely based on what
        // WindowPolicy reports - proving a real runtime dependency, not two lists that
        // happen to agree.
        lastRunAgeHours("someMethod", 40);

        when(windowPolicy.isMonthlyMethod("someMethod")).thenReturn(false);
        Health asDaily = indicatorWith(Duration.ofHours(36), Duration.ofHours(840)).health();
        assertThat(asDaily.getStatus()).as("WindowPolicy said not-monthly -> daily threshold applies -> STALE")
                .isEqualTo(Status.DOWN);

        when(windowPolicy.isMonthlyMethod("someMethod")).thenReturn(true);
        Health asMonthly = indicatorWith(Duration.ofHours(36), Duration.ofHours(840)).health();
        assertThat(asMonthly.getStatus()).as("WindowPolicy said monthly -> monthly threshold applies -> still fresh")
                .isEqualTo(Status.UP);

        verify(windowPolicy, atLeastOnce()).isMonthlyMethod(eq("someMethod"));
    }

    @Test
    @DisplayName("a method WindowPolicy does not recognize gets the daily (stricter) threshold, matching WindowPolicy's own 'not monthly' default for an unknown method")
    void unrecognizedMethod_getsDailyThreshold() {
        lastRunAgeHours("totallyUnknownMethod", 37);
        when(windowPolicy.isMonthlyMethod("totallyUnknownMethod")).thenReturn(false);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).as("37h > the 36h daily threshold - an unrecognized method is not exempt")
                .isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("no successful runs yet reports UNKNOWN, unaffected by thresholds")
    void noRuns_reportsUnknown() {
        when(runRepository.findLastRunPerMethodByStatus(IngestionRunStatus.SUCCEEDED))
                .thenReturn(List.of());
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    @DisplayName("TECH-056 regression: no hardcoded method-name collection field exists on this class anymore")
    void noHardcodedMethodListField_reintroduced() {
        // Structural, not a text/grep match: fails if any future field of a Collection
        // type (Set/List/anything implementing java.util.Collection) is added back to
        // this class - regardless of its name - which is exactly the shape the removed
        // DAILY_METHODS field had. The real classification now lives exclusively behind
        // the WindowPolicy dependency (constructor-injected, verified below).
        java.util.List<java.lang.reflect.Field> collectionFields = java.util.Arrays
                .stream(SipsaHealthIndicator.class.getDeclaredFields())
                .filter(f -> java.util.Collection.class.isAssignableFrom(f.getType()))
                .toList();

        assertThat(collectionFields)
                .as("SipsaHealthIndicator must not hold its own method-name collection; "
                        + "classification comes exclusively from WindowPolicy")
                .isEmpty();
    }

    @Test
    @DisplayName("TECH-056 regression: SipsaHealthIndicator's constructor requires a WindowPolicy dependency")
    void constructorRequiresWindowPolicy() {
        boolean hasWindowPolicyParam = java.util.Arrays
                .stream(SipsaHealthIndicator.class.getDeclaredConstructors())
                .flatMap(c -> java.util.Arrays.stream(c.getParameterTypes()))
                .anyMatch(WindowPolicy.class::equals);

        assertThat(hasWindowPolicyParam)
                .as("the single source of truth must be wired in via the constructor, not looked up statically")
                .isTrue();
    }
}
