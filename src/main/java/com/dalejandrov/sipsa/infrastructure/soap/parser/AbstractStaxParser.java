package com.dalejandrov.sipsa.infrastructure.soap.parser;

import com.dalejandrov.sipsa.domain.exception.SipsaParseException;
import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.infrastructure.soap.util.XmlParsingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Abstract base class for StAX-based XML parsers.
 * Provides common parsing infrastructure and reusable methods.
 *
 * @param <T> The type of record produced by the parser
 */
public abstract class AbstractStaxParser<T> implements Iterator<T> {

    private static final String RETURN_ELEMENT = "return";
    private static final String FAULT_ELEMENT = "Fault";
    private static final String FAULT_TEXT_ELEMENT = "Text";
    private static final String FAULT_STRING_ELEMENT = "faultstring";

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final XMLStreamReader reader;
    protected T nextItem;
    protected boolean finished = false;

    protected AbstractStaxParser(InputStream inputStream) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        this.reader = factory.createXMLStreamReader(inputStream);
        advance();
    }

    @Override
    public boolean hasNext() {
        return nextItem != null;
    }

    @Override
    public T next() {
        if (nextItem == null) {
            throw new NoSuchElementException();
        }
        T current = nextItem;
        advance();
        return current;
    }

    protected void advance() {
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    if (FAULT_ELEMENT.equalsIgnoreCase(localName)) {
                        handleSoapFault();
                    }
                    if (RETURN_ELEMENT.equalsIgnoreCase(localName)) {
                        try {
                            nextItem = parseItem(reader);
                            return;
                        } catch (SipsaParseException e) {
                            // SipsaParseException already has proper message, just re-throw
                            logger.error("Parse error: {}", e.getMessage());
                            closeReaderSafely();
                            nextItem = null;
                            finished = true;
                            throw e;
                        } catch (Exception e) {
                            logger.error("Error parsing XML item: {}", e.getMessage(), e);
                            closeReaderSafely();
                            nextItem = null;
                            finished = true;
                            throw new SipsaParseException("Error parsing item: " + e.getMessage(), e);
                        }
                    }
                }
            }
            nextItem = null;
            finished = true;
            reader.close();
        } catch (XMLStreamException e) {
            nextItem = null;
            finished = true;
            String message = e.getMessage();

            // Check if it's a stream closed error (common with chunked transfer encoding)
            if (message != null && (message.contains("closed") || message.contains("Stream closed"))) {
                logger.warn("Stream closed unexpectedly during parsing: {}", message);
                throw new SipsaParseException("Stream closed during parsing: " + message, e);
            }

            // Check if it's an IO exception wrapped in XMLStreamException
            Throwable cause = e.getCause();
            if (cause instanceof java.io.IOException) {
                logger.warn("IO error during parsing: {}", cause.getMessage());
                throw new SipsaParseException("IO error during parsing: " + cause.getMessage(), e);
            }

            throw new SipsaParseException("XML Stream Error: " + message, e);
        }
    }

    /**
     * Safely closes the reader, ignoring any exceptions.
     */
    private void closeReaderSafely() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (Exception e) {
            logger.debug("Error closing reader: {}", e.getMessage());
        }
    }

    private void handleSoapFault() throws XMLStreamException {
        String faultString = "Unknown Fault";
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                String elementName = reader.getLocalName();
                if (FAULT_TEXT_ELEMENT.equals(elementName) || FAULT_STRING_ELEMENT.equals(elementName)) {
                    faultString = reader.getElementText();
                    break;
                }
            }
            if (event == XMLStreamConstants.END_ELEMENT && FAULT_ELEMENT.equals(reader.getLocalName())) {
                break;
            }
        }
        logger.error("SOAP Fault: {}", faultString);
        throw new SipsaExternalException("SOAP Fault in response: " + faultString);
    }

    /**
     * Generic parsing loop using handler map.
     * Handlers receive (parser, builder, text) for maximum flexibility without coupling.
     *
     * @param <B> Builder type
     * @param builder The builder to populate
     * @param handlers Immutable map of field name to handler (TriConsumer)
     * @param endElementName The closing element name (e.g., "return")
     */
    protected <B> void parseWithHandlers(
            B builder,
            Map<String, TriConsumer<AbstractStaxParser<T>, B, String>> handlers,
            String endElementName) throws XMLStreamException {

        while (!finished && reader.hasNext()) {
            int event = reader.next();

            if (event == XMLStreamConstants.END_ELEMENT
                    && endElementName.equalsIgnoreCase(reader.getLocalName())) {
                break;
            }

            if (event == XMLStreamConstants.START_ELEMENT) {
                String fieldName = reader.getLocalName().toLowerCase(Locale.ROOT);
                String text = readElementTextSafe();

                if (text == null || text.isBlank()) {
                    continue;
                }

                TriConsumer<AbstractStaxParser<T>, B, String> handler = handlers.get(fieldName);
                if (handler != null) {
                    handler.accept(this, builder, text);
                }
            }
        }
    }

    private String readElementTextSafe() {
        try {
            return reader.getElementText();
        } catch (XMLStreamException e) {
            // Check if the error is due to closed stream - this is critical and should fail fast
            String message = e.getMessage();
            if (message != null && (message.contains("closed") || message.contains("Stream closed"))) {
                logger.error("Stream closed unexpectedly during parsing: {}", message);
                throw new SipsaParseException("Stream closed during parsing: " + message, e);
            }
            // For other errors (empty text, malformed content), log and return null
            logger.trace("Failed to read element text: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Template method for parsing a single record from XML.
     * <p>
     * Subclasses must implement this method to define entity-specific parsing logic.
     * The reader is positioned at the start of a {@code <return>} element when called.
     * <p>
     * <b>Implementation Note:</b><br>
     * Use {@link XmlParsingUtil} for safe type conversions:
     * <ul>
     *   <li>{@link XmlParsingUtil#parseLong(String)} for Long fields</li>
     *   <li>{@link XmlParsingUtil#parseDecimal(String)} for BigDecimal fields</li>
     *   <li>{@link XmlParsingUtil#parseXmlDateTime(String)} for date/time fields</li>
     * </ul>
     *
     * @param reader the XML stream reader positioned at a {@code <return>} element
     * @return parsed record of type T
     * @throws XMLStreamException if XML parsing fails
     */

    protected abstract T parseItem(XMLStreamReader reader) throws XMLStreamException;

    /**
     * Functional interface for handlers that need parser, builder, and text.
     */
    @FunctionalInterface
    protected interface TriConsumer<P, B, T> {
        void accept(P parser, B builder, T text);
    }
}

