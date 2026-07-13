package com.dalejandrov.sipsa.infrastructure.config;

import com.dalejandrov.sipsa.application.ingestion.scheduler.SipsaIngestionScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code sipsa.scheduling.enabled=false} default set in
 * {@code src/test/resources/application.yaml} actually takes effect, using the ordinary
 * (unmodified) test properties every other test in the suite runs with. This is the
 * negative counterpart to {@link SipsaSchedulingContextTest}, which re-enables scheduling
 * to prove the wiring itself is correct when active.
 * <p>
 * Before this story, no property existed to disable {@code @Scheduled} execution during
 * tests at all (see {@code docs/architecture/scheduled-ingestion-validation.md} §4). This
 * test is the regression guard for that gap: if a future change removes the
 * {@code @ConditionalOnProperty} guard from {@link SchedulingConfig}, or the test property
 * is deleted, {@link #taskSchedulerBean_doesNotExist_whenSchedulingDisabled()} fails loudly
 * instead of the test suite silently gaining a live, real-clock-driven cron job.
 */
@SpringBootTest
@DisplayName("Scheduling context (default test properties: sipsa.scheduling.enabled=false)")
class SipsaSchedulingDisabledByDefaultTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("SchedulingConfig's taskScheduler bean does NOT exist when scheduling is disabled")
    void taskSchedulerBean_doesNotExist_whenSchedulingDisabled() {
        assertThat(context.containsBean("taskScheduler")).isFalse();
    }

    @Test
    @DisplayName("SipsaIngestionScheduler bean still exists (it is a plain @Component, unaffected by @EnableScheduling)")
    void schedulerComponent_stillExists_evenThoughItsMethodsWillNeverFire() {
        assertThat(context.getBean(SipsaIngestionScheduler.class)).isNotNull();
    }
}
