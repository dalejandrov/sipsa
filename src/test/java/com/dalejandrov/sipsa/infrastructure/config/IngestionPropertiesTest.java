package com.dalejandrov.sipsa.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding and validation tests for {@link IngestionProperties} (TECH-071).
 * <p>
 * Verifies the single-source-of-truth contract for the ingestion batch size:
 * <ul>
 *   <li>Canonical default of 500 when nothing overrides it</li>
 *   <li>Override via the {@code sipsa.ingestion.batch-size} property</li>
 *   <li>Override via the {@code INGESTION_BATCH_SIZE} variable through the
 *       {@code ${INGESTION_BATCH_SIZE:500}} placeholder used in application.yaml
 *       (simulated with runner-scoped system properties, never by mutating the
 *       real process environment)</li>
 *   <li>Invalid values (zero, negative, non-numeric, above the maximum) abort
 *       context startup with a clear validation message</li>
 * </ul>
 */
@DisplayName("IngestionProperties — batch-size binding and validation")
class IngestionPropertiesTest {

    /**
     * Mirrors the exact placeholder chain declared in application.yaml so the
     * env-var precedence tests exercise the same resolution path production uses.
     */
    private static final String YAML_PLACEHOLDER_CHAIN =
            "sipsa.ingestion.batch-size=${INGESTION_BATCH_SIZE:500}";

    /** Same, for the monthly window start (TECH-133). */
    private static final String MONTHLY_YAML_PLACEHOLDER_CHAIN =
            "sipsa.ingestion.monthly-window-start=${INGESTION_MONTHLY_WINDOW_START:14:00}";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesHost.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(IngestionProperties.class)
    static class PropertiesHost {
    }

