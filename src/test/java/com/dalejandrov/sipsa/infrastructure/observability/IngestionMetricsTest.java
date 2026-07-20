package com.dalejandrov.sipsa.infrastructure.observability;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TECH-032: {@link IngestionMetrics} semantics, verified against a real
 * {@link SimpleMeterRegistry} (no Prometheus backend needed — {@code SimpleMeterRegistry}
 * is Micrometer's in-memory test registry).
 * <p>
 * Covers: a successful run (timer count 1, correct outcome, aggregated record counts),
 * a failed run (not counted as success), two ingestion methods staying on separate,
 * stable tag series (no dynamic/unbounded tag values), SOAP call success/failure/retry,
 * a zero-record run (valid, no exception), and negative values being refused rather than
 * silently recorded.
 */
@DisplayName("IngestionMetrics — Micrometer instrumentation semantics")
class IngestionMetricsTest {

    private MeterRegistry registry;
    private IngestionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new IngestionMetrics(registry);
    }

    private static IngestionContext contextFor(String method, RequestSource source) {
        return new IngestionContext(1L, method, "2026-07-20", "req-1", source);
    }

    @Test
    @DisplayName("a successful run: timer count 1, outcome success, record counts aggregated correctly")
    void successfulRun_recordsCorrectly() {
        IngestionContext context = contextFor("promediosSipsaCiudad", RequestSource.MANUAL);
        context.incrementSeen();
        context.incrementSeen();
        context.incrementInserted();
        context.incrementSkipped();

        Timer.Sample sample = metrics.startRun();
        metrics.recordRunCompleted(sample, context, IngestionMetrics.OUTCOME_SUCCESS);

        Timer timer = registry.find("sipsa.ingestion.duration")
                .tag("method", "promediosSipsaCiudad")
                .tag("outcome", "success")
                .tag("source", "manual")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);

        double runs = registry.find("sipsa.ingestion.runs")
                .tag("method", "promediosSipsaCiudad")
                .tag("outcome", "success")
                .tag("source", "manual")
                .counter().count();
        assertThat(runs).isEqualTo(1.0);

        assertThat(seenSummary("promediosSipsaCiudad", "success").totalAmount()).isEqualTo(2.0);
        assertThat(insertedSummary("promediosSipsaCiudad", "success").totalAmount()).isEqualTo(1.0);
        assertThat(skippedSummary("promediosSipsaCiudad", "success").totalAmount()).isEqualTo(1.0);
        assertThat(rejectedSummary("promediosSipsaCiudad", "success").totalAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a failed run: failure recorded, timer stopped, never counted under outcome=success")
    void failedRun_isNotCountedAsSuccess() {
        IngestionContext context = contextFor("promediosSipsaParcial", RequestSource.SCHEDULED);
        context.incrementSeen();
        context.incrementReject();

        Timer.Sample sample = metrics.startRun();
        metrics.recordRunCompleted(sample, context, IngestionMetrics.OUTCOME_FAILURE);

        Timer failureTimer = registry.find("sipsa.ingestion.duration")
                .tag("method", "promediosSipsaParcial")
                .tag("outcome", "failure")
                .timer();
        assertThat(failureTimer).isNotNull();
        assertThat(failureTimer.count()).isEqualTo(1);

        Timer successTimer = registry.find("sipsa.ingestion.duration")
                .tag("method", "promediosSipsaParcial")
                .tag("outcome", "success")
                .timer();
        assertThat(successTimer).isNull();

        assertThat(rejectedSummary("promediosSipsaParcial", "failure").totalAmount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("two ingestion methods produce separate, stable tag series — no cross-contamination")
    void twoMethods_stayOnSeparateSeries() {
        IngestionContext ciudad = contextFor("promediosSipsaCiudad", RequestSource.MANUAL);
        IngestionContext parcial = contextFor("promediosSipsaParcial", RequestSource.MANUAL);

        metrics.recordRunCompleted(metrics.startRun(), ciudad, IngestionMetrics.OUTCOME_SUCCESS);
        metrics.recordRunCompleted(metrics.startRun(), parcial, IngestionMetrics.OUTCOME_SUCCESS);

        assertThat(registry.find("sipsa.ingestion.runs").tag("method", "promediosSipsaCiudad").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("sipsa.ingestion.runs").tag("method", "promediosSipsaParcial").counter().count())
                .isEqualTo(1.0);

        // Exactly 2 distinct series for sipsa.ingestion.runs — one per method, not one per
        // run (which would indicate an unbounded/dynamic tag leaking in, e.g. requestId).
        long distinctSeries = registry.find("sipsa.ingestion.runs").meters().size();
        assertThat(distinctSeries).isEqualTo(2);
    }

    @Test
    @DisplayName("SOAP call success: calls counter incremented with outcome=success, no failure recorded")
    void soapCall_success() {
        Timer.Sample sample = metrics.startSoapCall();
        metrics.recordSoapCallCompleted(sample, "promediosSipsaCiudad", true);

        assertThat(registry.find("sipsa.soap.calls")
                .tag("method", "promediosSipsaCiudad").tag("outcome", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find("sipsa.soap.failures").tag("method", "promediosSipsaCiudad").counter())
                .isNull();
        assertThat(registry.find("sipsa.soap.duration")
                .tag("method", "promediosSipsaCiudad").tag("outcome", "success")
                .timer().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("SOAP call failure: calls counter and failures counter both incremented")
    void soapCall_failure() {
        Timer.Sample sample = metrics.startSoapCall();
        metrics.recordSoapCallCompleted(sample, "promediosSipsaCiudad", false);

        assertThat(registry.find("sipsa.soap.calls")
                .tag("method", "promediosSipsaCiudad").tag("outcome", "failure")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find("sipsa.soap.failures").tag("method", "promediosSipsaCiudad")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("SOAP retry: retries counter increments once per call to recordSoapRetry")
    void soapRetry_incrementsOncePerAttempt() {
        metrics.recordSoapRetry("promediosSipsaCiudad");
        metrics.recordSoapRetry("promediosSipsaCiudad");

        assertThat(registry.find("sipsa.soap.retries").tag("method", "promediosSipsaCiudad")
                .counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("zero records: a valid metric is still recorded, no exception")
    void zeroRecords_isValidNotAnException() {
        IngestionContext context = contextFor("promediosSipsaSemanaMadr", RequestSource.MANUAL);

        assertThatCode(() -> metrics.recordRunCompleted(metrics.startRun(), context, IngestionMetrics.OUTCOME_SUCCESS))
                .doesNotThrowAnyException();

        assertThat(seenSummary("promediosSipsaSemanaMadr", "success").count()).isEqualTo(1);
        assertThat(seenSummary("promediosSipsaSemanaMadr", "success").totalAmount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("a negative record count is refused, not silently recorded")
    void negativeValue_isRefused() {
        IngestionContext context = contextFor("promediosSipsaCiudad", RequestSource.MANUAL);
        context.setRecordsSeen(-5);

        assertThatCode(() -> metrics.recordBatchResult(context, IngestionMetrics.OUTCOME_SUCCESS))
                .doesNotThrowAnyException();

        // Nothing was ever recorded for this series: the DistributionSummary doesn't exist at all.
        assertThat(registry.find("sipsa.ingestion.records.seen")
                        .tag("method", "promediosSipsaCiudad").tag("outcome", "success")
                        .summary())
                .isNull();
    }

    @Test
    @DisplayName("no prohibited tag (requestId, runId) is ever attached to any meter")
    void noProhibitedTagsAttached() {
        IngestionContext context = contextFor("promediosSipsaCiudad", RequestSource.MANUAL);
        metrics.recordRunCompleted(metrics.startRun(), context, IngestionMetrics.OUTCOME_SUCCESS);
        metrics.recordSoapCallCompleted(metrics.startSoapCall(), "promediosSipsaCiudad", true);
        metrics.recordSoapRetry("promediosSipsaCiudad");

        List<String> allowedTagKeys = List.of("method", "outcome", "source");
        registry.getMeters().forEach(meter ->
                meter.getId().getTags().forEach(tag ->
                        assertThat(allowedTagKeys).contains(tag.getKey())));
    }

    private io.micrometer.core.instrument.DistributionSummary seenSummary(String method, String outcome) {
        return registry.find("sipsa.ingestion.records.seen").tag("method", method).tag("outcome", outcome).summary();
    }

    private io.micrometer.core.instrument.DistributionSummary insertedSummary(String method, String outcome) {
        return registry.find("sipsa.ingestion.records.inserted").tag("method", method).tag("outcome", outcome).summary();
    }

    private io.micrometer.core.instrument.DistributionSummary skippedSummary(String method, String outcome) {
        return registry.find("sipsa.ingestion.records.skipped").tag("method", method).tag("outcome", outcome).summary();
    }

    private io.micrometer.core.instrument.DistributionSummary rejectedSummary(String method, String outcome) {
        return registry.find("sipsa.ingestion.records.rejected").tag("method", method).tag("outcome", outcome).summary();
    }
}
