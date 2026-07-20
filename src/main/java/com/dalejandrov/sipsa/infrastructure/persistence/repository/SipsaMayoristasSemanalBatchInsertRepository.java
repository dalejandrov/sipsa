package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaMayoristasSemanal;

import java.util.List;

/**
 * Custom repository fragment for the concurrency-safe batch insert used by the
 * business-key fallback path (TECH-060, same technique as
 * {@link SipsaParcialBatchInsertRepository}, TECH-117).
 * <p>
 * The insert is atomic per row inside PostgreSQL — {@code ON CONFLICT (arti_id, fuen_id,
 * fecha_ini) DO NOTHING}, backed by the {@code ux_semana_fallback} unique constraint — so
 * a concurrent writer racing between the batch's in-memory dedup and the insert no longer
 * produces a unique-violation exception: the collision resolves to "row not inserted" and
 * is reported back through the per-row outcome, leaving the surrounding transaction fully
 * usable.
 *
 * @see SipsaMayoristasSemanalRepository#upsertFallbackBatch(List)
 */
public interface SipsaMayoristasSemanalBatchInsertRepository {

    /**
     * Inserts the given rows with {@code INSERT … ON CONFLICT (arti_id, fuen_id, fecha_ini)
     * DO NOTHING}, executed as a single JDBC batch (one statement per row, one network
     * round trip — no per-row existence-check query, no N+1).
     * <p>
     * A row with a {@code null} {@code artiId}, {@code fuenId}, or {@code fechaIni} can
     * never match the conflict target (SQL {@code NULL} is never equal to anything,
     * including itself), so it always inserts — the same behavior the previous
     * per-row {@code findByBusinessKeys} lookup had for an incomplete key.
     *
     * @param rows entities to insert; {@code fechaSincronizacion} must already be set
     * @return one outcome per row, aligned with {@code rows}: {@code 1} if the row was
     *         inserted, {@code 0} if it was dropped by a business-key conflict
     */
    int[] insertIgnoringConflicts(List<SipsaMayoristasSemanal> rows);
}
