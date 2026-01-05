package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaCiudadRepository;
import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper;
import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaCiudadRecord;
import com.dalejandrov.sipsa.infrastructure.soap.parser.CiudadStaxParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for ingesting city-level pricing data (Promedios Ciudad).
 * <p>
 * This handler processes daily price information collected at city level,
 * including average prices per product across different cities. The data is sourced
 * from the SOAP service method {@code promediosSipsaCiudad}.
 * <p>
 * <b>Processing Strategy:</b>
 * <ul>
 *   <li>Single batch type (no tmpId distinction)</li>
 *   <li>Uses upsert to handle duplicates based on business keys</li>
 *   <li>Processes records in configurable batch sizes for optimal performance</li>
 * </ul>
 * <p>
 * <b>Validation Rules:</b>
 * <ul>
 *   <li>regId (registration ID) must not be null</li>
 *   <li>codProducto (product code) must not be null</li>
 *   <li>fechaCaptura (capture date) must not be null</li>
 * </ul>
 * <p>
 * Records failing validation are logged with detailed rejection reasons
 * including the raw data for debugging purposes.
 *
 * @see SipsaCiudad
 * @see CiudadStaxParser
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CiudadIngestionHandler implements IngestionHandler {

    private final SoapGateway soapGateway;
    private final SipsaCiudadRepository repository;
    private final SipsaIngestionMapper mapper;

    @Value("${sipsa.ingestion.batch-size:2000}")
    private int batchSize;

    @Override
    public String getMethodName() {
        return "promediosSipsaCiudad";
    }

    /**
     * Executes the city pricing data ingestion process.
     * <p>
     * This method:
     * <ol>
     *   <li>Fetches XML data from SOAP gateway</li>
     *   <li>Parses records using StAX parser for memory efficiency</li>
     *   <li>Validates each record (null checks on required fields)</li>
     *   <li>Accumulates records in batches for bulk insert</li>
     *   <li>Persists batches using upsert strategy</li>
     *   <li>Logs rejected records with detailed reasons</li>
     * </ol>
     *
     * @param context the ingestion context for tracking metrics and rejected records
     * @throws Exception if SOAP call fails, parsing fails, or database operations fail
     */
    @Override
    public void execute(IngestionContext context) throws Exception {
        List<SipsaCiudad> batch = new ArrayList<>(batchSize);

        try (InputStream stream = soapGateway.getCiudadData()) {
            CiudadStaxParser parser = new CiudadStaxParser(stream);

            while (parser.hasNext()) {
                SipsaCiudadRecord record = parser.next();
                context.incrementSeen();

                if (record.regId() == null || record.codProducto() == null || record.fechaCaptura() == null) {
                    String rawData = String.format("regId=%s, codProducto=%s, fechaCaptura=%s, ciudad=%s, producto=%s",
                            record.regId(), record.codProducto(), record.fechaCaptura(),
                            record.ciudad(), record.producto());
                    String reason = "Missing required fields: " +
                            (record.regId() == null ? "regId " : "") +
                            (record.codProducto() == null ? "codProducto " : "") +
                            (record.fechaCaptura() == null ? "fechaCaptura " : "");
                    context.addRejectedRecord(rawData, reason.trim());
                    continue;
                }

                batch.add(mapper.toEntity(record, context.getRunId()));

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
     * Persists a batch of city pricing records to the database.
     * <p>
     * Uses upsert logic to handle duplicates gracefully. Records are matched
     * on business keys (regId, codProducto) which correspond to the unique
     * constraint {@code ux_ciudad}. If a record with the same business key exists,
     * it will be skipped; otherwise, a new record is inserted.
     *
     * @param batch the list of entities to persist
     * @param context the ingestion context for metrics tracking
     */
    private void flushBatch(List<SipsaCiudad> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.batchUpsert(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        batch.clear();
    }
}
