package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaAbastecimientosMensual;

import java.util.List;

/**
 * Custom repository fragment for the concurrency-safe batch insert used by the
 * tmpId-matching upsert path, same technique as
 * {@link SipsaMayoristasMensualTmpBatchInsertRepository} (TECH-060/TECH-117 lineage).
 * <p>
 * The insert is atomic per row inside PostgreSQL — {@code ON CONFLICT (tmp_abas_mes_id)
 * DO NOTHING}, backed by the {@code ux_abas_tmp} unique constraint — so a concurrent writer
 * racing between the batch's in-memory dedup and the insert no longer produces a
 * unique-violation exception: the collision resolves to "row not inserted" and is reported
 * back through the per-row outcome, leaving the surrounding transaction fully usable.
 *
 * @see SipsaAbastecimientosMensualRepository#upsertTmpBatch(List)
 */
public interface SipsaAbastecimientosMensualTmpBatchInsertRepository {

    /**
     * Inserts the given rows with {@code INSERT … ON CONFLICT (tmp_abas_mes_id)
     * DO NOTHING}, executed as a single JDBC batch (one statement per row, one network
     * round trip — no per-row {@code findByTmpId} query, no N+1).
     * <p>
     * A row with a {@code null} {@code tmpAbasMesId} can never match the conflict target
     * (SQL {@code NULL} is never equal to anything, including itself), so it always
     * inserts — callers should not pass such rows here (the handler only routes rows with
     * a non-null tmpId into this path).
     *
     * @param rows entities to insert; {@code fechaSincronizacion} must already be set
     * @return one outcome per row, aligned with {@code rows}: {@code 1} if the row was
     *         inserted, {@code 0} if it was dropped by a {@code tmp_abas_mes_id} conflict
     */
    int[] insertIgnoringTmpConflicts(List<SipsaAbastecimientosMensual> rows);
}
