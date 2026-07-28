package com.dalejandrov.sipsa.infrastructure.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TECH-041/TECH-104: {@link SpecificationBuilder} — pure construction-logic unit tests, no
 * database.
 * <p>
 * {@code SpecificationBuilder} is entity-type-agnostic ({@code <T>}), so a mocked JPA
 * Criteria API (`Root`/`CriteriaBuilder`/`Path`/`Predicate`) is enough to verify which
 * builder method fires with which arguments — the real, stable contract this class
 * actually implements, not an internal/private detail. AND-composition across multiple
 * filters is verified separately, against real PostgreSQL, in
 * {@code SpecificationBuilderPostgresTest} (mocking Spring Data's own
 * {@code Specification.and()} internals here would be fragile and would not prove
 * anything about actual database semantics).
 * <p>
 * TECH-104: {@code withDateOrRange} used to build an {@code Instant} range from a
 * timezone-converted {@code LocalDate} — the builder no longer takes a {@code timezone}
 * constructor argument, and date predicates compare {@code LocalDate} directly (no
 * conversion, inclusive bounds on both ends).
 * <p>
 * Scope note: {@code SpecificationBuilder} has no LIKE/partial-match, no OR composition,
 * no join support, no field-name allowlist, and no type-conversion logic — confirmed by
 * reading the production source before writing any test here. Every real caller
 * ({@code SipsaReadService}) passes a hardcoded, literal attribute name; no test invents
 * behavior (case-insensitivity, wildcard escaping, join traversal) the class does not
 * have.
 */
@DisplayName("SpecificationBuilder — pure construction logic (TECH-041)")
class SpecificationBuilderTest {

    private record Dummy(Long id) {}

    @SuppressWarnings("unchecked")
    private final Root<Dummy> root = mock(Root.class);
    private final CriteriaQuery<?> query = mock(CriteriaQuery.class);
    private final CriteriaBuilder cb = mock(CriteriaBuilder.class);

