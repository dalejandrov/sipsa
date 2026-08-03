package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaCiudadRepository;
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
 * TECH-151 (resolves TECH-044/ADR-011): {@link CiudadIngestionHandler} through its real
 * transport and real persistence - the first of the per-handler suite, establishing the
 * pattern TECH-152..155 copy. Runs the actual Spring-managed handler bean (real
 * {@code SoapGatewayImpl} -&gt; {@code SoapStreamingClient}, real
 * {@code SipsaCiudadRepository} -&gt; real {@code batchUpsert}), against a WireMock SOAP
 * endpoint and a Testcontainers PostgreSQL 18 instance - neither of which
 * {@code ParcialIngestionHandlerTest} (TECH-011, mocked repository, hand-built
 * {@code InputStream}) exercises.
 * <p>
 * WireMock has to be running <em>before</em> the Spring context is created, so its
 * dynamic port can be published via {@link DynamicPropertySource} - this test manages
 * its own class-scoped {@link WireMockServer} (started in the
 * {@code @DynamicPropertySource} method, stopped in {@code @AfterAll}) and re-stubs it
 * per test via {@link SoapWireMockSupport}'s static helpers (see that class's Javadoc for
 * why the server is caller-managed rather than owned by a JUnit extension).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-151: CiudadIngestionHandler against real SOAP transport (WireMock) and real PostgreSQL")
class CiudadIngestionHandlerIT {

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
    private CiudadIngestionHandler handler;

    @Autowired
    private SipsaCiudadRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        soap.resetAll();
        jdbc.update("DELETE FROM sipsa_ciudad");
    }

    /**
     * Each call needs its own {@code window_key} - {@code uq_ingestion_runs_window}
     * is a real unique constraint on {@code (method_name, window_key)}, and
     * {@code @BeforeEach} intentionally does not wipe {@code ingestion_runs} (no test
     * here asserts against that table), so a nanoTime suffix keeps every run row
     * distinct both within and across test methods in this class.
     */
    private long createRun() {
        return jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaCiudad', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "it-window-" + System.nanoTime());
    }

    private IngestionContext contextFor(long runId) {
        return new IngestionContext(runId, "promediosSipsaCiudad", "window-" + runId,
                "req-" + runId, RequestSource.MANUAL);
    }

    @Test
    @DisplayName("golden path: WireMock fixture -> real SOAP transport -> real StAX parse -> real Postgres rows")
    void goldenPath_fixtureRecordsArePersisted() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "CiudadIngestionHandler", "two-records.xml");
        IngestionContext context = contextFor(createRun());

        handler.execute(context);

        assertThat(context.getRecordsSeen()).isEqualTo(2);
        assertThat(context.getRecordsInserted()).isEqualTo(2);
        assertThat(context.getRejectCount()).isZero();

        List<SipsaCiudad> rows = repository.findAll();
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(SipsaCiudad::getRegId).containsExactlyInAnyOrder(100001L, 100002L);
        SipsaCiudad bogota = rows.stream().filter(r -> r.getRegId() == 100001L).findFirst().orElseThrow();
        assertThat(bogota.getCiudad()).isEqualTo("BOGOTA, D.C.");
        assertThat(bogota.getCodProducto()).isEqualTo(101L);
        assertThat(bogota.getPrecioPromedio()).isEqualTo(new BigDecimal("2500.00"));
    }

    @Test
    @DisplayName("idempotency: a second execution against the same fixture skips both records, inserts zero")
    void secondExecution_sameFixture_skipsExistingRecords() throws Exception {
        SoapWireMockSupport.stubFixture(soap, "CiudadIngestionHandler", "two-records.xml");
        handler.execute(contextFor(createRun()));

        IngestionContext second = contextFor(createRun());
        handler.execute(second);

        assertThat(second.getRecordsSeen()).isEqualTo(2);
        assertThat(second.getRecordsInserted()).isZero();
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("SOAP fault: a WireMock HTTP 500 surfaces as SipsaIngestionException (SoapGatewayImpl's wrapper "
            + "around the SipsaExternalException SoapStreamingClient throws), no rows persisted")
    void soapFault_surfacesAsException_noRowsPersisted() {
        SoapWireMockSupport.stubHttpStatus(soap, 500);
        IngestionContext context = contextFor(createRun());

        assertThatThrownBy(() -> handler.execute(context))
                .isInstanceOf(SipsaIngestionException.class)
                .hasCauseInstanceOf(SipsaExternalException.class);

        assertThat(repository.findAll()).isEmpty();
    }
}
