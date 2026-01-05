package com.dalejandrov.sipsa.api.util;

import com.dalejandrov.sipsa.api.dto.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Utility class for building standardized API responses from Spring Data Page objects.
 * <p>
 * This class transforms Spring's {@link Page} objects (0-based) into our custom
 * {@link ApiResponse} format with 1-based page numbers for better user experience.
 * <p>
 * <b>Page Number Convention:</b>
 * <ul>
 *   <li>Internal (Spring Data): 0-based (page 0, 1, 2...)</li>
 *   <li>API Response: 1-based (page 1, 2, 3...)</li>
 * </ul>
 *
 * @see ApiResponse
 * @see org.springframework.data.domain.Page
 */
public class PaginationUtils {

    private PaginationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Converts a Spring Data Page to our standardized ApiResponse format.
     * <p>
     * Converts 0-based page numbers (internal) to 1-based (API) for better UX.
     *
     * @param page the Spring Data page object to convert
     * @param <T> the type of data in the page
     * @return standardized API response with pagination metadata and URLs
     */
    public static <T> ApiResponse<T> toApiResponse(Page<T> page) {
        return ApiResponse.<T>builder()
                .count(page.getTotalElements())
                .pages(page.getTotalPages())
                .results(page.getContent())
                .next(page.hasNext() ? buildPageUrl(page.getNumber() + 2) : null)  // +2 because next page in 1-based
                .prev(page.hasPrevious() ? buildPageUrl(page.getNumber()) : null)  // current page number is already 1-based equivalent
                .build();
    }

    /**
     * Builds a complete URL for a specific page number (1-based).
     *
     * @param pageNumber 1-based page number to build URL for
     * @return complete URL string with updated page parameter
     */
    private static String buildPageUrl(int pageNumber) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", pageNumber)
                .toUriString();
    }
}