    // -----------------------------------------------------------------------
    // withAttribute
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("withAttribute(null): no predicate added - build() returns a bare conjunction, root untouched")
    void withAttribute_null_noPredicateAdded() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Specification<Dummy> spec = SpecificationBuilder.<Dummy>builder()
                .withAttribute("ciudad", null)
                .build();
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunction);
        verifyNoInteractions(root);
    }

    @Test
    @DisplayName("withAttribute(value): an equal predicate is built against root.get(attribute)")
    void withAttribute_value_equalPredicate() {
        Path<Object> path = mock(Path.class);
        Predicate equalPredicate = mock(Predicate.class);
        when(root.<Object>get("ciudad")).thenReturn(path);
        when(cb.equal(path, "Bogota")).thenReturn(equalPredicate);

        Specification<Dummy> spec = SpecificationBuilder.<Dummy>builder()
                .withAttribute("ciudad", "Bogota")
                .build();
        Predicate result = spec.toPredicate(root, query, cb);

        verify(root).get("ciudad");
        verify(cb).equal(path, "Bogota");
        assertThat(result).isSameAs(equalPredicate);
    }

    // -----------------------------------------------------------------------
    // withDateOrRange - precedence and predicate selection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("exact date: a between(date, date) predicate — inclusive single-day match")
    void withDateOrRange_exactDate_betweenSameDayTwice() {
        Path<LocalDate> path = mock(Path.class);
        Predicate betweenPredicate = mock(Predicate.class);
        when(root.<LocalDate>get("fecha")).thenReturn(path);
        ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> end = ArgumentCaptor.forClass(LocalDate.class);
        when(cb.between(eq(path), start.capture(), end.capture())).thenReturn(betweenPredicate);

        LocalDate exact = LocalDate.of(2026, 7, 20);
        Specification<Dummy> spec = SpecificationBuilder.<Dummy>builder()
                .withDateOrRange("fecha", exact, null, null)
                .build();
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(betweenPredicate);
        assertThat(start.getValue()).isEqualTo(exact);
        assertThat(end.getValue()).isEqualTo(exact);
    }

    @Test
    @DisplayName("exact date takes precedence over an accompanying start/end range")
    void withDateOrRange_exactDateTakesPrecedenceOverRange() {
        Path<LocalDate> path = mock(Path.class);
        Predicate betweenPredicate = mock(Predicate.class);
        when(root.<LocalDate>get("fecha")).thenReturn(path);
        ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> end = ArgumentCaptor.forClass(LocalDate.class);
        when(cb.between(eq(path), start.capture(), end.capture())).thenReturn(betweenPredicate);

        LocalDate exact = LocalDate.of(2026, 7, 20);
        LocalDate ignoredStart = LocalDate.of(2020, 1, 1);
        LocalDate ignoredEnd = LocalDate.of(2020, 12, 31);
        SpecificationBuilder.<Dummy>builder()
                .withDateOrRange("fecha", exact, ignoredStart, ignoredEnd)
                .build()
                .toPredicate(root, query, cb);

        assertThat(start.getValue()).as("the exact date is used, not ignoredStart").isEqualTo(exact);
        assertThat(end.getValue()).as("the exact date is used, not ignoredEnd").isEqualTo(exact);
    }

    @Test
    @DisplayName("start and end both set (no exact date): a [start, end] between predicate, both bounds inclusive")
    void withDateOrRange_startAndEnd_range() {
        Path<LocalDate> path = mock(Path.class);
        Predicate betweenPredicate = mock(Predicate.class);
        when(root.<LocalDate>get("fecha")).thenReturn(path);
        ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> end = ArgumentCaptor.forClass(LocalDate.class);
        when(cb.between(eq(path), start.capture(), end.capture())).thenReturn(betweenPredicate);

        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 20);
        Predicate result = SpecificationBuilder.<Dummy>builder()
                .withDateOrRange("fecha", null, from, to)
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(betweenPredicate);
        assertThat(start.getValue()).isEqualTo(from);
        assertThat(end.getValue()).as("end boundary is inclusive: `to` itself, no +1 day").isEqualTo(to);
    }

    @Test
    @DisplayName("only start set: a >= predicate")
    void withDateOrRange_onlyStart_greaterThanOrEqual() {
        Path<LocalDate> path = mock(Path.class);
        Predicate gtePredicate = mock(Predicate.class);
        when(root.<LocalDate>get("fecha")).thenReturn(path);
        ArgumentCaptor<LocalDate> startArg = ArgumentCaptor.forClass(LocalDate.class);
        when(cb.greaterThanOrEqualTo(eq(path), startArg.capture())).thenReturn(gtePredicate);

        LocalDate from = LocalDate.of(2026, 7, 10);
        Predicate result = SpecificationBuilder.<Dummy>builder()
                .withDateOrRange("fecha", null, from, null)
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(gtePredicate);
        assertThat(startArg.getValue()).isEqualTo(from);
        verify(cb, never()).between(org.mockito.ArgumentMatchers.<Path<LocalDate>>any(),
                org.mockito.ArgumentMatchers.any(LocalDate.class), org.mockito.ArgumentMatchers.any(LocalDate.class));
        verify(cb, never()).lessThanOrEqualTo(org.mockito.ArgumentMatchers.<Path<LocalDate>>any(), org.mockito.ArgumentMatchers.any(LocalDate.class));
    }

    @Test
    @DisplayName("only end set: a <= predicate — inclusive of the end date itself")
    void withDateOrRange_onlyEnd_lessThanOrEqual() {
        Path<LocalDate> path = mock(Path.class);
        Predicate ltePredicate = mock(Predicate.class);
        when(root.<LocalDate>get("fecha")).thenReturn(path);
        ArgumentCaptor<LocalDate> endArg = ArgumentCaptor.forClass(LocalDate.class);
        when(cb.lessThanOrEqualTo(eq(path), endArg.capture())).thenReturn(ltePredicate);

        LocalDate to = LocalDate.of(2026, 7, 15);
        Predicate result = SpecificationBuilder.<Dummy>builder()
                .withDateOrRange("fecha", null, null, to)
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(ltePredicate);
        assertThat(endArg.getValue()).isEqualTo(to);
    }

    @Test
    @DisplayName("all three date args null: no filter added, build() returns a bare conjunction")
    void withDateOrRange_allNull_noPredicateAdded() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Predicate result = SpecificationBuilder.<Dummy>builder()
                .withDateOrRange("fecha", null, null, null)
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunction);
        verifyNoInteractions(root);
    }

    // -----------------------------------------------------------------------
    // build()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("no filters at all: build() returns an always-true conjunction")
    void build_empty_returnsConjunction() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Predicate result = SpecificationBuilder.<Dummy>builder().build().toPredicate(root, query, cb);

        assertThat(result).isSameAs(conjunction);
        verifyNoInteractions(root);
    }

    @Test
    @DisplayName("exactly one filter: build() returns that filter's own predicate directly, unwrapped")
    void build_singleFilter_returnsThatPredicateDirectly() {
        Path<Object> path = mock(Path.class);
        Predicate equalPredicate = mock(Predicate.class);
        when(root.<Object>get("ciudad")).thenReturn(path);
        when(cb.equal(path, "Bogota")).thenReturn(equalPredicate);

        Predicate result = SpecificationBuilder.<Dummy>builder()
                .withAttribute("ciudad", "Bogota")
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).as("no AND-wrapping needed/attempted for a single predicate")
                .isSameAs(equalPredicate);
    }
}
