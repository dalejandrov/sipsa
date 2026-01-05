package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing monthly wholesale market pricing data.
 * <p>
 * This entity stores monthly aggregated pricing information from wholesale markets,
 * including minimum, maximum, and average prices per product.
 * Data is sourced from SOAP method {@code promediosSipsaMesMadr}.
 * <p>
 * <b>Primary Use Cases:</b>
 * <ul>
 *   <li>Monthly price trend analysis</li>
 *   <li>Long-term market performance tracking</li>
 *   <li>Seasonal pattern identification</li>
 *   <li>Annual comparison and forecasting</li>
 * </ul>
 * <p>
 * <b>Dual Upsert Strategy:</b><br>
 * Records WITH tmpMayoMesId use specialized upsert.
 * Records WITHOUT tmpMayoMesId use fallback upsert on business keys.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.handler.MesIngestionHandler
 */
@Entity
@Table(name = "sipsa_mayoristas_mensual")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SipsaMayoristasMensual {

    /** Primary key - auto-generated identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Temporary ID for May (month) - usually null from SOAP */
    @Column(name = "tmp_mayo_mes_id")
    private Long tmpMayoMesId;

    /** Article ID - foreign key to articles table */
    @Column(name = "arti_id", nullable = false)
    private Long artiId;

    /** Article name - description of the article */
    @Column(name = "arti_nombre", nullable = false)
    private String artiNombre;

    /** Source ID - foreign key to sources table */
    @Column(name = "fuen_id", nullable = false)
    private Long fuenId;

    /** Source name - description of the source */
    @Column(name = "fuen_nombre", nullable = false)
    private String fuenNombre;

    /** Type ID - foreign key to types table */
    @Column(name = "futi_id", nullable = false)
    private Long futiId;

    /** Start date of the month - when the data becomes effective */
    @Column(name = "fecha_mes_ini", nullable = false)
    private Instant fechaMesIni;

    /** Creation date - usually null from SOAP */
    @Column(name = "fecha_creacion")
    private Instant fechaCreacion;

    /** Minimum weight in kg - lowest recorded weight for the month */
    @Column(name = "minimo_kg", precision = 19, scale = 2)
    private BigDecimal minimoKg;

    /** Maximum weight in kg - highest recorded weight for the month */
    @Column(name = "maximo_kg", precision = 19, scale = 2)
    private BigDecimal maximoKg;

    /** Average weight in kg - average weight calculated for the month */
    @Column(name = "promedio_kg", precision = 19, scale = 2)
    private BigDecimal promedioKg;

    /** Sent weight in kg - amount of weight sent, for tracking deliveries */
    @Column(name = "enviado", precision = 19, scale = 2)
    private BigDecimal enviado;

    /** Ingestion run ID - identifier for the ingestion process run */
    @Column(name = "ingestion_run_id", nullable = false)
    private Long ingestionRunId;

    /** Last updated timestamp - tracks when the record was last updated */
    @Column(name = "last_updated")
    @Builder.Default
    private Instant lastUpdated = Instant.now();
}
