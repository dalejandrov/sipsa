package com.dalejandrov.sipsa.application.ingestion.handler;

import com.dalejandrov.sipsa.application.ingestion.core.IngestionContext;

/**
 * Interface for implementing SIPSA data ingestion handlers.
 * <p>
 * Each handler is responsible for ingesting data from a specific SOAP service method.
 * Implementations encapsulate the complete pipeline for a single data source:
 * <ul>
 *   <li>Fetching data from the SOAP gateway</li>
 *   <li>Parsing XML data using StAX for memory efficiency</li>
 *   <li>Validating records according to business rules</li>
 *   <li>Mapping records to domain entities</li>
 *   <li>Persisting entities to the database using batch operations</li>
 *   <li>Tracking metrics and rejected records in the context</li>
 * </ul>
 * <p>
 * <b>Implementation Guidelines:</b>
 * <ul>
 *   <li>Process records in batches (configurable via {@code sipsa.ingestion.batch-size})</li>
 *   <li>Use StAX parsing for large XML files to avoid memory issues</li>
 *   <li>Validate required fields before entity creation</li>
 *   <li>Log rejected records with {@link IngestionContext#addRejectedRecord}</li>
 *   <li>Use upsert strategies to handle duplicates gracefully</li>
 *   <li>Update context metrics (seen, inserted, updated, rejected)</li>
 * </ul>
 * <p>
 * <b>Handler Registration:</b><br>
 * All Spring beans implementing this interface are automatically registered
 * in {@link com.dalejandrov.sipsa.application.service.IngestionService}
 * and can be invoked by their method name.
 * <p>
 * <b>Example Implementation:</b>
 * <pre>{@code
 * @Component
 * public class MyDataHandler implements IngestionHandler {
 *
 *     @Override
 *     public String getMethodName() {
 *         return "myDataMethod";
 *     }
 *
 *     @Override
 *     public void execute(IngestionContext context) throws Exception {
 *         // 1. Fetch data
 *         try (InputStream stream = gateway.getData()) {
 *             // 2. Parse with StAX
 *             MyParser parser = new MyParser(stream);
 *             while (parser.hasNext()) {
 *                 MyRecord record = parser.next();
 *                 context.incrementSeen();
 *
 *                 // 3. Validate
 *                 if (record.getId() == null) {
 *                     context.addRejectedRecord(record.toString(), "Missing ID");
 *                     continue;
 *                 }
 *
 *                 // 4. Map and accumulate
 *                 batch.add(mapper.toEntity(record));
 *
 *                 // 5. Flush batch when full
 *                 if (batch.size() >= batchSize) {
 *                     repository.upsert(batch);
 *                     context.incrementInserted();
 *                     batch.clear();
 *                 }
 *             }
 *         }
 *     }
 * }
 * }</pre>
 *
 * @see IngestionContext
 * @see com.dalejandrov.sipsa.application.service.IngestionService
 * @see com.dalejandrov.sipsa.application.ingestion.core.IngestionJob
 */
public interface IngestionHandler {

    /**
     * Returns the SOAP method name this handler processes.
     * <p>
     * This identifier is used to:
     * <ul>
     *   <li>Register the handler in the handler registry</li>
     *   <li>Route ingestion requests to the appropriate handler</li>
     *   <li>Display available methods in API endpoints</li>
     *   <li>Configure method-specific windows and schedules</li>
     * </ul>
     * <p>
     * The method name must be unique across all handlers and should match
     * the SOAP service operation name exactly.
     * <p>
     * <b>Examples:</b>
     * <ul>
     *   <li>{@code promediosSipsaCiudad} - City-level pricing data</li>
     *   <li>{@code promediosSipsaParcial} - Municipal partial market data</li>
     *   <li>{@code promediosSipsaSemanaMadr} - Weekly wholesale data</li>
     *   <li>{@code promediosSipsaMesMadr} - Monthly wholesale data</li>
     *   <li>{@code promedioAbasSipsaMesMadr} - Monthly supply data</li>
     * </ul>
     *
     * @return the SOAP method name (must be non-null and unique)
     */
    String getMethodName();

    /**
     * Executes the complete ingestion process for this data source.
     * <p>
     * This method is called by {@link com.dalejandrov.sipsa.application.ingestion.core.IngestionJob}
     * after window validation and run initialization. The implementation should:
     * <ol>
     *   <li>Fetch data from the SOAP gateway (wrapped in try-with-resources)</li>
     *   <li>Initialize a StAX parser for memory-efficient XML processing</li>
     *   <li>Iterate through records, validating each one</li>
     *   <li>Accumulate valid records in batches</li>
     *   <li>Persist batches using repository upsert methods</li>
     *   <li>Track rejected records with detailed reasons</li>
     *   <li>Update context metrics throughout the process</li>
     *   <li>Ensure final batch is flushed even if not full</li>
     * </ol>
     * <p>
     * <b>Error Handling:</b><br>
     * Any exception thrown will be caught by the parent job, which will:
     * <ul>
     *   <li>Mark the run as FAILED</li>
     *   <li>Log the error with context</li>
     *   <li>Persist accumulated metrics and rejected records</li>
     *   <li>Record audit events</li>
     * </ul>
     * <p>
     * <b>Context Usage:</b>
     * <ul>
     *   <li>{@code context.incrementSeen()} - Call for each record encountered</li>
     *   <li>{@code context.incrementInserted()} - Call after successful batch insert</li>
     *   <li>{@code context.incrementUpdated()} - Call after updating existing records</li>
     *   <li>{@code context.addRejectedRecord()} - Call for each validation failure</li>
     * </ul>
     * <p>
     * <b>Performance Considerations:</b>
     * <ul>
     *   <li>Use StAX (not DOM) to avoid loading entire XML into memory</li>
     *   <li>Process in batches (default 2000) to optimize database I/O</li>
     *   <li>Use native upsert queries to minimize round trips</li>
     *   <li>Avoid N+1 query problems by batch operations</li>
     * </ul>
     *
     * @param context the ingestion context for tracking metrics, run metadata,
     *                and rejected records. Never null.
     * @throws Exception if data fetching fails, parsing fails, validation fails,
     *                   or database operations fail. The exception will be caught
     *                   and handled by the parent job.
     * @see IngestionContext#incrementSeen()
     * @see IngestionContext#incrementInserted()
     * @see IngestionContext#addRejectedRecord(String, String)
     */
    void execute(IngestionContext context) throws Exception;
}
