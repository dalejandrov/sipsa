package com.dalejandrov.sipsa.api.controller;

import com.dalejandrov.sipsa.domain.entity.IngestionRun;
import com.dalejandrov.sipsa.domain.entity.IngestionRunStatus;
import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.IngestionRunRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaParcialRepository;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.ParcialKeyHash;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TECH-113 end-to-end filter tests against REAL PostgreSQL (Testcontainers) through the
 * public HTTP endpoint {@code GET /api/sipsa/parcial}.
 * <p>
 * Reproduces both confirmed defects and proves the fix:
 * <ul>
 *   <li><b>H-2:</b> the article filter used to target the nonexistent entity attribute
 *       {@code artiId} → {@code IllegalArgumentException} → 500 whenever used.</li>
 *   <li><b>H-3:</b> {@code muniId} used to be bound as {@code Long}, destroying DIVIPOLA
 *       leading zeros ({@code "05001"} → {@code 5001}) and mismatching the
 *       {@code VARCHAR} column.</li>
 * </ul>
 * Fixtures deliberately include {@code "05001"} and {@code "5001"} as DIFFERENT
 * municipalities. The endpoint is public ({@code /api/sipsa/**}), so no token is needed;
 * the JWT decoder stays lazy against the dummy test issuer.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@AutoConfigureMockMvc
@DisplayName("GET /api/sipsa/parcial — TECH-113 municipality and article filters (real PostgreSQL)")
class ParcialQueryFilterIntegrationTest {

    /** Same PostgreSQL version as docker-compose.yml / the other migration gates. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    private static final Instant FECHA_1 = Instant.parse("2026-07-14T05:00:00Z");
    private static final Instant FECHA_2 = Instant.parse("2026-07-15T05:00:00Z");

    private static boolean fixturesLoaded = false;

    @Autowired
    private MockMvc mockMvc;

    @BeforeAll
    static void loadFixtures(@Autowired IngestionRunRepository runRepository,
                             @Autowired SipsaParcialRepository parcialRepository) {
        if (fixturesLoaded) {
            return;
        }
        IngestionRun run = runRepository.save(IngestionRun.builder()
                .methodName("promediosSipsaParcial")
                .windowKey("2026-07-15")
                .status(IngestionRunStatus.SUCCEEDED)
                .startTime(Instant.now())
                .build());

        // "05001" and "5001" are DIFFERENT municipalities on purpose (H-3 evidence).
        parcialRepository.saveAll(java.util.List.of(
                fixture("05001", 101L, FECHA_1, run.getRunId()),
                fixture("05001", 102L, FECHA_2, run.getRunId()),
                fixture("5001", 101L, FECHA_1, run.getRunId()),
                fixture("11001", 103L, FECHA_2, run.getRunId())));
        parcialRepository.flush();
        fixturesLoaded = true;
    }

    private static SipsaParcial fixture(String muniId, Long idArtiSemana, Instant fecha, Long runId) {
        return SipsaParcial.builder()
                .keyHash(ParcialKeyHash.compute(muniId, 10L, 2L, idArtiSemana, fecha))
                .muniId(muniId)
                .muniNombre("Muni-" + muniId)
                .fuenId(10L)
                .futiId(2L)
                .idArtiSemana(idArtiSemana)
                .artiNombre("Arti-" + idArtiSemana)
                .enmaFecha(fecha)
                .promedioKg(new BigDecimal("1500.00"))
                .fechaSincronizacion(Instant.now())
                .ingestionRunId(runId)
                .build();
    }

    @Test
    @DisplayName("muniId=05001 returns only the zero-padded municipality (leading zeros preserved)")
    void muniIdWithLeadingZeroMatchesExactly() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial").param("muniId", "05001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].muniId").value("05001"))
                .andExpect(jsonPath("$.results[1].muniId").value("05001"));
    }

    @Test
    @DisplayName("muniId=5001 does NOT return 05001 — the codes are distinct values")
    void muniIdWithoutLeadingZeroDoesNotMatchPadded() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial").param("muniId", "5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].muniId").value("5001"));
    }

    @Test
    @DisplayName("Canonical idArtiSemana filter works (used to be impossible: H-2)")
    void canonicalArticleFilter() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial").param("idArtiSemana", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].idArtiSemana").value(101))
                .andExpect(jsonPath("$.results[1].idArtiSemana").value(101));
    }

    @Test
    @DisplayName("Alias artiId returns the same result as idArtiSemana (used to be a 500: H-2)")
    void aliasArticleFilterMatchesCanonical() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial").param("artiId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].idArtiSemana").value(101));
    }

    @Test
    @DisplayName("Equal canonical + alias values collapse to one condition → 200")
    void equalAliasAndCanonicalAccepted() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial")
                        .param("idArtiSemana", "101").param("artiId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(2)));
    }

    @Test
    @DisplayName("Contradictory idArtiSemana and artiId → 400, never 500")
    void contradictoryAliasRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial")
                        .param("idArtiSemana", "101").param("artiId", "102"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Blank muniId → 400, never 500")
    void blankMuniIdRejectedWith400() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial").param("muniId", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Combined municipality + article filter")
    void combinedMunicipalityAndArticle() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial")
                        .param("muniId", "05001").param("idArtiSemana", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(1)))
                .andExpect(jsonPath("$.results[0].muniId").value("05001"))
                .andExpect(jsonPath("$.results[0].idArtiSemana").value(101));
    }

    @Test
    @DisplayName("Combined municipality + date range keeps pagination and descending sort")
    void combinedMunicipalityAndDateRangeWithPagination() throws Exception {
        mockMvc.perform(get("/api/sipsa/parcial")
                        .param("muniId", "05001")
                        .param("startDate", "2026-07-13").param("endDate", "2026-07-16")
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.pages").value(2))
                .andExpect(jsonPath("$.results", hasSize(1)))
                // default sort enmaFecha,desc → the most recent survey first
                .andExpect(jsonPath("$.results[0].idArtiSemana").value(102));
    }
}
