package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;

import java.util.List;

/**
 * Custom repository fragment for the concurrency-safe batch insert of
 * {@link SipsaCiudad} rows, same technique as {@link SipsaParcialBatchInsertRepository}
 * (TECH-117).
 * <p>
 * The insert is atomic per row inside PostgreSQL — {@code ON CONFLICT (reg_id,
 * cod_producto) DO NOTHING}, backed by the {@code ux_ciudad} unique constraint — so a
 * concurrent writer racing between the batch's dedup/existence lookup and the insert no
 * longer produces a unique-violation exception: the collision resolves to "row not
 * inserted" and is reported back through the per-row outcome, leaving the surrounding
 * transaction fully usable.
 *
 * @see SipsaCiudadRepository#batchUpsert(List)
 */
public interface SipsaCiudadBatchInsertRepository {

    /**
     * Inserts the given rows with {@code INSERT … ON CONFLICT (reg_id, cod_producto)
     * DO NOTHING}, executed as a single JDBC batch (one statement per row, one network
     * round trip — no per-row existence-check query, no N+1).
     *
     * @param rows entities to insert; {@code fechaSincronizacion} must already be set
     * @return one outcome per row, aligned with {@code rows}: {@code 1} if the row was
     *         inserted, {@code 0} if it was dropped by a business-key conflict
     */
    int[] insertIgnoringConflicts(List<SipsaCiudad> rows);
}
