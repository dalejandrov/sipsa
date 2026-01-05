package com.dalejandrov.sipsa.infrastructure.soap.parser;

import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaCiudadRecord;
import com.dalejandrov.sipsa.infrastructure.soap.util.XmlParsingUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import static com.dalejandrov.sipsa.infrastructure.soap.parser.XmlFieldNames.*;

public class CiudadStaxParser extends AbstractStaxParser<SipsaCiudadRecord> {

    private static final String END_ELEMENT = "return";
    private static final Map<String, TriConsumer<AbstractStaxParser<SipsaCiudadRecord>, Builder, String>> HANDLERS =
            Map.of(
                    REG_ID, (p, b, t) -> b.regId = XmlParsingUtil.parseLong(t),
                    CIUDAD, (p, b, t) -> b.ciudad = t,
                    COD_PRODUCTO, (p, b, t) -> b.codProducto = XmlParsingUtil.parseLong(t),
                    PRODUCTO, (p, b, t) -> b.producto = t,
                    FECHA_CAPTURA, (p, b, t) -> b.fechaCaptura = XmlParsingUtil.parseXmlDateTime(t),
                    FECHA_CREACION, (p, b, t) -> b.fechaCreacion = XmlParsingUtil.parseXmlDateTime(t),
                    PRECIO_PROMEDIO, (p, b, t) -> b.precioPromedio = XmlParsingUtil.parseDecimal(t),
                    ENVIADO, (p, b, t) -> b.enviado = XmlParsingUtil.parseDecimal(t)
            );

    /**
     * Creates a CiudadStaxParser for the given input stream.
     *
     * @param inputStream the XML input stream containing SOAP response
     * @throws XMLStreamException if XML stream initialization fails
     */
    public CiudadStaxParser(InputStream inputStream) throws XMLStreamException {
        super(inputStream);
    }

    /**
     * Parses a single city pricing record from XML.
     * <p>
     * Extracts field values using handler-based parsing and constructs
     * a {@link SipsaCiudadRecord} with the parsed data.
     *
     * @param reader the XML stream reader positioned at a return element
     * @return parsed city pricing record
     * @throws XMLStreamException if XML parsing fails
     */
    @Override
    protected SipsaCiudadRecord parseItem(XMLStreamReader reader) throws XMLStreamException {
        Builder builder = new Builder();
        parseWithHandlers(builder, HANDLERS, END_ELEMENT);
        return builder.build();
    }

    /**
     * Internal builder for constructing {@link SipsaCiudadRecord} instances.
     * <p>
     * Accumulates field values during parsing before creating the immutable record.
     */
    private static class Builder {
        Long regId, codProducto, fechaCaptura, fechaCreacion;
        String ciudad, producto;
        BigDecimal precioPromedio, enviado;

        /**
         * Builds the immutable city pricing record from accumulated fields.
         *
         * @return constructed SipsaCiudadRecord
         */
        SipsaCiudadRecord build() {
            return new SipsaCiudadRecord(regId, ciudad, codProducto, producto,
                    fechaCaptura, fechaCreacion, precioPromedio, enviado);
        }
    }
}