    @Test
    @DisplayName("without any override the canonical default 500 applies")
    void defaultBatchSizeIs500() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IngestionProperties.class);
            assertThat(context.getBean(IngestionProperties.class).getBatchSize())
                    .isEqualTo(IngestionProperties.DEFAULT_BATCH_SIZE)
                    .isEqualTo(500);
        });
    }

    @Test
    @DisplayName("sipsa.ingestion.batch-size=250 overrides the default")
    void propertyOverrideWins() {
        runner.withPropertyValues("sipsa.ingestion.batch-size=250")
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getBatchSize())
                        .isEqualTo(250));
    }

    @Test
    @DisplayName("the yaml placeholder chain resolves to 500 when INGESTION_BATCH_SIZE is unset")
    void placeholderChainFallsBackTo500() {
        runner.withPropertyValues(YAML_PLACEHOLDER_CHAIN)
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getBatchSize())
                        .isEqualTo(500));
    }

    @Test
    @DisplayName("INGESTION_BATCH_SIZE=750 wins over the placeholder default")
    void environmentVariableEquivalentWins() {
        runner.withSystemProperties("INGESTION_BATCH_SIZE=750")
                .withPropertyValues(YAML_PLACEHOLDER_CHAIN)
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getBatchSize())
                        .isEqualTo(750));
    }

    @Test
    @DisplayName("batch-size=0 aborts startup with a clear validation message")
    void zeroFailsStartup() {
        runner.withPropertyValues("sipsa.ingestion.batch-size=0")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.batch-size must be a positive integer"));
    }

    @Test
    @DisplayName("batch-size=-1 aborts startup with a clear validation message")
    void negativeFailsStartup() {
        runner.withPropertyValues("sipsa.ingestion.batch-size=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.batch-size must be a positive integer"));
    }

    @Test
    @DisplayName("a non-numeric batch-size aborts startup as a binding failure")
    void nonNumericFailsStartup() {
        runner.withPropertyValues("sipsa.ingestion.batch-size=not-a-number")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion"));
    }

    @Test
    @DisplayName("values above the preventive maximum abort startup")
    void aboveMaximumFailsStartup() {
        runner.withPropertyValues("sipsa.ingestion.batch-size=" + (IngestionProperties.MAX_BATCH_SIZE + 1))
                .run(context -> assertThat(failureMessages(context))
                        .contains("must not exceed " + IngestionProperties.MAX_BATCH_SIZE));
    }

    @Test
    @DisplayName("the boundary values 1 and MAX_BATCH_SIZE are accepted")
    void boundaryValuesAreAccepted() {
        runner.withPropertyValues("sipsa.ingestion.batch-size=1")
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getBatchSize())
                        .isEqualTo(1));
        runner.withPropertyValues("sipsa.ingestion.batch-size=" + IngestionProperties.MAX_BATCH_SIZE)
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getBatchSize())
                        .isEqualTo(IngestionProperties.MAX_BATCH_SIZE));
    }

    // -------------------------------------------------------------------
    // Monthly ingestion window start (TECH-133)
    // -------------------------------------------------------------------

    @Test
    @DisplayName("without any override the canonical monthly window start is 14:00 — never the old 06:00 fallback")
    void defaultMonthlyWindowStartIs1400() {
        runner.run(context -> assertThat(context.getBean(IngestionProperties.class).getMonthlyWindowStart())
                .isEqualTo(IngestionProperties.DEFAULT_MONTHLY_WINDOW_START)
                .isEqualTo(LocalTime.of(14, 0)));
    }

    @Test
    @DisplayName("sipsa.ingestion.monthly-window-start=10:30 overrides the default")
    void monthlyWindowStartPropertyOverrideWins() {
        runner.withPropertyValues("sipsa.ingestion.monthly-window-start=10:30")
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getMonthlyWindowStart())
                        .isEqualTo(LocalTime.of(10, 30)));
    }

    @Test
    @DisplayName("the yaml placeholder chain resolves to 14:00 when INGESTION_MONTHLY_WINDOW_START is unset")
    void monthlyPlaceholderChainFallsBackTo1400() {
        runner.withPropertyValues(MONTHLY_YAML_PLACEHOLDER_CHAIN)
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getMonthlyWindowStart())
                        .isEqualTo(LocalTime.of(14, 0)));
    }

    @Test
    @DisplayName("INGESTION_MONTHLY_WINDOW_START=18:45 wins over the placeholder default")
    void monthlyEnvironmentVariableEquivalentWins() {
        runner.withSystemProperties("INGESTION_MONTHLY_WINDOW_START=18:45")
                .withPropertyValues(MONTHLY_YAML_PLACEHOLDER_CHAIN)
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getMonthlyWindowStart())
                        .isEqualTo(LocalTime.of(18, 45)));
    }

    @Test
    @DisplayName("monthly-window-start=24:00 aborts startup naming the property")
    void monthlyWindowStart2400FailsStartup() {
        runner.withPropertyValues("sipsa.ingestion.monthly-window-start=24:00")
                .run(context -> assertThat(failureMessages(context))
                        .contains("monthly-window-start"));
    }

    @Test
    @DisplayName("monthly-window-start=14:99 aborts startup naming the property")
    void monthlyWindowStartBadMinutesFailsStartup() {
        runner.withPropertyValues("sipsa.ingestion.monthly-window-start=14:99")
                .run(context -> assertThat(failureMessages(context))
                        .contains("monthly-window-start"));
    }

    @Test
    @DisplayName("a non-time monthly-window-start aborts startup naming the property")
    void monthlyWindowStartNonTimeFailsStartup() {
        runner.withPropertyValues("sipsa.ingestion.monthly-window-start=invalid")
                .run(context -> assertThat(failureMessages(context))
                        .contains("monthly-window-start"));
    }

    @Test
    @DisplayName("an explicitly empty monthly-window-start behaves as unset under standard Spring binding — the canonical default applies")
    void monthlyWindowStartEmptyBehavesAsUnset() {
        // Spring's binder treats an empty value as absent for typed targets, so the
        // field keeps its canonical default instead of binding null. Pinned here so
        // a future switch to stricter semantics is a conscious decision. Note the
        // docker-compose passthrough uses ${VAR:-14:00}, which already replaces an
        // empty environment variable with the canonical value before Spring sees it.
        runner.withPropertyValues("sipsa.ingestion.monthly-window-start=")
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getMonthlyWindowStart())
                        .isEqualTo(IngestionProperties.DEFAULT_MONTHLY_WINDOW_START));
    }

    @Test
    @DisplayName("midnight and end-of-day boundary values are accepted")
    void monthlyWindowStartBoundaryValuesAccepted() {
        runner.withPropertyValues("sipsa.ingestion.monthly-window-start=00:00")
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getMonthlyWindowStart())
                        .isEqualTo(LocalTime.MIDNIGHT));
        runner.withPropertyValues("sipsa.ingestion.monthly-window-start=23:59")
                .run(context -> assertThat(context.getBean(IngestionProperties.class).getMonthlyWindowStart())
                        .isEqualTo(LocalTime.of(23, 59)));
    }

    /**
     * Asserts the context failed to start and returns every message in the
     * startup-failure cause chain, so assertions can match the validation text
     * regardless of how deeply Spring nests the binding exception.
     */
    private static String failureMessages(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        StringBuilder messages = new StringBuilder();
        for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
