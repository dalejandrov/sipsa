package com.dalejandrov.sipsa.domain.entity;

/**
 * Enum representing the possible statuses of an ingestion run.
 * <p>
 * This enum defines the lifecycle states of an ingestion process,
 * from initiation through completion or failure.
 */
public enum IngestionRunStatus {
    /**
     * Run has been created and initialized, but not yet started.
     */
    STARTED,

    /**
     * Run is actively processing data.
     */
    RUNNING,

    /**
     * Run completed successfully with all data processed.
     */
    SUCCEEDED,

    /**
     * Run terminated with an error.
     */
    FAILED,

    /**
     * Run was manually canceled by an operator.
     */
    CANCELED
}
