package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.application.service.IngestionAuditService;
import com.dalejandrov.sipsa.application.service.IngestionControlService;
import com.dalejandrov.sipsa.application.service.IngestionService;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

/**
 * Behavior gate for the centralized rejection thresholds (TECH-135, C-04).
 * <p>
 * The semantics centralized here are the PRE-EXISTING ones, unchanged by the refactor:
 * <ul>
 *   <li>{@code max-reject-rate} is a fraction of {@code recordsSeen} in {@code [0..1]}
 *       (0.01 = 1%), NOT a percentage;</li>
 *   <li>both gates are evaluated once, at the end of a run, over its final totals
 *       (never per batch — small batches cannot trip the rate on their own);</li>
 *   <li>OR semantics: strictly exceeding EITHER the absolute count OR the rate throws
 *       {@link SipsaIngestionException}, which the job maps to a FAILED run;</li>
 *   <li>values exactly AT a threshold pass (both comparisons are strict {@code >});</li>
 *   <li>{@code seen == 0} skips the rate check entirely — only the count gate applies
 *       (no division by zero, no NaN).</li>
 * </ul>
 * Both {@code IngestionJob} (the logic owner) and {@code GenericIngestionJob} (the
 * Spring-managed subclass) receive the values from the same {@link IngestionProperties}
 * instance — the duplicated {@code @Value} bindings are gone.
 */
@DisplayName("TECH-135: ingestion rejection thresholds — centralized values, unchanged semantics")
class IngestionJobRejectThresholdTest {

    private GenericIngestionJob job(double maxRate, int maxCount) {
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxRejectRate(maxRate);
        properties.setMaxRejectCount(maxCount);
        return new GenericIngestionJob(
                mock(IngestionService.class), mock(WindowPolicy.class),
                mock(IngestionControlService.class), mock(IngestionAuditService.class),
                properties);
    }

    private static IngestionContext contextWith(int seen, int rejected) {
        IngestionContext context = new IngestionContext(
                1L, "promediosSipsaParcial", "2026-07-19", "req-t135", RequestSource.MANUAL);
        for (int i = 0; i < seen; i++) {
            context.incrementSeen();
        }
        for (int i = 0; i < rejected; i++) {
            context.incrementReject();
        }
        return context;
    }

    @Test
    @DisplayName("both jobs read the thresholds from the same IngestionProperties instance")
    void jobsShareTheCentralizedValues() {
        GenericIngestionJob generic = job(0.25, 42);
        assertThat(generic.maxRejectRate).isEqualTo(0.25);
        assertThat(generic.maxRejectCount).isEqualTo(42);
        // The field lives in the IngestionJob superclass — one binding, one value,
        // no per-class @Value defaults left to drift.
        assertThat((IngestionJob) generic).extracting(j -> j.maxRejectRate).isEqualTo(0.25);
    }

    @Test
    @DisplayName("run within both limits passes")
    void withinBothLimitsPasses() {
        // 100 seen, 1 rejected → rate 0.01 == threshold (not >), count 1 <= 5000.
        assertThatCode(() -> job(0.01, 5000).validateThresholds(contextWith(100, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rate strictly above the threshold fails the run")
    void rateAboveThresholdFails() {
        // 100 seen, 2 rejected → rate 0.02 > 0.01 while count 2 is far below 5000.
        assertThatExceptionOfType(SipsaIngestionException.class)
                .isThrownBy(() -> job(0.01, 5000).validateThresholds(contextWith(100, 2)))
                .withMessageContaining("Reject rate");
    }

    @Test
    @DisplayName("count strictly above the threshold fails the run even at a tiny rate")
    void countAboveThresholdFails() {
        // 1,000,000 seen, 5001 rejected → rate 0.005 < 0.01 but count 5001 > 5000 (OR).
        assertThatExceptionOfType(SipsaIngestionException.class)
                .isThrownBy(() -> job(0.01, 5000).validateThresholds(contextWith(1_000_000, 5_001)))
                .withMessageContaining("Reject count");
    }

    @Test
    @DisplayName("values exactly at both thresholds pass — comparisons are strict")
    void exactlyAtThresholdsPasses() {
        // 100 seen, 5 rejected with rate threshold 0.05 and count threshold 5.
        assertThatCode(() -> job(0.05, 5).validateThresholds(contextWith(100, 5)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("seen=0: the rate check is skipped, only the count gate applies")
    void seenZeroSkipsRateCheck() {
        // Zero seen with zero rejects: nothing to evaluate, passes.
        assertThatCode(() -> job(0.01, 5000).validateThresholds(contextWith(0, 0)))
                .doesNotThrowAnyException();
        // Zero seen with rejects above the count gate: the count check still fires.
        assertThatExceptionOfType(SipsaIngestionException.class)
                .isThrownBy(() -> job(0.01, 2).validateThresholds(contextWith(0, 3)))
                .withMessageContaining("Reject count");
    }

    @Test
    @DisplayName("max-reject-count=0 keeps zero-tolerance semantics: the first reject fails the run")
    void zeroCountThresholdIsZeroTolerance() {
        assertThatExceptionOfType(SipsaIngestionException.class)
                .isThrownBy(() -> job(1.0, 0).validateThresholds(contextWith(10, 1)))
                .withMessageContaining("Reject count");
        assertThatCode(() -> job(1.0, 0).validateThresholds(contextWith(10, 0)))
                .doesNotThrowAnyException();
    }
}
