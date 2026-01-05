package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing city-level agricultural pricing data.
 * <p>
 * This entity stores daily price information collected at city level,
 * including average prices per product across different Colombian cities.
 * Data is sourced from SOAP method {@code promediosSipsaCiudad}.
 * <p>
 * <b>Primary Use Cases:</b>
 * <ul>
 *   <li>Track daily price variations by city</li>
 *   <li>Compare prices across different urban markets</li>
 *   <li>Analyze price trends over time</li>
 *   <li>Support consumer price monitoring</li>
 * </ul>
 * <p>
 * <b>Upsert Strategy:</b><br>
 * Records are upserted based on business key (regId, codProducto) using the
 * unique constraint {@code ux_ciudad}. This ensures that reprocessing the same
 * data will update existing records rather than failing with duplicate key errors.
 * When the same (regId, codProducto) combination is encountered, the existing
 * record is updated with the latest data.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.handler.CiudadIngestionHandler
 */
@Entity
@Table(name = "sipsa_ciudad")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SipsaCiudad {

    /** Primary key - auto-generated identifier */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Registration ID from source system */
    @Column(name = "reg_id")
    private Long regId;

    /** City name where data was collected */
    @Column(name = "ciudad")
    private String ciudad;

    /** Product code identifier */
    @Column(name = "cod_producto")
    private Long codProducto;

    /** Product name/description */
    @Column(name = "producto")
    private String producto;

    /** Date when the price was captured */
    @Column(name = "fecha_captura")
    private Instant fechaCaptura;

    /** Date when the record was created in source system */
    @Column(name = "fecha_creacion")
    private Instant fechaCreacion;

    /** Average price for the product (in local currency per unit) */
    @Column(name = "precio_promedio", precision = 15, scale = 2)
    private BigDecimal precioPromedio;

    /** Amount sent/dispatched (specific to source system) */
    @Column(name = "enviado", precision = 15, scale = 2)
    private BigDecimal enviado;

    /** Timestamp when the record was ingested into this system */
    @Column(name = "fecha_ingestion")
    private Instant fechaIngestion;

    /** Foreign key to the ingestion run that created this record */
    @Column(name = "ingestion_run_id")
    private Long ingestionRunId;
}
