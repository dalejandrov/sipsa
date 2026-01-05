package com.dalejandrov.sipsa.infrastructure.soap.gateway;

import com.dalejandrov.sipsa.domain.exception.SipsaIngestionException;
import com.dalejandrov.sipsa.domain.gateway.SoapGateway;
import com.dalejandrov.sipsa.infrastructure.soap.client.*;
import com.dalejandrov.sipsa.infrastructure.soap.config.SoapProperties;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import java.io.InputStream;
import java.io.StringWriter;

/**
 * Implementation of {@link SoapGateway} using JAXB marshalling and HTTP streaming.
 * <p>
 * This class provides the concrete implementation for accessing SIPSA SOAP web services.
 * It handles:
 * <ul>
 *   <li>SOAP request construction using JAXB</li>
 *   <li>HTTP communication via {@link SoapStreamingClient}</li>
 *   <li>Response streaming for memory-efficient processing</li>
 *   <li>Error handling and exception translation</li>
 * </ul>
 * <p>
 * <b>Design Pattern:</b><br>
 * Implements the Gateway pattern, isolating domain logic from infrastructure concerns.
 * <p>
 * <b>JAXB Context:</b><br>
 * Initializes once at startup with all request types for performance.
 * <p>
 * <b>Error Handling:</b><br>
 * Translates SOAP/HTTP exceptions to {@link SipsaIngestionException} for
 * consistent error handling across the application.
 *
 * @see SoapGateway
 * @see SoapStreamingClient
 * @see SoapProperties
 */
@Component
@Slf4j
public class SoapGatewayImpl implements SoapGateway {

    private final SoapStreamingClient soapClient;
    private final JAXBContext jaxbContext;
    private final SoapProperties soapProperties;

    /**
     * Creates the SOAP gateway with required dependencies.
     * <p>
     * Initializes JAXB context with all SOAP request types for marshalling.
     * This is done once at startup for performance.
     *
     * @param soapClient HTTP client for streaming SOAP responses
     * @param soapProperties configuration properties for SOAP service
     * @throws JAXBException if JAXB context initialization fails
     */
    public SoapGatewayImpl(SoapStreamingClient soapClient, SoapProperties soapProperties) throws JAXBException {
        this.soapClient = soapClient;
        this.soapProperties = soapProperties;
        this.jaxbContext = JAXBContext.newInstance(
            PromedioAbasSipsaMesMadr.class,
            PromediosSipsaCiudad.class,
            PromediosSipsaMesMadr.class,
            PromediosSipsaParcial.class,
            PromediosSipsaSemanaMadr.class,
            ConsultarInsumosSipsaMesMadr.class
        );
    }

    /**
     * Retrieves monthly supply data stream from SOAP service.
     * <p>
     * Calls: {@code promedioAbasSipsaMesMadr}
     *
     * @return InputStream containing XML response data
     * @throws SipsaIngestionException if SOAP call fails
     */
    @Override
    public InputStream getAbastecimientosMensualData() {
        try {
            String payload = marshalRequest(new PromedioAbasSipsaMesMadr());
            return soapClient.stream("promedioAbasSipsaMesMadr", payload);
        } catch (Exception e) {
            throw new SipsaIngestionException("Failed to retrieve abastecimientos mensual data", e);
        }
    }

    /**
     * Retrieves city pricing data stream from SOAP service.
     * <p>
     * Calls: {@code promediosSipsaCiudad}
     *
     * @return InputStream containing XML response data
     * @throws SipsaIngestionException if SOAP call fails
     */
    @Override
    public InputStream getCiudadData() {
        try {
            String payload = marshalRequest(new PromediosSipsaCiudad());
            return soapClient.stream("promediosSipsaCiudad", payload);
        } catch (Exception e) {
            throw new SipsaIngestionException("Failed to retrieve ciudad data", e);
        }
    }

    /**
     * Retrieves monthly wholesale market pricing data.
     * <p>
     * Calls: {@code promediosSipsaMesMadr}
     *
     * @return InputStream containing XML response data
     * @throws SipsaIngestionException if SOAP call fails
     */
    @Override
    public InputStream getMesMadrData() {
        try {
            String payload = marshalRequest(new PromediosSipsaMesMadr());
            return soapClient.stream("promediosSipsaMesMadr", payload);
        } catch (Exception e) {
            throw new SipsaIngestionException("Failed to retrieve mes madr data", e);
        }
    }

    /**
     * Retrieves partial market data stream from SOAP service.
     * <p>
     * Calls: {@code promediosSipsaParcial}
     *
     * @return InputStream containing XML response data
     * @throws SipsaIngestionException if SOAP call fails
     */
    @Override
    public InputStream getParcialData() {
        try {
            String payload = marshalRequest(new PromediosSipsaParcial());
            return soapClient.stream("promediosSipsaParcial", payload);
        } catch (Exception e) {
            throw new SipsaIngestionException("Failed to retrieve parcial data", e);
        }
    }

    /**
     * Retrieves weekly wholesale market data stream from SOAP service.
     * <p>
     * Calls: {@code promediosSipsaSemanaMadr}
     *
     * @return InputStream containing XML response data
     * @throws SipsaIngestionException if SOAP call fails
     */
    @Override
    public InputStream getSemanaMadrData() {
        try {
            String payload = marshalRequest(new PromediosSipsaSemanaMadr());
            return soapClient.stream("promediosSipsaSemanaMadr", payload);
        } catch (Exception e) {
            throw new SipsaIngestionException("Failed to retrieve semana madr data", e);
        }
    }

    /**
     * Marshals a SOAP request object to XML string using JAXB.
     * <p>
     * Creates a SOAP envelope with the request wrapped in a JAXBElement.
     * The result is a complete SOAP XML payload ready for HTTP POST.
     *
     * @param request the SOAP request object (e.g., PromediosSipsaCiudad)
     * @return XML string representation of the SOAP request
     * @throws JAXBException if marshalling fails
     */
    private String marshalRequest(Object request) throws JAXBException {
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        StringWriter writer = new StringWriter();

        String localName = request.getClass().getSimpleName();
        localName = Character.toLowerCase(localName.charAt(0)) + localName.substring(1);
        QName qname = new QName(soapProperties.getNamespace(), localName);
        @SuppressWarnings("unchecked")
        JAXBElement<Object> element = new JAXBElement<>(qname, (Class<Object>) request.getClass(), request);

        marshaller.marshal(element, writer);
        return writer.toString();
    }
}
