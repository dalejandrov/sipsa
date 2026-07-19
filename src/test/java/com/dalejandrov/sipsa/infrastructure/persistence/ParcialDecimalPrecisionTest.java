package com.dalejandrov.sipsa.infrastructure.persistence;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaParcialRepository;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.ParcialKeyHash;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decimal-precision gate for TECH-118: the JPA annotation ({@code precision=19, scale=2})
 * now mirrors the versioned DDL ({@code NUMERIC(19,2)}, V1 — unchanged), and this class
 * proves the pairing against real PostgreSQL through the REAL write path
 * ({@code batchUpsert} → JDBC batch insert) and the JPA read path:
 * <ul>
 *   <li>context boots with {@code ddl-auto=validate} → entity/DDL compatible;</li>
 *   <li>values that fit {@code NUMERIC(19,2)} but NOT {@code DECIMAL(15,2)} round-trip
 *       exactly — the DDL, not the old annotation, is the storage truth;</li>
 *   <li>scale coercion at the database boundary is explicit and pinned: PostgreSQL
 *       rounds scale &gt; 2 half-away-from-zero on insert (documented, not silent);</li>
 *   <li>Jackson serializes the stored {@code BigDecimal} exactly (scale preserved) —
 *       JSON stays a number, no contract change.</li>
 * </ul>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
@DisplayName("TECH-118: SipsaParcial decimal precision against real PostgreSQL")
class ParcialDecimalPrecisionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.0-alpine3.22");

    @Autowired
    private SipsaParcialRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long runId;
    private int keySeq;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM sipsa_parcial");
        runId = jdbc.queryForObject("""
                INSERT INTO ingestion_runs (method_name, window_key, status)
                VALUES ('promediosSipsaParcial', ?, 'RUNNING')
                RETURNING run_id""", Long.class, "tech118-" + System.nanoTime());
    }

    @Test
    @DisplayName("boundary values round-trip exactly through the real write and read paths")
    void boundaryValuesRoundTripExactly() {
        assertRoundTrip("230.00");                  // observed minimum (real DANE data)
        assertRoundTrip("22000.00");                // observed maximum (real DANE data)
        assertRoundTrip("0.00");                    // zero (10,427 real rows carry it)
        assertRoundTrip("7.50");                    // one meaningful decimal, stored scale 2
        assertRoundTrip("15500.00");                // integer price at scale 2
        assertRoundTrip("9999999999999.99");        // DECIMAL(15,2) upper edge — fits both
        assertRoundTrip("99999999999999999.99");    // fits NUMERIC(19,2), NOT DECIMAL(15,2)
        assertRoundTrip("-230.77");                 // no CHECK constraint: DDL accepts sign
    }

    @Test
    @DisplayName("null prices persist and read back as null (xs:decimal minOccurs=0)")
    void nullPricesSupported() {
        SipsaParcial stored = insertAndReload(null);
        assertThat(stored.getPromedioKg()).isNull();
        assertThat(stored.getMaximoKg()).isNull();
        assertThat(stored.getMinimoKg()).isNull();
    }

    @Test
    @DisplayName("scale > 2 is coerced by NUMERIC(19,2) with explicit half-away-from-zero rounding")
    void scaleCoercionAtDatabaseBoundaryIsPinned() {
        // The parser preserves 123.456 exactly (XmlParsingUtilDecimalTest); the typmod
        // rounds at insert. This pins that behavior so it is documented, not silent.
        assertThat(insertAndReload(new BigDecimal("123.456")).getPromedioKg())
                .isEqualTo(new BigDecimal("123.46"));
        assertThat(insertAndReload(new BigDecimal("123.454")).getPromedioKg())
                .isEqualTo(new BigDecimal("123.45"));
        assertThat(insertAndReload(new BigDecimal("-123.455")).getPromedioKg())
                .as("half-away-from-zero on negatives")
                .isEqualTo(new BigDecimal("-123.46"));
    }

    @Test
    @DisplayName("values with scale < 2 are padded to scale 2 by the column type")
    void lowScalePaddedToTwo() {
        assertThat(insertAndReload(new BigDecimal("1.5")).getPromedioKg())
                .isEqualTo(new BigDecimal("1.50"));
        assertThat(insertAndReload(new BigDecimal("15500")).getPromedioKg())
                .isEqualTo(new BigDecimal("15500.00"));
    }

    @Test
    @DisplayName("Jackson serializes stored prices exactly — scale-2 numbers, unquoted")
    void jsonSerializationExact() throws Exception {
        SipsaParcial stored = insertAndReload(new BigDecimal("22000.00"));
        String json = new ObjectMapper().writeValueAsString(stored.getPromedioKg());
        assertThat(json).isEqualTo("22000.00");
    }

    /** Inserts through the real batchUpsert path and reloads through JPA. */
    private SipsaParcial insertAndReload(BigDecimal price) {
        SipsaParcial entity = entity(price);
        var metrics = repository.batchUpsert(List.of(entity));
        assertThat(metrics.inserted()).isEqualTo(1);
        return repository.findByKeyHashIn(List.of(entity.getKeyHash())).getFirst();
    }

    private void assertRoundTrip(String value) {
        BigDecimal expected = new BigDecimal(value);
        SipsaParcial stored = insertAndReload(expected);
        assertThat(stored.getPromedioKg())
                .as("promedio_kg round-trips exactly for " + value)
                .isEqualTo(expected);
        assertThat(stored.getMaximoKg()).isEqualTo(expected);
        assertThat(stored.getMinimoKg()).isEqualTo(expected);
        assertThat(stored.getPromedioKg().scale()).isEqualTo(2);
    }

    private SipsaParcial entity(BigDecimal price) {
        String muniId = String.format("%05d", ++keySeq);
        Instant fecha = Instant.parse("2026-07-15T05:00:00Z");
        return SipsaParcial.builder()
                .keyHash(ParcialKeyHash.compute(muniId, 10L, 2L, 101L, fecha))
                .muniId(muniId)
                .muniNombre("MUNI " + muniId)
                .fuenId(10L).futiId(2L).idArtiSemana(101L)
                .enmaFecha(fecha)
                .promedioKg(price).maximoKg(price).minimoKg(price)
                .ingestionRunId(runId)
                .build();
    }
}
