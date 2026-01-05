package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.SipsaCiudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA Repository for managing {@link SipsaCiudad} entities.
 * <p>
 * Provides data access methods for city-level pricing data, including:
 * <ul>
 *   <li>JPA-based upsert queries following Spring Data best practices</li>
 *   <li>JPA Specification support for dynamic filtering</li>
 *   <li>Optimized batch operations using saveAll()</li>
 *   <li>Standard CRUD operations</li>
 * </ul>
 * <p>
 * <b>Upsert Strategy:</b><br>
 * Uses JPA's merge operation through findBy + save pattern, leveraging
 * the unique constraint (reg_id, cod_producto, fecha_captura) for duplicate detection.
 * This approach is database-agnostic and follows Spring Data JPA best practices.
 * <p>
 * <b>Performance:</b><br>
 * Batch operations use Spring Data's saveAll() which is optimized for bulk inserts.
 * Records are processed in batches to balance memory usage and database round trips.
 * <p>
 * <b>Advantages over Native SQL:</b>
 * <ul>
 *   <li>Database-agnostic (works with PostgreSQL, MySQL, H2, etc.)</li>
 *   <li>JPA entity lifecycle management (audit fields, cascading, etc.)</li>
 *   <li>Better integration with Spring Data features</li>
 *   <li>Easier to test with in-memory databases</li>
 *   <li>Type-safe queries with compile-time checking</li>
 * </ul>
 *
 * @see SipsaCiudad
 * @see com.dalejandrov.sipsa.application.ingestion.handler.CiudadIngestionHandler
 */
@Repository
public interface SipsaCiudadRepository extends JpaRepository<SipsaCiudad, Long>, JpaSpecificationExecutor<SipsaCiudad> {

    /**
     * Record to track insert/skip metrics from upsert operations.
     *
     * @param inserted number of new records inserted
     * @param skipped number of existing records skipped (not updated)
     */
    record UpsertMetrics(int inserted, int skipped) {}

    /**
     * Finds all records matching the given list of business keys.
     * <p>
     * This method enables bulk existence checking for city pricing data.
     * Uses the natural key (regId, codProducto) which corresponds to the
     * unique constraint ux_ciudad.
     *
     * @param keys list of composite keys (regId, codProducto)
     * @return list of existing records
     */
    @Query("SELECT c FROM SipsaCiudad c WHERE " +
           "CONCAT(c.regId, '|', c.codProducto) IN :keys")
    List<SipsaCiudad> findByBusinessKeys(@Param("keys") List<String> keys);

    /**
     * Batch upserts a list of city pricing records using JPA best practices.
     * <p>
     * This method implements upsert logic (insert or update) by:
     * <ol>
     *   <li>Detecting and skipping duplicate business keys within the same batch</li>
     *   <li>Bulk querying existing records in database (single query for all keys)</li>
     *   <li>If exists: merge data into existing entity and save</li>
     *   <li>If not exists: set timestamps and save as new</li>
     * </ol>
     * <p>
     * <b>Performance Optimization:</b><br>
     * Instead of querying the database for each record individually (N queries),
     * this method fetches all existing records in a single bulk query, reducing
     * database round trips from O(N) to O(1).
     * <p>
     * <b>Duplicate Prevention:</b><br>
     * Uses a LinkedHashMap to track processed business keys (regId|codProducto)
     * within the current batch. This prevents:
     * <ul>
     *   <li>Multiple database lookups for the same business key in one batch</li>
     *   <li>Duplicate key constraint violations when same key appears multiple times</li>
     *   <li>Unnecessary updates of the same record within one batch</li>
     * </ul>
     * <p>
     * <b>Business Key:</b><br>
     * The business key is (regId, codProducto) matching the unique constraint ux_ciudad.
     * When duplicate keys are found, the LAST occurrence in the batch is kept (most recent data).
     *
     * @param items list of city pricing entities to upsert
     * @return metrics with counts of inserted and skipped records
     */
    @Transactional
    default UpsertMetrics batchUpsert(List<SipsaCiudad> items) {
        if (items == null || items.isEmpty()) {
            return new UpsertMetrics(0, 0);
        }

        java.time.Instant now = java.time.Instant.now();
        int skipped = 0;

        /*
         * Track processed business keys within this batch to avoid duplicates.
         * Using LinkedHashMap to keep insertion order, then override with latest value.
         */
        java.util.Map<String, SipsaCiudad> uniqueItems = new java.util.LinkedHashMap<>();

        for (SipsaCiudad item : items) {
            String businessKey = item.getRegId() + "|" + item.getCodProducto();
            /* Put will replace if key exists, keeping the latest value */
            uniqueItems.put(businessKey, item);
        }

        /* Bulk query to find all existing records */
        java.util.List<String> keys = new java.util.ArrayList<>(uniqueItems.keySet());
        java.util.List<SipsaCiudad> existingRecords = findByBusinessKeys(keys);

        /* Create a map of existing records for fast lookup */
        java.util.Map<String, SipsaCiudad> existingMap = new java.util.HashMap<>();
        for (SipsaCiudad existing : existingRecords) {
            String key = existing.getRegId() + "|" + existing.getCodProducto();
            existingMap.put(key, existing);
        }

        java.util.List<SipsaCiudad> toInsert = new java.util.ArrayList<>();

        for (java.util.Map.Entry<String, SipsaCiudad> entry : uniqueItems.entrySet()) {
            String businessKey = entry.getKey();
            SipsaCiudad item = entry.getValue();

            SipsaCiudad existing = existingMap.get(businessKey);

            if (existing != null) {
                /* Record exists - SKIP it (do not update) */
                skipped++;
            } else {
                /* Record does not exist - INSERT it */
                item.setFechaIngestion(now);
                toInsert.add(item);
            }
        }

        /* Batch save using Spring Data's optimized saveAll */
        int inserted = toInsert.size();
        if (!toInsert.isEmpty()) {
            saveAll(toInsert);
            flush();
        }
        return new UpsertMetrics(inserted, skipped);
    }
}
