package com.dalejandrov.sipsa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing monthly supply data to wholesale markets.
 * <p>
 * This entity stores information about product supply volumes to wholesale markets
 * on a monthly basis, measured in tons. Data is sourced from SOAP method
 * {@code promedioAbasSipsaMesMadr}.
 * <p>
 * <b>Primary Use Cases:</b>
 * <ul>
 *   <li>Track supply volumes to wholesale markets</li>
 *   <li>Monitor product availability trends</li>
 *   <li>Analyze supply chain efficiency</li>
 *   <li>Support inventory and logistics planning</li>
 * </ul>
 * <p>
 * <b>Dual Upsert Strategy:</b><br>
 * Records WITH tmpAbasMesId use specialized upsert.
 * Records WITHOUT tmpAbasMesId use fallback upsert on business keys.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.handler.AbasIngestionHandler
 */
@Entity
@Table(name = "sipsa_abastecimientos_mensual")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SipsaAbastecimientosMensual {

    /**
     * Unique identifier for the record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Identifier for the monthly supply batch.
     */
    @Column(name = "tmp_abas_mes_id")
    private Long tmpAbasMesId;

    /**
     * Identifier for the product.
     */
    @Column(name = "arti_id")
    private Long artiId;

    /**
     * Name of the product.
     */
    @Column(name = "arti_nombre")
    private String artiNombre;

    /**
     * Identifier for the supply source.
     */
    @Column(name = "fuen_id")
    private Long fuenId;

    /**
     * Name of the supply source.
     */
    @Column(name = "fuen_nombre")
    private String fuenNombre;

    /**
     * Identifier for the supply type.
     */
    @Column(name = "futi_id")
    private Long futiId;

    /**
     * Start date of the month for the supply data.
     */
    @Column(name = "fecha_mes_ini")
    private Instant fechaMesIni;

    /**
     * Record creation date.
     */
    @Column(name = "fecha_creacion")
    private Instant fechaCreacion;

    /**
     * Quantity supplied, in tons.
     */
    @Column(name = "cantidad_ton", precision = 19, scale = 2)
    private BigDecimal cantidadTon;

    /**
     * Quantity sent to the market, in tons.
     */
    @Column(name = "enviado", precision = 19, scale = 2)
    private BigDecimal enviado;

    /**
     * Identifier for the ingestion run.
     */
    @Column(name = "ingestion_run_id", nullable = false)
    private Long ingestionRunId;

    /**
     * Ingestion date, defaults to the current date and time.
     */
    @Column(name = "fecha_ingestion")
    @Builder.Default
    private Instant fechaIngestion = Instant.now();
}
