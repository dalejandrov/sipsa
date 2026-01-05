package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * JPA Repository for managing {@link SipsaParcial} entities.
 * <p>
 * Provides data access methods for partial market data by municipality.
 * Uses hash-based deduplication strategy following Spring Data JPA best practices.
 *
 * @see SipsaParcial
 * @see com.dalejandrov.sipsa.application.ingestion.handler.ParcialIngestionHandler
 */
@Repository
public interface SipsaParcialRepository
        extends JpaRepository<SipsaParcial, Long>, JpaSpecificationExecutor<SipsaParcial> {

    /**
     * Record to track insert/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Finds all records matching the given list of hash keys (bulk query).
     * <p>
     * This method enables efficient existence checking for partial market data.
     * The keyHash is a SHA-256 computed from business keys to ensure
     * idempotent processing and prevent duplicates.
     *
     * @param hashes list of SHA-256 hash keys
     * @return list of existing records
     */
    @Query("SELECT p FROM SipsaParcial p WHERE p.keyHash IN :hashes")
    List<SipsaParcial> findByKeyHashes(@Param("hashes") List<String> hashes);

    /**
     * Batch upserts partial market records using hash-based matching.
     * <p>
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     *
     * @param items list of partial market entities to upsert
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics batchUpsert(List<SipsaParcial> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();
        int skipped = 0;

        /* Deduplicate within batch - keep latest value */
        java.util.Map<String, SipsaParcial> uniqueItems = new java.util.LinkedHashMap<>();
        for (SipsaParcial item : items) {
            /* Put will replace if key exists, keeping the latest value */
            uniqueItems.put(item.getKeyHash(), item);
        }

        /* Bulk query to find all existing records (1 DB query instead of N) */
        List<String> hashes = new java.util.ArrayList<>(uniqueItems.keySet());
        List<SipsaParcial> existingRecords = findByKeyHashes(hashes);

        /* Create map for fast lookup */
        java.util.Map<String, SipsaParcial> existingMap = new java.util.HashMap<>();
        for (SipsaParcial existing : existingRecords) {
            existingMap.put(existing.getKeyHash(), existing);
        }

        /* Process all items */
        List<SipsaParcial> toInsert = new java.util.ArrayList<>();
        for (SipsaParcial item : uniqueItems.values()) {
            SipsaParcial existing = existingMap.get(item.getKeyHash());
            if (existing != null) {
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
