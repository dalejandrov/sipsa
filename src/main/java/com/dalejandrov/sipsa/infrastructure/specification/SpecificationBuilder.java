package com.dalejandrov.sipsa.infrastructure.specification;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluent API builder for constructing JPA Specifications with common filtering patterns.
 * <p>
 * <b>Example:</b>
 * <pre>
 * SpecificationBuilder.&lt;Product&gt;builder()
 *     .withAttribute("categoryId", categoryId)
 *     .withDateOrRange("createdAt", exactDate, startDate, endDate)
 *     .build();
 * </pre>
 * <p>
 * TECH-104: {@code withDateOrRange} used to build an {@code Instant} range from a
 * timezone-converted {@code LocalDate} — necessary when the target attribute was an
 * instant. Every attribute it's ever called with (the 5 DANE calendar-date fields) is now
 * itself {@code LocalDate}, so no timezone conversion happens here anymore; the builder
 * lost its {@code timezone} constructor parameter entirely (dead once that conversion was
 * removed, not deprecated in place).
 */
public class SpecificationBuilder<T> {

    private final List<Specification<T>> specifications;

    private SpecificationBuilder() {
        this.specifications = new ArrayList<>();
    }

    /**
     * Creates a new SpecificationBuilder instance.
     *
     * @param <T> the entity type
     * @return a new SpecificationBuilder instance
     */
    public static <T> SpecificationBuilder<T> builder() {
        return new SpecificationBuilder<>();
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
     * @param attribute the entity attribute name (must be a {@code LocalDate} field)
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
     * Internal method to add a date range filter directly on a {@code LocalDate}
     * attribute — no timezone conversion, no half-open-range/next-day trick: a
     * {@code DATE} column has no time component, so inclusive bounds on both ends are
     * exact (TECH-104).
     */
    private SpecificationBuilder<T> addDateFilter(String attribute, LocalDate start, LocalDate end) {
        specifications.add((root, query, cb) -> {
            if (start != null && end != null) {
                return cb.between(root.get(attribute), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get(attribute), start);
            } else {
                return cb.lessThanOrEqualTo(root.get(attribute), end);
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
