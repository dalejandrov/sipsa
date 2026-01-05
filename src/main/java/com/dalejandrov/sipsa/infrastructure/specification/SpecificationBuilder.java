package com.dalejandrov.sipsa.infrastructure.specification;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent API builder for constructing JPA Specifications with common filtering patterns.
 * <p>
 * <b>Example:</b>
 * <pre>
 * SpecificationBuilder.&lt;Product&gt;builder("America/Bogotá")
 *     .withAttribute("categoryId", categoryId)
 *     .withDateOrRange("createdAt", exactDate, startDate, endDate)
 *     .build();
 * </pre>
 */
public class SpecificationBuilder<T> {

    private final String timezone;
    private final List<Specification<T>> specifications;

    private SpecificationBuilder(String timezone) {
        this.timezone = timezone;
        this.specifications = new ArrayList<>();
    }

    /**
     * Creates a new SpecificationBuilder instance.
     *
     * @param timezone the timezone ID for date conversions (e.g., "America/Bogotá")
     * @param <T>      the entity type
     * @return a new SpecificationBuilder instance
     * @throws SipsaConfigurationException if timezone is null or blank
     */
    public static <T> SpecificationBuilder<T> builder(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new SipsaConfigurationException("Timezone cannot be null or blank");
        }
        return new SpecificationBuilder<>(timezone);
    }

    /**
     * Adds an exact attribute matching filter.
     * <p>
     * If the value is null, this filter is skipped.
     *
     * @param attribute the entity attribute name to match
     * @param value     the value to match (null means no filter)
     * @return this builder for method chaining
     */
    public SpecificationBuilder<T> withAttribute(String attribute, Object value) {
        if (value != null) {
            specifications.add((root, query, cb) -> cb.equal(root.get(attribute), value));
        }
        return this;
    }

    /**
     * Adds a date filter with automatic precedence logic.
     * <p>
     * <b>Precedence Rules:</b>
     * <ol>
     *   <li>If exactDate is not null → use exact date filter (full day)</li>
     *   <li>Otherwise, if startDate or endDate is not null → use range filter</li>
     *   <li>Otherwise → no filter added</li>
     * </ol>
     *
     * @param attribute the entity attribute name
     * @param exactDate optional exact date (takes precedence)
     * @param startDate optional range start date
     * @param endDate   optional range end date
     * @return this builder for method chaining
     */
    public SpecificationBuilder<T> withDateOrRange(String attribute, LocalDate exactDate,
                                                   LocalDate startDate, LocalDate endDate) {
        if (exactDate != null) {
            return addDateFilter(attribute, exactDate, exactDate);
        } else if (startDate != null || endDate != null) {
            return addDateFilter(attribute, startDate, endDate);
        }
        return this;
    }

    /**
     * Internal method to add date range filter.
     */
    private SpecificationBuilder<T> addDateFilter(String attribute, LocalDate start, LocalDate end) {
        specifications.add((root, query, cb) -> {
            ZoneId zone = ZoneId.of(timezone);

            if (start != null && end != null) {
                Instant startInstant = start.atStartOfDay(zone).toInstant();
                Instant endInstant = (end.plusDays(1))
                        .atStartOfDay(zone).toInstant();
                return cb.between(root.get(attribute), startInstant, endInstant);
            } else if (start != null) {
                Instant startInstant = start.atStartOfDay(zone).toInstant();
                return cb.greaterThanOrEqualTo(root.get(attribute), startInstant);
            } else {
                Instant endInstant = end.plusDays(1).atStartOfDay(zone).toInstant();
                return cb.lessThan(root.get(attribute), endInstant);
            }
        });
        return this;
    }

    /**
     * Builds the final Specification by combining all added filters with AND logic.
     *
     * @return the combined Specification (never null)
     */
    public Specification<T> build() {
        if (specifications.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }

        Specification<T> result = specifications.getFirst();
        for (int i = 1; i < specifications.size(); i++) {
            result = result.and(specifications.get(i));
        }
        return result;
    }
}

