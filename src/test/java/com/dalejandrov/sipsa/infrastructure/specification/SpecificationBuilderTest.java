package com.dalejandrov.sipsa.infrastructure.specification;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TECH-041: {@link SpecificationBuilder} — pure construction-logic unit tests, no database.
 * <p>
 * {@code SpecificationBuilder} is entity-type-agnostic ({@code <T>}), so a mocked JPA
 * Criteria API (`Root`/`CriteriaBuilder`/`Path`/`Predicate`) is enough to verify which
 * builder method fires with which arguments — the real, stable contract this class
 * actually implements, not an internal/private detail. AND-composition across multiple
 * filters and real date-boundary/timezone semantics are verified separately, against real
 * PostgreSQL, in {@code SpecificationBuilderPostgresTest} (mocking Spring Data's own
 * {@code Specification.and()} internals here would be fragile and would not prove
 * anything about actual database semantics).
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

    private static final String ZONE = "America/Bogota";

    // -----------------------------------------------------------------------
    // builder() factory
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("null timezone: throws SipsaConfigurationException")
    void builder_nullTimezone_throws() {
        assertThatExceptionOfType(SipsaConfigurationException.class)
                .isThrownBy(() -> SpecificationBuilder.builder(null));
    }

    @Test
    @DisplayName("blank timezone: throws SipsaConfigurationException")
    void builder_blankTimezone_throws() {
        assertThatExceptionOfType(SipsaConfigurationException.class)
                .isThrownBy(() -> SpecificationBuilder.builder("   "));
    }

    @Test
    @DisplayName("valid timezone: builder is created without error")
    void builder_validTimezone_succeeds() {
        assertThatCode(() -> SpecificationBuilder.builder(ZONE)).doesNotThrowAnyException();
    }

    // -----------------------------------------------------------------------
    // withAttribute
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("withAttribute(null): no predicate added - build() returns a bare conjunction, root untouched")
    void withAttribute_null_noPredicateAdded() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Specification<Dummy> spec = SpecificationBuilder.<Dummy>builder(ZONE)
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

        Specification<Dummy> spec = SpecificationBuilder.<Dummy>builder(ZONE)
                .withAttribute("ciudad", "Bogota")
                .build();
        Predicate result = spec.toPredicate(root, query, cb);

        verify(root).get("ciudad");
        verify(cb).equal(path, "Bogota");
        assertThat(result).isSameAs(equalPredicate);
    }

    // -----------------------------------------------------------------------
    // withDateOrRange - precedence and boundary math
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("exact date: a full-day range [startOfDay, startOfNextDay) in the builder's timezone")
    void withDateOrRange_exactDate_singleDayRange() {
        Path<Instant> path = mock(Path.class);
        Predicate betweenPredicate = mock(Predicate.class);
        when(root.<Instant>get("fecha")).thenReturn(path);
        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        when(cb.between(eq(path), start.capture(), end.capture())).thenReturn(betweenPredicate);

        LocalDate exact = LocalDate.of(2026, 7, 20);
        Specification<Dummy> spec = SpecificationBuilder.<Dummy>builder(ZONE)
                .withDateOrRange("fecha", exact, null, null)
                .build();
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isSameAs(betweenPredicate);
        ZoneId zone = ZoneId.of(ZONE);
        assertThat(start.getValue()).isEqualTo(exact.atStartOfDay(zone).toInstant());
        assertThat(end.getValue()).isEqualTo(exact.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Test
    @DisplayName("exact date takes precedence over an accompanying start/end range")
    void withDateOrRange_exactDateTakesPrecedenceOverRange() {
        Path<Instant> path = mock(Path.class);
        Predicate betweenPredicate = mock(Predicate.class);
        when(root.<Instant>get("fecha")).thenReturn(path);
        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        when(cb.between(eq(path), start.capture(), end.capture())).thenReturn(betweenPredicate);

        LocalDate exact = LocalDate.of(2026, 7, 20);
        LocalDate ignoredStart = LocalDate.of(2020, 1, 1);
        LocalDate ignoredEnd = LocalDate.of(2020, 12, 31);
        SpecificationBuilder.<Dummy>builder(ZONE)
                .withDateOrRange("fecha", exact, ignoredStart, ignoredEnd)
                .build()
                .toPredicate(root, query, cb);

        ZoneId zone = ZoneId.of(ZONE);
        assertThat(start.getValue()).as("the exact date's own boundary is used, not ignoredStart")
                .isEqualTo(exact.atStartOfDay(zone).toInstant());
        assertThat(end.getValue()).isEqualTo(exact.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Test
    @DisplayName("start and end both set (no exact date): a [start, end+1day) between predicate")
    void withDateOrRange_startAndEnd_range() {
        Path<Instant> path = mock(Path.class);
        Predicate betweenPredicate = mock(Predicate.class);
        when(root.<Instant>get("fecha")).thenReturn(path);
        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        when(cb.between(eq(path), start.capture(), end.capture())).thenReturn(betweenPredicate);

        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 20);
        Predicate result = SpecificationBuilder.<Dummy>builder(ZONE)
                .withDateOrRange("fecha", null, from, to)
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(betweenPredicate);
        ZoneId zone = ZoneId.of(ZONE);
        assertThat(start.getValue()).isEqualTo(from.atStartOfDay(zone).toInstant());
        assertThat(end.getValue()).as("end boundary is exclusive: start of the day AFTER `to`")
                .isEqualTo(to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Test
    @DisplayName("only start set: a >= predicate at start-of-day")
    void withDateOrRange_onlyStart_greaterThanOrEqual() {
        Path<Instant> path = mock(Path.class);
        Predicate gtePredicate = mock(Predicate.class);
        when(root.<Instant>get("fecha")).thenReturn(path);
        ArgumentCaptor<Instant> startArg = ArgumentCaptor.forClass(Instant.class);
        when(cb.greaterThanOrEqualTo(eq(path), startArg.capture())).thenReturn(gtePredicate);

        LocalDate from = LocalDate.of(2026, 7, 10);
        Predicate result = SpecificationBuilder.<Dummy>builder(ZONE)
                .withDateOrRange("fecha", null, from, null)
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(gtePredicate);
        assertThat(startArg.getValue()).isEqualTo(from.atStartOfDay(ZoneId.of(ZONE)).toInstant());
        verify(cb, never()).between(org.mockito.ArgumentMatchers.<Path<Instant>>any(),
                org.mockito.ArgumentMatchers.any(Instant.class), org.mockito.ArgumentMatchers.any(Instant.class));
        verify(cb, never()).lessThan(org.mockito.ArgumentMatchers.<Path<Instant>>any(), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    @DisplayName("only end set: a < predicate at start-of-the-following-day (exclusive)")
    void withDateOrRange_onlyEnd_lessThan() {
        Path<Instant> path = mock(Path.class);
        Predicate ltPredicate = mock(Predicate.class);
        when(root.<Instant>get("fecha")).thenReturn(path);
        ArgumentCaptor<Instant> endArg = ArgumentCaptor.forClass(Instant.class);
        when(cb.lessThan(eq(path), endArg.capture())).thenReturn(ltPredicate);

        LocalDate to = LocalDate.of(2026, 7, 15);
        Predicate result = SpecificationBuilder.<Dummy>builder(ZONE)
                .withDateOrRange("fecha", null, null, to)
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).isSameAs(ltPredicate);
        assertThat(endArg.getValue()).isEqualTo(to.plusDays(1).atStartOfDay(ZoneId.of(ZONE)).toInstant());
    }

    @Test
    @DisplayName("all three date args null: no filter added, build() returns a bare conjunction")
    void withDateOrRange_allNull_noPredicateAdded() {
        Predicate conjunction = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(conjunction);

        Predicate result = SpecificationBuilder.<Dummy>builder(ZONE)
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

        Predicate result = SpecificationBuilder.<Dummy>builder(ZONE).build().toPredicate(root, query, cb);

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

        Predicate result = SpecificationBuilder.<Dummy>builder(ZONE)
                .withAttribute("ciudad", "Bogota")
                .build()
                .toPredicate(root, query, cb);

        assertThat(result).as("no AND-wrapping needed/attempted for a single predicate")
                .isSameAs(equalPredicate);
    }
}
