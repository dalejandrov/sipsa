package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaParcialRepository;
import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper;
import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaParcialRecord;
import com.dalejandrov.sipsa.infrastructure.soap.parser.ParcialStaxParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for ingesting partial market data by municipality (Parcial).
 * <p>
 * This handler processes detailed market information at municipality level,
 * including price ranges, product availability, and market source details.
 * The data is sourced from the SOAP service method {@code promediosParcialSipsa}.
 * <p>
 * <b>Processing Strategy:</b>
 * <ul>
 *   <li>Generates a unique hash key for each record to prevent duplicates</li>
 *   <li>Hash is computed from muniId, fuenId, futiId, idArtiSemana, and enmaFecha</li>
 *   <li>Uses upsert based on the hash key for idempotent processing</li>
 * </ul>
 * <p>
 * <b>Validation Rules:</b>
 * <ul>
 *   <li>muniId (municipality ID) must not be null</li>
 *   <li>fuenId (source/market ID) must not be null</li>
 *   <li>futiId (source type ID) must not be null</li>
 *   <li>idArtiSemana (weekly article ID) must not be null</li>
 *   <li>enmaFecha (survey date) must not be null</li>
 * </ul>
 * <p>
 * The hash-based deduplication ensures that even if the same data is ingested
 * multiple times, only one record will exist in the database.
 *
 * @see SipsaParcial
 * @see ParcialStaxParser
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParcialIngestionHandler implements IngestionHandler {

    private final SoapGateway soapGateway;
    private final SipsaParcialRepository repository;
    private final SipsaIngestionMapper mapper;

    @Value("${sipsa.ingestion.batch-size:2000}")
    private int batchSize;

    @Override
    public String getMethodName() {
        return "promediosSipsaParcial";
    }

    @Override
    public void execute(IngestionContext context) throws Exception {
        List<SipsaParcial> batch = new ArrayList<>(batchSize);

        try (InputStream stream = soapGateway.getParcialData()) {
            ParcialStaxParser parser = new ParcialStaxParser(stream);

            while (parser.hasNext()) {
                SipsaParcialRecord record = parser.next();
                context.incrementSeen();

                if (record.muniId() == null || record.fuenId() == null || record.futiId() == null
                        || record.idArtiSemana() == null || record.enmaFecha() == null) {
                    String rawData = String.format("muniId=%s, fuenId=%s, futiId=%s, idArtiSemana=%s, enmaFecha=%s, muniNombre=%s, artiNombre=%s",
                            record.muniId(), record.fuenId(), record.futiId(), record.idArtiSemana(),
                            record.enmaFecha(), record.muniNombre(), record.artiNombre());
                    String reason = "Missing required fields: " +
                            (record.muniId() == null ? "muniId " : "") +
                            (record.fuenId() == null ? "fuenId " : "") +
                            (record.futiId() == null ? "futiId " : "") +
                            (record.idArtiSemana() == null ? "idArtiSemana " : "") +
                            (record.enmaFecha() == null ? "enmaFecha " : "");
                    context.addRejectedRecord(rawData, reason.trim());
                    continue;
                }

                String rawKey = record.muniId() + "|" + record.fuenId() + "|" + record.futiId() + "|" +
                        record.idArtiSemana() + "|" + record.enmaFecha() + "|"
                        + (record.artiNombre() != null ? record.artiNombre() : "");
                String hash = sha256(rawKey);

                Instant fechaEncuesta = parseDate(record.fechaEncuestaText());

                batch.add(mapper.toEntity(record, hash, fechaEncuesta, context.getRunId()));

                if (batch.size() >= batchSize) {
                    flushBatch(batch, context);
                }
            }
            // Final flush of remaining records
            flushBatch(batch, context);

            // Log completion with method name and total records
            log.info("SOAP method '{}' completed successfully. Total records obtained: {}, Rejected: {}",
                     getMethodName(), context.getRecordsSeen(), context.getRejectCount());
        } catch (Exception e) {
            // ...existing code...
            log.warn("Error during ingestion, attempting to save partial progress. Processed {} records so far",
                    context.getRecordsSeen());
            try {
                flushBatch(batch, context);
                log.info("Successfully saved {} records before failure", context.getRecordsInserted());
            } catch (Exception flushEx) {
                log.error("Failed to save partial progress: {}", flushEx.getMessage());
            }
            throw e;
        }
    }

    /**
     * Persists a batch of partial market records to the database.
     * <p>
     * Uses hash-based upsert logic. If a record exists, it is skipped;
     * otherwise, it is inserted as new.
     *
     * @param batch the list of entities to persist
     * @param context the ingestion context for metrics tracking
     */
    private void flushBatch(List<SipsaParcial> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.batchUpsert(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        batch.clear();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new SipsaIngestionException("SHA-256 hashing failed", e);
        }
    }

    private Instant parseDate(String dateStr) {
        if (dateStr == null)
            return null;
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}
