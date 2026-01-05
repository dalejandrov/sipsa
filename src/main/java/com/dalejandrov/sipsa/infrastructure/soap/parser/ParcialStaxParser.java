package com.dalejandrov.sipsa.infrastructure.soap.parser;

import com.dalejandrov.sipsa.infrastructure.soap.dto.SipsaParcialRecord;
import com.dalejandrov.sipsa.infrastructure.soap.util.XmlParsingUtil;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Map;

import static com.dalejandrov.sipsa.infrastructure.soap.parser.XmlFieldNames.*;

public class ParcialStaxParser extends AbstractStaxParser<SipsaParcialRecord> {

    private static final String END_ELEMENT = "return";
    private static final Map<String, TriConsumer<AbstractStaxParser<SipsaParcialRecord>, Builder, String>> HANDLERS =
            Map.ofEntries(
                    Map.entry(MUNI_ID, (p, b, t) -> b.muniId = t),
                    Map.entry(MUNI_NOMBRE, (p, b, t) -> b.muniNombre = t),
                    Map.entry(DEPT_NOMBRE, (p, b, t) -> b.deptNombre = t),
                    Map.entry(FUEN_ID, (p, b, t) -> b.fuenId = XmlParsingUtil.parseLong(t)),
                    Map.entry(FUEN_NOMBRE, (p, b, t) -> b.fuenNombre = t),
                    Map.entry(FUTI_ID, (p, b, t) -> b.futiId = XmlParsingUtil.parseLong(t)),
                    Map.entry(ARTI_NOMBRE, (p, b, t) -> b.artiNombre = t),
                    Map.entry(GRUP_NOMBRE, (p, b, t) -> b.grupNombre = t),
                    Map.entry(ID_ARTI_SEMANA, (p, b, t) -> b.idArtiSemana = XmlParsingUtil.parseLong(t)),
                    Map.entry(ENMA_FECHA, (p, b, t) -> b.enmaFecha = t),
                    Map.entry(PROMEDIO_KG, (p, b, t) -> b.promedioKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(MAXIMO_KG, (p, b, t) -> b.maximoKg = XmlParsingUtil.parseDecimal(t)),
                    Map.entry(MINIMO_KG, (p, b, t) -> b.minimoKg = XmlParsingUtil.parseDecimal(t))
            );

    /**
     * Creates a ParcialStaxParser for the given input stream.
     *
     * @param inputStream the XML input stream containing SOAP response
     * @throws XMLStreamException if XML stream initialization fails
     */
    public ParcialStaxParser(InputStream inputStream) throws XMLStreamException {
        super(inputStream);
    }

    /**
     * Parses a single partial municipal market record from XML.
     * <p>
     * Extracts field values using handler-based parsing and constructs
     * a {@link SipsaParcialRecord} with the parsed data.
     *
     * @param reader the XML stream reader positioned at a return element
     * @return parsed partial market record
     * @throws XMLStreamException if XML parsing fails
     */
    @Override
    protected SipsaParcialRecord parseItem(XMLStreamReader reader) throws XMLStreamException {
        Builder builder = new Builder();
        parseWithHandlers(builder, HANDLERS, END_ELEMENT);
        return builder.build();
    }

    /**
     * Internal builder for constructing {@link SipsaParcialRecord} instances.
     * <p>
     * Accumulates field values during parsing before creating the immutable record.
     * Handles field aliasing where some XML fields map to multiple record fields.
     */
    private static class Builder {
        String muniId, muniNombre, deptNombre, fuenNombre, artiNombre, grupNombre, enmaFecha;
        Long fuenId, futiId, idArtiSemana;
        BigDecimal promedioKg, maximoKg, minimoKg;

        /**
         * Builds the immutable partial market record from accumulated fields.
         * <p>
         * Post-processes dual-mapped fields where the same XML element
         * is used for multiple purposes in the domain model.
         *
         * @return constructed SipsaParcialRecord
         */
        SipsaParcialRecord build() {
            Long artiId = idArtiSemana;
            String fechaEncuestaText = enmaFecha;

            return new SipsaParcialRecord(muniId, muniNombre, deptNombre, fuenId, fuenNombre,
                    futiId, artiId, artiNombre, grupNombre, fechaEncuestaText, idArtiSemana,
                    enmaFecha, promedioKg, maximoKg, minimoKg);
        }
    }
}

