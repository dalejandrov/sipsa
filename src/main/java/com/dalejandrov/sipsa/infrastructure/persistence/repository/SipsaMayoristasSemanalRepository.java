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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Records WITHOUT tmpMayoSemId use business keys (artiId, fuenId, fechaIni) — see
 * {@link #upsertFallbackBatch(List)} (TECH-060: atomic {@code ON CONFLICT} insert, no
 * per-row existence query).
 *
 * @see SipsaMayoristasSemanal
 * @see com.dalejandrov.sipsa.application.ingestion.handler.SemanaIngestionHandler
 */
@Repository
public interface SipsaMayoristasSemanalRepository
        extends JpaRepository<SipsaMayoristasSemanal, Long>, JpaSpecificationExecutor<SipsaMayoristasSemanal>,
        SipsaMayoristasSemanalBatchInsertRepository {

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
                    item.setFechaSincronizacion(now);
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
     * Business key for the fallback upsert path: {@code (artiId, fuenId, fechaIni)},
     * backed by the {@code ux_semana_fallback} unique constraint (V1). Used only as an
     * in-memory {@code Map} key for intra-batch deduplication — two {@code null}
     * components are treated as equal here (a record's generated {@code equals} is
     * {@code null}-safe field-by-field), the same collapsing behavior the previous
     * string-concatenation key had, even though PostgreSQL's own unique constraint
     * treats {@code NULL} as distinct from itself.
     */
    record BusinessKey(Long artiId, Long fuenId, Instant fechaIni) {}

    /**
     * Batch upserts records without temporary IDs (fallback strategy).
     * <p>
     * Uses business keys (artiId, fuenId, fechaIni) for matching.
     * Strategy: If exists, SKIP (do not update). If not exists, INSERT.
     * <p>
     * <b>TECH-060:</b> previously issued one {@code findByBusinessKeys} SELECT per
     * (deduplicated) item — N+1 round trips per batch. Now: in-batch dedup stays
     * in-memory (unchanged: last occurrence per key wins, and — like the prior
     * implementation — a duplicate discarded here is not separately counted as
     * {@code inserted} or {@code skipped}, so {@code inserted + skipped} equals the
     * number of *unique* keys in the batch, not {@code items.size()}), then the
     * deduplicated rows are sent in a single {@code INSERT … ON CONFLICT (arti_id,
     * fuen_id, fecha_ini) DO NOTHING} JDBC batch ({@link #insertIgnoringConflicts}) — one
     * round trip total, replacing both the per-row existence check and the separate
     * {@code saveAll}/{@code flush}. A row with any {@code null} key component can never
     * match the conflict target and therefore always inserts, exactly like the removed
     * {@code findByBusinessKeys} lookup always returned empty for a {@code null}
     * parameter.
     * <p>
     * <b>Concurrency:</b> the previous SELECT-then-{@code saveAll} sequence had the same
     * TOCTOU gap as {@code SipsaParcialRepository.batchUpsert} before TECH-117 — a
     * concurrent writer could insert the same key between the check and the write,
     * surfacing as a unique-violation exception that discarded the whole batch. The
     * atomic {@code ON CONFLICT} clause removes that gap entirely: the losing side's
     * conflicting rows resolve to "not inserted" (counted as {@code skipped}) with no
     * exception and no effect on its non-conflicting rows, exactly like TECH-117.
     *
     * @param items list of entities without tmpMayoSemId values
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics upsertFallbackBatch(List<SipsaMayoristasSemanal> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        /* Deduplicate within batch - keep latest value (unchanged semantics) */
        Map<BusinessKey, SipsaMayoristasSemanal> uniqueItems = new LinkedHashMap<>();
        for (SipsaMayoristasSemanal item : items) {
            uniqueItems.put(new BusinessKey(item.getArtiId(), item.getFuenId(), item.getFechaIni()), item);
        }

        Instant now = Instant.now();
        List<SipsaMayoristasSemanal> candidates = new ArrayList<>(uniqueItems.values());
        for (SipsaMayoristasSemanal candidate : candidates) {
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
