package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaParcialRepository;
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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TECH-155 (resolves TECH-044/ADR-011): {@link ParcialIngestionHandler} through its real
 * transport and real persistence - the last of the 5 per-handler ITs. Unlike the other 4,
 * this handler already has a dedicated unit test, {@link ParcialIngestionHandlerTest}
 * (TECH-011), but that test deliberately builds its own {@code InputStream} (bypassing
 * {@code SoapGateway}/{@code SoapStreamingClient} entirely) and fakes the repository with
 * an in-memory {@code Map} (bypassing the real {@code key_hash VARCHAR(100) UNIQUE}
 * constraint and the real {@code INSERT ... ON CONFLICT (key_hash) DO NOTHING} path,
 * TECH-117). This test exists to cover exactly what that one does not - real transport,
 * real DB, real unique constraint - and does not replace it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-155: ParcialIngestionHandler against real SOAP transport (WireMock) and real PostgreSQL")
class ParcialIngestionHandlerIT {

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
    private ParcialIngestionHandler handler;

    @Autowired
    private SipsaParcialRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        soap.resetAll();
        jdbc.update("DELETE FROM sipsa_parcial");
    }

    /** See {@code CiudadIngestionHandlerIT.createRun} for why the window_key must be unique per call. */
    private long createRun() {
        return jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaParcial', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "it-window-" + System.nanoTime());
    }

    private IngestionContext contextFor(long runId) {
        return new IngestionContext(runId, "promediosSipsaParcial", "window-" + runId,
                "req-" + runId, RequestSource.MANUAL);
    }

    @Test
    @DisplayName("golden path: WireMock fixture -> real SOAP transport -> real StAX parse -> real Postgres rows")
    void goldenPath_fixtureRecordsArePersisted() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "ParcialIngestionHandler", "two-records.xml");
        IngestionContext context = contextFor(createRun());

        handler.execute(context);

        assertThat(context.getRecordsSeen()).isEqualTo(2);
        assertThat(context.getRecordsInserted()).isEqualTo(2);
        assertThat(context.getRecordsSkipped()).isZero();
        assertThat(context.getRejectCount()).isZero();

        List<SipsaParcial> rows = repository.findAll();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(SipsaParcial::getMuniId).containsExactlyInAnyOrder("05001", "05002");
        assertThat(rows).extracting(SipsaParcial::getKeyHash).doesNotContainNull();

        SipsaParcial medellin = rows.stream().filter(r -> r.getMuniId().equals("05001")).findFirst().orElseThrow();
        assertThat(medellin.getArtiNombre()).isEqualTo("AGUACATE HASS");
        assertThat(medellin.getEnmaFecha()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(medellin.getPromedioKg()).isEqualTo(new BigDecimal("3200.00"));
    }

    @Test
    @DisplayName("idempotency: a second execution against the same fixture inserts zero, skips both, via the real ON CONFLICT (key_hash) path")
    void secondExecution_sameFixture_skipsExistingRecords() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "ParcialIngestionHandler", "two-records.xml");
        handler.execute(contextFor(createRun()));

        IngestionContext second = contextFor(createRun());
        handler.execute(second);

        assertThat(second.getRecordsSeen()).isEqualTo(2);
        assertThat(second.getRecordsInserted()).isZero();
        assertThat(second.getRecordsSkipped()).isEqualTo(2);
        assertThat(repository.findAll()).hasSize(2);
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
