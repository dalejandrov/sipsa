package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;
import com.dalejandrov.sipsa.infrastructure.soap.mapper.ParcialKeyHash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JPA Repository for managing {@link SipsaParcial} entities.
 * <p>
 * <b>Upsert strategy (ADR-001, Option A — insert-only + skip):</b> the business key is
 * {@code (muniId, fuenId, futiId, idArtiSemana, enmaFecha)}, materialized as the
 * deterministic {@code key_hash} computed by {@link ParcialKeyHash}. A record whose key
 * already exists is skipped — never updated — matching the skip-first semantics of the
 * other four data types.
 * <p>
 * <b>Legacy rows:</b> rows persisted before the deterministic hash carry a random UUID in
 * {@code key_hash}, so a hash lookup cannot see them. {@link #batchUpsert(List)} therefore
 * performs a second bulk lookup by survey date and matches candidates by their
 * <i>recomputed</i> natural-key hash, deduplicating against legacy data without any
 * backfill. Both lookups are one query per batch — no per-record queries (no N+1).
 *
 * @see SipsaParcial
 * @see ParcialKeyHash
 * @see com.dalejandrov.sipsa.application.ingestion.handler.ParcialIngestionHandler
 */
@Repository
public interface SipsaParcialRepository
        extends JpaRepository<SipsaParcial, Long>, JpaSpecificationExecutor<SipsaParcial>,
        SipsaParcialBatchInsertRepository {

    /**
     * Record to track insert/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Bulk lookup of existing rows by deterministic key hash (single indexed query).
     *
     * @param keyHashes deterministic hashes of the batch
     * @return existing rows whose {@code key_hash} matches
     */
    List<SipsaParcial> findByKeyHashIn(Collection<String> keyHashes);

    /**
     * Bulk lookup of candidate rows by survey date, used to deduplicate against legacy
     * rows whose {@code key_hash} is a pre-fix random UUID. The DANE stream is
     * date-ordered, so a batch spans very few distinct dates and this stays one small,
     * index-supported query per batch.
     *
     * @param fechas distinct survey dates present in the batch
     * @return all rows for those dates (legacy and deterministic alike)
     */
    List<SipsaParcial> findByEnmaFechaIn(Collection<LocalDate> fechas);

    /**
     * Batch upsert with skip-first deduplication (ADR-001, Option A).
     * <ol>
     *   <li>Intra-batch dedupe by {@code keyHash} ({@code LinkedHashMap}, last occurrence wins).</li>
     *   <li>Bulk lookup by deterministic hash — one query — skips rows already stored
     *       with the new format.</li>
     *   <li>Bulk lookup by survey date — one query — recomputes the natural-key hash of
     *       each candidate (covers legacy UUID rows) and skips matches.</li>
     *   <li>Inserts the remainder atomically via
     *       {@code INSERT … ON CONFLICT (key_hash) DO NOTHING} in a single JDBC batch
     *       ({@link SipsaParcialBatchInsertRepository}).</li>
     * </ol>
     * <b>Concurrency (TECH-117):</b> the lookups are an optimization, not a guarantee —
     * under READ COMMITTED a concurrent run can insert the same key after the lookup and
     * before the insert. The {@code UNIQUE (key_hash)} constraint
     * ({@code sipsa_parcial_key_hash_key}) remains the final barrier, and the conflict
     * clause turns a lost race into a per-row "not inserted" outcome counted as
     * {@code skipped}: no exception, no rollback-only transaction, no failed run, and
     * non-conflicting rows of the same batch persist normally. The legacy-UUID dedup
     * (step 3) is untouched: {@code ON CONFLICT} targets only {@code key_hash} — never
     * the natural key, which has no unique constraint yet.
     *
     * @param items batch of entities with deterministic {@code keyHash} already set
     * @return metrics with counts of inserted and skipped records; for every batch
     *         {@code items.size() == inserted + skipped} holds
     */
    @Transactional
    default UpsertMetrics batchUpsert(List<SipsaParcial> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        /* 1. Intra-batch dedupe: same business key twice in one batch → last wins. */
        Map<String, SipsaParcial> uniqueItems = new LinkedHashMap<>();
        for (SipsaParcial item : items) {
            uniqueItems.put(item.getKeyHash(), item);
        }
        int skipped = items.size() - uniqueItems.size();

        /* 2. Bulk existence check by deterministic hash (rows written after the fix). */
        Set<String> existingHashes = new HashSet<>();
        for (SipsaParcial existing : findByKeyHashIn(uniqueItems.keySet())) {
            existingHashes.add(existing.getKeyHash());
        }

        /* 3. Legacy check: fetch same-date candidates once and match by the recomputed
         *    natural-key hash, so pre-fix UUID rows also deduplicate (no backfill needed). */
        Set<LocalDate> fechas = new HashSet<>();
        for (SipsaParcial item : uniqueItems.values()) {
            if (!existingHashes.contains(item.getKeyHash())) {
                fechas.add(item.getEnmaFecha());
            }
        }
        if (!fechas.isEmpty()) {
            for (SipsaParcial candidate : findByEnmaFechaIn(fechas)) {
                String naturalHash = recomputeNaturalHash(candidate);
                if (naturalHash != null) {
                    existingHashes.add(naturalHash);
                }
            }
        }

        /* 4. Insert only genuinely new business keys. */
        Instant now = Instant.now();
        List<SipsaParcial> toInsert = new ArrayList<>();
        for (SipsaParcial item : uniqueItems.values()) {
            if (existingHashes.contains(item.getKeyHash())) {
                skipped++;
            } else {
                item.setFechaSincronizacion(now);
                toInsert.add(item);
            }
        }

        /* Atomic insert: a key that a concurrent run committed after our lookup resolves
         * to update-count 0 inside PostgreSQL and is counted as skipped — the transaction
         * stays valid and the rest of the batch is unaffected. */
        int inserted = 0;
        if (!toInsert.isEmpty()) {
            for (int outcome : insertIgnoringConflicts(toInsert)) {
                if (outcome > 0) {
                    inserted++;
                } else {
                    skipped++;
                }
            }
        }
        return new UpsertMetrics(inserted, skipped);
    }

    /**
     * Recomputes the deterministic natural-key hash of a stored row, or returns null if
     * the row is missing a key component (cannot match anything — defensive only, the
     * ingestion validation has always rejected incomplete keys).
     */
    private static String recomputeNaturalHash(SipsaParcial row) {
        if (row.getMuniId() == null || row.getFuenId() == null || row.getFutiId() == null
                || row.getIdArtiSemana() == null || row.getEnmaFecha() == null) {
            return null;
        }
        return ParcialKeyHash.compute(row.getMuniId(), row.getFuenId(), row.getFutiId(),
                row.getIdArtiSemana(), row.getEnmaFecha());
    }
}
