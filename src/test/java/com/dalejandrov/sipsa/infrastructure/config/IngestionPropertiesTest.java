package com.dalejandrov.sipsa.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

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
