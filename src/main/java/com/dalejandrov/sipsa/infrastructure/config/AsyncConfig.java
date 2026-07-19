package com.dalejandrov.sipsa.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>
 * <b>Pool geometry (TECH-136, C-05):</b> sourced exclusively from the typed and
 * validated {@link AsyncExecutorProperties} ({@code sipsa.ingestion.async.*},
 * env {@code SIPSA_ASYNC_*}) — this class no longer carries its own
 * {@code @Value} defaults that could drift from application.yaml.
 * <p>
 * <b>Executor resolution:</b> every {@code @Async} method in the codebase names
 * this bean explicitly ({@code @Async("ingestionTaskExecutor")}). Nothing relies
 * on Spring's default-executor lookup, which is ambiguous here by construction:
 * the {@code taskScheduler} bean ({@code ThreadPoolTaskScheduler}) also
 * implements {@code TaskExecutor}, so an unqualified {@code @Async} would log
 * "More than one TaskExecutor bean found" and silently fall back to ad-hoc
 * {@code SimpleAsyncTaskExecutor} threads (the mechanism behind the 2026-07-19
 * CI flake investigation).
 */
@Configuration
@EnableAsync
@Slf4j
@RequiredArgsConstructor
public class AsyncConfig {

    private final AsyncExecutorProperties asyncProperties;

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

        executor.setCorePoolSize(asyncProperties.getCorePoolSize());
        executor.setMaxPoolSize(asyncProperties.getMaxPoolSize());
        executor.setQueueCapacity(asyncProperties.getQueueCapacity());
        executor.setThreadNamePrefix("ingestion-async-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(asyncProperties.getKeepAliveSeconds());

        executor.initialize();

        log.info("Initialized ingestion async executor: core={}, max={}, queue={}",
                asyncProperties.getCorePoolSize(), asyncProperties.getMaxPoolSize(),
                asyncProperties.getQueueCapacity());

        return executor;
    }
}
