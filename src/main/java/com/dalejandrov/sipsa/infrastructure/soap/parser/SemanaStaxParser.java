package com.dalejandrov.sipsa.infrastructure.soap.parser;

import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaSemanaRecord;
import com.dalejandrov.sipsa.infrastructure.soap.util.XmlParsingUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import static com.dalejandrov.sipsa.infrastructure.soap.parser.XmlFieldNames.*;

public class SemanaStaxParser extends AbstractStaxParser<SipsaSemanaRecord> {

    private static final String END_ELEMENT = "return";
    private static final Map<String, TriConsumer<AbstractStaxParser<SipsaSemanaRecord>, Builder, String>> HANDLERS =
            Map.ofEntries(
                    Map.entry(TMP_MAYO_SEM_ID, (p, b, t) -> b.tmpMayoSemId = XmlParsingUtil.parseLong(t)),
                    Map.entry(ARTI_ID, (p, b, t) -> b.artiId = XmlParsingUtil.parseLong(t)),
                    Map.entry(ARTI_NOMBRE, (p, b, t) -> b.artiNombre = t),
                    Map.entry(FUEN_ID, (p, b, t) -> b.fuenId = XmlParsingUtil.parseLong(t)),
                    Map.entry(FUEN_NOMBRE, (p, b, t) -> b.fuenNombre = t),
                    Map.entry(FUTI_ID, (p, b, t) -> b.futiId = XmlParsingUtil.parseLong(t)),
                    Map.entry(FECHA_INI, (p, b, t) -> b.fechaIni = XmlParsingUtil.parseXmlDateTime(t)),
                    Map.entry(FECHA_CREACION, (p, b, t) -> b.fechaCreacion = XmlParsingUtil.parseXmlDateTime(t)),
                    Map.entry(MAXIMO_KG, (p, b, t) -> b.maximoKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(MINIMO_KG, (p, b, t) -> b.minimoKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(PROMEDIO_KG, (p, b, t) -> b.promedioKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(ENVIADO, (p, b, t) -> b.enviado = XmlParsingUtil.parseDecimal(t))
            );

    /**
     * Creates a SemanaStaxParser for the given input stream.
     *
     * @param inputStream the XML input stream containing SOAP response
     * @throws XMLStreamException if XML stream initialization fails
     */
    public SemanaStaxParser(InputStream inputStream) throws XMLStreamException {
        super(inputStream);
    }

    /**
     * Parses a single weekly wholesale market record from XML.
     *
     * @param reader the XML stream reader positioned at a return element
     * @return parsed weekly wholesale record
     * @throws XMLStreamException if XML parsing fails
     */
    @Override
    protected SipsaSemanaRecord parseItem(XMLStreamReader reader) throws XMLStreamException {
        Builder builder = new Builder();
        parseWithHandlers(builder, HANDLERS, END_ELEMENT);
        return builder.build();
    }

    /** Internal builder for constructing {@link SipsaSemanaRecord} instances. */
    private static class Builder {
        Long tmpMayoSemId, artiId, fuenId, futiId, fechaIni, fechaCreacion;
        String artiNombre, fuenNombre;
        BigDecimal minimoKg, maximoKg, promedioKg, enviado;

        /** Builds the immutable weekly wholesale record from accumulated fields. */
        SipsaSemanaRecord build() {
            return new SipsaSemanaRecord(tmpMayoSemId, artiId, artiNombre, fuenId,
                    fuenNombre, futiId, fechaIni, fechaCreacion, minimoKg, maximoKg, promedioKg, enviado);
        }
    }
}

