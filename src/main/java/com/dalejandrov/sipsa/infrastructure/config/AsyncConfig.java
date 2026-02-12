package com.dalejandrov.sipsa.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous execution in the SIPSA application.
 * <p>
 * This configuration provides a dedicated thread pool for async operations,
 * ensuring that long-running ingestion processes don't block the main application threads.
 * <p>
 * Key features:
 * - Dedicated thread pool with configurable size
 * - Proper rejection policy for overload protection
 * - Thread naming for better monitoring and debugging
 * - Graceful shutdown with timeout
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${sipsa.ingestion.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${sipsa.ingestion.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${sipsa.ingestion.async.queue-capacity:25}")
    private int queueCapacity;

    @Value("${sipsa.ingestion.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /**
     * Creates a thread pool executor for asynchronous ingestion operations.
     * <p>
     * This executor is used by methods annotated with {@link org.springframework.scheduling.annotation.Async}
     * in the ingestion services. It provides:
     * <ul>
     *   <li>Configurable core and max pool sizes</li>
     *   <li>Queue for handling burst loads</li>
     *   <li>Caller runs policy to prevent unbounded queuing</li>
     *   <li>Descriptive thread names for monitoring</li>
     * </ul>
     *
     * @return configured executor for async operations
     */
    @Bean(name = "ingestionTaskExecutor")
    public Executor ingestionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ingestion-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(keepAliveSeconds);

        executor.initialize();

        log.info("Initialized ingestion async executor: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);

        return executor;
    }
}
