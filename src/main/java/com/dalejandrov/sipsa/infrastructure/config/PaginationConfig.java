package com.dalejandrov.sipsa.infrastructure.config;

import com.dalejandrov.sipsa.domain.exception.SipsaValidationException;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Centralized configuration and utilities for pagination and validation.
 * <p>
 * This configuration class provides a single source of truth for all pagination
 * parameters and validation logic across the application. It implements the
 * <b>Single Responsibility Principle</b> by consolidating pagination concerns.
 * <p>
 * <b>Architecture Layer:</b> This class belongs to the <b>Application Layer</b>
 * because it contains business logic and validation rules that are shared across
 * multiple use cases. It can be used by:
 * <ul>
 *   <li>Application services (SipsaReadService)</li>
 *   <li>API controllers (SipsaRestController)</li>
 *   <li>Future interfaces (GraphQL, gRPC, etc.)</li>
 * </ul>
 * <p>
 * <b>Configuration Properties:</b>
 * Configure via {@code application.yml}:
 * <pre>
 * sipsa:
 *   pagination:
 *     max-page-size: 1000
 *     default-page-size: 20
 *     max-user-page-size: 100
 * </pre>
 * <p>
 * <b>Features:</b>
 * <ul>
 *   <li>Configurable pagination limits (no hardcoded values)</li>
 *   <li>Centralized validation logic (DRY principle)</li>
 *   <li>Pageable factory with sort support</li>
 *   <li>ID validation for business keys</li>
 * </ul>
 * <p>
 * <b>Design Patterns:</b>
 * <ul>
 *   <li><b>Configuration Properties Pattern:</b> Externalized configuration</li>
 *   <li><b>Factory Pattern:</b> buildPageable() method</li>
 *   <li><b>Strategy Pattern:</b> Validation strategies</li>
 * </ul>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 * @see org.springframework.data.domain.Pageable
 */
@Configuration
@ConfigurationProperties(prefix = "sipsa.pagination")
@Data
public class PaginationConfig {

    /**
     * Maximum page size allowed for internal queries.
     * <p>
     * This is the absolute maximum to prevent memory issues and database overload.
     * Default: 1000
     */
    private int maxPageSize = 1000;

    /**
     * Default page size when not specified by the client.
     * <p>
     * Default: 20
     */
    private int defaultPageSize = 20;

    /**
     * Maximum page size that users can request via API.
     * <p>
     * This is typically lower than maxPageSize to prevent API abuse.
     * Default: 100
     */
    private int maxUserPageSize = 100;

    /**
     * Validates pagination parameters to prevent abuse and errors.
     * <p>
     * Note: This validates the internal 0-based Pageable object, not the user-facing
     * 1-based page numbers.
     *
     * @param pageable the pagination parameters to validate
     * @throws SipsaValidationException if validation fails
     */
    public void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() > maxPageSize) {
            throw new SipsaValidationException("Page size cannot exceed " + maxPageSize);
        }
        if (pageable.getPageNumber() < 0) {
            throw new SipsaValidationException("Page number cannot be negative");
        }
    }

    /**
     * Validates that all provided ID values are positive integers.
     * <p>
     * Null IDs are allowed (treated as optional filters).
     * Non-null IDs must be positive (&gt; 0).
     *
     * @param ids variable number of ID values to validate
     * @throws SipsaValidationException if any non-null ID is zero or negative
     */
    public void validateIds(Long... ids) {
        for (Long id : ids) {
            if (id != null && id <= 0) {
                throw new SipsaValidationException("ID values must be positive");
            }
        }
    }

    /**
     * Builds a Pageable object from page, size, and optional sort parameters.
     * <p>
     * This factory method handles sort parameter parsing and converts from
     * 1-based page numbers (API) to 0-based (Spring Data internal).
     * <p>
     * Supported formats:
     * <ul>
     *   <li><code>null</code> or empty → no sorting</li>
     *   <li><code>"fieldName"</code> → ascending</li>
     *   <li><code>"fieldName,asc"</code> → ascending</li>
     *   <li><code>"fieldName,desc"</code> → descending</li>
     * </ul>
     *
     * @param page 1-based page number (user-facing)
     * @param size number of records per page
     * @param sort optional sort expression (e.g., "fieldName,desc")
     * @return configured Pageable instance (never null)
     */
    public Pageable buildPageable(int page, int size, String sort) {
        int zeroBasedPage = Math.max(0, page - 1);

        if (sort == null || sort.isBlank()) {
            return PageRequest.of(zeroBasedPage, size);
        }

        String[] parts = sort.split(",");
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        return PageRequest.of(zeroBasedPage, size, Sort.by(direction, parts[0].trim()));
    }
}

