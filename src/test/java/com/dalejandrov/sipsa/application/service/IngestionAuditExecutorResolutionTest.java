package com.dalejandrov.sipsa.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dalejandrov.sipsa.application.command.AuditEventRequest;
import com.dalejandrov.sipsa.domain.entity.AuditEventType;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionAuditRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Executor-resolution gate for the async audit trail (TECH-136).
 * <p>
 * Before this story, {@code IngestionAuditService.logEvent} carried a bare
 * {@code @Async}: with two {@code TaskExecutor} beans in the context
 * ({@code ingestionTaskExecutor} and the {@code taskScheduler}, which also implements
 * {@code TaskExecutor}) and none called {@code taskExecutor}, Spring logged
 * {@code "More than one TaskExecutor bean found"} and fell back to ad-hoc
 * {@code SimpleAsyncTaskExecutor} threads. This test pins the fixed contract:
 * <ul>
 *   <li>the audit insert executes on the managed pool — the persisting thread is
 *       captured structurally from the service's own DEBUG log event (Logback
 *       {@code ListAppender}) and must carry the {@code ingestion-async-} prefix;</li>
 *   <li>no {@code SimpleAsyncTaskExecutor} thread is involved;</li>
 *   <li>the ambiguous-resolution warning never appears in the captured output;</li>
 *   <li>the call stays asynchronous (caller thread ≠ persisting thread) and the row
 *       is appended — {@code REQUIRES_NEW} semantics untouched.</li>
 * </ul>
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("TECH-136: audit @Async resolves to the managed ingestionTaskExecutor")
class IngestionAuditExecutorResolutionTest {

    @Autowired
    private IngestionAuditService auditService;

    @Autowired
    private IngestionAuditRepository auditRepository;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("ingestionTaskExecutor")
    private ThreadPoolTaskExecutor ingestionTaskExecutor;

    private ListAppender<ILoggingEvent> auditLogs;
    private Logger auditLogger;
    private Level previousLevel;

    @BeforeEach
    void captureAuditServiceLogs() {
        auditLogger = (Logger) LoggerFactory.getLogger(IngestionAuditService.class);
        previousLevel = auditLogger.getLevel();
        auditLogger.setLevel(Level.DEBUG);
        auditLogs = new ListAppender<>();
        auditLogs.start();
        auditLogger.addAppender(auditLogs);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(auditLogs);
        auditLogger.setLevel(previousLevel);
    }

    @Test
    @DisplayName("logEvent persists on an ingestion-async-* thread, with no ambiguity warning")
    void logEventRunsOnManagedExecutor(CapturedOutput output) {
        String requestId = "tech136-executor-resolution";
        long before = auditRepository.count();

        auditService.logEvent(new AuditEventRequest(
                requestId, null, RequestSource.MANUAL,
                AuditEventType.INGESTION_STARTED, "executor resolution probe"));

        // The row must appear (async + REQUIRES_NEW commits independently of the caller).
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> assertThat(auditRepository.count()).isGreaterThan(before));

        // Structural thread evidence: the service's own "Audit event logged" DEBUG event
        // carries the thread that executed the insert.
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> assertThat(persistThreads())
                        .as("audit insert thread(s); executor active threads="
                                + ingestionTaskExecutor.getActiveCount())
                        .isNotEmpty());

        List<String> threads = persistThreads();
        assertThat(threads)
                .as("audit runs on the managed pool, asynchronously")
                .allSatisfy(thread -> {
                    assertThat(thread).startsWith("ingestion-async-");
                    assertThat(thread).doesNotStartWith("SimpleAsyncTaskExecutor");
                    assertThat(thread).isNotEqualTo(Thread.currentThread().getName());
                });

        // The ambiguous default-executor resolution must be gone entirely.
        assertThat(output.getAll())
                .doesNotContain("More than one TaskExecutor bean found")
                .doesNotContain("SimpleAsyncTaskExecutor");
    }

    private List<String> persistThreads() {
        return auditLogs.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("Audit event logged"))
                .map(ILoggingEvent::getThreadName)
                .toList();
    }
}
