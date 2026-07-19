package com.dalejandrov.sipsa.infrastructure.soap.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Binding and validation tests for {@link SoapProperties} (TECH-070).
 * <p>
 * {@code SoapProperties} carries no Java-level default field values — the canonical
 * defaults live only in {@code application.yaml}'s {@code ${SOAP_*:default}} placeholder
 * chains — so "canonical defaults" here means binding that exact chain, not an empty
 * {@code ApplicationContextRunner} (which correctly fails: {@code endpoint} has no safe
 * default, the SOAP client bean is always constructed).
 */
@DisplayName("SoapProperties — binding and validation")
class SoapPropertiesTest {

    /** Mirrors the exact placeholder chain application.yaml declares under sipsa.soap. */
    private static final String[] YAML_PLACEHOLDER_CHAIN = {
            "sipsa.soap.endpoint=${SOAP_ENDPOINT:https://appweb.dane.gov.co/sipsaWS/SrvSipsaUpraBeanService}",
            "sipsa.soap.namespace=http://servicios.sipsa.co.gov.dane/",
            "sipsa.soap.connect-timeout-ms=${SOAP_CONNECT_TIMEOUT_MS:30000}",
            "sipsa.soap.read-timeout-ms=${SOAP_READ_TIMEOUT_MS:3600000}",
            "sipsa.soap.max-retries=${SOAP_MAX_RETRIES:3}",
            "sipsa.soap.retry-backoff-ms=${SOAP_RETRY_BACKOFF_MS:2000}",
            "sipsa.soap.max-child-elements=${SOAP_MAX_CHILD_ELEMENTS:0}",
            "sipsa.soap.logging-enabled=${SOAP_LOGGING_ENABLED:false}"
    };

    /** Smallest configuration that satisfies every constraint. */
    private static final String[] MINIMAL_VALID_CONFIG = {
            "sipsa.soap.endpoint=http://localhost:9999/mock",
            "sipsa.soap.namespace=http://example.org/",
            "sipsa.soap.connect-timeout-ms=1",
            "sipsa.soap.read-timeout-ms=1",
            "sipsa.soap.max-retries=0",
            "sipsa.soap.retry-backoff-ms=0",
            "sipsa.soap.logging-limit-bytes=0",
            "sipsa.soap.max-child-elements=0"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesHost.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SoapProperties.class)
    static class PropertiesHost {
    }

    @Test
    @DisplayName("the yaml placeholder chain resolves to the canonical defaults when SOAP_* are unset")
    void placeholderChainFallsBackToCanonicalDefaults() {
        runner.withPropertyValues(YAML_PLACEHOLDER_CHAIN).run(context -> {
            SoapProperties props = context.getBean(SoapProperties.class);
            assertThat(props.getEndpoint())
                    .isEqualTo("https://appweb.dane.gov.co/sipsaWS/SrvSipsaUpraBeanService");
            assertThat(props.getNamespace()).isEqualTo("http://servicios.sipsa.co.gov.dane/");
            assertThat(props.getConnectTimeoutMs()).isEqualTo(30000);
            assertThat(props.getReadTimeoutMs()).isEqualTo(3600000);
            assertThat(props.getMaxRetries()).isEqualTo(3);
            assertThat(props.getRetryBackoffMs()).isEqualTo(2000L);
            assertThat(props.isLoggingEnabled()).isFalse();
            assertThat(props.getMaxChildElements()).isZero();
        });
    }

    @Test
    @DisplayName("property overrides win over the placeholder defaults")
    void propertyOverridesWin() {
        runner.withPropertyValues(YAML_PLACEHOLDER_CHAIN)
                .withPropertyValues(
                        "sipsa.soap.connect-timeout-ms=5000",
                        "sipsa.soap.max-retries=5")
                .run(context -> {
                    SoapProperties props = context.getBean(SoapProperties.class);
                    assertThat(props.getConnectTimeoutMs()).isEqualTo(5000);
                    assertThat(props.getMaxRetries()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("SOAP_* environment variable equivalents win over the placeholder defaults")
    void environmentVariableEquivalentsWin() {
        runner.withSystemProperties(
                        "SOAP_CONNECT_TIMEOUT_MS=15000",
                        "SOAP_MAX_RETRIES=1")
                .withPropertyValues(YAML_PLACEHOLDER_CHAIN)
                .run(context -> {
                    SoapProperties props = context.getBean(SoapProperties.class);
                    assertThat(props.getConnectTimeoutMs()).isEqualTo(15000);
                    assertThat(props.getMaxRetries()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("the minimal valid configuration binds successfully")
    void minimalValidConfigurationBinds() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG).run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("zero for maxChildElements is valid — it means unlimited, not disabled")
    void zeroMaxChildElementsIsValid() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.max-child-elements=0")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("zero for loggingLimitBytes is valid")
    void zeroLoggingLimitBytesIsValid() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.logging-limit-bytes=0")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("zero for maxRetries is valid — it means no retries, not disabled validation")
    void zeroMaxRetriesIsValid() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.max-retries=0")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("a blank endpoint aborts startup naming the property")
    void blankEndpointFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.endpoint=")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.endpoint must not be blank"));
    }

    @Test
    @DisplayName("a blank namespace aborts startup naming the property")
    void blankNamespaceFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.namespace=")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.namespace must not be blank"));
    }

    @Test
    @DisplayName("connect-timeout-ms=0 aborts startup naming the property")
    void zeroConnectTimeoutFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.connect-timeout-ms=0")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.connect-timeout-ms must be > 0"));
    }

    @Test
    @DisplayName("a negative read-timeout-ms aborts startup naming the property")
    void negativeReadTimeoutFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.read-timeout-ms=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.read-timeout-ms must be > 0"));
    }

    @Test
    @DisplayName("a negative max-retries aborts startup naming the property")
    void negativeMaxRetriesFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.max-retries=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.max-retries must be >= 0"));
    }

    @Test
    @DisplayName("a negative retry-backoff-ms aborts startup naming the property")
    void negativeRetryBackoffFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.retry-backoff-ms=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.retry-backoff-ms must be >= 0"));
    }

    @Test
    @DisplayName("a negative logging-limit-bytes aborts startup naming the property")
    void negativeLoggingLimitBytesFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.logging-limit-bytes=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.logging-limit-bytes must be >= 0"));
    }

    @Test
    @DisplayName("a negative max-child-elements aborts startup naming the property")
    void negativeMaxChildElementsFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.max-child-elements=-1")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap.max-child-elements must be >= 0"));
    }

    @Test
    @DisplayName("a non-numeric value aborts startup as a binding failure")
    void nonNumericValueFails() {
        runner.withPropertyValues(MINIMAL_VALID_CONFIG)
                .withPropertyValues("sipsa.soap.connect-timeout-ms=not-a-number")
                .run(context -> assertThat(failureMessages(context))
                        .contains("sipsa.soap"));
    }

    /** Same helper contract as the other *PropertiesTest classes in this project. */
    private static String failureMessages(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        StringBuilder messages = new StringBuilder();
        for (Throwable t = context.getStartupFailure(); t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
