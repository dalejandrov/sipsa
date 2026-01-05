package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
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
 * JPA Repository for managing {@link SipsaMayoristasMensual} entities.
 * <p>
 * Provides data access methods for monthly wholesale market pricing data with
 * dual upsert strategies following Spring Data JPA best practices.
 *
 * @see SipsaMayoristasMensual
 * @see com.dalejandrov.sipsa.application.ingestion.handler.MesIngestionHandler
 */
@Repository
public interface SipsaMayoristasMensualRepository
        extends JpaRepository<SipsaMayoristasMensual, Long>, JpaSpecificationExecutor<SipsaMayoristasMensual> {

    /**
     * Record to track insert/update/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Finds a record by its temporary ID.
     *
     * @param tmpMayoMesId the temporary monthly ID
     * @return Optional containing the entity if found
     */
    @Query("SELECT m FROM SipsaMayoristasMensual m WHERE m.tmpMayoMesId = :tmpMayoMesId")
    Optional<SipsaMayoristasMensual> findByTmpId(@Param("tmpMayoMesId") Long tmpMayoMesId);

    /**
     * Batch upserts records with temporary IDs.
     * <p>
     * Uses tmpMayoMesId for matching existing records.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     *
     * @param items list of entities with tmpMayoMesId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertTmpBatch(List<SipsaMayoristasMensual> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();
        List<SipsaMayoristasMensual> toInsert = new ArrayList<>();
        int skipped = 0;

        /* Track processed tmpIds within this batch to avoid duplicates */
        java.util.Set<Long> processedTmpIds = new java.util.HashSet<>();

        for (SipsaMayoristasMensual item : items) {
            if (item.getTmpMayoMesId() != null) {
                /* Skip if already processed in this batch */
                if (processedTmpIds.contains(item.getTmpMayoMesId())) {
                    skipped++;
                    continue;
                }

                Optional<SipsaMayoristasMensual> existing = findByTmpId(item.getTmpMayoMesId());
                if (existing.isPresent()) {
                    /* Record exists - SKIP it (do not update) */
                    skipped++;
                } else {
                    /* Record does not exist - INSERT it */
                    item.setLastUpdated(now);
                    toInsert.add(item);
                }
                processedTmpIds.add(item.getTmpMayoMesId());
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
     * @param fechaMesIni month start date
     * @return Optional containing the entity if found
     */
    @Query("SELECT m FROM SipsaMayoristasMensual m WHERE " +
           "m.artiId = :artiId AND m.fuenId = :fuenId AND m.fechaMesIni = :fechaMesIni")
    Optional<SipsaMayoristasMensual> findByBusinessKeys(
            @Param("artiId") Long artiId,
            @Param("fuenId") Long fuenId,
            @Param("fechaMesIni") Instant fechaMesIni);

    /**
     * Batch upserts records without temporary IDs (fallback strategy).
     * <p>
     * Uses business keys (artiId, fuenId, fechaMesIni) for matching.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     *
     * @param items list of entities without tmpMayoMesId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertFallbackBatch(List<SipsaMayoristasMensual> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();
        int skipped = 0;

        /* Deduplicate within batch - keep latest value */
        java.util.Map<String, SipsaMayoristasMensual> uniqueItems = new java.util.LinkedHashMap<>();
        for (SipsaMayoristasMensual item : items) {
            String businessKey = item.getArtiId() + "|" + item.getFuenId() + "|" + item.getFechaMesIni();
            uniqueItems.put(businessKey, item);
        }

        /* Process each unique item */
        List<SipsaMayoristasMensual> toInsert = new java.util.ArrayList<>();
        for (SipsaMayoristasMensual item : uniqueItems.values()) {
            Optional<SipsaMayoristasMensual> existing = findByBusinessKeys(
                    item.getArtiId(),
                    item.getFuenId(),
                    item.getFechaMesIni()
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
