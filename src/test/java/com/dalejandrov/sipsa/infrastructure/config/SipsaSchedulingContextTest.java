package com.dalejandrov.sipsa.infrastructure.config;

import com.dalejandrov.sipsa.application.ingestion.scheduler.SipsaIngestionScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Limited Spring context test for the scheduling configuration.
 * <p>
 * The rest of the test suite runs with {@code sipsa.scheduling.enabled=false}
 * (see {@code src/test/resources/application.yaml}), which skips {@link SchedulingConfig}
 * entirely so no {@code @Scheduled} method is ever registered during ordinary tests. This
 * class is the one deliberate exception: it re-enables scheduling
 * ({@code sipsa.scheduling.enabled=true}) to prove the wiring itself is correct — the
 * scheduler bean exists, cron properties resolve to real (non-placeholder) values, and the
 * configured timezone is {@code America/Bogota} — while still never allowing a real job to
 * fire: the test cron values in {@code application.yaml} (test) are pinned to midnight, and
 * this class explicitly asserts that the next trigger for every cron is not imminent before
 * the test process could possibly reach it. No SOAP call is made and no ingestion runs.
 *
 * @see SchedulingConfig
 * @see SipsaIngestionScheduler
 */
@SpringBootTest(properties = "sipsa.scheduling.enabled=true")
@DisplayName("Scheduling context (scheduling explicitly re-enabled for this test only)")
class SipsaSchedulingContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("context loads and the scheduler bean exists")
    void contextLoads_schedulerBeanExists() {
        assertThat(context.getBean(SipsaIngestionScheduler.class)).isNotNull();
    }

    @Test
    @DisplayName("SchedulingConfig's taskScheduler bean is created when scheduling is enabled")
    void taskSchedulerBean_isCreated() {
        assertThat(context.getBean("taskScheduler", ThreadPoolTaskScheduler.class)).isNotNull();
    }

    @Test
    @DisplayName("the three cron properties resolve to real values, not unresolved placeholders")
    void cronProperties_resolveToRealValues() {
        assertThat(environment.getProperty("sipsa.ingestion.cron.daily"))
                .isNotBlank().doesNotContain("${");
        assertThat(environment.getProperty("sipsa.ingestion.cron.monthly-mes"))
                .isNotBlank().doesNotContain("${");
        assertThat(environment.getProperty("sipsa.ingestion.cron.monthly-abas"))
                .isNotBlank().doesNotContain("${");
    }

    @Test
    @DisplayName("sipsa.timezone resolves to America/Bogota")
    void timezoneProperty_resolvesToBogota() {
        assertThat(environment.getProperty("sipsa.timezone")).isEqualTo("America/Bogota");
    }

    @Test
    @DisplayName("none of the three cron triggers is due within the next minute -- no job can fire during this test")
    void noCronTriggerIsImminent() {
        ZoneId zone = ZoneId.of(environment.getProperty("sipsa.timezone", "America/Bogota"));
        ZonedDateTime now = ZonedDateTime.now(zone);

        for (String property : List.of(
                "sipsa.ingestion.cron.daily",
                "sipsa.ingestion.cron.monthly-mes",
                "sipsa.ingestion.cron.monthly-abas")) {

            String cron = environment.getProperty(property);
            ZonedDateTime next = CronExpression.parse(cron).next(now);

            assertThat(next).as("next trigger for %s", property).isAfter(now.plusMinutes(1));
        }
    }
}
