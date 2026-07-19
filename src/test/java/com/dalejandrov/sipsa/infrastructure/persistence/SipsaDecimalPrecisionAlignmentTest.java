package com.dalejandrov.sipsa.infrastructure.persistence;

import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaCiudadRepository;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasSemanalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decimal-precision gate for TECH-134: {@code SipsaCiudad} (precioPromedio, enviado) and
 * {@code SipsaMayoristasSemanal} (minimoKg, maximoKg, promedioKg, enviado) now declare
 * {@code precision=19, scale=2}, mirroring the untouched V1 DDL ({@code NUMERIC(19,2)})
 * — the same alignment TECH-118 proved for {@code SipsaParcial}. Verified here against
 * real PostgreSQL through JPA write/read:
 * <ul>
 *   <li>context boots with {@code ddl-auto=validate};</li>
 *   <li>values that fit {@code NUMERIC(19,2)} but NOT {@code DECIMAL(15,2)} round-trip
 *       exactly — the DDL is the storage truth;</li>
 *   <li>scale &gt; 2 coercion at the column boundary stays the TECH-118-documented
 *       half-away-from-zero rounding;</li>
 *   <li>Jackson keeps serializing exact unquoted numbers.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-134: Ciudad and Semanal decimal precision against real PostgreSQL")
class SipsaDecimalPrecisionAlignmentTest {

    /** Fits NUMERIC(19,2); overflows DECIMAL(15,2) — proves which definition rules. */
    private static final BigDecimal ONLY_19_2 = new BigDecimal("99999999999999999.99");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private SipsaCiudadRepository ciudadRepository;

