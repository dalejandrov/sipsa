package com.dalejandrov.sipsa.infrastructure.observability;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Central Micrometer instrumentation point for ingestion runs and SOAP calls (TECH-032).
 * <p>
 * Before this class, there was zero custom Micrometer usage anywhere in this repository
 * — {@code spring-boot-starter-actuator} and {@code micrometer-registry-prometheus} were
 * already on the classpath (no new dependency added), but nothing used the auto-configured
 * {@link MeterRegistry}.
 * <p>
 * <b>Design:</b> one dedicated component with semantic operations
 * ({@link #recordRunCompleted}, {@link #recordSoapCallCompleted}, ...), rather than
 * scattered {@code meterRegistry.counter(...)} calls in {@code IngestionJob} and
 * {@code SoapStreamingClient}. Every public method swallows and logs any exception from
 * the registry itself (a misbehaving meter/backend must never fail an ingestion run or a
 * SOAP call) — this is the one deliberate broad catch in this class, and exists for
 * exactly that reason.
 * <p>
 * <b>Metrics:</b>
 * <ul>
 *   <li>{@code sipsa.ingestion.duration} (Timer) — one ingestion run attempt, from run
 *       creation to final status. Tags: {@code method}, {@code outcome}
 *       ({@value #OUTCOME_SUCCESS}/{@value #OUTCOME_FAILURE}/{@value #OUTCOME_CANCELED}),
 *       {@code source} (lowercased {@link com.dalejandrov.sipsa.domain.entity.RequestSource}).</li>
 *   <li>{@code sipsa.ingestion.runs} (Counter) — same tags; a plain total alongside the
 *       Timer's own count, for dashboards that don't want histogram/percentile config.</li>
 *   <li>{@code sipsa.ingestion.records.seen/inserted/skipped/rejected}
 *       (DistributionSummary) — one aggregate value per run, from
 *       {@link IngestionContext}'s final counters (not incremented per-record; that
 *       object already accumulates the true per-run totals). Tags: {@code method},
 *       {@code outcome}.</li>
 *   <li>{@code sipsa.soap.calls} (Counter) — one per {@code SoapStreamingClient.stream(...)}
 *       invocation, regardless of how many internal HTTP attempts it took. Tags:
 *       {@code method} (the SOAP action), {@code outcome}.</li>
 *   <li>{@code sipsa.soap.failures} (Counter) — same event as a
 *       {@code sipsa.soap.calls[outcome=failure]} increment, recorded alongside it for a
 *       simpler failure-count query. Tag: {@code method}.</li>
 *   <li>{@code sipsa.soap.retries} (Counter) — one per retry attempt inside
 *       {@code stream(...)}'s backoff loop. Tag: {@code method}.</li>
 *   <li>{@code sipsa.soap.duration} (Timer) — the full {@code stream(...)} call,
 *       including all retries and backoff sleep. Tags: {@code method}, {@code outcome}.</li>
 * </ul>
 * <p>
 * <b>Cardinality:</b> {@code method} is always a value from a closed, small catalog — the
 * ~5 registered ingestion methods ({@code IngestionService}'s handler map) for ingestion
 * metrics, the ~5 SOAP actions {@code SoapGatewayImpl} calls for SOAP metrics. No tag ever
 * carries a {@code requestId}, {@code runId}, raw exception message, or any other
 * unbounded value.
 * <p>
 * <b>Boundary — SOAP call "success" vs. ingestion "success":</b> a SOAP call succeeding
 * only means {@code SoapStreamingClient} got a usable HTTP response stream; it says
 * nothing about whether the StAX parser later finds a SOAP fault in that stream or the
 * ingestion run ultimately succeeds. These are two different, independently tracked
 * concerns by design.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.core.IngestionJob
 * @see com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionMetrics {

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
    public static final String OUTCOME_CANCELED = "canceled";

    private final MeterRegistry registry;

    /**
     * Starts timing a single ingestion run attempt. Call once, right after the run's
     * {@link IngestionContext} is created; pair with {@link #recordRunCompleted}.
     *
     * @return a timer sample, or {@code null} if the registry itself failed (logged; the
     *         caller must tolerate a {@code null} sample — {@link #recordRunCompleted}
     *         does)
     */
    public Timer.Sample startRun() {
        return safely("start ingestion run timer", () -> Timer.start(registry));
    }

    /**
     * Records the outcome of one ingestion run attempt: stops the duration timer,
     * increments the runs counter, and records the run's final record counts — all with
     * the same {@code method}/{@code outcome}/{@code source} tags, from the single call
     * site in {@code IngestionJob}'s {@code finally} block.
     *
     * @param sample the sample from {@link #startRun}; a {@code null} sample (registry
     *               failure at start) is tolerated — only the timer is skipped
     * @param context the completed run's context (final record counts read from here)
     * @param outcome one of {@link #OUTCOME_SUCCESS}, {@link #OUTCOME_FAILURE}, or
     *                {@link #OUTCOME_CANCELED}
     */
    public void recordRunCompleted(Timer.Sample sample, IngestionContext context, String outcome) {
        String method = context.getMethodName();
        String source = context.getRequestSource().name().toLowerCase();

        if (sample != null) {
            safely("stop ingestion duration timer", () -> {
                sample.stop(Timer.builder("sipsa.ingestion.duration")
                        .description("Duration of a single ingestion run attempt, from run creation to final status")
                        .tag("method", method)
                        .tag("outcome", outcome)
                        .tag("source", source)
                        .register(registry));
                return null;
            });
        }

        safely("increment ingestion runs counter", () -> {
            Counter.builder("sipsa.ingestion.runs")
                    .description("Number of completed ingestion run attempts")
                    .tag("method", method)
                    .tag("outcome", outcome)
                    .tag("source", source)
                    .register(registry)
                    .increment();
            return null;
        });

        recordBatchResult(context, outcome);
    }

    /**
     * Records the final record counts (seen/inserted/skipped/rejected) for one run.
     * Called by {@link #recordRunCompleted}; also exposed standalone since the counts
     * are a self-contained fact about the run's batch result.
     *
     * @param context the run's context, read once for its final aggregate counters —
     *                never recalculated from a repository query
     * @param outcome one of {@link #OUTCOME_SUCCESS}, {@link #OUTCOME_FAILURE}, or
     *                {@link #OUTCOME_CANCELED}
     */
    public void recordBatchResult(IngestionContext context, String outcome) {
        String method = context.getMethodName();

        recordDistribution("sipsa.ingestion.records.seen", "Records seen per ingestion run",
                method, outcome, context.getRecordsSeen());
        recordDistribution("sipsa.ingestion.records.inserted", "Records inserted per ingestion run",
                method, outcome, context.getRecordsInserted());
        recordDistribution("sipsa.ingestion.records.skipped", "Records skipped (already existed) per ingestion run",
                method, outcome, context.getRecordsSkipped());
        recordDistribution("sipsa.ingestion.records.rejected", "Records rejected per ingestion run",
                method, outcome, context.getRejectCount());
    }

    /**
     * Starts timing a single {@code SoapStreamingClient.stream(...)} invocation,
     * including any internal retries. Pair with {@link #recordSoapCallCompleted}.
     *
     * @return a timer sample, or {@code null} if the registry itself failed (logged)
     */
    public Timer.Sample startSoapCall() {
        return safely("start SOAP call timer", () -> Timer.start(registry));
    }

    /**
     * Records the outcome of one SOAP call (one {@code stream(...)} invocation, whatever
     * number of internal HTTP attempts it took).
     *
     * @param sample the sample from {@link #startSoapCall}; a {@code null} sample is
     *               tolerated
     * @param soapMethod the SOAP action name (e.g. {@code promediosSipsaCiudad})
     * @param success {@code true} if a usable response stream was obtained, {@code false}
     *                if the call failed after exhausting retries or hit a non-retryable
     *                error
     */
    public void recordSoapCallCompleted(Timer.Sample sample, String soapMethod, boolean success) {
        String outcome = success ? OUTCOME_SUCCESS : OUTCOME_FAILURE;

        if (sample != null) {
            safely("stop SOAP call duration timer", () -> {
                sample.stop(Timer.builder("sipsa.soap.duration")
                        .description("Duration of a single SOAP call, including internal retries and backoff")
                        .tag("method", soapMethod)
                        .tag("outcome", outcome)
                        .register(registry));
                return null;
            });
        }

        safely("increment SOAP calls counter", () -> {
            Counter.builder("sipsa.soap.calls")
                    .description("Number of SOAP call invocations (one per stream() call, not per HTTP attempt)")
                    .tag("method", soapMethod)
                    .tag("outcome", outcome)
                    .register(registry)
                    .increment();
            return null;
        });

        if (!success) {
            safely("increment SOAP failures counter", () -> {
                Counter.builder("sipsa.soap.failures")
                        .description("Number of SOAP calls that failed after exhausting retries or hit a non-retryable error")
                        .tag("method", soapMethod)
                        .register(registry)
                        .increment();
                return null;
            });
        }
    }

    /**
     * Records one retry attempt inside {@code SoapStreamingClient.stream(...)}'s backoff
     * loop. Called once per retry, not once per call — a call that succeeds on the first
     * attempt records zero retries.
     *
     * @param soapMethod the SOAP action name being retried
     */
    public void recordSoapRetry(String soapMethod) {
        safely("increment SOAP retries counter", () -> {
            Counter.builder("sipsa.soap.retries")
                    .description("Number of retry attempts made during SOAP calls")
                    .tag("method", soapMethod)
                    .register(registry)
                    .increment();
            return null;
        });
    }

    private void recordDistribution(String name, String description, String method, String outcome, int value) {
        if (value < 0) {
            log.warn("Refusing to record negative value {} for metric {} (method={}, outcome={})",
                    value, name, method, outcome);
            return;
        }

        safely("record " + name, () -> {
            DistributionSummary.builder(name)
                    .description(description)
                    .tag("method", method)
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(value);
            return null;
        });
    }

    private <T> T safely(String action, java.util.function.Supplier<T> operation) {
        try {
            return operation.get();
        } catch (Exception e) {
            log.warn("Failed to {} — ingestion metrics must never break the ingestion flow: {}",
                    action, e.getMessage(), e);
            return null;
        }
    }
}
