package com.dalejandrov.sipsa.application.service;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.application.ingestion.handler.IngestionHandler;
import com.dalejandrov.sipsa.domain.exception.SipsaBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for managing and executing ingestion handlers.
 * <p>
 * This service acts as a registry and dispatcher for all ingestion handlers.
 * It provides:
 * <ul>
 *   <li>Handler registration by method name at startup</li>
 *   <li>Method name validation before execution</li>
 *   <li>Delegation to the appropriate handler for each method</li>
 *   <li>Discovery of available ingestion methods</li>
 * </ul>
 * <p>
 * The service automatically discovers all Spring beans implementing
 * {@link IngestionHandler} and registers them in a map indexed by their method name.
 * <p>
 * <b>Usage Pattern:</b>
 * <ol>
 *   <li>Validate method exists with {@link #isValidMethod}</li>
 *   <li>Execute handler with {@link #execute}</li>
 * </ol>
 *
 * @see IngestionHandler
 * @see com.dalejandrov.sipsa.application.ingestion.core.GenericIngestionJob
 */
@Service
@Slf4j
public class IngestionService {

    private final Map<String, IngestionHandler> handlerMap;

    /**
     * Creates the service and registers all available ingestion handlers.
     * <p>
     * Spring automatically injects all beans implementing {@link IngestionHandler}.
     * Each handler is registered under its method name for O(1) lookup.
     *
     * @param handlers list of all ingestion handler beans (auto-injected by Spring)
     */
    public IngestionService(List<IngestionHandler> handlers) {
        if (handlers == null || handlers.isEmpty()) {
            log.warn("No ingestion handlers found");
            this.handlerMap = Map.of();
            return;
        }

        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(
                        IngestionHandler::getMethodName,
                        Function.identity()
                ));

        log.info("Initialized {} ingestion handlers", handlerMap.size());
    }

    /**
     * Validates if a method name has a registered handler.
     * <p>
     * This should be called before attempting to execute a method
     * to provide early validation and better error messages.
     *
     * @param methodName the SOAP method name to validate
     * @return true if a handler exists for this method, false otherwise
     */
    public boolean isValidMethod(String methodName) {
        return methodName != null && handlerMap.containsKey(methodName);
    }

    /**
     * Returns the set of all available method names.
     * <p>
     * Useful for API endpoints that list available ingestion methods
     * and for validation error messages.
     *
     * @return unmodifiable set of method names that have registered handlers
     */
    public Set<String> getAvailableMethodNames() {
        return handlerMap.keySet();
    }

    /**
     * Executes the ingestion handler for the specified method.
     * <p>
     * This method delegates to the appropriate handler registered for
     * the given method name. The handler performs the actual data
     * extraction, parsing, validation, and persistence.
     *
     * @param methodName the SOAP method name to execute
     * @param context the ingestion context for tracking metrics and state
     * @throws SipsaBusinessException if no handler exists for the method
     * @throws Exception if the handler execution fails
     */
    public void execute(String methodName, IngestionContext context) throws Exception {
        if (methodName == null || methodName.isBlank()) {
            throw new SipsaBusinessException("Method name cannot be null or empty");
        }

        if (context == null) {
            throw new SipsaBusinessException("Ingestion context cannot be null");
        }

        IngestionHandler handler = handlerMap.get(methodName);
        if (handler == null) {
            throw new SipsaBusinessException("No handler found for method: " + methodName);
        }

        handler.execute(context);
    }
}
