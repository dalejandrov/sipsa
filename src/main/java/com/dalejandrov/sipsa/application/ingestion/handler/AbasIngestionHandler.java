package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.domain.entity.SipsaAbastecimientosMensual;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaAbastecimientosMensualRepository;
import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper;
import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaAbasRecord;
import com.dalejandrov.sipsa.infrastructure.soap.parser.AbasStaxParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for ingesting monthly supply data (Abastecimientos Mensuales).
 * <p>
 * This handler processes supply volume data for wholesale markets on a monthly basis,
 * including quantities in tons delivered to each market source. The data is sourced
 * from the SOAP service method {@code promedioAbasSipsaMesMadr}.
 * <p>
 * <b>Processing Strategy:</b>
 * <ul>
 *   <li>Records are split into two categories based on {@code tmpAbasMesId} presence</li>
 *   <li>Records WITH tmpId: Use specialized upsert with temporary ID matching</li>
 *   <li>Records WITHOUT tmpId: Use fallback upsert based on business keys</li>
 * </ul>
 * <p>
 * <b>Validation Rules:</b>
 * <ul>
 *   <li>artiId (product ID) must not be null</li>
 *   <li>fuenId (source/market ID) must not be null</li>
 *   <li>fechaMes (month date) must not be null</li>
 * </ul>
 * <p>
 * Records failing validation are logged with detailed rejection reasons
 * for later analysis and reprocessing.
 *
 * @see SipsaAbastecimientosMensual
 * @see AbasStaxParser
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AbasIngestionHandler implements IngestionHandler {

    private final SoapGateway soapGateway;
    private final SipsaAbastecimientosMensualRepository repository;
    private final SipsaIngestionMapper mapper;

    @Value("${sipsa.ingestion.batch-size:2000}")
    private int batchSize;

    @Override
    public String getMethodName() {
        return "promedioAbasSipsaMesMadr";
    }

    /**
     * Executes the monthly supply data ingestion process.
     * <p>
     * This method:
     * <ol>
     *   <li>Fetches XML data from SOAP gateway</li>
     *   <li>Parses records using StAX parser for memory efficiency</li>
     *   <li>Validates each record (null checks on required fields)</li>
     *   <li>Separates records into two batches (with/without tmpId)</li>
     *   <li>Persists batches using appropriate upsert strategies</li>
     *   <li>Logs rejected records with detailed reasons</li>
     * </ol>
     *
     * @param context the ingestion context for tracking metrics and rejected records
     * @throws Exception if SOAP call fails, parsing fails, or database operations fail
     */
    @Override
    public void execute(IngestionContext context) throws Exception {
        List<SipsaAbastecimientosMensual> withTmp = new ArrayList<>(batchSize);
        List<SipsaAbastecimientosMensual> noTmp = new ArrayList<>(batchSize);

        try (InputStream stream = soapGateway.getAbastecimientosMensualData()) {
            AbasStaxParser parser = new AbasStaxParser(stream);

            while (parser.hasNext()) {
                SipsaAbasRecord record = parser.next();
                context.incrementSeen();

                if (record.artiId() == null || record.fuenId() == null || record.fechaMes() == null) {
                    String rawData = String.format("artiId=%s, fuenId=%s, fechaMes=%s, artiNombre=%s",
                            record.artiId(), record.fuenId(), record.fechaMes(), record.artiNombre());
                    String reason = "Missing: " +
                            (record.artiId() == null ? "artiId " : "") +
                            (record.fuenId() == null ? "fuenId " : "") +
                            (record.fechaMes() == null ? "fechaMes" : "");
                    context.addRejectedRecord(rawData, reason.trim());
                    continue;
                }

                SipsaAbastecimientosMensual entity = mapper.toEntity(record, context.getRunId());

                if (record.tmpAbasMesId() != null) {
                    withTmp.add(entity);
                } else {
                    noTmp.add(entity);
                }

                if (withTmp.size() >= batchSize)
                    flushTmp(withTmp, context);
                if (noTmp.size() >= batchSize)
                    flushNoTmp(noTmp, context);
            }
            // Final flush of remaining records
            flushTmp(withTmp, context);
            flushNoTmp(noTmp, context);

            // Log completion with method name and total records
            log.info("SOAP method '{}' completed successfully. Total records obtained: {}, Rejected: {}",
                     getMethodName(), context.getRecordsSeen(), context.getRejectCount());
        } catch (Exception e) {
            // ...existing code...
            log.warn("Error during ingestion, attempting to save partial progress. Processed {} records so far",
                    context.getRecordsSeen());
            try {
                flushTmp(withTmp, context);
                flushNoTmp(noTmp, context);
                log.info("Successfully saved {} records before failure", context.getRecordsInserted());
            } catch (Exception flushEx) {
                log.error("Failed to save partial progress: {}", flushEx.getMessage());
            }
            throw e;
        }
    }

    /**
     * Persists a batch of records that have temporary IDs.
     * <p>
     * Uses the specialized upsert query that matches on {@code tmpAbasMesId}
     * for more accurate duplicate detection and updates.
     *
     * @param batch the list of entities with tmpAbasMesId values
     * @param context the ingestion context for metrics tracking
     */
    private void flushTmp(List<SipsaAbastecimientosMensual> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.upsertTmpBatch(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        batch.clear();
    }

    /**
     * Persists a batch of records without temporary IDs.
     * <p>
     * Uses the fallback upsert query that matches on business keys
     * (artiId, fuenId, fechaMesIni) for duplicate detection.
     *
     * @param batch the list of entities without tmpAbasMesId values
     * @param context the ingestion context for metrics tracking
     */
    private void flushNoTmp(List<SipsaAbastecimientosMensual> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.upsertFallbackBatch(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        batch.clear();
    }
}
