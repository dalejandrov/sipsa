package com.dalejandrov.sipsa.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding and validation tests for {@link AsyncExecutorProperties} (TECH-136, C-05).
 * <p>
 * Verifies the single-source-of-truth contract for the async pool geometry:
 * canonical defaults (2/10/25/60s), overrides via property and via the
 * {@code SIPSA_ASYNC_*} placeholder chains application.yaml uses (simulated with
 * runner-scoped system properties, never by mutating the process environment),
 * boundary values, and startup aborts with property-naming messages for every
 * invalid shape — including the cross-field rule {@code max >= core}.
 */
@DisplayName("AsyncExecutorProperties — async pool binding and validation")
class AsyncExecutorPropertiesTest {

    private static final String[] YAML_PLACEHOLDER_CHAINS = {
            "sipsa.ingestion.async.core-pool-size=${SIPSA_ASYNC_CORE_POOL_SIZE:2}",
            "sipsa.ingestion.async.max-pool-size=${SIPSA_ASYNC_MAX_POOL_SIZE:10}",
            "sipsa.ingestion.async.queue-capacity=${SIPSA_ASYNC_QUEUE_CAPACITY:25}",
            "sipsa.ingestion.async.keep-alive-seconds=${SIPSA_ASYNC_KEEP_ALIVE_SECONDS:60}"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesHost.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AsyncExecutorProperties.class)
    static class PropertiesHost {
    }

    @Test
    @DisplayName("without any override the canonical geometry applies: 2/10/25/60s")
    void canonicalDefaults() {
        runner.run(context -> {
            AsyncExecutorProperties props = context.getBean(AsyncExecutorProperties.class);
            assertThat(props.getCorePoolSize()).isEqualTo(2);
            assertThat(props.getMaxPoolSize()).isEqualTo(10);
            assertThat(props.getQueueCapacity()).isEqualTo(25);
            assertThat(props.getKeepAliveSeconds()).isEqualTo(60);
        });
    }

    @Test
    @DisplayName("property overrides win: 3/6/40/90s")
    void propertyOverridesWin() {
        runner.withPropertyValues(
                        "sipsa.ingestion.async.core-pool-size=3",
                        "sipsa.ingestion.async.max-pool-size=6",
                        "sipsa.ingestion.async.queue-capacity=40",
                        "sipsa.ingestion.async.keep-alive-seconds=90")
                .run(context -> {
                    AsyncExecutorProperties props = context.getBean(AsyncExecutorProperties.class);
                    assertThat(props.getCorePoolSize()).isEqualTo(3);
                    assertThat(props.getMaxPoolSize()).isEqualTo(6);
                    assertThat(props.getQueueCapacity()).isEqualTo(40);
                    assertThat(props.getKeepAliveSeconds()).isEqualTo(90);
                });
    }

    @Test
    @DisplayName("the yaml placeholder chains fall back to the canonical values when SIPSA_ASYNC_* are unset")
    void placeholderChainsFallBack() {
        runner.withPropertyValues(YAML_PLACEHOLDER_CHAINS)
                .run(context -> {
                    AsyncExecutorProperties props = context.getBean(AsyncExecutorProperties.class);
                    assertThat(props.getCorePoolSize()).isEqualTo(2);
                    assertThat(props.getMaxPoolSize()).isEqualTo(10);
                    assertThat(props.getQueueCapacity()).isEqualTo(25);
                    assertThat(props.getKeepAliveSeconds()).isEqualTo(60);
                });
    }

    @Test
    @DisplayName("SIPSA_ASYNC_* equivalents win over the placeholder defaults")
    void environmentVariableEquivalentsWin() {
        runner.withSystemProperties(
                        "SIPSA_ASYNC_CORE_POOL_SIZE=3",
                        "SIPSA_ASYNC_MAX_POOL_SIZE=6",
                        "SIPSA_ASYNC_QUEUE_CAPACITY=40",
                        "SIPSA_ASYNC_KEEP_ALIVE_SECONDS=90")
                .withPropertyValues(YAML_PLACEHOLDER_CHAINS)
                .run(context -> {
                    AsyncExecutorProperties props = context.getBean(AsyncExecutorProperties.class);
                    assertThat(props.getCorePoolSize()).isEqualTo(3);
                    assertThat(props.getMaxPoolSize()).isEqualTo(6);
                    assertThat(props.getQueueCapacity()).isEqualTo(40);
                    assertThat(props.getKeepAliveSeconds()).isEqualTo(90);
                });
    }

    @Test
    @DisplayName("boundary values are accepted: core=1, max=1, queue=0, keepAlive=0")
    void boundaryValuesAccepted() {
        runner.withPropertyValues(
                        "sipsa.ingestion.async.core-pool-size=1",
                        "sipsa.ingestion.async.max-pool-size=1",
                        "sipsa.ingestion.async.queue-capacity=0",
                        "sipsa.ingestion.async.keep-alive-seconds=0")
                .run(context -> {
                    AsyncExecutorProperties props = context.getBean(AsyncExecutorProperties.class);
                    assertThat(props.getCorePoolSize()).isEqualTo(1);
                    assertThat(props.getMaxPoolSize()).isEqualTo(1);
                    assertThat(props.getQueueCapacity()).isZero();
                    assertThat(props.getKeepAliveSeconds()).isZero();
                });
    }

    @Test
    @DisplayName("core-pool-size=0 aborts startup naming the property")
    void coreZeroFails() {
        runner.withPropertyValues("sipsa.ingestion.async.core-pool-size=0")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.async.core-pool-size must be >= 1"));
    }

    @Test
    @DisplayName("core-pool-size=-1 aborts startup naming the property")
    void coreNegativeFails() {
        runner.withPropertyValues("sipsa.ingestion.async.core-pool-size=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.async.core-pool-size must be >= 1"));
    }

    @Test
    @DisplayName("max-pool-size=0 aborts startup naming the property")
    void maxZeroFails() {
        runner.withPropertyValues("sipsa.ingestion.async.max-pool-size=0")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.async.max-pool-size must be >= 1"));
    }

    @Test
    @DisplayName("max-pool-size below core-pool-size aborts startup with the cross-field message")
    void maxBelowCoreFails() {
        runner.withPropertyValues(
                        "sipsa.ingestion.async.core-pool-size=10",
                        "sipsa.ingestion.async.max-pool-size=2")
                .run(context -> assertThat(failureMessages(context))
                        .contains("max-pool-size must be >= sipsa.ingestion.async.core-pool-size"));
    }

    @Test
    @DisplayName("queue-capacity=-1 aborts startup naming the property")
    void negativeQueueFails() {
        runner.withPropertyValues("sipsa.ingestion.async.queue-capacity=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.async.queue-capacity must be >= 0"));
    }

    @Test
    @DisplayName("negative keep-alive aborts startup naming the property")
    void negativeKeepAliveFails() {
        runner.withPropertyValues("sipsa.ingestion.async.keep-alive-seconds=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.async.keep-alive-seconds must be >= 0"));
    }

    @Test
    @DisplayName("non-numeric values abort startup as binding failures")
    void nonNumericFails() {
        runner.withPropertyValues("sipsa.ingestion.async.core-pool-size=many")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.async"));
        runner.withPropertyValues("sipsa.ingestion.async.keep-alive-seconds=soon")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.ingestion.async"));
    }

    /** Same helper contract as IngestionPropertiesTest: full failure-cause chain text. */
    private static String failureMessages(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        StringBuilder messages = new StringBuilder();
        for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
