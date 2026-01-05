package com.dalejandrov.sipsa.infrastructure.soap.util;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for safe XML data parsing with null handling.
 * <p>
 * Provides static helper methods for parsing common data types from XML text
 * content, with graceful handling of null, empty, or malformed values.
 * <p>
 * All methods return {@code null} on parse failure rather than throwing exceptions,
 * allowing parsers to continue processing even when individual fields are invalid.
 * <p>
 * <b>Design Pattern:</b><br>
 * Utility class with private constructor to prevent instantiation.
 *
 * @see com.dalejandrov.sipsa.infrastructure.soap.parser.AbstractStaxParser
 */
public class XmlParsingUtil {

    private XmlParsingUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Safely parses a string to Long with null handling.
     * <p>
     * Converts via BigDecimal to handle various numeric formats including
     * scientific notation and decimal values. Trims whitespace before parsing.
     * <p>
     * Returns {@code null} if:
     * <ul>
     *   <li>Input is null</li>
     *   <li>Input cannot be parsed as a number</li>
     * </ul>
     *
     * @param text the string to parse
     * @return parsed Long value, or null if parsing fails
     */
    public static Long parseLong(String text) {
        BigDecimal decimal = parseDecimal(text);
        return decimal != null ? decimal.longValue() : null;
    }

    /**
     * Safely parses a string to BigDecimal with null handling.
     * <p>
     * Trims whitespace before parsing. Returns {@code null} if:
     * <ul>
     *   <li>Input is null</li>
     *   <li>Input cannot be parsed as BigDecimal</li>
     * </ul>
     * <p>
     * Used for parsing price and quantity fields from SOAP responses.
     *
     * @param text the string to parse
     * @return parsed BigDecimal value, or null if parsing fails
     */
    public static BigDecimal parseDecimal(String text) {
        if (text == null) return null;
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Safely parses XML datetime to epoch milliseconds with fallback strategies.
     * <p>
     * Attempts to parse datetime in multiple formats:
     * <ol>
     *   <li>ISO 8601 datetime (e.g., "2024-01-15T10:30:00Z")</li>
     *   <li>Epoch milliseconds as string (e.g., "1704153600000")</li>
     * </ol>
     * <p>
     * Returns {@code null} if all parsing strategies fail or input is null.
     * <p>
     * This flexible approach handles the inconsistent datetime formats
     * returned by the SIPSA SOAP service.
     *
     * @param text the datetime string to parse
     * @return epoch milliseconds, or null if parsing fails
     */
    public static Long parseXmlDateTime(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        try {
            return ZonedDateTime.parse(trimmed, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli();
        } catch (Exception e) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
    }
}
