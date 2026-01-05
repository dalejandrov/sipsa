package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.domain.exception.WindowViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Policy for validating ingestion execution time windows and generating window keys.
 * <p>
 * This component enforces scheduling rules to prevent ingestion from running
 * at inappropriate times, ensuring data freshness and system stability. It handles:
 * <ul>
 *   <li>Daily methods: Run within a specific time window (e.g., 14:20-23:59)</li>
 *   <li>Monthly methods: Run only on specific days of the month (e.g., 8th and 10th)</li>
 *   <li>Window key generation for idempotent run tracking</li>
 * </ul>
 * <p>
 * <b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code sipsa.ingestion.daily-window-start} - Daily window start time (HH:mm)</li>
 *   <li>{@code sipsa.ingestion.daily-window-end} - Daily window end time (HH:mm)</li>
 *   <li>{@code sipsa.ingestion.monthly-run-days} - Comma-separated days (e.g., "8,10")</li>
 *   <li>{@code sipsa.ingestion.monthly-window-start} - Monthly window start time</li>
 *   <li>{@code sipsa.timezone} - Timezone for all time calculations</li>
 * </ul>
 * <p>
 * <b>Window Keys:</b>
 * <ul>
 *   <li>Daily: {@code YYYY-MM-DD} (e.g., "2026-01-02")</li>
 *   <li>Monthly: {@code YYYY-MM-M8} or {@code YYYY-MM-M10} (e.g., "2026-01-M8")</li>
 * </ul>
 * <p>
 * The {@code force} parameter bypasses window checks for manual executions.
 *
 * @see IngestionJob
 * @see WindowViolationException
 */
@Component
public class WindowPolicy {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LocalTime dailyStart;
    private final LocalTime dailyEnd;

    private final Set<Integer> monthlyRunDays;
    private final LocalTime monthlyStart;

    private final ZoneId zone;

    /**
     * Creates the window policy with configured time windows and timezone.
     *
     * @param dailyStartStr daily window start time (HH:mm format)
     * @param dailyEndStr daily window end time (HH:mm format)
     * @param monthlyRunDaysStr comma-separated days of month for monthly runs (e.g., "8,10")
     * @param monthlyStartStr monthly window start time (HH:mm format)
     * @param zoneStr timezone identifier (e.g., "America/Bogota")
     */
    public WindowPolicy(
            @Value("${sipsa.ingestion.daily-window-start:14:20}") String dailyStartStr,
            @Value("${sipsa.ingestion.daily-window-end:23:59}") String dailyEndStr,
            @Value("${sipsa.ingestion.monthly-run-days:8,10}") String monthlyRunDaysStr,
            @Value("${sipsa.ingestion.monthly-window-start:06:00}") String monthlyStartStr,
            @Value("${sipsa.timezone:America/Bogota}") String zoneStr) {

        this.dailyStart = LocalTime.parse(dailyStartStr);
        this.dailyEnd = LocalTime.parse(dailyEndStr);
        this.monthlyStart = LocalTime.parse(monthlyStartStr);

        this.monthlyRunDays = Arrays.stream(monthlyRunDaysStr.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toSet());

        this.zone = ZoneId.of(zoneStr);
    }

    /**
     * Validates the current time against the method's window and generates a window key.
     * <p>
     * This is the main entry point for window validation. It:
     * <ol>
     *   <li>Determines if the method is daily or monthly</li>
     *   <li>Validates current time is within allowed window (unless force=true)</li>
     *   <li>Generates a stable window key for idempotent run tracking</li>
     * </ol>
     * <p>
     * The window key ensures that the same logical period isn't ingested twice
     * (e.g., data for 2026-01-02 should only be ingested once).
     *
     * @param methodName the ingestion method name (determines daily vs monthly)
     * @param force if true, bypasses time window checks but still generates key
     * @return stable window key for the current logical execution period
     * @throws WindowViolationException if called outside allowed window and force=false
     */
    public String validateAndGetKey(String methodName, boolean force) {
        ZonedDateTime now = ZonedDateTime.now(zone);

        if (isMonthlyMethod(methodName)) {
            return validateMonthly(now, force);
        } else {
            return validateDaily(now, force);
        }
    }

    /**
     * Validates daily method execution window.
     * <p>
     * Daily methods can run between configured start and end times.
     * The window key is the current date in YYYY-MM-DD format.
     *
     * @param now current time in configured timezone
     * @param force if true, bypasses window check
     * @return window key (YYYY-MM-DD)
     * @throws WindowViolationException if outside window and force=false
     */
    private String validateDaily(ZonedDateTime now, boolean force) {
        // Daily Window: [Start, End]
        // Key: YYYY-MM-DD

        String key = now.format(DATE_FMT);

        if (force)
            return key;

        LocalTime time = now.toLocalTime();
        if (time.isBefore(dailyStart) || time.isAfter(dailyEnd)) {
            throw new WindowViolationException(
                    "Daily run outside window. Current: " + time + ", Allowed: " + dailyStart + "-" + dailyEnd);
        }
        return key;
    }

    /**
     * Validates monthly method execution window.
     * <p>
     * Monthly methods can only run on specific days of the month
     * (configured via monthly-run-days property), and only after
     * the configured start time.
     * <p>
     * The window key includes the day number (e.g., "2026-01-M8" for day 8)
     * to distinguish between different monthly run days in the same month.
     *
     * @param now current time in configured timezone
     * @param force if true, bypasses window check
     * @return window key (YYYY-MM-M{day})
     * @throws WindowViolationException if not on allowed day/time and force=false
     */
    private String validateMonthly(ZonedDateTime now, boolean force) {
        // Monthly Window: Day 8 06:00 -> Day 9 23:59 (for M8)
        // Day 10 06:00 -> Day 11 23:59 (for M10/Abas)

        int day = now.getDayOfMonth();
        LocalTime time = now.toLocalTime();

        // For monthly, window_key is the exact date of the run
        String key = now.format(DATE_FMT);

        if (force)
            return key;

        if (monthlyRunDays.contains(day) && !time.isBefore(monthlyStart)) {
            // Driven strictly by the scheduled days (8 and 10) starts
            return key;
        }

        if ((day == 8 && !time.isBefore(monthlyStart)) || day == 9) {
            return key;
        }

        if ((day == 10 && !time.isBefore(monthlyStart)) || day == 11) {
            return key;
        }

        throw new WindowViolationException(
                "Monthly run outside window. Current Day: " + day + " Time: " + time);
    }

    /**
     * Determines if a method should be treated as monthly based on its name.
     * <p>
     * Methods containing "Mes" or "Abas" are considered monthly.
     * All others are daily.
     *
     * @param methodName the ingestion method name
     * @return true if method is monthly, false if daily
     */
    private boolean isMonthlyMethod(String methodName) {
        return methodName.toLowerCase().contains("mesmadr") || methodName.toLowerCase().contains("abas");
    }
}
