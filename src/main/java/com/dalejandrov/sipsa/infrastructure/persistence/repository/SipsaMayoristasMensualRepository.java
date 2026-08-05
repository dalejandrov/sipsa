package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasMensual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA Repository for managing {@link SipsaMayoristasMensual} entities.
 * <p>
 * Provides data access methods for monthly wholesale market pricing data with dual atomic
 * {@code INSERT ... ON CONFLICT DO NOTHING} upsert strategies (same technique as
 * {@code SipsaMayoristasSemanalRepository}, TECH-060/TECH-117 lineage).
 *
 * @see SipsaMayoristasMensual
 * @see com.dalejandrov.sipsa.application.ingestion.handler.MesIngestionHandler
 */
@Repository
public interface SipsaMayoristasMensualRepository
        extends JpaRepository<SipsaMayoristasMensual, Long>, JpaSpecificationExecutor<SipsaMayoristasMensual>,
        SipsaMayoristasMensualTmpBatchInsertRepository, SipsaMayoristasMensualBatchInsertRepository {

    /**
     * Record to track insert/update/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Batch upserts records with temporary IDs.
     * <p>
     * Uses tmpMayoMesId for matching existing records.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     * <p>
     * Previously issued one {@code findByTmpId} SELECT per (deduplicated) item — N+1
     * round trips per batch. Now: in-batch dedup by {@code tmpMayoMesId} stays in-memory
     * (unchanged: last occurrence wins), then the deduplicated rows are sent in a single
     * {@code INSERT … ON CONFLICT (tmp_mayo_mes_id) DO NOTHING} JDBC batch
     * ({@link #insertIgnoringTmpConflicts}) — one round trip total, replacing both the
     * per-row existence check and the separate {@code saveAll}/{@code flush}.
     * <p>
     * <b>Concurrency:</b> the previous per-row {@code findByTmpId}-then-{@code saveAll}
     * sequence had the same TOCTOU gap {@code SipsaParcialRepository.batchUpsert} had
     * before TECH-117 — a concurrent writer could insert the same tmpId between the check
     * and the write, surfacing as a unique-violation exception that discarded the whole
     * batch. The atomic {@code ON CONFLICT} clause removes that gap entirely: the losing
     * side's conflicting rows resolve to "not inserted" (counted as {@code skipped}) with
     * no exception and no effect on its non-conflicting rows.
     *
     * @param items list of entities with tmpMayoMesId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertTmpBatch(List<SipsaMayoristasMensual> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        /* Deduplicate within batch by tmpMayoMesId - keep latest value (unchanged semantics). */
        Map<Long, SipsaMayoristasMensual> uniqueItems = new LinkedHashMap<>();
        for (SipsaMayoristasMensual item : items) {
            if (item.getTmpMayoMesId() != null) {
                uniqueItems.put(item.getTmpMayoMesId(), item);
            }
        }

        Instant now = Instant.now();
        List<SipsaMayoristasMensual> candidates = new ArrayList<>(uniqueItems.values());
        for (SipsaMayoristasMensual candidate : candidates) {
            candidate.setFechaSincronizacion(now);
        }

        int inserted = 0;
        int skipped = 0;
        for (int outcome : insertIgnoringTmpConflicts(candidates)) {
            if (outcome > 0) {
                inserted++;
            } else {
                skipped++;
            }
        }
        return new UpsertMetrics(inserted, skipped);
    }

    /**
     * Business key for the fallback upsert path: {@code (artiId, fuenId, fechaMesIni)},
     * backed by the {@code ux_mes_fallback} unique constraint (V1). Used only as an
     * in-memory {@code Map} key for intra-batch deduplication.
     */
    record BusinessKey(Long artiId, Long fuenId, LocalDate fechaMesIni) {}

    /**
     * Batch upserts records without temporary IDs (fallback strategy).
     * <p>
     * Uses business keys (artiId, fuenId, fechaMesIni) for matching.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     * <p>
     * Previously issued one {@code findByBusinessKeys} SELECT per (deduplicated) item —
     * N+1 round trips per batch. Now: in-batch dedup stays in-memory (unchanged: last
     * occurrence per key wins), then the deduplicated rows are sent in a single
     * {@code INSERT … ON CONFLICT (arti_id, fuen_id, fecha_mes_ini) DO NOTHING} JDBC batch
     * ({@link #insertIgnoringConflicts}) — one round trip total, replacing both the per-row
     * existence check and the separate {@code saveAll}/{@code flush}. A row with any
     * {@code null} key component can never match the conflict target and therefore always
     * inserts, exactly like the removed {@code findByBusinessKeys} lookup always returned
     * empty for a {@code null} parameter.
     * <p>
     * <b>Concurrency:</b> the previous SELECT-then-{@code saveAll} sequence had the same
     * TOCTOU gap as {@code SipsaParcialRepository.batchUpsert} before TECH-117 — a
     * concurrent writer could insert the same key between the check and the write,
     * surfacing as a unique-violation exception that discarded the whole batch. The
     * atomic {@code ON CONFLICT} clause removes that gap entirely: the losing side's
     * conflicting rows resolve to "not inserted" (counted as {@code skipped}) with no
     * exception and no effect on its non-conflicting rows.
     *
     * @param items list of entities without tmpMayoMesId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertFallbackBatch(List<SipsaMayoristasMensual> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        /* Deduplicate within batch - keep latest value (unchanged semantics). */
        Map<BusinessKey, SipsaMayoristasMensual> uniqueItems = new LinkedHashMap<>();
        for (SipsaMayoristasMensual item : items) {
            uniqueItems.put(new BusinessKey(item.getArtiId(), item.getFuenId(), item.getFechaMesIni()), item);
        }

        Instant now = Instant.now();
        List<SipsaMayoristasMensual> candidates = new ArrayList<>(uniqueItems.values());
        for (SipsaMayoristasMensual candidate : candidates) {
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
