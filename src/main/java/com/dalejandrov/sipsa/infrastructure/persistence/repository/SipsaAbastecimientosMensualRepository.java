package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaAbastecimientosMensual;
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
 * JPA Repository for managing {@link SipsaAbastecimientosMensual} entities.
 * <p>
 * Provides data access methods for monthly supply data with dual upsert
 * strategies following Spring Data JPA best practices.
 *
 * @see SipsaAbastecimientosMensual
 * @see com.dalejandrov.sipsa.application.ingestion.handler.AbasIngestionHandler
 */
@Repository
public interface SipsaAbastecimientosMensualRepository extends JpaRepository<SipsaAbastecimientosMensual, Long>,
        JpaSpecificationExecutor<SipsaAbastecimientosMensual> {

    /**
     * Record to track insert/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Finds a record by its temporary ID.
     *
     * @param tmpAbasMesId the temporary monthly supply ID
     * @return Optional containing the entity if found
     */
    @Query("SELECT a FROM SipsaAbastecimientosMensual a WHERE a.tmpAbasMesId = :tmpAbasMesId")
    Optional<SipsaAbastecimientosMensual> findByTmpId(@Param("tmpAbasMesId") Long tmpAbasMesId);

    /**
     * Batch upserts records with temporary IDs.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     *
     * @param items list of entities with tmpAbasMesId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertTmpBatch(List<SipsaAbastecimientosMensual> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();
        List<SipsaAbastecimientosMensual> toInsert = new ArrayList<>();
        int skipped = 0;

        /* Track processed tmpIds within this batch to avoid duplicates */
        java.util.Set<Long> processedTmpIds = new java.util.HashSet<>();

        for (SipsaAbastecimientosMensual item : items) {
            if (item.getTmpAbasMesId() != null) {
                /* Skip if already processed in this batch */
                if (processedTmpIds.contains(item.getTmpAbasMesId())) {
                    skipped++;
                    continue;
                }

                Optional<SipsaAbastecimientosMensual> existing = findByTmpId(item.getTmpAbasMesId());
                if (existing.isPresent()) {
                    /* Record exists - SKIP it (do not update) */
                    skipped++;
                } else {
                    /* Record does not exist - INSERT it */
                    item.setFechaIngestion(now);
                    toInsert.add(item);
                }
                processedTmpIds.add(item.getTmpAbasMesId());
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
    @Query("SELECT a FROM SipsaAbastecimientosMensual a WHERE " +
           "a.artiId = :artiId AND a.fuenId = :fuenId AND a.fechaMesIni = :fechaMesIni")
    Optional<SipsaAbastecimientosMensual> findByBusinessKeys(
            @Param("artiId") Long artiId,
            @Param("fuenId") Long fuenId,
            @Param("fechaMesIni") Instant fechaMesIni);

    /**
     * Batch upserts records without temporary IDs (fallback strategy).
     * <p>
     * Uses business keys (artiId, fuenId, fechaMesIni) for matching.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     *
     * @param items list of entities without tmpAbasMesId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertFallbackBatch(List<SipsaAbastecimientosMensual> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();
        int skipped = 0;

        /* Deduplicate within batch - keep latest value */
        java.util.Map<String, SipsaAbastecimientosMensual> uniqueItems = new java.util.LinkedHashMap<>();
        for (SipsaAbastecimientosMensual item : items) {
            String businessKey = item.getArtiId() + "|" + item.getFuenId() + "|" + item.getFechaMesIni();
            uniqueItems.put(businessKey, item);
        }

        /* Process each unique item */
        List<SipsaAbastecimientosMensual> toInsert = new java.util.ArrayList<>();
        for (SipsaAbastecimientosMensual item : uniqueItems.values()) {
            Optional<SipsaAbastecimientosMensual> existing = findByBusinessKeys(
                    item.getArtiId(),
                    item.getFuenId(),
                    item.getFechaMesIni()
            );

            if (existing.isPresent()) {
                /* Record exists - SKIP it (do not update) */
                skipped++;
            } else {
                /* Record does not exist - INSERT it */
                item.setFechaIngestion(now);
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
