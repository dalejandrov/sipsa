package com.dalejandrov.sipsa.infrastructure.soap.client;

import com.dalejandrov.sipsa.domain.exception.SipsaExternalException;
import com.dalejandrov.sipsa.infrastructure.soap.config.SoapProperties;
import com.dalejandrov.sipsa.infrastructure.soap.gateway.SoapGatewayImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * High-performance streaming HTTP client for SOAP web services.
 * <p>
 * This client bypasses traditional JAX-WS marshalling to enable direct streaming
 * of SOAP responses, allowing memory-efficient processing of large XML datasets
 * using StAX parsers.
 * <p>
 * <b>Key Features:</b>
 * <ul>
 *   <li>HTTP streaming with Java's native HttpClient (HTTP/1.1)</li>
 *   <li>Automatic GZIP decompression</li>
 *   <li>Exponential backoff retry strategy</li>
 *   <li>SOAP fault detection (handled by parsers)</li>
 *   <li>Configurable timeouts and retry policies</li>
 * </ul>
 * <p>
 * <b>Design Rationale:</b><br>
 * Traditional JAX-WS clients load entire responses into memory before unmarshalling,
 * which is problematic for SIPSA's large datasets (10K+ records). This streaming
 * approach processes data incrementally using StAX, reducing memory footprint
 * from GBs to MBs.
 * <p>
 * <b>Error Handling:</b><br>
 * Retries automatically on transient errors (5xx, timeouts) with exponential
 * backoff. Non-retryable errors (4xx) fail immediately. SOAP faults are
 * detected by the StAX parsers during XML processing.
 *
 * @see SoapProperties
 * @see SoapGatewayImpl
 * @see com.dalejandrov.sipsa.infrastructure.soap.parser.AbstractStaxParser
 */
@Service
@Slf4j
public class SoapStreamingClient {

    private final SoapProperties soapProperties;
    private final HttpClient httpClient;

    /**
     * Creates the SOAP streaming client with configured properties.
     * <p>
     * Initializes a reusable HttpClient with connection pooling and
     * configured timeouts for optimal performance. Uses HTTP/1.1 for
     * better compatibility with legacy SOAP services and long-running
     * chunked transfers.
     *
     * @param soapProperties configuration properties for SOAP service
     */
    public SoapStreamingClient(SoapProperties soapProperties) {
        this.soapProperties = soapProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(soapProperties.getConnectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)  // HTTP/1.1 for better chunked transfer support
                .build();
    }

    /**
     * Streams the SOAP response for a given payload with automatic retry logic.
     * <p>
     * This method:
     * <ol>
     *   <li>Wraps the payload in a SOAP 1.2 envelope</li>
     *   <li>Sends HTTP POST request to SOAP endpoint</li>
     *   <li>Returns streamed response as InputStream</li>
     *   <li>Retries on transient errors (5xx, timeouts) with exponential backoff</li>
     * </ol>
     * <p>
     * <b>Retry Strategy:</b>
     * <ul>
     *   <li>Max retries: Configurable via {@code sipsa.soap.max-retries} (default 3)</li>
     *   <li>Backoff: Exponential ({@code retryBackoffMs * 2^attempt})</li>
     *   <li>Retryable: 5xx errors, timeouts, network issues</li>
     *   <li>Non-retryable: 4xx client errors</li>
     * </ul>
     * <p>
     * <b>GZIP Support:</b><br>
     * Automatically detects and decompresses GZIP responses based on
     * Content-Encoding header.
     * <p>
     * <b>Memory Efficiency:</b><br>
     * Returns InputStream for incremental processing with StAX, avoiding
     * loading entire response into memory. The stream should be consumed
     * by a StAX parser immediately.
     * <p>
     * <b>SOAP Fault Detection:</b><br>
     * Note that SOAP faults may be returned with HTTP 200 status. Fault
     * detection is delegated to the StAX parsers (AbstractStaxParser subclasses).
     *
     * @param soapAction the SOAP action header (method name, not used in SOAP 1.2)
     * @param soapPayload the XML payload to send (method call, inside SOAP Body)
     * @return InputStream containing the SOAP response body (decompressed if GZIP)
     * @throws RuntimeException if call fails after all retries or on non-retryable error
     */
    @SuppressWarnings("unused")
    public InputStream stream(String soapAction, String soapPayload) {
        String envelope = wrapEnvelope(soapPayload);

        int attempt = 0;
        Exception lastException = null;

        while (attempt <= soapProperties.getMaxRetries()) {
            if (attempt > 0) {
                try {
                    /* Exponential backoff: retryBackoffMs * 2^(attempt-1) */
                    long sleep = soapProperties.getRetryBackoffMs() * (long) Math.pow(2, attempt - 1);
                    log.info("Retrying SOAP call (attempt {}/{}) in {}ms...", attempt, soapProperties.getMaxRetries(), sleep);
                    TimeUnit.MILLISECONDS.sleep(sleep);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SipsaExternalException("Interrupted during retry backoff", e);
                }
            }

            try {
                return executeCall(envelope);
            } catch (Exception e) {
                lastException = e;
                if (!isRetryable(e)) {
                    log.error("Non-retryable error encountered: {}", e.getMessage());
                    throw new SipsaExternalException("SOAP call failed (non-retryable)", e);
                }
                log.warn("Retryable error on attempt {}: {}", attempt, e.getMessage());
            }
            attempt++;
        }

        throw new SipsaExternalException("SOAP call failed after " + soapProperties.getMaxRetries() + " retries", lastException);
    }

