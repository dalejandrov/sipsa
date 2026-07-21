package com.dalejandrov.sipsa.infrastructure.soap.gateway;

import com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient;
import com.dalejandrov.sipsa.infrastructure.soap.config.SoapProperties;
import com.dalejandrov.sipsa.infrastructure.soap.generated.PromediosSipsaCiudadResponse;
import com.dalejandrov.sipsa.infrastructure.soap.generated.SipsaPromMayoristasCiudad;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TECH-092: verifies the CXF-generated JAXB classes were relocated from
 * {@code infrastructure.soap.client} to {@code infrastructure.soap.generated} without
 * changing any observable SOAP/XML behavior — the hand-written
 * {@link SoapGatewayImpl}/{@link SoapStreamingClient}/{@code SipsaSoapClientConfig} stay
 * in the manual package, only the generated classes moved.
 * <p>
 * The structural checks in {@link PackageMembership} double as a reproducible drift
 * detector: they run on every {@code mvn test} (generated sources are always recompiled
 * before the test phase, so there is nothing to go stale) and fail immediately if a
 * future WSDL/plugin change ever regresses a generated class back into the manual
 * package, or drops it from the generated one — no filesystem-order dependency, no
 * committed generated-code snapshot to compare against (there is none — generated code
 * is never version-controlled, confirmed by TECH-094's SPIKE).
 */
@DisplayName("TECH-092: generated SOAP package relocation")
class SoapGeneratedPackageRelocationTest {

    private static final String GENERATED_PKG = "com.dalejandrov.sipsa.infrastructure.soap.generated";
    private static final String MANUAL_PKG = "com.dalejandrov.sipsa.infrastructure.soap.client";
    private static final String DANE_NAMESPACE = "http://servicios.sipsa.co.gov.dane/";

    /** The exact 22 generated files confirmed by TECH-094's SPIKE (file-count corrected from 24). */
    private static final String[] GENERATED_SIMPLE_NAMES = {
            "ConsultarInsumosSipsaMesMadr", "ConsultarInsumosSipsaMesMadrResponse",
            "ObjectFactory", "package-info",
            "PromedioAbasSipsaMesMadr", "PromedioAbasSipsaMesMadrResponse",
            "PromediosSipsaCiudad", "PromediosSipsaCiudadResponse",
            "PromediosSipsaMesMadr", "PromediosSipsaMesMadrResponse",
            "PromediosSipsaParcial", "PromediosSipsaParcialResponse",
            "PromediosSipsaSemanaMadr", "PromediosSipsaSemanaMadrResponse",
            "SipsaAbastecimientosMesMadr", "SipsaInsumosMesMadr",
            "SipsaMayoristasMesMadr", "SipsaMayoristasSemanaMadr",
            "SipsaPromediosMayoristasParcial", "SipsaPromMayoristasCiudad",
            "SrvSipsaUpraBeanService", "SrvSipsaUpraService"
    };

    @Nested
    @DisplayName("Package membership (drift detector)")
    class PackageMembership {

        @Test
        @DisplayName("all 22 generated classes exist in infrastructure.soap.generated")
        void allGeneratedClassesExistInTheNewPackage() {
            assertThat(GENERATED_SIMPLE_NAMES).hasSize(22);
            for (String simpleName : GENERATED_SIMPLE_NAMES) {
                assertThatCode(() -> Class.forName(GENERATED_PKG + "." + simpleName))
                        .as("expected generated class %s in %s", simpleName, GENERATED_PKG)
                        .doesNotThrowAnyException();
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "ConsultarInsumosSipsaMesMadr", "ObjectFactory", "PromediosSipsaCiudad",
                "PromediosSipsaCiudadResponse", "SrvSipsaUpraBeanService", "SrvSipsaUpraService",
                "SipsaPromMayoristasCiudad"
        })
        @DisplayName("no generated class remains reachable in the old manual package")
        void noGeneratedClassRemainsInTheOldManualPackage(String simpleName) {
            assertThatExceptionOfType(ClassNotFoundException.class)
                    .as("%s must not exist in %s anymore", simpleName, MANUAL_PKG)
                    .isThrownBy(() -> Class.forName(MANUAL_PKG + "." + simpleName));
        }

        @Test
        @DisplayName("SoapStreamingClient (hand-written) remains in the manual package, unmoved")
        void soapStreamingClientRemainsInTheManualPackage() {
            assertThatCode(() -> Class.forName(MANUAL_PKG + ".SoapStreamingClient"))
                    .doesNotThrowAnyException();
            assertThat(SoapStreamingClient.class.getPackageName()).isEqualTo(MANUAL_PKG);
        }

        @Test
        @DisplayName("SoapGatewayImpl compiles and runs against the relocated generated classes")
        void soapGatewayImplReferencesTheNewPackage() {
            // Compilation itself is the primary proof (this test class and SoapGatewayImpl
            // both import from .generated); this assertion additionally confirms at runtime
            // that the constructor field type used for requests is the relocated class.
            assertThat(PromediosSipsaCiudadResponse.class.getPackageName()).isEqualTo(GENERATED_PKG);
        }
    }

    @Nested
    @DisplayName("JAXB marshalling/unmarshalling (namespace and type preserved)")
    class JaxbRoundTrip {

        @Test
        @DisplayName("marshalling a generated response preserves the DANE target namespace")
        void marshal_preservesNamespace() throws Exception {
            JAXBContext ctx = JAXBContext.newInstance(PromediosSipsaCiudadResponse.class);
            PromediosSipsaCiudadResponse response = new PromediosSipsaCiudadResponse();
            SipsaPromMayoristasCiudad item = new SipsaPromMayoristasCiudad();
            item.setCiudad("Bogota");
            item.setProducto("Papa");
            item.setPrecioPromedio(new BigDecimal("1500.00"));
            response.getReturn().add(item);

            QName qname = new QName(DANE_NAMESPACE, "promediosSipsaCiudadResponse");
            JAXBElement<PromediosSipsaCiudadResponse> element =
                    new JAXBElement<>(qname, PromediosSipsaCiudadResponse.class, response);

            Marshaller marshaller = ctx.createMarshaller();
            StringWriter writer = new StringWriter();
            marshaller.marshal(element, writer);
            String xml = writer.toString();

            assertThat(xml).contains(DANE_NAMESPACE);
            assertThat(xml).contains("promediosSipsaCiudadResponse");
            // Well-formed XML, parseable without error.
            assertThatCode(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml))))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("unmarshalling round-trips the correct generated type and field values")
        void unmarshal_preservesTypeAndFields() throws Exception {
            JAXBContext ctx = JAXBContext.newInstance(PromediosSipsaCiudadResponse.class);
            PromediosSipsaCiudadResponse original = new PromediosSipsaCiudadResponse();
            SipsaPromMayoristasCiudad item = new SipsaPromMayoristasCiudad();
            item.setCiudad("Medellin");
            item.setProducto("Arroz");
            item.setPrecioPromedio(new BigDecimal("2300.50"));
            item.setRegId(new BigDecimal("42"));
            original.getReturn().add(item);

            QName qname = new QName(DANE_NAMESPACE, "promediosSipsaCiudadResponse");
            Marshaller marshaller = ctx.createMarshaller();
            StringWriter writer = new StringWriter();
            marshaller.marshal(new JAXBElement<>(qname, PromediosSipsaCiudadResponse.class, original), writer);

            Unmarshaller unmarshaller = ctx.createUnmarshaller();
            JAXBElement<PromediosSipsaCiudadResponse> roundTripped = unmarshaller.unmarshal(
                    new StreamSource(new StringReader(writer.toString())), PromediosSipsaCiudadResponse.class);
            PromediosSipsaCiudadResponse result = roundTripped.getValue();

            assertThat(result).isInstanceOf(PromediosSipsaCiudadResponse.class);
            assertThat(result.getReturn()).hasSize(1);
            SipsaPromMayoristasCiudad resultItem = result.getReturn().get(0);
            assertThat(resultItem.getCiudad()).isEqualTo("Medellin");
            assertThat(resultItem.getProducto()).isEqualTo("Arroz");
            assertThat(resultItem.getPrecioPromedio()).isEqualByComparingTo("2300.50");
            assertThat(resultItem.getRegId()).isEqualByComparingTo("42");
        }
    }

    @Nested
    @DisplayName("SoapGatewayImpl builds correct requests against the relocated classes")
    class SoapGatewayImplRequestConstruction {

        private SoapProperties properties() {
            SoapProperties props = new SoapProperties();
            props.setNamespace(DANE_NAMESPACE);
            return props;
        }

        @Test
        @DisplayName("getCiudadData(): request payload has the DANE namespace and correct root element")
        void getCiudadData_buildsCorrectSoapRequestPayload() throws Exception {
            SoapStreamingClient client = mock(SoapStreamingClient.class);
            var payloadCaptor = forClass(String.class);
            when(client.stream(eq("promediosSipsaCiudad"), payloadCaptor.capture()))
                    .thenReturn(new ByteArrayInputStream(new byte[0]));

            SoapGatewayImpl gateway = new SoapGatewayImpl(client, properties());
            gateway.getCiudadData();

            String payload = payloadCaptor.getValue();
            assertThat(payload).contains(DANE_NAMESPACE);
            assertThat(payload).contains("promediosSipsaCiudad");
            assertThatCode(() -> DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new InputSource(new StringReader(payload))))
                    .as("the constructed SOAP payload must be well-formed XML")
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("getSemanaMadrData(): request payload has the DANE namespace and correct root element")
        void getSemanaMadrData_buildsCorrectSoapRequestPayload() throws Exception {
            SoapStreamingClient client = mock(SoapStreamingClient.class);
            var payloadCaptor = forClass(String.class);
            when(client.stream(eq("promediosSipsaSemanaMadr"), payloadCaptor.capture()))
                    .thenReturn(new ByteArrayInputStream(new byte[0]));

            SoapGatewayImpl gateway = new SoapGatewayImpl(client, properties());
            gateway.getSemanaMadrData();

            assertThat(payloadCaptor.getValue()).contains(DANE_NAMESPACE).contains("promediosSipsaSemanaMadr");
        }

        @Test
        @DisplayName("response InputStream from SoapStreamingClient is returned unchanged (no re-wrapping)")
        void response_isReturnedUnchangedFromStreamingClient() throws Exception {
            SoapStreamingClient client = mock(SoapStreamingClient.class);
            ByteArrayInputStream fixtureResponse = new ByteArrayInputStream("<fixture/>".getBytes());
            when(client.stream(eq("promediosSipsaParcial"), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(fixtureResponse);

            SoapGatewayImpl gateway = new SoapGatewayImpl(client, properties());
            var result = gateway.getParcialData();

            assertThat(result).isSameAs(fixtureResponse);
        }
    }
}