    @Autowired
    private SipsaMayoristasSemanalRepository semanalRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private long runId;
    private long seq;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sipsa_ciudad");
        jdbc.update("DELETE FROM sipsa_mayoristas_semanal");
        runId = jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('tech134', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "tech134-" + System.nanoTime());
    }

    @Test
    @DisplayName("Ciudad: boundary values round-trip exactly through JPA and NUMERIC(19,2)")
    void ciudadBoundaryRoundTrip() {
        Function<BigDecimal, BigDecimal> roundTrip = price -> {
            SipsaCiudad saved = ciudadRepository.saveAndFlush(ciudad(price));
            ciudadRepository.flush();
            return ciudadRepository.findById(saved.getId()).orElseThrow().getPrecioPromedio();
        };

        assertExactMatrix(roundTrip);
        // Real-data shape: enviado is 0.00 on every observed row — stored exactly.
        SipsaCiudad zeroEnviado = ciudadRepository.saveAndFlush(ciudad(new BigDecimal("270.00")));
        assertThat(ciudadRepository.findById(zeroEnviado.getId()).orElseThrow().getEnviado())
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Semanal: boundary values round-trip exactly on all four decimal columns")
    void semanalBoundaryRoundTrip() {
        Function<BigDecimal, BigDecimal> roundTrip = price -> {
            SipsaMayoristasSemanal saved = semanalRepository.saveAndFlush(semanal(price));
            semanalRepository.flush();
            SipsaMayoristasSemanal reloaded = semanalRepository.findById(saved.getId()).orElseThrow();
            // The four columns carry the same value; all must agree after the round-trip.
            assertThat(reloaded.getMinimoKg()).isEqualTo(reloaded.getMaximoKg());
            assertThat(reloaded.getMinimoKg()).isEqualTo(reloaded.getPromedioKg());
            return reloaded.getPromedioKg();
        };

        assertExactMatrix(roundTrip);
        // Real-data shape: enviado arrives null on every observed row (xs:decimal minOccurs=0).
        SipsaMayoristasSemanal reloaded = semanalRepository.findById(
                semanalRepository.saveAndFlush(semanal(new BigDecimal("182.00"))).getId()).orElseThrow();
        assertThat(reloaded.getEnviado()).isNull();
    }

    @Test
    @DisplayName("scale > 2 keeps the TECH-118 semantics: half-away-from-zero at the column boundary")
    void scaleCoercionConsistentAcrossModels() {
        SipsaCiudad c = ciudadRepository.saveAndFlush(ciudad(new BigDecimal("123.456")));
        assertThat(ciudadRepository.findById(c.getId()).orElseThrow().getPrecioPromedio())
                .isEqualTo(new BigDecimal("123.46"));

        SipsaMayoristasSemanal s = semanalRepository.saveAndFlush(semanal(new BigDecimal("-123.455")));
        assertThat(semanalRepository.findById(s.getId()).orElseThrow().getPromedioKg())
                .isEqualTo(new BigDecimal("-123.46"));
    }

    @Test
    @DisplayName("Jackson serializes stored decimals from both models as exact unquoted numbers")
    void jsonStaysExactNumbers() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SipsaCiudad c = ciudadRepository.saveAndFlush(ciudad(new BigDecimal("15500.00")));
        assertThat(mapper.writeValueAsString(
                ciudadRepository.findById(c.getId()).orElseThrow().getPrecioPromedio()))
                .isEqualTo("15500.00");

        SipsaMayoristasSemanal s = semanalRepository.saveAndFlush(semanal(new BigDecimal("280000.00")));
        assertThat(mapper.writeValueAsString(
                semanalRepository.findById(s.getId()).orElseThrow().getPromedioKg()))
                .isEqualTo("280000.00");
    }

    /**
     * Shared boundary matrix: observed extremes of the real DANE data, zero, null,
     * integer, one-decimal, the DECIMAL(15,2) edge, the 19,2-only value, and a negative
     * (no CHECK constraint exists — recorded finding, unchanged in this story).
     */
    private void assertExactMatrix(Function<BigDecimal, BigDecimal> roundTrip) {
        assertThat(roundTrip.apply(new BigDecimal("182.00"))).isEqualTo(new BigDecimal("182.00"));
        assertThat(roundTrip.apply(new BigDecimal("280000.00"))).isEqualTo(new BigDecimal("280000.00"));
        assertThat(roundTrip.apply(new BigDecimal("0.00"))).isEqualTo(new BigDecimal("0.00"));
        assertThat(roundTrip.apply(new BigDecimal("7.5"))).isEqualTo(new BigDecimal("7.50"));
        assertThat(roundTrip.apply(new BigDecimal("15500"))).isEqualTo(new BigDecimal("15500.00"));
        assertThat(roundTrip.apply(new BigDecimal("9999999999999.99")))
                .isEqualTo(new BigDecimal("9999999999999.99"));
        assertThat(roundTrip.apply(ONLY_19_2)).isEqualTo(ONLY_19_2);
        assertThat(roundTrip.apply(new BigDecimal("-230.77"))).isEqualTo(new BigDecimal("-230.77"));
        assertThat(roundTrip.apply(null)).isNull();
    }

    private SipsaCiudad ciudad(BigDecimal precio) {
        return SipsaCiudad.builder()
                .regId(++seq)
                .ciudad("MEDELLIN")
                .codProducto(seq)
                .producto("PRODUCTO " + seq)
                .fechaCaptura(Instant.parse("2026-07-15T05:00:00Z"))
                .precioPromedio(precio)
                .enviado(new BigDecimal("0.00"))
                .ingestionRunId(runId)
                .build();
    }

    private SipsaMayoristasSemanal semanal(BigDecimal price) {
        return SipsaMayoristasSemanal.builder()
                .artiId(++seq)
                .artiNombre("ARTICULO " + seq)
                .fuenId(10L)
                .fuenNombre("FUENTE")
                .futiId(2L)
                .fechaIni(Instant.parse("2026-07-14T05:00:00Z"))
                .minimoKg(price)
                .maximoKg(price)
                .promedioKg(price)
                .enviado(null)
                .ingestionRunId(runId)
                .build();
    }
}
