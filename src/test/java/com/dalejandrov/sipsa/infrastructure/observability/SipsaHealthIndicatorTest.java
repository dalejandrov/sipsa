package com.dalejandrov.sipsa.infrastructure.observability;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Behavior gate for {@link SipsaHealthIndicator}'s staleness thresholds (TECH-031):
 * pins that externalizing them to {@link SipsaHealthProperties} preserved the exact
 * prior comparison (strictly-greater-than the threshold, in whole hours) for both the
 * "daily" method group and every other ("monthly") method, at both the canonical
 * defaults and under overrides. The clock is fixed via the package-private test seam —
 * no dependency on wall-clock time.
 */
@DisplayName("SipsaHealthIndicator — configurable staleness thresholds")
class SipsaHealthIndicatorTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");

    private final IngestionRunRepository runRepository = mock(IngestionRunRepository.class);

    private SipsaHealthIndicator indicatorWith(Duration dailyThreshold, Duration monthlyThreshold) {
        SipsaHealthProperties properties = new SipsaHealthProperties();
        properties.setDailyStalenessThreshold(dailyThreshold);
        properties.setMonthlyStalenessThreshold(monthlyThreshold);
        SipsaHealthIndicator indicator = new SipsaHealthIndicator(runRepository, properties);
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
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("daily method one hour past the default 36h threshold goes DOWN and is marked STALE")
    void dailyMethod_pastDefaultThreshold_goesDown() {
        lastRunAgeHours("promediosSipsaCiudad", 37);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("promediosSipsaCiudad_status", "STALE");
    }

    @Test
    @DisplayName("monthly method exactly at the default 840h (35d) threshold stays UP")
    void monthlyMethod_exactlyAtDefaultThreshold_staysUp() {
        lastRunAgeHours("promediosSipsaMesMadr", 840);
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(36), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("monthly method one hour past the default 840h threshold goes DOWN")
    void monthlyMethod_pastDefaultThreshold_goesDown() {
        lastRunAgeHours("promediosSipsaMesMadr", 841);
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
        SipsaHealthIndicator indicator = indicatorWith(Duration.ofHours(12), Duration.ofHours(840));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
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
}
