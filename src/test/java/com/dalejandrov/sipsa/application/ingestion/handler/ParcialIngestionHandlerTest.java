package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.domain.entity.RequestSource;
import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaParcialRepository;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Idempotency tests for {@link ParcialIngestionHandler} (TECH-011 acceptance criteria):
 * two consecutive executions of the same DANE payload must insert everything once and
 * skip everything the second time — including against legacy rows whose {@code key_hash}
 * is a pre-fix random UUID. Uses the real StAX parser and the real MapStruct mapper; the
 * repository is an in-memory fake wired through Mockito that executes the REAL
 * {@code batchUpsert} default method.
 */
@DisplayName("ParcialIngestionHandler — deduplication across runs")
class ParcialIngestionHandlerTest {

    private static final String XML_TWO_RECORDS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
              <return>
                <muniId>05001</muniId>
                <muniNombre>Medellin</muniNombre>
                <fuenId>10</fuenId>
                <futiId>2</futiId>
                <idArtiSemana>101</idArtiSemana>
                <enmaFecha>2026-07-15T05:00:00Z</enmaFecha>
                <promedioKg>1500.50</promedioKg>
              </return>
              <return>
                <muniId>05002</muniId>
                <muniNombre>Envigado</muniNombre>
                <fuenId>10</fuenId>
                <futiId>2</futiId>
                <idArtiSemana>102</idArtiSemana>
                <enmaFecha>2026-07-15T05:00:00Z</enmaFecha>
                <promedioKg>1200.00</promedioKg>
              </return>
            </response>
            """;

    /** Same first record, plus one with an unparseable zoneless date (must be rejected). */
    private static final String XML_BAD_DATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
              <return>
                <muniId>05001</muniId>
                <fuenId>10</fuenId>
                <futiId>2</futiId>
                <idArtiSemana>101</idArtiSemana>
                <enmaFecha>2026-07-15T00:00:00</enmaFecha>
                <promedioKg>1500.50</promedioKg>
              </return>
            </response>
            """;

    private SoapGateway soapGateway;
    private SipsaParcialRepository repository;
    private IngestionProperties properties;
    private ParcialIngestionHandler handler;

    /** In-memory store simulating the database across executions, keyed by primary key. */
    private final Map<Long, SipsaParcial> store = new LinkedHashMap<>();
    private long idSequence = 0;

    @BeforeEach
    void setUp() {
        soapGateway = mock(SoapGateway.class);
        repository = mock(SipsaParcialRepository.class);
        SipsaIngestionMapper mapper = new SipsaIngestionMapperImpl();

        when(repository.batchUpsert(any())).thenCallRealMethod();
        when(repository.findByKeyHashIn(anyCollection())).thenAnswer(inv -> {
            Collection<String> hashes = inv.getArgument(0);
            List<SipsaParcial> found = new ArrayList<>();
            for (SipsaParcial row : store.values()) {
                if (hashes.contains(row.getKeyHash())) {
                    found.add(row);
                }
            }
            return found;
        });
        when(repository.findByEnmaFechaIn(anyCollection())).thenAnswer(inv -> {
            Collection<Instant> fechas = inv.getArgument(0);
            List<SipsaParcial> found = new ArrayList<>();
            for (SipsaParcial row : store.values()) {
                if (fechas.contains(row.getEnmaFecha())) {
                    found.add(row);
                }
            }
            return found;
        });
        when(repository.saveAll(any())).thenAnswer(inv -> {
            Iterable<SipsaParcial> rows = inv.getArgument(0);
            List<SipsaParcial> saved = new ArrayList<>();
            for (SipsaParcial row : rows) {
                row.setId(++idSequence);
                store.put(row.getId(), row);
                saved.add(row);
            }
            return saved;
        });
        // flush() is void: the Mockito mock is a no-op by default, which is what we want.

        // Plain POJO with the canonical default (500) — no Spring context needed.
        properties = new IngestionProperties();
        handler = new ParcialIngestionHandler(soapGateway, repository, mapper, properties);
    }

    private static InputStream xml(String payload) {
        return new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static IngestionContext context(long runId) {
        return new IngestionContext(runId, "promediosSipsaParcial", "2026-07-16", "req-" + runId,
                RequestSource.MANUAL);
    }

    @Test
    @DisplayName("First run inserts every record; identical second run skips them all")
    void secondRunIsIdempotent() throws Exception {
        when(soapGateway.getParcialData()).thenReturn(xml(XML_TWO_RECORDS));
        IngestionContext first = context(1);
        handler.execute(first);

        assertThat(first.getRecordsInserted()).isEqualTo(2);
        assertThat(first.getRecordsSkipped()).isZero();
        assertThat(store).hasSize(2);

        when(soapGateway.getParcialData()).thenReturn(xml(XML_TWO_RECORDS));
        IngestionContext second = context(2);
        handler.execute(second);

        assertThat(second.getRecordsInserted()).isZero();
        assertThat(second.getRecordsSkipped()).isEqualTo(2);
        assertThat(store).as("no new rows on re-ingestion of the same publication").hasSize(2);
    }

    @Test
    @DisplayName("Legacy rows with random-UUID key_hash are deduplicated via the natural-key lookup")
    void legacyUuidRowsAreDeduplicated() throws Exception {
        SipsaParcial legacy = SipsaParcial.builder()
                .id(++idSequence)
                .keyHash("7d0e8400-e29b-41d4-a716-446655440000")
                .muniId("05001").fuenId(10L).futiId(2L).idArtiSemana(101L)
                .enmaFecha(Instant.parse("2026-07-15T05:00:00Z"))
                .ingestionRunId(99L)
                .build();
        store.put(legacy.getId(), legacy);

        when(soapGateway.getParcialData()).thenReturn(xml(XML_TWO_RECORDS));
        IngestionContext context = context(3);
        handler.execute(context);

        assertThat(context.getRecordsSkipped())
                .as("the record matching the legacy row must be skipped")
                .isEqualTo(1);
        assertThat(context.getRecordsInserted())
                .as("only the genuinely new record is inserted")
                .isEqualTo(1);
        assertThat(store).hasSize(2);
    }

    @Test
    @DisplayName("The configured batch size drives flush cadence — no internal fallback to another size")
    void configuredBatchSizeDrivesFlushCadence() throws Exception {
        List<Integer> flushSizes = new ArrayList<>();
        when(repository.batchUpsert(any())).thenAnswer(inv -> {
            flushSizes.add(inv.<List<SipsaParcial>>getArgument(0).size());
            return inv.callRealMethod();
        });
        properties.setBatchSize(1);

        when(soapGateway.getParcialData()).thenReturn(xml(XML_TWO_RECORDS));
        handler.execute(context(5));

        assertThat(flushSizes)
                .as("each batch handed to the repository matches the configured size")
                .containsExactly(1, 1);
    }

    @Test
    @DisplayName("A zoneless xs:dateTime is rejected explicitly, never persisted with an implicit zone")
    void zonelessDateIsRejected() throws Exception {
        when(soapGateway.getParcialData()).thenReturn(xml(XML_BAD_DATE));
        IngestionContext context = context(4);
        handler.execute(context);

        assertThat(context.getRecordsInserted()).isZero();
        assertThat(context.getRejectCount()).isEqualTo(1);
        assertThat(context.getRejectedRecords())
                .singleElement()
                .satisfies(r -> assertThat(r.reason()).contains("enmaFecha"));
        assertThat(store).isEmpty();
    }
}
