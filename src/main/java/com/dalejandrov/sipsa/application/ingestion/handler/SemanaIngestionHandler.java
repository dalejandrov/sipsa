package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import com.dalejandrov.sipsa.infrastructure.persistence.repository.SipsaMayoristasSemanalRepository;
import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.SipsaIngestionMapper;
import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaSemanaRecord;
import com.dalejandrov.sipsa.infrastructure.soap.parser.SemanaStaxParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for ingesting weekly wholesale market data (Mayoristas Semanal).
 * <p>
 * This handler processes weekly aggregated pricing data from wholesale markets,
 * including minimum, maximum, and average prices per product. The data is sourced
 * from the SOAP service method {@code promediosSipsaSemanaMadr}.
 * <p>
 * <b>Processing Strategy:</b>
 * <ul>
 *   <li>Records WITH tmpMayoSemId: Use specialized upsert with temporary ID matching</li>
 *   <li>Records WITHOUT tmpMayoSemId: Use fallback upsert based on business keys</li>
 * </ul>
 * <p>
 * <b>Validation Rules:</b>
 * <ul>
 *   <li>artiId (product ID) must not be null</li>
 *   <li>fuenId (source/market ID) must not be null</li>
 *   <li>fechaIni (week start date) must not be null</li>
 * </ul>
 *
 * @see SipsaMayoristasSemanal
 * @see SemanaStaxParser
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SemanaIngestionHandler implements IngestionHandler {

    private final SoapGateway soapGateway;
    private final SipsaMayoristasSemanalRepository repository;
    private final SipsaIngestionMapper mapper;

    @Value("${sipsa.ingestion.batch-size:2000}")
    private int batchSize;

    @Override
    public String getMethodName() {
        return "promediosSipsaSemanaMadr";
    }

    @Override
    public void execute(IngestionContext context) throws Exception {
        List<SipsaMayoristasSemanal> withTmp = new ArrayList<>(batchSize);
        List<SipsaMayoristasSemanal> noTmp = new ArrayList<>(batchSize);

        try (InputStream stream = soapGateway.getSemanaMadrData()) {
            SemanaStaxParser parser = new SemanaStaxParser(stream);

            while (parser.hasNext()) {
                SipsaSemanaRecord record = parser.next();
                context.incrementSeen();

                if (record.artiId() == null || record.fuenId() == null || record.fechaIni() == null) {
                    String rawData = String.format("artiId=%s, fuenId=%s, fechaIni=%s, artiNombre=%s",
                            record.artiId(), record.fuenId(), record.fechaIni(), record.artiNombre());
                    String reason = "Missing: " +
                            (record.artiId() == null ? "artiId " : "") +
                            (record.fuenId() == null ? "fuenId " : "") +
                            (record.fechaIni() == null ? "fechaIni" : "");
                    context.addRejectedRecord(rawData, reason.trim());
                    continue;
                }

                SipsaMayoristasSemanal entity = mapper.toEntity(record, context.getRunId());

                if (record.tmpMayoSemId() != null) {
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
     * @param batch the list of entities with tmpMayoSemId values
     * @param context the ingestion context for metrics tracking
     */
    private void flushTmp(List<SipsaMayoristasSemanal> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.upsertTmpBatch(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        batch.clear();
    }

    /**
     * Persists records without temporary IDs using fallback upsert.
     *
     * @param batch the list of entities without tmpMayoSemId values
     * @param context the ingestion context for metrics tracking
     */
    private void flushNoTmp(List<SipsaMayoristasSemanal> batch, IngestionContext context) {
        if (batch.isEmpty())
            return;
        var metrics = repository.upsertFallbackBatch(batch);
        for (int i = 0; i < metrics.inserted(); i++) {
            context.incrementInserted();
        }
        batch.clear();
    }
}
