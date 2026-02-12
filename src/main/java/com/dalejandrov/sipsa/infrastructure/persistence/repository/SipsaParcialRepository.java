package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * JPA Repository for managing {@link SipsaParcial} entities.
 * <p>
 * Provides data access methods for partial market data by municipality.
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
     * Batch inserts partial market records.
     * <p>
     * Inserts all provided records without deduplication.
     *
     * @param items list of partial market entities to insert
     * @return metrics with counts of inserted and skipped records (skipped always 0)
     */
    @Transactional
    default UpsertMetrics batchUpsert(List<SipsaParcial> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        Instant now = Instant.now();

        /* Set synchronization timestamp */
        for (SipsaParcial item : items) {
            item.setFechaSincronizacion(now);
        }

        int inserted = items.size();
        saveAll(items);
        flush();
        return new UpsertMetrics(inserted, 0);
    }
}
