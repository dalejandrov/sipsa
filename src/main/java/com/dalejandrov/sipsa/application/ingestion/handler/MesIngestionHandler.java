package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasMensualRepository;
import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper;
import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaMayoristasMensualRecord;
import com.dalejandrov.sipsa.infrastructure.soap.parser.MesStaxParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for ingesting monthly wholesale market data (Mayoristas Mensual).
 * <p>
 * This handler processes monthly aggregated pricing data from wholesale markets,
 * including minimum, maximum, and average prices per product. The data is sourced
 * from the SOAP service method {@code promediosSipsaMesMadr}.
 * <p>
 * <b>Processing Strategy:</b>
 * <ul>
 *   <li>Records WITH tmpMayoMesId: Use specialized upsert with temporary ID matching</li>
 *   <li>Records WITHOUT tmpMayoMesId: Use fallback upsert based on business keys</li>
 * </ul>
 * <p>
 * <b>Validation Rules:</b>
 * <ul>
 *   <li>artiId (product ID) must not be null</li>
 *   <li>fuenId (source/market ID) must not be null</li>
 *   <li>fechaMesIni (month start date) must not be null</li>
 * </ul>
 *
 * @see SipsaMayoristasMensual
 * @see MesStaxParser
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MesIngestionHandler implements IngestionHandler {

    private final SoapGateway soapGateway;
    private final SipsaMayoristasMensualRepository repository;
    private final SipsaIngestionMapper mapper;

    @Value("${sipsa.ingestion.batch-size:2000}")
    private int batchSize;

    @Override
    public String getMethodName() {
        return "promediosSipsaMesMadr";
    }

    /**
     * Executes the monthly wholesale market data ingestion process.
     *
     * @param context the ingestion context for tracking metrics and rejected records
     * @throws Exception if SOAP call fails, parsing fails, or database operations fail
     */
    @Override
    public void execute(IngestionContext context) throws Exception {
        List<SipsaMayoristasMensual> withTmp = new ArrayList<>(batchSize);
        List<SipsaMayoristasMensual> noTmp = new ArrayList<>(batchSize);

        try (InputStream stream = soapGateway.getMesMadrData()) {
            MesStaxParser parser = new MesStaxParser(stream);

            while (parser.hasNext()) {
                SipsaMayoristasMensualRecord record = parser.next();
                context.incrementSeen();

                if (record.artiId() == null || record.fuenId() == null || record.fechaMesIni() == null) {
                    String rawData = String.format("artiId=%s, fuenId=%s, fechaMesIni=%s, artiNombre=%s",
                            record.artiId(), record.fuenId(), record.fechaMesIni(), record.artiNombre());
                    String reason = "Missing: " +
                            (record.artiId() == null ? "artiId " : "") +
                            (record.fuenId() == null ? "fuenId " : "") +
                            (record.fechaMesIni() == null ? "fechaMesIni" : "");
                    context.addRejectedRecord(rawData, reason.trim());
                    continue;
                }

                SipsaMayoristasMensual entity = mapper.toEntity(record, context.getRunId());

                if (record.tmpMayoMesId() != null) {
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
     * Persists records with temporary IDs using specialized upsert.
     *
     * @param batch the list of entities with tmpMayoMesId values
     * @param context the ingestion context for metrics tracking
     */
    private void flushTmp(List<SipsaMayoristasMensual> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.upsertTmpBatch(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        // Skipped records are not counted - they already exist
        batch.clear();
    }

    /**
     * Persists records without temporary IDs using fallback upsert.
     *
     * @param batch the list of entities without tmpMayoMesId values
     * @param context the ingestion context for metrics tracking
     */
    private void flushNoTmp(List<SipsaMayoristasMensual> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.upsertFallbackBatch(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        // Skipped records are not counted - they already exist
        batch.clear();
    }
}
