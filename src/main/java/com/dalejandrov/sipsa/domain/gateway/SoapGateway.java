package com.dalejandrov.sipsa.domain.gateway;

import com.dalejandrov.sipsa.infrastructure.soap.gateway.SoapGatewayImpl;

import java.io.InputStream;

/**
 * Gateway interface for accessing SIPSA SOAP web services.
 * <p>
 * This interface defines the contract for retrieving agricultural price and supply data
 * from external SOAP services. It provides:
 * <ul>
 *   <li>Business-oriented methods abstracting SOAP implementation details</li>
 *   <li>Streaming data access for memory-efficient processing</li>
 *   <li>Separation of domain logic from infrastructure concerns</li>
 * </ul>
 * <p>
 * <b>Implementation:</b><br>
 * The actual SOAP communication is handled by infrastructure layer implementations
 * (typically using Apache CXF or similar SOAP client libraries).
 * <p>
 * <b>Data Format:</b><br>
 * All methods return XML data as InputStreams for StAX parsing, allowing
 * processing of large datasets without loading entire responses into memory.
 * <p>
 * <b>Error Handling:</b><br>
 * Implementations should throw {@link com.dalejandrov.sipsa.domain.exception.SipsaExternalException}
 * for any SOAP service failures, network errors, or timeout issues.
 * <p>
 * <b>SOAP Methods Mapping:</b>
 * <ul>
 *   <li>{@link #getCiudadData()} → {@code promediosSipsaCiudad}</li>
 *   <li>{@link #getParcialData()} → {@code promediosSipsaParcial}</li>
 *   <li>{@link #getSemanaMadrData()} → {@code promediosSipsaSemanaMadr}</li>
 *   <li>{@link #getMesMadrData()} → {@code promediosSipsaMesMadr}</li>
 *   <li>{@link #getAbastecimientosMensualData()} → {@code promedioAbasSipsaMesMadr}</li>
 * </ul>
 *
 * @see SoapGatewayImpl
 */
public interface SoapGateway {

    /**
     * Retrieves monthly supply data for wholesale markets.
     * <p>
     * Calls SOAP method: {@code promedioAbasSipsaMesMadr}
     * <p>
     * Returns data about product supply volumes (in tons) delivered to
     * wholesale markets on a monthly basis.
     *
     * @return InputStream containing XML response data
     * @throws com.dalejandrov.sipsa.domain.exception.SipsaExternalException if SOAP call fails
     */
    InputStream getAbastecimientosMensualData();

    /**
     * Retrieves city-level pricing data.
     * <p>
     * Calls SOAP method: {@code promediosSipsaCiudad}
     * <p>
     * Returns daily price information collected at city level,
     * including average prices per product across different cities.
     *
     * @return InputStream containing XML response data
     * @throws com.dalejandrov.sipsa.domain.exception.SipsaExternalException if SOAP call fails
     */
    InputStream getCiudadData();

    /**
     * Retrieves monthly wholesale market pricing data from Madrid markets.
     * <p>
     * Calls SOAP method: {@code promediosSipsaMesMadr}
     * <p>
     * Returns monthly aggregated wholesale market prices, including
     * minimum, maximum, and average prices per product.
     *
     * @return InputStream containing XML response data
     * @throws com.dalejandrov.sipsa.domain.exception.SipsaExternalException if SOAP call fails
     */
    InputStream getMesMadrData();

    /**
     * Retrieves partial market data by municipality.
     * <p>
     * Calls SOAP method: {@code promediosSipsaParcial}
     * <p>
     * Returns detailed market information at municipality level,
     * including price ranges and product availability.
     *
     * @return InputStream containing XML response data
     * @throws com.dalejandrov.sipsa.domain.exception.SipsaExternalException if SOAP call fails
     */
    InputStream getParcialData();

    /**
     * Retrieves weekly wholesale market pricing data.
     * <p>
     * Calls SOAP method: {@code promediosSipsaSemanaMadr}
     * <p>
     * Returns weekly aggregated wholesale market prices, including
     * price statistics per product and market.
     *
     * @return InputStream containing XML response data
     * @throws com.dalejandrov.sipsa.domain.exception.SipsaExternalException if SOAP call fails
     */
    InputStream getSemanaMadrData();
}
