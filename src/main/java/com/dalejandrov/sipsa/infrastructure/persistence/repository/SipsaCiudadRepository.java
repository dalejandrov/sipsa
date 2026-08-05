package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA Repository for managing {@link SipsaCiudad} entities.
 * <p>
 * Provides data access methods for city-level pricing data, including:
 * <ul>
 *   <li>An atomic {@code INSERT ... ON CONFLICT DO NOTHING} upsert</li>
 *   <li>JPA Specification support for dynamic filtering</li>
 *   <li>Standard CRUD operations</li>
 * </ul>
 * <p>
 * <b>Upsert Strategy:</b><br>
 * The business key is {@code (regId, codProducto)}, backed by the {@code ux_ciudad}
 * unique constraint. A record whose key already exists is skipped — never updated —
 * matching the skip-first semantics of the other data types (see
 * {@link #batchUpsert(List)}, same technique as {@code SipsaParcialRepository.batchUpsert}
 * TECH-117).
 *
 * @see SipsaCiudad
 * @see com.dalejandrov.sipsa.application.ingestion.handler.CiudadIngestionHandler
 */
@Repository
public interface SipsaCiudadRepository extends JpaRepository<SipsaCiudad, Long>,
        JpaSpecificationExecutor<SipsaCiudad>, SipsaCiudadBatchInsertRepository {

    /**
     * Record to track insert/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Batch upsert with skip-first deduplication (same technique as
     * {@code SipsaParcialRepository.batchUpsert}, TECH-117).
     * <ol>
     *   <li>Intra-batch dedupe by {@code (regId, codProducto)} ({@code LinkedHashMap},
     *       last occurrence wins — duplicates are silently uncounted, matching the prior
     *       implementation).</li>
     *   <li>Inserts the deduplicated candidates atomically via
     *       {@code INSERT … ON CONFLICT (reg_id, cod_producto) DO NOTHING} in a single
     *       JDBC batch ({@link SipsaCiudadBatchInsertRepository}) — no bulk existence
     *       lookup needed, the database itself resolves "already exists" at insert time.</li>
     * </ol>
     * <b>Concurrency:</b> the previous bulk-lookup-then-{@code saveAll} sequence had the
     * same TOCTOU gap {@code SipsaParcialRepository.batchUpsert} had before TECH-117 — a
     * concurrent writer could insert the same key between the lookup and the write,
     * surfacing as a unique-violation exception that discarded the whole batch. The atomic
     * {@code ON CONFLICT} clause removes that gap entirely: the losing side's conflicting
     * rows resolve to "not inserted" (counted as {@code skipped}) with no exception and no
     * effect on its non-conflicting rows.
     *
     * @param items list of city pricing entities to upsert
     * @return metrics with counts of inserted and skipped records; for every batch
     *         {@code inserted + skipped} equals the number of *unique* keys in the batch,
     *         not {@code items.size()}
     */
    @Transactional
    default UpsertMetrics batchUpsert(List<SipsaCiudad> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        /* Intra-batch dedupe: same business key twice in one batch -> last wins. */
        Map<String, SipsaCiudad> uniqueItems = new LinkedHashMap<>();
        for (SipsaCiudad item : items) {
            String businessKey = item.getRegId() + "|" + item.getCodProducto();
            uniqueItems.put(businessKey, item);
        }

        Instant now = Instant.now();
        List<SipsaCiudad> candidates = new ArrayList<>(uniqueItems.values());
        for (SipsaCiudad candidate : candidates) {
            candidate.setFechaSincronizacion(now);
        }

        int inserted = 0;
        int skipped = 0;
        for (int outcome : insertIgnoringConflicts(candidates)) {
            if (outcome > 0) {
                inserted++;
            } else {
                skipped++;
            }
        }
        return new UpsertMetrics(inserted, skipped);
    }
}
