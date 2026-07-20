package com.dalejandrov.sipsa.infrastructure.persistence;

import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TECH-054: real PostgreSQL (Testcontainers) evidence that
 * {@code IngestionRunRepository.findAll(Pageable)} — Spring Data JPA's built-in
 * paginated finder, no custom {@code @Query} — genuinely retrieves only the requested
 * page, in a stable order, without loading the full table.
 * <p>
 * Uses real PostgreSQL rather than H2: the whole point is proving the paginated SQL
 * behavior (a real {@code LIMIT}/{@code OFFSET} + a separate {@code COUNT} query) that
 * only a real JDBC round trip can demonstrate.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@DisplayName("TECH-054: IngestionRun pagination against real PostgreSQL")
class IngestionRunPaginationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private IngestionRunRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private static final Sort STABLE_ORDER = Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("runId"));

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM ingestion_runs");
        entityManagerFactory.unwrap(SessionFactory.class).getStatistics().clear();
    }

    private IngestionRun run(String windowKey, Instant startTime) {
        return repository.save(IngestionRun.builder()
                .methodName("promediosSipsaCiudad")
                .windowKey(windowKey)
                .requestId("req-" + windowKey)
                .requestSource(RequestSource.MANUAL)
                .status(IngestionRunStatus.SUCCEEDED)
                .startTime(startTime)
                .build());
    }

    @Test
    @DisplayName("only the requested page size is retrieved, regardless of total table size")
    void onlyRequestedPageSizeRetrieved() {
        Instant base = Instant.parse("2026-07-01T00:00:00Z");
        for (int i = 0; i < 25; i++) {
            run("2026-07-" + String.format("%02d", i + 1), base.plusSeconds(i));
        }

        Page<IngestionRun> page = repository.findAll(PageRequest.of(0, 5, STABLE_ORDER));

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(25);
        assertThat(page.getTotalPages()).isEqualTo(5);
    }

    @Test
    @DisplayName("order is stable: startTime DESC, runId DESC across repeated identical requests")
    void orderIsStable() {
        Instant base = Instant.parse("2026-07-01T00:00:00Z");
        for (int i = 0; i < 10; i++) {
            run("2026-07-" + String.format("%02d", i + 1), base.plusSeconds(i * 3600L));
        }

        List<Long> firstCall = repository.findAll(PageRequest.of(0, 10, STABLE_ORDER))
                .map(IngestionRun::getRunId).getContent();
        List<Long> secondCall = repository.findAll(PageRequest.of(0, 10, STABLE_ORDER))
                .map(IngestionRun::getRunId).getContent();

        assertThat(firstCall).isEqualTo(secondCall);
        // Most recently started run first.
        assertThat(firstCall).isSortedAccordingTo((a, b) -> -Long.compare(a, b));
    }

    @Test
    @DisplayName("total count is correct")
    void totalCountIsCorrect() {
        Instant base = Instant.parse("2026-07-01T00:00:00Z");
        for (int i = 0; i < 17; i++) {
            run("2026-07-" + String.format("%02d", i + 1), base.plusSeconds(i));
        }

        Page<IngestionRun> page = repository.findAll(PageRequest.of(0, 5, STABLE_ORDER));

        assertThat(page.getTotalElements()).isEqualTo(17);
        assertThat(page.getTotalPages()).isEqualTo(4); // ceil(17/5)
    }

    @Test
    @DisplayName("pages neither duplicate nor omit runs: the union of every page equals the full set")
    void pagesDoNotDuplicateOrOmit() {
        Instant base = Instant.parse("2026-07-01T00:00:00Z");
        Set<Long> inserted = new HashSet<>();
        for (int i = 0; i < 23; i++) {
            inserted.add(run("2026-07-" + String.format("%02d", i + 1), base.plusSeconds(i)).getRunId());
        }

        Set<Long> collected = new HashSet<>();
        int totalCollected = 0;
        Pageable pageable = PageRequest.of(0, 7, STABLE_ORDER);
        Page<IngestionRun> page;
        do {
            page = repository.findAll(pageable);
            for (IngestionRun r : page.getContent()) {
                assertThat(collected.add(r.getRunId())).as("run %d must not appear on two pages", r.getRunId()).isTrue();
            }
            totalCollected += page.getNumberOfElements();
            pageable = pageable.next();
        } while (page.hasNext());

        assertThat(totalCollected).isEqualTo(23);
        assertThat(collected).isEqualTo(inserted);
    }

    @Test
    @DisplayName("equal startTime values still resolve deterministically via the runId tie-breaker")
    void equalTimestamps_tieBreakByRunId() {
        Instant sameInstant = Instant.parse("2026-07-15T14:20:00Z");
        Long id1 = run("2026-07-15", sameInstant).getRunId();
        Long id2 = run("2026-07-16", sameInstant).getRunId();
        Long id3 = run("2026-07-17", sameInstant).getRunId();

        List<Long> ids = repository.findAll(PageRequest.of(0, 10, STABLE_ORDER))
                .map(IngestionRun::getRunId).getContent();

        // Same startTime for all three -> order must fall back to runId DESC.
        assertThat(ids).containsExactly(
                Math.max(id1, Math.max(id2, id3)),
                id1 + id2 + id3 - Math.max(id1, Math.max(id2, id3)) - Math.min(id1, Math.min(id2, id3)),
                Math.min(id1, Math.min(id2, id3)));
    }

    @Test
    @DisplayName("performance evidence: a paginated query on a 40-row table issues a small, bounded number of statements, not one proportional to table size")
    void paginatedQuery_isBoundedNotProportionalToTableSize() {
        Instant base = Instant.parse("2026-07-01T00:00:00Z");
        for (int i = 0; i < 40; i++) {
            run("2026-07-" + (i + 1), base.plusSeconds(i));
        }

        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        Page<IngestionRun> page = repository.findAll(PageRequest.of(0, 5, STABLE_ORDER));

        assertThat(page.getContent()).hasSize(5);
        // findAll(Pageable) issues exactly one paginated SELECT (LIMIT/OFFSET) and one
        // COUNT query - never one query per row, regardless of the 40 rows in the table.
        assertThat(statistics.getQueryExecutionCount())
                .as("a paginated fetch of 5 rows out of 40 must not scale with table size")
                .isEqualTo(2);
    }
}
