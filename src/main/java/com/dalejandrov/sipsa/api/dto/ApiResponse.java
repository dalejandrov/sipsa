package com.dalejandrov.sipsa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Standardized API response wrapper for paginated endpoints.
 * <p>
 * This class provides a consistent response structure across all API endpoints,
 * following REST best practices and making client integration easier.
 * <p>
 * <b>Response Structure:</b>
 * <pre>
 * {
 *   "count": 150,           // Total number of records across all pages
 *   "next": "http://...",   // URL to next page (null if last page)
 *   "prev": "http://...",   // URL to previous page (null if first page)
 *   "pages": 15,            // Total number of pages
 *   "results": [...]        // Array of data for current page
 * }
 * </pre>
 * <p>
 * The {@code next} and {@code prev} fields are omitted (null) when not applicable,
 * making the response cleaner and reducing payload size.
 *
 * @param <T> the type of data contained in results
 * @see org.springframework.data.domain.Page
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Total number of records across all pages.
     * <p>
     * This represents the complete dataset size, not just the current page.
     */
    private Long count;

    /**
     * URL to fetch the next page of results.
     * <p>
     * Will be {@code null} if this is the last page.
     */
    private String next;

    /**
     * URL to fetch the previous page of results.
     * <p>
     * Will be {@code null} if this is the first page.
     */
    private String prev;

    /**
     * Total number of pages available.
     * <p>
     * Calculated as: {@code ceil(count / pageSize)}
     */
    private Integer pages;

    /**
     * Array of data records for the current page.
     * <p>
     * The size of this list will be at most the requested page size,
     * and may be smaller on the last page.
     */
    private List<T> results;
}

