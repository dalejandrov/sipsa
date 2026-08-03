package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasMensualRepository;
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
 * TECH-153 (resolves TECH-044/ADR-011): {@link MesIngestionHandler} through its real
 * transport and real persistence, same shape as {@link SemanaIngestionHandlerIT}
 * (TECH-152) - two real upsert paths (tmp-id vs. business-key fallback), exercised by a
 * fixture with 2 records of each shape, against the real {@code ux_mes_tmp}/
 * {@code ux_mes_fallback} unique constraints on {@code sipsa_mayoristas_mensual}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-153: MesIngestionHandler against real SOAP transport (WireMock) and real PostgreSQL")
class MesIngestionHandlerIT {

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
    private MesIngestionHandler handler;

    @Autowired
    private SipsaMayoristasMensualRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        soap.resetAll();
        jdbc.update("DELETE FROM sipsa_mayoristas_mensual");
    }

    /** See {@code CiudadIngestionHandlerIT.createRun} for why the window_key must be unique per call. */
    private long createRun() {
        return jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaMesMadr', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "it-window-" + System.nanoTime());
    }

    private IngestionContext contextFor(long runId) {
        return new IngestionContext(runId, "promediosSipsaMesMadr", "window-" + runId,
                "req-" + runId, RequestSource.MANUAL);
    }

    @Test
    @DisplayName("golden path: 2 tmp-id records (upsertTmpBatch) + 2 fallback records (upsertFallbackBatch) all persist")
    void goldenPath_bothUpsertPathsPersist() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "MesIngestionHandler", "four-records.xml");
        IngestionContext context = contextFor(createRun());

        handler.execute(context);

        assertThat(context.getRecordsSeen()).isEqualTo(4);
        assertThat(context.getRecordsInserted()).isEqualTo(4);
        assertThat(context.getRejectCount()).isZero();

        List<SipsaMayoristasMensual> rows = repository.findAll();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(SipsaMayoristasMensual::getArtiId)
                .containsExactlyInAnyOrder(301L, 302L, 303L, 304L);

        SipsaMayoristasMensual tmpRouted = rows.stream()
                .filter(r -> r.getArtiId() == 301L).findFirst().orElseThrow();
        assertThat(tmpRouted.getTmpMayoMesId()).isEqualTo(600001L);
        assertThat(tmpRouted.getPromedioKg()).isEqualTo(new BigDecimal("2500.00"));

        SipsaMayoristasMensual fallbackRouted = rows.stream()
                .filter(r -> r.getArtiId() == 303L).findFirst().orElseThrow();
        assertThat(fallbackRouted.getTmpMayoMesId()).isNull();
        assertThat(fallbackRouted.getFuenNombre()).isEqualTo("BAZURTO");
    }

    @Test
    @DisplayName("idempotency: a second execution against the same fixture skips all 4 records via both upsert paths")
    void secondExecution_sameFixture_skipsExistingRecords() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "MesIngestionHandler", "four-records.xml");
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
