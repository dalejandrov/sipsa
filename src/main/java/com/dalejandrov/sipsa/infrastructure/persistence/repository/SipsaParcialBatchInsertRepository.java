package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaParcial;

import java.util.List;

/**
 * Custom repository fragment for the concurrency-safe batch insert of
 * {@link SipsaParcial} rows (TECH-117).
 * <p>
 * The insert is atomic per row inside PostgreSQL — {@code ON CONFLICT (key_hash)
 * DO NOTHING} — so a concurrent writer winning the race between the dedup lookup and
 * the insert no longer produces a unique-violation exception: the collision resolves
 * to "row not inserted" and is reported back through the per-row outcome, leaving the
 * surrounding transaction fully usable.
 *
 * @see SipsaParcialRepository#batchUpsert(List)
 */
public interface SipsaParcialBatchInsertRepository {

    /**
     * Inserts the given rows with {@code INSERT … ON CONFLICT (key_hash) DO NOTHING},
     * executed as a single JDBC batch (one statement per row, one network round trip —
     * no per-row calls, no N+1).
     *
     * @param rows entities to insert; {@code keyHash} must be set on every row
     * @return one outcome per row, aligned with {@code rows}: {@code 1} if the row was
     *         inserted, {@code 0} if it was dropped by a {@code key_hash} conflict
     */
    int[] insertIgnoringConflicts(List<SipsaParcial> rows);
}
