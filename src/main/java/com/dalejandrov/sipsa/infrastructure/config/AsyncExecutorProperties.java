package com.dalejandrov.sipsa.infrastructure.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the application's asynchronous executor
 * ({@code ingestionTaskExecutor}) — the single source of truth for the pool
 * geometry (TECH-136, C-05). {@code AsyncConfig} must obtain these values from
 * this class; no component may re-declare them through local {@code @Value}
 * defaults.
 * <p>
 * Binds the pre-existing official prefix — deliberately NOT renamed, so the
 * operational contract (yaml keys and {@code SIPSA_ASYNC_*} environment
 * variables) is unchanged:
 * <pre>{@code
 * sipsa:
 *   ingestion:
 *     async:
 *       core-pool-size: ${SIPSA_ASYNC_CORE_POOL_SIZE:2}
 *       max-pool-size: ${SIPSA_ASYNC_MAX_POOL_SIZE:10}
 *       queue-capacity: ${SIPSA_ASYNC_QUEUE_CAPACITY:25}
 *       keep-alive-seconds: ${SIPSA_ASYNC_KEEP_ALIVE_SECONDS:60}
 * }</pre>
 * Resolution precedence: environment variable → Spring property → typed
 * canonical default.
 * <p>
 * <b>Semantics:</b> the executor grows from {@code corePoolSize} threads,
 * queues up to {@code queueCapacity} tasks, then adds threads up to
 * {@code maxPoolSize}; beyond that the CallerRunsPolicy applies (the caller
 * executes the task — backpressure, never silent drops). {@code queueCapacity
 * = 0} is valid and means direct handoff: no task waits in a queue, every
 * submission either takes a thread immediately or falls to the rejection
 * policy. {@code keepAliveSeconds} also applies to core threads
 * ({@code allowCoreThreadTimeOut(true)} in {@code AsyncConfig}).
 * <p>
 * Startup validation aborts the application on invalid values instead of
 * building a mis-shaped pool: sizes must be ≥ 1, {@code maxPoolSize} must not
 * be smaller than {@code corePoolSize}, queue and keep-alive must be ≥ 0.
 */
@Component
@ConfigurationProperties(prefix = "sipsa.ingestion.async")
@Validated
@Data
@Slf4j
public class AsyncExecutorProperties {

    /** Canonical pool geometry — the values application.yaml always made effective. */
    public static final int DEFAULT_CORE_POOL_SIZE = 2;
    public static final int DEFAULT_MAX_POOL_SIZE = 10;
    public static final int DEFAULT_QUEUE_CAPACITY = 25;
    public static final int DEFAULT_KEEP_ALIVE_SECONDS = 60;

    /** Threads kept ready for async work (audit events, async ingestion dispatch). */
    @Min(value = 1, message = "sipsa.ingestion.async.core-pool-size must be >= 1")
    private int corePoolSize = DEFAULT_CORE_POOL_SIZE;

    /** Upper bound of pool growth once the queue is full. */
    @Min(value = 1, message = "sipsa.ingestion.async.max-pool-size must be >= 1")
    private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;

    /**
     * Tasks buffered before the pool grows beyond core size. Zero is valid:
     * direct handoff, no intermediate queue.
     */
    @Min(value = 0, message = "sipsa.ingestion.async.queue-capacity must be >= 0")
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    /** Idle-thread timeout in seconds (applies to core threads too). */
    @Min(value = 0, message = "sipsa.ingestion.async.keep-alive-seconds must be >= 0")
    private int keepAliveSeconds = DEFAULT_KEEP_ALIVE_SECONDS;

    /**
     * Cross-field rule a plain {@code @Min} cannot express: a pool whose max is
     * below its core size is a startup configuration error, not something to
     * discover at the first burst of async work.
     */
    @AssertTrue(message = "sipsa.ingestion.async.max-pool-size must be >= sipsa.ingestion.async.core-pool-size")
    public boolean isMaxNotBelowCore() {
        return maxPoolSize >= corePoolSize;
    }

    /** One startup log line so operators can confirm the resolved pool geometry. */
    @PostConstruct
    void logEffectiveConfiguration() {
        log.info("Async executor configuration: core={}, max={}, queue={}, keepAlive={}s",
                corePoolSize, maxPoolSize, queueCapacity, keepAliveSeconds);
    }
}
