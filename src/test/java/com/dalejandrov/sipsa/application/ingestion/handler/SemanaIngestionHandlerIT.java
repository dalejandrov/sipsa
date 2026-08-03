package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasSemanalRepository;
import com.dalejandrov.sipsa.support.soap.SoapWireMockSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TECH-152 (resolves TECH-044/ADR-011): {@link SemanaIngestionHandler} through its real
 * transport and real persistence, following the pattern {@link CiudadIngestionHandlerIT}
 * (TECH-151) established. Unlike Ciudad's single upsert path, Semana routes each record
 * to one of two real repository methods depending on whether {@code tmpMayoSemId} is
 * present - {@code upsertTmpBatch} (matched by tmp id) or {@code upsertFallbackBatch}
 * (TECH-060's atomic {@code ON CONFLICT (arti_id, fuen_id, fecha_ini) DO NOTHING}) - so
 * the fixture below deliberately includes 2 records of each shape to exercise both
 * against a real unique index, not a mock.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-152: SemanaIngestionHandler against real SOAP transport (WireMock) and real PostgreSQL")
class SemanaIngestionHandlerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    private static WireMockServer soap;

    @DynamicPropertySource
    static void soapEndpoint(DynamicPropertyRegistry registry) {
        soap = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        soap.start();
        registry.add("sipsa.soap.endpoint", () -> SoapWireMockSupport.endpointOf(soap));
    }

    @AfterAll
    static void stopSoap() {
        if (soap != null) {
            soap.stop();
        }
    }

    @Autowired
    private SemanaIngestionHandler handler;

    @Autowired
    private SipsaMayoristasSemanalRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        soap.resetAll();
        jdbc.update("DELETE FROM sipsa_mayoristas_semanal");
    }

    /** See {@code CiudadIngestionHandlerIT.createRun} for why the window_key must be unique per call. */
    private long createRun() {
        return jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaSemanaMadr', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "it-window-" + System.nanoTime());
    }

    private IngestionContext contextFor(long runId) {
        return new IngestionContext(runId, "promediosSipsaSemanaMadr", "window-" + runId,
                "req-" + runId, RequestSource.MANUAL);
    }

    @Test
    @DisplayName("golden path: 2 tmp-id records (upsertTmpBatch) + 2 fallback records (upsertFallbackBatch) all persist")
    void goldenPath_bothUpsertPathsPersist() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "SemanaIngestionHandler", "four-records.xml");
        IngestionContext context = contextFor(createRun());

        handler.execute(context);

        assertThat(context.getRecordsSeen()).isEqualTo(4);
        assertThat(context.getRecordsInserted()).isEqualTo(4);
        assertThat(context.getRejectCount()).isZero();

        List<SipsaMayoristasSemanal> rows = repository.findAll();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(SipsaMayoristasSemanal::getArtiId)
                .containsExactlyInAnyOrder(201L, 202L, 203L, 204L);

        SipsaMayoristasSemanal tmpRouted = rows.stream()
                .filter(r -> r.getArtiId() == 201L).findFirst().orElseThrow();
        assertThat(tmpRouted.getTmpMayoSemId()).isEqualTo(500001L);
        assertThat(tmpRouted.getPromedioKg()).isEqualTo(new BigDecimal("1200.00"));

        SipsaMayoristasSemanal fallbackRouted = rows.stream()
                .filter(r -> r.getArtiId() == 203L).findFirst().orElseThrow();
        assertThat(fallbackRouted.getTmpMayoSemId()).isNull();
        assertThat(fallbackRouted.getFuenNombre()).isEqualTo("BAZURTO");
    }

    @Test
    @DisplayName("idempotency: a second execution against the same fixture skips all 4 records via both upsert paths")
    void secondExecution_sameFixture_skipsExistingRecords() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "SemanaIngestionHandler", "four-records.xml");
        handler.execute(contextFor(createRun()));

        IngestionContext second = contextFor(createRun());
        handler.execute(second);

        assertThat(second.getRecordsSeen()).isEqualTo(4);
        assertThat(second.getRecordsInserted()).isZero();
        assertThat(repository.findAll()).hasSize(4);
    }

    @Test
    @DisplayName("SOAP fault: a WireMock HTTP 500 surfaces as SipsaIngestionException, no rows persisted")
    void soapFault_surfacesAsException_noRowsPersisted() {
        SoapWireMockSupport.stubHttpStatus(soap, 500);
        IngestionContext context = contextFor(createRun());

        assertThatThrownBy(() -> handler.execute(context))
                .isInstanceOf(SipsaIngestionException.class)
                .hasCauseInstanceOf(SipsaExternalException.class);

        assertThat(repository.findAll()).isEmpty();
    }
}
