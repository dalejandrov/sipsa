package com.dalejandrov.sipsa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response wrapper for endpoints that list available API resources.
 * <p>
 * Used by the API root endpoint to provide autodiscoverable documentation
 * of all available endpoints, their paths, descriptions, and supported methods.
 * <p>
 * <b>Example Response:</b>
 * <pre>
 * {
 *   "name": "ciudad",
 *   "description": "City-level pricing data",
 *   "path": "/api/sipsa/ciudad",
 *   "methods": ["GET"]
 * }
 * </pre>
 *
 * @see com.dalejandrov.sipsa.api.controller.SipsaRestController
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointInfo {

    /**
     * Logical name of the endpoint/resource.
     */
    private String name;

    /**
     * Human-readable description of what the endpoint provides.
     */
    private String description;

    /**
     * Full URL path to the endpoint.
     */
    private String path;

    /**
     * Array of supported HTTP methods (e.g., ["GET", "POST"]).
     */
    private String[] methods;
}

