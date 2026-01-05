package com.dalejandrov.sipsa.infrastructure.soap.parser;

import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaAbasRecord;
import com.dalejandrov.sipsa.infrastructure.soap.util.XmlParsingUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import static com.dalejandrov.sipsa.infrastructure.soap.parser.XmlFieldNames.*;

public class AbasStaxParser extends AbstractStaxParser<SipsaAbasRecord> {

    private static final String END_ELEMENT = "return";
    private static final Map<String, TriConsumer<AbstractStaxParser<SipsaAbasRecord>, Builder, String>> HANDLERS =
            Map.of(
                    TMP_ABAS_MES_ID, (p, b, t) -> b.tmpAbasMesId = XmlParsingUtil.parseLong(t),
                    ARTI_ID, (p, b, t) -> b.artiId = XmlParsingUtil.parseLong(t),
                    ARTI_NOMBRE, (p, b, t) -> b.artiNombre = t,
                    FUEN_ID, (p, b, t) -> b.fuenId = XmlParsingUtil.parseLong(t),
                    FUEN_NOMBRE, (p, b, t) -> b.fuenNombre = t,
                    FUTI_ID, (p, b, t) -> b.futiId = XmlParsingUtil.parseLong(t),
                    FECHA_MES_INI, (p, b, t) -> b.fechaMes = XmlParsingUtil.parseXmlDateTime(t),
                    FECHA_CREACION, (p, b, t) -> b.fechaCreacion = XmlParsingUtil.parseXmlDateTime(t),
                    CANTIDAD_TON, (p, b, t) -> b.cantidadTon = XmlParsingUtil.parseDecimal(t),
                    ENVIADO, (p, b, t) -> b.enviado = XmlParsingUtil.parseDecimal(t)
            );

    /**
     * Creates an AbasStaxParser for the given input stream.
     *
     * @param inputStream the XML input stream containing SOAP response
     * @throws XMLStreamException if XML stream initialization fails
     */
    public AbasStaxParser(InputStream inputStream) throws XMLStreamException {
        super(inputStream);
    }

    /**
     * Parses a single monthly supply record from XML.
     *
     * @param reader the XML stream reader positioned at a return element
     * @return parsed monthly supply record
     * @throws XMLStreamException if XML parsing fails
     */
    @Override
    protected SipsaAbasRecord parseItem(XMLStreamReader reader) throws XMLStreamException {
        Builder builder = new Builder();
        parseWithHandlers(builder, HANDLERS, END_ELEMENT);
        return builder.build();
    }

    /** Internal builder for constructing {@link SipsaAbasRecord} instances. */
    private static class Builder {
        Long tmpAbasMesId, artiId, fuenId, futiId, fechaMes, fechaCreacion;
        String artiNombre, fuenNombre;
        BigDecimal cantidadTon, enviado;

        /** Builds the immutable monthly supply record from accumulated fields. */
        SipsaAbasRecord build() {
            return new SipsaAbasRecord(tmpAbasMesId, artiId, artiNombre, fuenId,
                    fuenNombre, futiId, fechaMes, fechaCreacion, cantidadTon, enviado);
        }
    }
}

