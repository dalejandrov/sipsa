package com.dalejandrov.sipsa.application.ingestion.scheduler;

import com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * TECH-053: real Spring-managed async behavior of {@link ScheduledIngestionDispatcher}
 * — the concern {@link ScheduledIngestionDispatcherTest} cannot cover, since calling the
 * dispatcher directly on a plain (non-Spring) instance bypasses the {@code @Async} AOP
 * proxy entirely and runs synchronously. This class uses a real {@code @SpringBootTest}
 * context so the proxy — and the real {@code ingestionTaskExecutor} bean — are both
 * genuinely in play. {@code GenericIngestionJob} is mocked so no real SOAP/DB work
 * happens; only its threading and timing are observed.
 */
@SpringBootTest
@DisplayName("TECH-053: ScheduledIngestionDispatcher — real async dispatch via ingestionTaskExecutor")
class ScheduledIngestionAsyncDispatchTest {

    @Autowired
    private ScheduledIngestionDispatcher dispatcher;

    @MockitoBean
    private GenericIngestionJob ingestionJob;

    /**
     * The structural proof TECH-053 exists for: the dispatcher call returns to the
     * caller while the job is still blocked, and the job later completes exactly once,
     * on a managed {@code ingestion-async-*} thread — never {@code SimpleAsyncTaskExecutor}.
     */
    @Test
    @DisplayName("dispatch returns before the job finishes; the job then runs exactly once on an ingestion-async-* thread")
    void dispatchReturnsBeforeCompletion_jobRunsOnceOnManagedThread() throws Exception {
        CountDownLatch jobEntered = new CountDownLatch(1);
        CountDownLatch releaseJob = new CountDownLatch(1);
        CountDownLatch jobFinished = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        doAnswer(invocation -> {
            threadName.set(Thread.currentThread().getName());
            jobEntered.countDown();
            releaseJob.await(10, TimeUnit.SECONDS);
            jobFinished.countDown();
            return null;
        }).when(ingestionJob).execute(any());

        // The job is still blocked on releaseJob at this point - if dispatch() were
        // synchronous, this line itself would hang until releaseJob is counted down
        // (which hasn't happened yet), so simply reaching the next line already proves
        // dispatch() did not wait for the job.
        dispatcher.dispatchMonthlyMes();

        assertThat(jobFinished.getCount()).as("job must not have finished yet").isEqualTo(1L);
        assertThat(jobEntered.await(5, TimeUnit.SECONDS)).as("job must have started asynchronously").isTrue();
        assertThat(jobFinished.getCount()).as("still blocked - the caller did not wait for it either").isEqualTo(1L);

        assertThat(threadName.get())
                .as("must run on the managed ingestion-async-* pool, never SimpleAsyncTaskExecutor")
                .startsWith("ingestion-async-");

        releaseJob.countDown();
        assertThat(jobFinished.await(5, TimeUnit.SECONDS)).as("job completes exactly once, after release").isTrue();
    }

    @Test
    @DisplayName("dispatching multiple windows produces exactly one execution per dispatched request, none dropped or duplicated")
    void multipleDispatches_produceOneExecutionPerRequest() throws Exception {
        CountDownLatch allThreeDone = new CountDownLatch(3);
        List<String> methodsExecuted = new CopyOnWriteArrayList<>();
        List<String> threadsUsed = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            com.dalejandrov.sipsa.application.command.IngestionRequest request = invocation.getArgument(0);
            methodsExecuted.add(request.methodName());
            threadsUsed.add(Thread.currentThread().getName());
            allThreeDone.countDown();
            return null;
        }).when(ingestionJob).execute(any());

        dispatcher.dispatchMonthlyMes();
        dispatcher.dispatchMonthlyAbas();
        dispatcher.dispatchMonthlyMes();

        assertThat(allThreeDone.await(10, TimeUnit.SECONDS)).as("all three dispatches complete").isTrue();
        assertThat(methodsExecuted).containsExactlyInAnyOrder(
                "promediosSipsaMesMadr", "promedioAbasSipsaMesMadr", "promediosSipsaMesMadr");
        assertThat(threadsUsed).as("no SimpleAsyncTaskExecutor thread involved")
                .allSatisfy(name -> assertThat(name).startsWith("ingestion-async-"));
    }

    @Test
    @DisplayName("dispatchDailyWindow() still runs its three methods sequentially on one thread, not in parallel")
    void dailyWindow_staysSequentialOnOneThread_evenWhenAsync() throws Exception {
        CountDownLatch allDone = new CountDownLatch(3);
        Set<String> threadsUsed = java.util.concurrent.ConcurrentHashMap.newKeySet();
        List<String> order = new CopyOnWriteArrayList<>();

        doAnswer(invocation -> {
            com.dalejandrov.sipsa.application.command.IngestionRequest request = invocation.getArgument(0);
            order.add(request.methodName());
            threadsUsed.add(Thread.currentThread().getName());
            allDone.countDown();
            return null;
        }).when(ingestionJob).execute(any());

        dispatcher.dispatchDailyWindow();

        assertThat(allDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(order).as("sequential order preserved, not parallelized")
                .containsExactly("promediosSipsaCiudad", "promediosSipsaParcial", "promediosSipsaSemanaMadr");
        assertThat(threadsUsed).as("all three ran on the same single worker thread").hasSize(1);
    }
}
