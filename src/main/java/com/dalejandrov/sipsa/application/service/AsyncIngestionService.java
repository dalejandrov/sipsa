package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.api.dto.IngestionRequest;
import com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service responsible for executing ingestion jobs asynchronously.
 * <p>
 * This class exists to ensure that Spring's {@link Async} proxy mechanism
 * works correctly (avoiding self-invocation issues).
 * <p>
 * Each execution is fully asynchronous and non-blocking
 * for the calling thread.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AsyncIngestionService {

    private final GenericIngestionJob ingestionJob;

    /**
     * Executes the ingestion process asynchronously.
     * <p>
     * This method is executed in a separate thread managed by Spring's
     * {@link org.springframework.core.task.TaskExecutor}.
     * <p>
     * Any exception thrown during ingestion is caught and logged,
     * preventing it from crashing the async executor.
     *
     * @param request encapsulates all ingestion parameters
     */
    @Async
    public void executeAsync(IngestionRequest request) {
        long startTime = System.currentTimeMillis();

        log.info(
                "Async ingestion started requestId={} method={} force={}",
                request.requestId(), request.methodName(), request.force()
        );

        try {
            ingestionJob.execute(request);

            long durationMs = System.currentTimeMillis() - startTime;
            log.info(
                    "Async ingestion completed requestId={} method={} durationMs={}",
                    request.requestId(), request.methodName(), durationMs
            );
        } catch (Exception ex) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error(
                    "Async ingestion failed requestId={} method={} durationMs={}",
                    request.requestId(), request.methodName(), durationMs, ex
            );
        }
    }
}
