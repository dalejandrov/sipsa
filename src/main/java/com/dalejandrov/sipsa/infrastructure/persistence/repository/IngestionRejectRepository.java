package com.dalejandrov.sipsa.infrastructure.persistence.repository;

import com.dalejandrov.sipsa.domain.entity.IngestionReject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA Repository for managing {@link IngestionReject} entities.
 * <p>
 * Provides data access for rejected records that failed validation or parsing
 * during ingestion. This repository is used for:
 * <ul>
 *   <li>Persisting rejected records with reasons</li>
 *   <li>Data quality analysis</li>
 *   <li>Debugging ingestion issues</li>
 *   <li>Reprocessing after fixes</li>
 * </ul>
 * <p>
 * Rejected records are typically batch-inserted at the end of ingestion runs
 * to minimize transaction overhead during processing.
 *
 * @see IngestionReject
 * @see com.dalejandrov.sipsa.application.service.IngestionControlService#logReject
 */
@Repository
public interface IngestionRejectRepository extends JpaRepository<IngestionReject, Long> {
    // Standard JPA methods inherited from JpaRepository
}
