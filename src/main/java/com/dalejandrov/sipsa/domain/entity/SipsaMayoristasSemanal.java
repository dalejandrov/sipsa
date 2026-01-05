package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing weekly wholesale market pricing data.
 * <p>
 * This entity stores weekly aggregated pricing information from wholesale markets,
 * including minimum, maximum, and average prices per product.
 * Data is sourced from SOAP method {@code promediosSipsaSemanaMadr}.
 * <p>
 * <b>Primary Use Cases:</b>
 * <ul>
 *   <li>Weekly price trend analysis</li>
 *   <li>Wholesale market performance monitoring</li>
 *   <li>Short-term supply/demand tracking</li>
 *   <li>Price volatility analysis</li>
 * </ul>
 * <p>
 * <b>Dual Upsert Strategy:</b><br>
 * Records WITH tmpMayoSemId use specialized upsert.
 * Records WITHOUT tmpMayoSemId use fallback upsert on business keys.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.handler.SemanaIngestionHandler
 */
@Entity
@Table(name = "sipsa_mayoristas_semanal")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SipsaMayoristasSemanal {

    /** Primary key - auto-generated identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Temporary weekly identifier from source (for upsert matching) */
    @Column(name = "tmp_mayo_sem_id") // Usually null from SOAP
    private Long tmpMayoSemId;

    /** Article/product identifier */
    @Column(name = "arti_id", nullable = false)
    private Long artiId;

    /** Article/product name */
    @Column(name = "arti_nombre", nullable = false)
    private String artiNombre;

    /** Source/market identifier */
    @Column(name = "fuen_id", nullable = false)
    private Long fuenId;

    /** Source/market name */
    @Column(name = "fuen_nombre", nullable = false)
    private String fuenNombre;

    /** Source type identifier */
    @Column(name = "futi_id", nullable = false)
    private Long futiId;

    /** Week start date (stored as timestamp, received as milliseconds from SOAP) */
    @Column(name = "fecha_ini", nullable = false)
    private Instant fechaIni;

    /** Minimum price per kilogram for the week */
    @Column(name = "minimo_kg", precision = 15, scale = 2)
    private BigDecimal minimoKg;

    /** Maximum price per kilogram for the week */
    @Column(name = "maximo_kg", precision = 15, scale = 2)
    private BigDecimal maximoKg;

    /** Average price per kilogram for the week */
    @Column(name = "promedio_kg", precision = 15, scale = 2)
    private BigDecimal promedioKg;

    /** Timestamp when record was created in source system */
    @Column(name = "fecha_creacion") // Usually null from SOAP
    private Instant fechaCreacion;

    /** Amount sent/dispatched (specific to source system) */
    @Column(name = "enviado", precision = 15, scale = 2)
    private BigDecimal enviado;

    /** Timestamp of last update in this system */
    @Column(name = "last_updated")
    private Instant lastUpdated;

    /** Foreign key to the ingestion run that created this record */
    @Column(name = "ingestion_run_id", nullable = false)
    private Long ingestionRunId;
}
