package com.dalejandrov.sipsa.infrastructure.soap.parser;

import lombok.experimental.UtilityClass;

/**
 * Centralized XML field name constants for SIPSA SOAP parsers.
 * <p>
 * This utility class contains all XML element names used in SOAP response parsing,
 * organized by category to avoid duplication and ensure consistency across all parsers.
 * <p>
 * Field names are lowercase as they appear in the SOAP XML responses from the
 * SIPSA web service. These constants are used by all StAX parsers to extract
 * data from XML elements.
 * <p>
 * <b>Organization:</b>
 * <ul>
 *   <li>Common fields: Shared across multiple SOAP methods</li>
 *   <li>Entity-specific fields: Unique to particular response types</li>
 * </ul>
 *
 * @see AbstractStaxParser
 * @see CiudadStaxParser
 * @see ParcialStaxParser
 * @see SemanaStaxParser
 * @see MesStaxParser
 * @see AbasStaxParser
 */
@UtilityClass
public class XmlFieldNames {

    // ==================== Common Article Fields ====================

    /** Article/product ID field name */
    public static final String ARTI_ID = "artiid";

    /** Article/product name field name */
    public static final String ARTI_NOMBRE = "artinombre";

    // ==================== Common Source (Fuente) Fields ====================

    /** Market source ID field name */
    public static final String FUEN_ID = "fuenid";

    /** Market source name field name */
    public static final String FUEN_NOMBRE = "fuennombre";

    /** Market type ID field name */
    public static final String FUTI_ID = "futiid";

    // ==================== Common Date Fields ====================

    /** Creation date field name (epoch milliseconds) */
    public static final String FECHA_CREACION = "fechacreacion";

    /** Month start date field name (epoch milliseconds) */
    public static final String FECHA_MES_INI = "fechamesini";

    /** Start date field name (epoch milliseconds) */
    public static final String FECHA_INI = "fechaini";

    /** Capture/survey date field name (epoch milliseconds) */
    public static final String FECHA_CAPTURA = "fechacaptura";

    // ==================== Common Measurement Fields (Kg) ====================

    /** Maximum price per kilogram field name */
    public static final String MAXIMO_KG = "maximokg";

    /** Minimum price per kilogram field name */
    public static final String MINIMO_KG = "minimokg";

    /** Average price per kilogram field name */
    public static final String PROMEDIO_KG = "promediokg";

    // ==================== Common Status Fields ====================

    /** Amount sent/dispatched field name */
    public static final String ENVIADO = "enviado";

    // ==================== Abastecimientos (Supply) Specific Fields ====================

    /** Temporary monthly supply ID field name (for deduplication) */
    public static final String TMP_ABAS_MES_ID = "tmpabasmesid";

    /** Quantity in tons field name */
    public static final String CANTIDAD_TON = "cantidadton";

    // ==================== Mayoristas (Wholesale) Specific Fields ====================

    /** Temporary monthly wholesale ID field name (for deduplication) */
    public static final String TMP_MAYO_MES_ID = "tmpmayomesid";

    /** Temporary weekly wholesale ID field name (for deduplication) */
    public static final String TMP_MAYO_SEM_ID = "tmpmayosemid";

    // ==================== Ciudad (City) Specific Fields ====================

    /** Registration ID field name */
    public static final String REG_ID = "regid";

    /** City name field name */
    public static final String CIUDAD = "ciudad";

    /** Product code field name */
    public static final String COD_PRODUCTO = "codproducto";

    /** Product name field name */
    public static final String PRODUCTO = "producto";

    /** Average price field name */
    public static final String PRECIO_PROMEDIO = "preciopromedio";

    // ==================== Parcial (Municipal) Specific Fields ====================

    /** Municipality ID field name */
    public static final String MUNI_ID = "muniid";

    /** Municipality name field name */
    public static final String MUNI_NOMBRE = "muninombre";

    /** Department (state) name field name */
    public static final String DEPT_NOMBRE = "deptnombre";

    /** Product group name field name */
    public static final String GRUP_NOMBRE = "grupnombre";

    /** Weekly article ID field name */
    public static final String ID_ARTI_SEMANA = "idartisemana";

    /** Survey date field name */
    public static final String ENMA_FECHA = "enmafecha";
}

