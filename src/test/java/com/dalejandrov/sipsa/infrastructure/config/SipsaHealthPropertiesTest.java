package com.dalejandrov.sipsa.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding and validation tests for {@link SipsaHealthProperties} (TECH-031).
 * <p>
 * Verifies the single-source-of-truth contract for the health indicator's staleness
 * thresholds: canonical defaults (36h/840h — the values {@code SipsaHealthIndicator}
 * always hardcoded), overrides via property and via the {@code SIPSA_HEALTH_*}
 * placeholder chains {@code application.yaml} uses (simulated with runner-scoped system
 * properties, never by mutating the process environment), and startup aborts for
 * zero/negative/non-numeric values.
 */
@DisplayName("SipsaHealthProperties — staleness threshold binding and validation")
class SipsaHealthPropertiesTest {

    private static final String[] YAML_PLACEHOLDER_CHAINS = {
            "sipsa.health.daily-staleness-threshold=${SIPSA_HEALTH_DAILY_STALENESS_THRESHOLD:36h}",
            "sipsa.health.monthly-staleness-threshold=${SIPSA_HEALTH_MONTHLY_STALENESS_THRESHOLD:840h}"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesHost.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SipsaHealthProperties.class)
    static class PropertiesHost {
    }

    @Test
    @DisplayName("without any override the canonical thresholds apply: 36h daily, 840h monthly")
    void canonicalDefaults() {
        runner.run(context -> {
            SipsaHealthProperties props = context.getBean(SipsaHealthProperties.class);
            assertThat(props.getDailyStalenessThreshold())
                    .isEqualTo(SipsaHealthProperties.DEFAULT_DAILY_STALENESS_THRESHOLD)
                    .isEqualTo(Duration.ofHours(36));
            assertThat(props.getMonthlyStalenessThreshold())
                    .isEqualTo(SipsaHealthProperties.DEFAULT_MONTHLY_STALENESS_THRESHOLD)
                    .isEqualTo(Duration.ofHours(840));
        });
    }

    @Test
    @DisplayName("property overrides win, accepting both hour and day duration suffixes")
    void propertyOverridesWin() {
        runner.withPropertyValues(
                        "sipsa.health.daily-staleness-threshold=12h",
                        "sipsa.health.monthly-staleness-threshold=10d")
                .run(context -> {
                    SipsaHealthProperties props = context.getBean(SipsaHealthProperties.class);
                    assertThat(props.getDailyStalenessThreshold()).isEqualTo(Duration.ofHours(12));
                    assertThat(props.getMonthlyStalenessThreshold()).isEqualTo(Duration.ofDays(10));
                });
    }

    @Test
    @DisplayName("the yaml placeholder chains fall back to the canonical values when SIPSA_HEALTH_* are unset")
    void placeholderChainsFallBack() {
        runner.withPropertyValues(YAML_PLACEHOLDER_CHAINS)
                .run(context -> {
                    SipsaHealthProperties props = context.getBean(SipsaHealthProperties.class);
                    assertThat(props.getDailyStalenessThreshold()).isEqualTo(Duration.ofHours(36));
                    assertThat(props.getMonthlyStalenessThreshold()).isEqualTo(Duration.ofHours(840));
                });
    }

    @Test
    @DisplayName("SIPSA_HEALTH_* equivalents win over the placeholder defaults")
    void environmentVariableEquivalentsWin() {
        runner.withSystemProperties(
                        "SIPSA_HEALTH_DAILY_STALENESS_THRESHOLD=24h",
                        "SIPSA_HEALTH_MONTHLY_STALENESS_THRESHOLD=720h")
                .withPropertyValues(YAML_PLACEHOLDER_CHAINS)
                .run(context -> {
                    SipsaHealthProperties props = context.getBean(SipsaHealthProperties.class);
                    assertThat(props.getDailyStalenessThreshold()).isEqualTo(Duration.ofHours(24));
                    assertThat(props.getMonthlyStalenessThreshold()).isEqualTo(Duration.ofHours(720));
                });
    }

    @Test
    @DisplayName("a zero daily threshold aborts startup naming the property")
    void zeroDailyThresholdFails() {
        runner.withPropertyValues("sipsa.health.daily-staleness-threshold=0h")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.health.daily-staleness-threshold must be positive"));
    }

    @Test
    @DisplayName("a negative monthly threshold aborts startup naming the property")
    void negativeMonthlyThresholdFails() {
        runner.withPropertyValues("sipsa.health.monthly-staleness-threshold=-1h")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.health.monthly-staleness-threshold must be positive"));
    }

    @Test
    @DisplayName("a non-duration value aborts startup as a binding failure")
    void nonDurationValueFails() {
        runner.withPropertyValues("sipsa.health.daily-staleness-threshold=not-a-duration")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.health"));
    }

    /** Same helper contract as the other *PropertiesTest classes in this package. */
    private static String failureMessages(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        StringBuilder messages = new StringBuilder();
        for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
