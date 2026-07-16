package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing partial market data by municipality.
 * <p>
 * This entity stores detailed market information at municipality level,
 * including price ranges, product availability, and market source details.
 * Data is sourced from SOAP method {@code promediosSipsaParcial}.
 * <p>
 * <b>Primary Use Cases:</b>
 * <ul>
 *   <li>Monitor prices in smaller municipal markets</li>
 *   <li>Track regional price variations</li>
 *   <li>Analyze supply chain at municipality level</li>
 *   <li>Support rural market analysis</li>
 * </ul>
 * <p>
 * <b>Unique Hash Key:</b><br>
 * {@code keyHash} is the deterministic SHA-256 of the natural business key
 * {@code (muniId, fuenId, futiId, idArtiSemana, enmaFecha)} (ADR-001, Option A).
 * Rows persisted before the fix carry a legacy random UUID instead; the repository's
 * batch upsert deduplicates against them by recomputing the natural-key hash.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.handler.ParcialIngestionHandler
 */
@Entity
@Table(name = "sipsa_parcial")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SipsaParcial {

    /** Primary key - auto-generated identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Deterministic natural-key hash (SHA-256 hex); legacy rows hold a random UUID */
    @Column(name = "key_hash", length = 100)
    private String keyHash;

    /** Municipality identifier code */
    @Column(name = "muni_id")
    private String muniId;

    /** Municipality name */
    @Column(name = "muni_nombre")
    private String muniNombre;

    /** Department (state/province) name */
    @Column(name = "dept_nombre")
    private String deptNombre;

    /** Source/market identifier */
    @Column(name = "fuen_id")
    private Long fuenId;

    /** Source/market name */
    @Column(name = "fuen_nombre")
    private String fuenNombre;

    /** Source type identifier */
    @Column(name = "futi_id")
    private Long futiId;

    /** Weekly article/product identifier */
    @Column(name = "id_arti_semana")
    private Long idArtiSemana;

    /** Article/product name */
    @Column(name = "arti_nombre")
    private String artiNombre;

    /** Product group/category name */
    @Column(name = "grup_nombre")
    private String grupNombre;

    /** Survey/data collection date */
    @Column(name = "enma_fecha")
    private Instant enmaFecha;


    /** Average price per kilogram */
    @Column(name = "promedio_kg", precision = 15, scale = 2)
    private BigDecimal promedioKg;

    /** Maximum price per kilogram */
    @Column(name = "maximo_kg", precision = 15, scale = 2)
    private BigDecimal maximoKg;

    /** Minimum price per kilogram */
    @Column(name = "minimo_kg", precision = 15, scale = 2)
    private BigDecimal minimoKg;

    /** Timestamp of last update in this system */
    @Column(name = "fecha_sincronizacion")
    private Instant fechaSincronizacion;

    /** Foreign key to the ingestion run that created this record */
    @Column(name = "ingestion_run_id")
    private Long ingestionRunId;
}
