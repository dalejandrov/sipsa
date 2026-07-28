package com.dalejandrov.sipsa.infrastructure.specification;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasSemanalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TECH-041/TECH-104: {@link SpecificationBuilder} against real PostgreSQL — AND-composition
 * across multiple filters, real {@code DATE} filtering semantics, and filter+pagination
 * interaction. The pure predicate-selection logic (which builder method fires) is covered
 * without a database in {@code SpecificationBuilderTest}; what only a real database can
 * prove is exercised here: does the combined {@code Specification} actually select the
 * right rows through {@code JpaSpecificationExecutor.findAll(Specification, Pageable)} —
 * the real call site used by every {@code SipsaReadService} query method.
 * <p>
 * TECH-104 retyped {@code fecha_ini} from {@code TIMESTAMPTZ} to {@code DATE} — there is no
 * time component and no timezone conversion left to test here (that risk now lives at
 * ingestion time, in {@code SipsaIngestionMapper}); a {@code DATE} filter is an exact
 * calendar-day comparison with inclusive bounds on both ends.
 * <p>
 * Uses {@link SipsaMayoristasSemanalRepository} (real {@code JpaSpecificationExecutor},
 * already exercised for {@code SpecificationBuilder} by {@code SipsaReadService
 * .getMayoristasSemanal}) rather than a synthetic H2 entity — real column types
 * ({@code fecha_ini DATE}), real PostgreSQL date semantics.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-041: SpecificationBuilder against real PostgreSQL")
class SpecificationBuilderPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private SipsaMayoristasSemanalRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long runId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sipsa_mayoristas_semanal");
        runId = jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaSemanaMadr', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "spec-test-" + System.nanoTime());
    }

    private SipsaMayoristasSemanal row(long artiId, long fuenId, LocalDate fechaIni) {
        return repository.save(SipsaMayoristasSemanal.builder()
                .artiId(artiId)
                .artiNombre("ARTICULO " + artiId)
                .fuenId(fuenId)
                .fuenNombre("FUENTE " + fuenId)
                .futiId(1L)
                .fechaIni(fechaIni)
                .promedioKg(new BigDecimal("1000.00"))
                .ingestionRunId(runId)
                .build());
    }

    @Test
    @DisplayName("no filters: build() matches every row, unfiltered")
    void noFilters_returnsAllRows() {
        row(1L, 10L, LocalDate.of(2026, 7, 1));
        row(2L, 10L, LocalDate.of(2026, 7, 5));
        row(3L, 20L, LocalDate.of(2026, 7, 10));

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder().build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("equality filter: only matching rows are returned")
    void equalityFilter_onlyMatchingRows() {
        row(1L, 10L, LocalDate.of(2026, 7, 1));
        row(2L, 20L, LocalDate.of(2026, 7, 5));
        row(3L, 20L, LocalDate.of(2026, 7, 10));

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder()
                .withAttribute("fuenId", 20L)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("exact date filter: only the row on that exact calendar day matches, neighboring days excluded")
    void exactDateFilter_onlyExactDayMatches() {
        LocalDate targetDay = LocalDate.of(2026, 7, 15);
        row(1L, 10L, targetDay);
        row(2L, 10L, targetDay.plusDays(1));
        row(3L, 10L, targetDay.minusDays(1));

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder()
                .withDateOrRange("fechaIni", targetDay, null, null)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactly(1L);
    }

    @Test
    @DisplayName("start-only range: rows before the start are excluded, rows at/after are included")
    void startOnlyRange_boundaryInclusive() {
        LocalDate start = LocalDate.of(2026, 7, 10);
        row(1L, 10L, start.minusDays(1)); // just before -> excluded
        row(2L, 10L, start); // exactly at start -> included
        row(3L, 10L, start.plusDays(5)); // well after -> included

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder()
                .withDateOrRange("fechaIni", null, start, null)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("end-only range: rows on/before end are included, rows after end are excluded")
    void endOnlyRange_boundaryInclusive() {
        LocalDate end = LocalDate.of(2026, 7, 10);
        row(1L, 10L, end.minusDays(1)); // before end -> included
        row(2L, 10L, end); // exactly at end -> included
        row(3L, 10L, end.plusDays(1)); // after end -> excluded

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder()
                .withDateOrRange("fechaIni", null, null, end)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("combined attribute + date filters: AND composition - only rows matching BOTH survive")
    void combinedFilters_andComposition() {
        LocalDate targetDay = LocalDate.of(2026, 7, 15);

        row(1L, 20L, targetDay);              // matches both -> included
        row(2L, 20L, targetDay.plusDays(10));  // matches fuenId only -> excluded
        row(3L, 30L, targetDay);               // matches date only -> excluded

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder()
                .withAttribute("fuenId", 20L)
                .withDateOrRange("fechaIni", targetDay, null, null)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactly(1L);
    }

    @Test
    @DisplayName("filter combined with pagination: no duplicate or omitted rows across pages, unmatched rows never appear")
    void filterWithPagination_noDuplicatesOrOmissions() {
        LocalDate base = LocalDate.of(2026, 7, 1);
        Set<Long> matchingIds = new HashSet<>();
        for (int i = 0; i < 15; i++) {
            matchingIds.add(row(i, 20L, base.plusDays(i)).getId());
        }
        for (int i = 100; i < 105; i++) {
            row(i, 30L, base.plusDays(i)); // non-matching fuenId, must never appear
        }

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder()
                .withAttribute("fuenId", 20L)
                .build();

        Set<Long> collected = new HashSet<>();
        var pageable = PageRequest.of(0, 4);
        Page<SipsaMayoristasSemanal> page;
        do {
            page = repository.findAll(spec, pageable);
            for (SipsaMayoristasSemanal r : page.getContent()) {
                assertThat(collected.add(r.getId())).as("row %d must not appear twice across pages", r.getId()).isTrue();
            }
            pageable = pageable.next();
        } while (page.hasNext());

        assertThat(collected).as("exactly the matching rows, no duplicates, no omissions, no non-matching rows")
                .isEqualTo(matchingIds);
        assertThat(page.getTotalElements()).isEqualTo(15);
    }
}
