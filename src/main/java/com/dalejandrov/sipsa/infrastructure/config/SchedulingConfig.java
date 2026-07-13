package com.dalejandrov.sipsa.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuration for scheduled task execution in the SIPSA application.
 * <p>
 * This configuration enables Spring's {@code @Scheduled} annotation support
 * and provides a dedicated thread pool for scheduled tasks.
 * <p>
 * Key features:
 * - Dedicated thread pool for scheduled ingestion jobs
 * - Configurable pool size to handle multiple concurrent schedules
 * - Thread naming for better monitoring and debugging
 * - Graceful shutdown with await termination
 * <p>
 * <b>Scheduled Jobs:</b>
 * <ul>
 *   <li>Daily ingestion window (14:20 COT) - Ciudad, Parcial, Semana</li>
 *   <li>Monthly MesMadr (day 8, 14:30 COT)</li>
 *   <li>Monthly AbasMes (day 10, 14:30 COT)</li>
 * </ul>
 * <p>
 * <b>Disabling in tests:</b> set {@code sipsa.scheduling.enabled=false} to skip this
 * entire configuration class, which prevents {@code @EnableScheduling} from registering
 * its bean post-processor at all — no {@code @Scheduled} method in the application
 * (including {@link com.dalejandrov.sipsa.application.ingestion.scheduler.SipsaIngestionScheduler})
 * will fire. Defaults to {@code true} (enabled), so production behavior is unchanged.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.scheduler.SipsaIngestionScheduler
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "sipsa.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SchedulingConfig {

    @Value("${sipsa.scheduling.pool-size:5}")
    private int poolSize;

    @Value("${sipsa.scheduling.await-termination-seconds:30}")
    private int awaitTerminationSeconds;

    /**
     * Creates a thread pool task scheduler for scheduled jobs.
     * <p>
     * This scheduler is used by methods annotated with {@link org.springframework.scheduling.annotation.Scheduled}
     * in the application. It provides:
     * <ul>
     *   <li>Configurable pool size to handle concurrent scheduled tasks</li>
     *   <li>Descriptive thread names for monitoring</li>
     *   <li>Graceful shutdown with configurable timeout</li>
     *   <li>Removal policy for cancelled tasks</li>
     * </ul>
     * <p>
     * <b>Default Pool Size:</b> 5 threads (sufficient for current 3 scheduled jobs)
     *
     * @return configured scheduler for scheduled tasks
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduled-ingestion-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(awaitTerminationSeconds);
        scheduler.setRemoveOnCancelPolicy(true);

        scheduler.initialize();

        log.info("Initialized task scheduler: pool-size={}, await-termination={}s",
                poolSize, awaitTerminationSeconds);

        return scheduler;
    }
}

