package com.dalejandrov.sipsa.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate for the real {@code ingestionTaskExecutor} bean (TECH-136): the pool built by
 * {@link AsyncConfig} must reflect the typed {@link AsyncExecutorProperties} exactly,
 * keep its pre-existing identity (bean name, thread prefix, CallerRunsPolicy,
 * core-thread timeout) and keep its saturation behavior — verified deterministically
 * with latches, never sleeps.
 */
@DisplayName("AsyncConfig — ingestionTaskExecutor bean wiring and saturation")
class AsyncExecutorBeanTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Host.class, AsyncConfig.class);

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AsyncExecutorProperties.class)
    static class Host {
    }

    @Test
    @DisplayName("bean carries the canonical geometry and the preserved identity")
    void beanReflectsCanonicalConfiguration() {
        runner.run(context -> {
            assertThat(context).hasBean("ingestionTaskExecutor");
            ThreadPoolTaskExecutor executor =
                    context.getBean("ingestionTaskExecutor", ThreadPoolTaskExecutor.class);

            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(10);
            assertThat(executor.getQueueCapacity()).isEqualTo(25);
            assertThat(executor.getKeepAliveSeconds()).isEqualTo(60);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("ingestion-async-");
            assertThat(executor.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
            assertThat(executor.getThreadPoolExecutor().allowsCoreThreadTimeOut()).isTrue();
        });
    }

    @Test
    @DisplayName("property overrides reach the built pool")
    void overridesReachThePool() {
        runner.withPropertyValues(
                        "sipsa.ingestion.async.core-pool-size=3",
                        "sipsa.ingestion.async.max-pool-size=6",
                        "sipsa.ingestion.async.queue-capacity=40",
                        "sipsa.ingestion.async.keep-alive-seconds=90")
                .run(context -> {
                    ThreadPoolTaskExecutor executor =
                            context.getBean("ingestionTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(executor.getCorePoolSize()).isEqualTo(3);
                    assertThat(executor.getMaxPoolSize()).isEqualTo(6);
                    assertThat(executor.getQueueCapacity()).isEqualTo(40);
                    assertThat(executor.getKeepAliveSeconds()).isEqualTo(90);
                });
    }

    @Test
    @DisplayName("saturation with core=1/max=1/queue=1: third task falls to CallerRunsPolicy on the caller thread")
    void saturationKeepsCallerRunsPolicy() {
        runner.withPropertyValues(
                        "sipsa.ingestion.async.core-pool-size=1",
                        "sipsa.ingestion.async.max-pool-size=1",
                        "sipsa.ingestion.async.queue-capacity=1")
                .run(context -> {
                    ThreadPoolTaskExecutor executor =
                            context.getBean("ingestionTaskExecutor", ThreadPoolTaskExecutor.class);

                    CountDownLatch task1Running = new CountDownLatch(1);
                    CountDownLatch releaseTask1 = new CountDownLatch(1);
                    AtomicReference<String> task1Thread = new AtomicReference<>();
                    AtomicReference<String> task3Thread = new AtomicReference<>();

                    try {
                        // Task 1 occupies the single pool thread until released.
                        executor.execute(() -> {
                            task1Thread.set(Thread.currentThread().getName());
                            task1Running.countDown();
                            awaitQuietly(releaseTask1);
                        });
                        assertThat(task1Running.await(10, TimeUnit.SECONDS))
                                .as("task 1 took the pool thread").isTrue();

                        // Task 2 fills the single queue slot.
                        CountDownLatch task2Done = new CountDownLatch(1);
                        executor.execute(task2Done::countDown);

                        // Task 3 saturates the pool: CallerRunsPolicy executes it HERE,
                        // synchronously, on the submitting thread — no drop, no exception.
                        executor.execute(() -> task3Thread.set(Thread.currentThread().getName()));

                        assertThat(task3Thread.get())
                                .as("rejected task ran synchronously on the caller (backpressure)")
                                .isEqualTo(Thread.currentThread().getName());
                        assertThat(task1Thread.get()).startsWith("ingestion-async-");

                        releaseTask1.countDown();
                        assertThat(task2Done.await(10, TimeUnit.SECONDS))
                                .as("queued task 2 completed after the pool thread freed up").isTrue();
                    } finally {
                        releaseTask1.countDown();
                    }
                });
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
