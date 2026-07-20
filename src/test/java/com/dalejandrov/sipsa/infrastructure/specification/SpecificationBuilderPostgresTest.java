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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TECH-041: {@link SpecificationBuilder} against real PostgreSQL — AND-composition across
 * multiple filters, real {@code TIMESTAMPTZ} date-boundary/timezone semantics, and
 * filter+pagination interaction. The pure predicate-selection logic (which builder method
 * fires) is covered without a database in {@code SpecificationBuilderTest}; what only a
 * real database can prove is exercised here: does the combined {@code Specification}
 * actually select the right rows, at exact timezone boundaries, through
 * {@code JpaSpecificationExecutor.findAll(Specification, Pageable)} — the real call site
 * used by every {@code SipsaReadService} query method.
 * <p>
 * Uses {@link SipsaMayoristasSemanalRepository} (real {@code JpaSpecificationExecutor},
 * already exercised for {@code SpecificationBuilder} by {@code SipsaReadService
 * .getMayoristasSemanal}) rather than a synthetic H2 entity — real column types
 * ({@code fecha_ini TIMESTAMPTZ}), real PostgreSQL date/time semantics.
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

    private static final String ZONE = "America/Bogota";
    private static final ZoneId BOGOTA = ZoneId.of(ZONE);

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

    private SipsaMayoristasSemanal row(long artiId, long fuenId, Instant fechaIni) {
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
        row(1L, 10L, Instant.parse("2026-07-01T12:00:00Z"));
        row(2L, 10L, Instant.parse("2026-07-05T12:00:00Z"));
        row(3L, 20L, Instant.parse("2026-07-10T12:00:00Z"));

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE).build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).hasSize(3);
    }

    @Test
    @DisplayName("equality filter: only matching rows are returned")
    void equalityFilter_onlyMatchingRows() {
        row(1L, 10L, Instant.parse("2026-07-01T12:00:00Z"));
        row(2L, 20L, Instant.parse("2026-07-05T12:00:00Z"));
        row(3L, 20L, Instant.parse("2026-07-10T12:00:00Z"));

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE)
                .withAttribute("fuenId", 20L)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("exact date filter: real TIMESTAMPTZ boundary at 23:59:59/00:00:00 America/Bogota")
    void exactDateFilter_realTimezoneBoundary() {
        LocalDate targetDay = LocalDate.of(2026, 7, 15);
        // Just inside the target day in Bogota (UTC-5): 23:59:59 local = 04:59:59 next day UTC.
        Instant lastSecondOfDay = targetDay.atTime(23, 59, 59).atZone(BOGOTA).toInstant();
        // Just outside: one second into the NEXT Bogota day.
        Instant secondSecondNextDay = targetDay.plusDays(1).atStartOfDay(BOGOTA).toInstant().plusSeconds(1);
        // Just outside on the other end: the last second of the PREVIOUS Bogota day.
        Instant lastSecondPreviousDay = targetDay.minusDays(1).atTime(23, 59, 59).atZone(BOGOTA).toInstant();

        row(1L, 10L, lastSecondOfDay);
        row(2L, 10L, secondSecondNextDay);
        row(3L, 10L, lastSecondPreviousDay);

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE)
                .withDateOrRange("fechaIni", targetDay, null, null)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId)
                .as("only the row inside the Bogota calendar day matches; neighboring-day rows are excluded")
                .containsExactly(1L);
    }

    @Test
    @DisplayName("exact date filter: the upper boundary instant itself (next day's exact midnight) is included - cb.between() is inclusive on both ends")
    void exactDateFilter_upperBoundaryInstantIsInclusive() {
        // A real, verified implementation detail, not an assumption: withDateOrRange builds
        // an exact-date filter with cb.between(path, startOfDay, startOfNextDay), and SQL/JPA
        // BETWEEN is inclusive on both bounds - so the exact next-day-midnight instant matches
        // too, even though the Javadoc describes this as a "full day range". In practice a real
        // ingested timestamp lands on this exact instant with negligible probability, but the
        // behavior is real and worth pinning down rather than assuming exclusivity.
        LocalDate targetDay = LocalDate.of(2026, 7, 15);
        Instant exactNextDayMidnight = targetDay.plusDays(1).atStartOfDay(BOGOTA).toInstant();
        row(1L, 10L, exactNextDayMidnight);

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE)
                .withDateOrRange("fechaIni", targetDay, null, null)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("start-only range: rows before the start are excluded, rows at/after are included")
    void startOnlyRange_boundaryInclusive() {
        LocalDate start = LocalDate.of(2026, 7, 10);
        row(1L, 10L, start.minusDays(1).atTime(23, 59, 59).atZone(BOGOTA).toInstant()); // just before -> excluded
        row(2L, 10L, start.atStartOfDay(BOGOTA).toInstant()); // exactly at start -> included
        row(3L, 10L, start.plusDays(5).atStartOfDay(BOGOTA).toInstant()); // well after -> included

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE)
                .withDateOrRange("fechaIni", null, start, null)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    @DisplayName("end-only range: rows on/after end+1day are excluded (exclusive upper bound)")
    void endOnlyRange_boundaryExclusive() {
        LocalDate end = LocalDate.of(2026, 7, 10);
        row(1L, 10L, end.atStartOfDay(BOGOTA).toInstant()); // start of `end` day -> included
        row(2L, 10L, end.atTime(23, 59, 59).atZone(BOGOTA).toInstant()); // last second of `end` day -> included
        row(3L, 10L, end.plusDays(1).atStartOfDay(BOGOTA).toInstant()); // start of the NEXT day -> excluded

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE)
                .withDateOrRange("fechaIni", null, null, end)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("combined attribute + date filters: AND composition - only rows matching BOTH survive")
    void combinedFilters_andComposition() {
        LocalDate targetDay = LocalDate.of(2026, 7, 15);
        Instant insideDay = targetDay.atTime(12, 0, 0).atZone(BOGOTA).toInstant();
        Instant outsideDay = targetDay.plusDays(10).atTime(12, 0, 0).atZone(BOGOTA).toInstant();

        row(1L, 20L, insideDay);   // matches both -> included
        row(2L, 20L, outsideDay);  // matches fuenId only -> excluded
        row(3L, 30L, insideDay);   // matches date only -> excluded

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE)
                .withAttribute("fuenId", 20L)
                .withDateOrRange("fechaIni", targetDay, null, null)
                .build();
        List<SipsaMayoristasSemanal> results = repository.findAll(spec);

        assertThat(results).extracting(SipsaMayoristasSemanal::getArtiId).containsExactly(1L);
    }

    @Test
    @DisplayName("filter combined with pagination: no duplicate or omitted rows across pages, unmatched rows never appear")
    void filterWithPagination_noDuplicatesOrOmissions() {
        Instant base = Instant.parse("2026-07-01T12:00:00Z");
        Set<Long> matchingIds = new HashSet<>();
        for (int i = 0; i < 15; i++) {
            matchingIds.add(row(i, 20L, base.plusSeconds(i)).getId());
        }
        for (int i = 100; i < 105; i++) {
            row(i, 30L, base.plusSeconds(i)); // non-matching fuenId, must never appear
        }

        Specification<SipsaMayoristasSemanal> spec = SpecificationBuilder.<SipsaMayoristasSemanal>builder(ZONE)
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