    /**
     * Executes a single HTTP POST call to the SOAP endpoint.
     * <p>
     * Sends the SOAP envelope with proper headers and returns the response body
     * as an InputStream. Handles GZIP decompression automatically based on
     * Content-Encoding header.
     * <p>
     * <b>HTTP Status Handling:</b>
     * <ul>
     *   <li>2xx: Returns stream (may still contain SOAP fault)</li>
     *   <li>5xx: Throws IOException (retryable)</li>
     *   <li>4xx: Throws IOException (non-retryable)</li>
     * </ul>
     *
     * @param envelope the complete SOAP 1.2 envelope XML
     * @return InputStream of the response body (decompressed if GZIP)
     * @throws IOException if network error or HTTP error occurs
     * @throws InterruptedException if thread is interrupted during request
     */
    private InputStream executeCall(String envelope) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(soapProperties.getEndpoint()))
                .timeout(Duration.ofMillis(soapProperties.getReadTimeoutMs()))
                /* SOAP 1.2 specification */
                .header("Content-Type", "application/soap+xml; charset=utf-8")
                .header("Accept-Encoding", "gzip")
                /* SOAP 1.2 uses Content-Type action parameter instead of SOAPAction header */
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        int status = response.statusCode();
        InputStream stream = response.body();

        /* Decompress GZIP response if Content-Encoding header indicates compression */
        String encoding = response.headers().firstValue("Content-Encoding").orElse("");
        if ("gzip".equalsIgnoreCase(encoding)) {
            stream = new GZIPInputStream(stream);
        }

        if (status >= 200 && status < 300) {
            /*
             * HTTP 2xx success status doesn't guarantee no SOAP fault.
             * Some SOAP implementations return HTTP 200 with a Fault in the body.
             * We cannot peek at the stream without consuming it, so we return it
             * and delegate fault detection to the StAX parser (AbstractStaxParser).
             */
            return stream;
        } else if (status >= 500) {
            /* Server errors (5xx) are retryable */
            throw new IOException("Server Error " + status);
        } else {
            /* Client errors (4xx) are non-retryable */
            throw new IOException("HTTP Client Error " + status);
        }
    }

    /**
     * Determines if an exception represents a retryable error.
     * <p>
     * <b>Retryable errors:</b>
     * <ul>
     *   <li>TimeoutException - Request timed out</li>
     *   <li>SocketTimeoutException - Socket read/write timeout</li>
     *   <li>ConnectException - Cannot establish connection</li>
     *   <li>IOException with "Server Error" - HTTP 5xx responses</li>
     *   <li>Other IOExceptions - General network issues</li>
     * </ul>
     * <p>
     * <b>Non-retryable errors:</b>
     * <ul>
     *   <li>IOException with "HTTP Client Error" - HTTP 4xx responses</li>
     *   <li>Other exceptions - Unexpected errors</li>
     * </ul>
     *
     * @param e the exception to evaluate
     * @return true if the error should trigger a retry, false otherwise
     */
    private boolean isRetryable(Exception e) {
        if (e instanceof java.util.concurrent.TimeoutException)
            return true;
        if (e instanceof java.net.SocketTimeoutException)
            return true;
        if (e instanceof java.net.ConnectException)
            return true;
        if (e instanceof IOException) {
            String message = e.getMessage();
            /* Null message means generic network error, which is typically retryable */
            if (message == null)
                return true;
            /* Server errors (5xx) are retryable */
            if (message.contains("Server Error"))
                return true;
            /* Client errors (4xx) are not retryable */
            return !message.contains("HTTP Client Error");
        }
        return false;
    }

    /**
     * Wraps the SOAP payload in a standard SOAP 1.2 envelope.
     * <p>
     * Creates the structure:
     * <pre>{@code
     * <?xml version="1.0" encoding="utf-8"?>
     * <soap12:Envelope xmlns:xsi="..." xmlns:xsd="..." xmlns:soap12="...">
     *   <soap12:Body>
     *     {payload}
     *   </soap12:Body>
     * </soap12:Envelope>
     * }</pre>
     *
     * @param payload the inner XML payload (method call with parameters)
     * @return complete SOAP 1.2 envelope as XML string
     */
    private String wrapEnvelope(String payload) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<soap12:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">" +
                "<soap12:Body>" +
                payload +
                "</soap12:Body>" +
                "</soap12:Envelope>";
    }
}
