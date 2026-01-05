package com.dalejandrov.sipsa.domain.exception;

/**
 * Exception thrown when ingestion is attempted outside its allowed time window.
 * <p>
 * This exception enforces scheduling rules that prevent ingestion from running
 * at inappropriate times. It's thrown for:
 * <ul>
 *   <li>Daily methods executed outside configured window (e.g., before 14:20)</li>
 *   <li>Monthly methods executed on wrong days (not 8th or 10th)</li>
 *   <li>Monthly methods executed before configured start time</li>
 * </ul>
 * <p>
 * The exception is caught by {@link com.dalejandrov.sipsa.application.ingestion.core.IngestionJob}
 * which logs it as {@code INGESTION_SKIPPED_WINDOW} event and terminates gracefully.
 * <p>
 * Window checks can be bypassed using {@code force=true} parameter.
 *
 * @see com.dalejandrov.sipsa.application.ingestion.core.WindowPolicy
 */
public class WindowViolationException extends RuntimeException {
    public WindowViolationException(String message) {
        super(message);
    }
}
