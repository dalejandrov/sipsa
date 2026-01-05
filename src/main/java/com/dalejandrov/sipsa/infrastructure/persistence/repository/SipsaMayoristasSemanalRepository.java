package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA Repository for managing {@link SipsaMayoristasSemanal} entities.
 * <p>
 * Provides data access methods for weekly wholesale market pricing data with:
 * <ul>
 *   <li>Dual upsert strategies (tmpId-based and fallback)</li>
 *   <li>JPA Specification support for dynamic filtering</li>
 *   <li>Database-agnostic implementation following Spring Data best practices</li>
 * </ul>
 * <p>
 * <b>Upsert Strategy:</b><br>
 * Records WITH tmpMayoSemId use the temporary ID for matching (more accurate).
 * Records WITHOUT tmpMayoSemId use business keys (artiId, fuenId, fechaIni).
 *
 * @see SipsaMayoristasSemanal
 * @see com.dalejandrov.sipsa.application.ingestion.handler.SemanaIngestionHandler
 */
@Repository
public interface SipsaMayoristasSemanalRepository
        extends JpaRepository<SipsaMayoristasSemanal, Long>, JpaSpecificationExecutor<SipsaMayoristasSemanal> {

    /**
     * Record to track insert/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Finds weekly data by temporary ID (when available from source system).
     *
     * @param tmpMayoSemId temporary weekly ID
     * @return Optional containing the entity if found
     */
    @Query("SELECT s FROM SipsaMayoristasSemanal s WHERE s.tmpMayoSemId = :tmpMayoSemId")
    Optional<SipsaMayoristasSemanal> findByTmpId(@Param("tmpMayoSemId") Long tmpMayoSemId);

    /**
     * Batch upserts records that have temporary IDs.
     * <p>
     * Uses tmpMayoSemId for matching existing records (more accurate).
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     *
     * @param items list of entities with tmpMayoSemId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertTmpBatch(List<SipsaMayoristasSemanal> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();
        List<SipsaMayoristasSemanal> toInsert = new ArrayList<>();
        int skipped = 0;

        /* Track processed tmpIds within this batch to avoid duplicates */
        java.util.Set<Long> processedTmpIds = new java.util.HashSet<>();

        for (SipsaMayoristasSemanal item : items) {
            if (item.getTmpMayoSemId() != null) {
                /* Skip if already processed in this batch */
                if (processedTmpIds.contains(item.getTmpMayoSemId())) {
                    skipped++;
                    continue;
                }

                Optional<SipsaMayoristasSemanal> existing = findByTmpId(item.getTmpMayoSemId());
                if (existing.isPresent()) {
                    /* Record exists - SKIP it (do not update) */
                    skipped++;
                } else {
                    /* Record does not exist - INSERT it */
                    item.setLastUpdated(now);
                    toInsert.add(item);
                }
                processedTmpIds.add(item.getTmpMayoSemId());
            }
        }

        int inserted = toInsert.size();
        if (!toInsert.isEmpty()) {
            saveAll(toInsert);
            flush();
        }
        return new UpsertMetrics(inserted, skipped);
    }

    /**
     * Finds a record by its business keys.
     *
     * @param artiId product ID
     * @param fuenId source/market ID
     * @param fechaIni week start date
     * @return Optional containing the entity if found
     */
    @Query("SELECT s FROM SipsaMayoristasSemanal s WHERE " +
           "s.artiId = :artiId AND s.fuenId = :fuenId AND s.fechaIni = :fechaIni")
    Optional<SipsaMayoristasSemanal> findByBusinessKeys(
            @Param("artiId") Long artiId,
            @Param("fuenId") Long fuenId,
            @Param("fechaIni") Instant fechaIni);

    /**
     * Batch upserts records without temporary IDs (fallback strategy).
     * <p>
     * Uses business keys (artiId, fuenId, fechaIni) for matching.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     *
     * @param items list of entities without tmpMayoSemId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertFallbackBatch(List<SipsaMayoristasSemanal> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();
        int skipped = 0;

        /* Deduplicate within batch - keep latest value */
        java.util.Map<String, SipsaMayoristasSemanal> uniqueItems = new java.util.LinkedHashMap<>();
        for (SipsaMayoristasSemanal item : items) {
            String businessKey = item.getArtiId() + "|" + item.getFuenId() + "|" + item.getFechaIni();
            uniqueItems.put(businessKey, item);
        }

        /* Process each unique item */
        List<SipsaMayoristasSemanal> toInsert = new java.util.ArrayList<>();
        for (SipsaMayoristasSemanal item : uniqueItems.values()) {
            Optional<SipsaMayoristasSemanal> existing = findByBusinessKeys(
                    item.getArtiId(),
                    item.getFuenId(),
                    item.getFechaIni()
            );

            if (existing.isPresent()) {
                /* Record exists - SKIP it (do not update) */
                skipped++;
            } else {
                /* Record does not exist - INSERT it */
                item.setLastUpdated(now);
                toInsert.add(item);
            }
        }

        int inserted = toInsert.size();
        if (!toInsert.isEmpty()) {
            saveAll(toInsert);
            flush();
        }
        return new UpsertMetrics(inserted, skipped);
    }
}
