package com.dalejandrov.sipsa.infrastructure.soap.parser;

import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaMayoristasMensualRecord;
import com.dalejandrov.sipsa.infrastructure.soap.util.XmlParsingUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import static com.dalejandrov.sipsa.infrastructure.soap.parser.XmlFieldNames.*;

public class MesStaxParser extends AbstractStaxParser<SipsaMayoristasMensualRecord> {

    private static final String END_ELEMENT = "return";
    private static final Map<String, TriConsumer<AbstractStaxParser<SipsaMayoristasMensualRecord>, Builder, String>> HANDLERS =
            Map.ofEntries(
                    Map.entry(TMP_MAYO_MES_ID, (p, b, t) -> b.tmpMayoMesId = XmlParsingUtil.parseLong(t)),
                    Map.entry(ARTI_ID, (p, b, t) -> b.artiId = XmlParsingUtil.parseLong(t)),
                    Map.entry(ARTI_NOMBRE, (p, b, t) -> b.artiNombre = t),
                    Map.entry(FUEN_ID, (p, b, t) -> b.fuenId = XmlParsingUtil.parseLong(t)),
                    Map.entry(FUEN_NOMBRE, (p, b, t) -> b.fuenNombre = t),
                    Map.entry(FUTI_ID, (p, b, t) -> b.futiId = XmlParsingUtil.parseLong(t)),
                    Map.entry(FECHA_MES_INI, (p, b, t) -> b.fechaMesIni = XmlParsingUtil.parseXmlDateTime(t)),
                    Map.entry(FECHA_CREACION, (p, b, t) -> b.fechaCreacion = XmlParsingUtil.parseXmlDateTime(t)),
                    Map.entry(MAXIMO_KG, (p, b, t) -> b.maximoKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(MINIMO_KG, (p, b, t) -> b.minimoKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(PROMEDIO_KG, (p, b, t) -> b.promedioKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(ENVIADO, (p, b, t) -> b.enviado = XmlParsingUtil.parseDecimal(t))
            );

    /**
     * Creates a MesStaxParser for the given input stream.
     *
     * @param inputStream the XML input stream containing SOAP response
     * @throws XMLStreamException if XML stream initialization fails
     */
    public MesStaxParser(InputStream inputStream) throws XMLStreamException {
        super(inputStream);
    }

    /**
     * Parses a single monthly wholesale market record from XML.
     *
     * @param reader the XML stream reader positioned at a return element
     * @return parsed monthly wholesale record
     * @throws XMLStreamException if XML parsing fails
     */
    @Override
    protected SipsaMayoristasMensualRecord parseItem(XMLStreamReader reader) throws XMLStreamException {
        Builder builder = new Builder();
        parseWithHandlers(builder, HANDLERS, END_ELEMENT);
        return builder.build();
    }

    /** Internal builder for constructing {@link SipsaMayoristasMensualRecord} instances. */
    private static class Builder {
        Long tmpMayoMesId, artiId, fuenId, futiId, fechaMesIni, fechaCreacion;
        String artiNombre, fuenNombre;
        BigDecimal minimoKg, maximoKg, promedioKg, enviado;

        /** Builds the immutable monthly wholesale record from accumulated fields. */
        SipsaMayoristasMensualRecord build() {
            return new SipsaMayoristasMensualRecord(tmpMayoMesId, artiId, artiNombre, fuenId,
                    fuenNombre, futiId, fechaMesIni, fechaCreacion, minimoKg, maximoKg, promedioKg, enviado);
        }
    }
}

