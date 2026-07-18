package com.dalejandrov.sipsa.application.ingestion.core;

import com.dalejandrov.sipsa.domain.exception.SipsaConfigurationException;
import com.dalejandrov.sipsa.domain.exception.WindowViolationException;
import com.dalejandrov.sipsa.infrastructure.config.IngestionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Policy for validating ingestion execution time windows and generating window keys.
 * <p>
 * This component enforces scheduling rules to prevent ingestion from running
 * at inappropriate times, ensuring data freshness and system stability. It handles:
 * <ul>
 *   <li>Daily methods: Run within a specific time window (e.g., 14:20-23:59)</li>
 *   <li>Monthly methods: Run only on the method's own publication day or its grace day,
 *       at or after the monthly start time (MesMadr: days 8/9; AbasMes: days 10/11)</li>
 *   <li>Window key generation for idempotent run tracking</li>
 * </ul>
 * <p>
 * <b>Monthly rules are contractual.</b> DANE publishes Mayoristas monthly data on day 8
 * and Abastecimientos monthly data on day 10; each method's principal day, grace day, and
 * key marker are therefore fixed in code ({@link MonthlyRule}), not configurable per
 * environment.
 * <p>
 * <b>Configuration Properties:</b>
 * <ul>
 *   <li>{@code sipsa.ingestion.daily-window-start} - Daily window start time (HH:mm)</li>
 *   <li>{@code sipsa.ingestion.daily-window-end} - Daily window end time (HH:mm)</li>
 *   <li>{@code sipsa.ingestion.monthly-run-days} - Comma-separated days (e.g., "8,10").
 *       Startup sanity check only: it must contain every principal day required by the
 *       code-level {@link MonthlyRule}s (8 and 10) or the application fails to start with
 *       {@link SipsaConfigurationException}. It does not participate in per-run
 *       validation.</li>
 *   <li>{@code sipsa.ingestion.monthly-window-start} - Monthly window start time (HH:mm),
 *       bound through {@link IngestionProperties} (env:
 *       {@code INGESTION_MONTHLY_WINDOW_START}, canonical default 14:00). This is the
 *       earliest time of day at which a monthly run is <b>authorized</b> on its
 *       publication/grace day — not the time the scheduler fires (crons fire at 14:30).</li>
 *   <li>{@code sipsa.timezone} - Timezone for all time calculations (explicit
 *       {@code America/Bogota} per ADR-008; never {@code ZoneId.systemDefault()})</li>
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
@Slf4j
public class WindowPolicy {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Publication rule for one monthly ingestion method, per DANE's documented schedule:
     * the principal publication day, a single grace day for retries of the same logical
     * period, and the stable window-key marker for that period.
     */
    private record MonthlyRule(int principalDay, int graceDay, String keySuffix) {

        boolean allowsDay(int day) {
            return day == principalDay || day == graceDay;
        }
    }

    /** Abastecimientos monthly (promedioAbasSipsaMesMadr): DANE publishes on day 10. */
    private static final MonthlyRule ABAS_RULE = new MonthlyRule(10, 11, "M10");

    /** Mayoristas monthly (promediosSipsaMesMadr): DANE publishes on day 8. */
    private static final MonthlyRule MES_MADR_RULE = new MonthlyRule(8, 9, "M8");

    private final LocalTime dailyStart;
    private final LocalTime dailyEnd;

    /**
     * Days parsed from {@code sipsa.ingestion.monthly-run-days}. Retained only for the
     * constructor's startup sanity check against the code-level {@link MonthlyRule}s;
     * per-run validation never consults it.
     */
    private final Set<Integer> monthlyRunDays;
    private final LocalTime monthlyStart;

    private final ZoneId zone;

    /**
     * Clock used to determine "now" for window validation.
     * <p>
     * Defaults to a system clock pinned to {@link #zone}, which is behaviorally identical
     * to the previous {@code ZonedDateTime.now(zone)} call. Package-private mutability
     * exists solely so tests in this package can substitute a {@link Clock#fixed} instance
     * for deterministic boundary testing (e.g., exact 14:00:00 window edges, day 8 vs. day
     * 9, month/year rollovers) without waiting for the system clock or depending on the
     * machine running the tests. Production code never calls {@link #setClock}.
     */
    private Clock clock;

    /**
     * Creates the window policy with configured time windows and timezone.
     * <p>
     * The monthly window start comes from the typed {@link IngestionProperties}
     * (TECH-133): the old {@code @Value} parameter carried a {@code 06:00} fallback that
     * diverged from the {@code 14:00} that {@code application.yaml} has always made
     * effective. The properties bean is a plain validated POJO, so tests can instantiate
     * this policy directly without a Spring context.
     *
     * @param dailyStartStr daily window start time (HH:mm format)
     * @param dailyEndStr daily window end time (HH:mm format)
     * @param monthlyRunDaysStr comma-separated days of month for monthly runs (e.g., "8,10")
     * @param ingestionProperties typed ingestion configuration (monthly window start)
     * @param zoneStr timezone identifier (e.g., "America/Bogota")
     */
    public WindowPolicy(
            @Value("${sipsa.ingestion.daily-window-start:14:20}") String dailyStartStr,
            @Value("${sipsa.ingestion.daily-window-end:23:59}") String dailyEndStr,
            @Value("${sipsa.ingestion.monthly-run-days:8,10}") String monthlyRunDaysStr,
            IngestionProperties ingestionProperties,
            @Value("${sipsa.timezone:America/Bogota}") String zoneStr) {

        this.dailyStart = LocalTime.parse(dailyStartStr);
        this.dailyEnd = LocalTime.parse(dailyEndStr);
        this.monthlyStart = Objects.requireNonNull(
                ingestionProperties.getMonthlyWindowStart(),
                "sipsa.ingestion.monthly-window-start must not be null");

        this.monthlyRunDays = Arrays.stream(monthlyRunDaysStr.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toSet());

        Set<Integer> requiredPrincipalDays = Set.of(MES_MADR_RULE.principalDay(), ABAS_RULE.principalDay());
        if (!this.monthlyRunDays.containsAll(requiredPrincipalDays)) {
            throw new SipsaConfigurationException(
                    "sipsa.ingestion.monthly-run-days=" + monthlyRunDaysStr
                            + " is incompatible with the DANE contractual monthly publication days "
                            + requiredPrincipalDays + " enforced by WindowPolicy's per-method rules. "
                            + "The configured set must contain at least days 8 (MesMadr) and 10 (AbasMes).");
        }

        this.zone = ZoneId.of(zoneStr);
        this.clock = Clock.system(this.zone);

        // Single safe startup log so operators can confirm the effective window
        // configuration; the policy itself never logs per evaluation.
        log.info("Monthly ingestion timezone = {}", this.zone);
    }

    /**
     * Test-only seam: replaces the clock used for "now" calculations.
     * <p>
     * Package-private by design — only test classes in
     * {@code com.dalejandrov.sipsa.application.ingestion.core} may call this. Not part of
     * the public API and not used by any production code path.
     *
     * @param clock a fixed or otherwise controlled clock for deterministic testing
     */
    void setClock(Clock clock) {
        this.clock = clock;
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
        ZonedDateTime now = ZonedDateTime.now(clock);

        return resolveMonthlyRule(methodName)
                .map(rule -> validateMonthly(methodName, rule, now, force))
                .orElseGet(() -> validateDaily(now, force));
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
     * Validates monthly method execution window against the method's own rule.
     * <p>
     * A monthly method may run only on its principal publication day or its grace day
     * (e.g., MesMadr: days 8/9; AbasMes: days 10/11), and in both cases only at or after
     * the configured monthly start time — the time gate applies identically to the
     * principal day and the grace day.
     * <p>
     * The window key is a stable per-period marker, {@code YYYY-MM-M{principalDay}}
     * (e.g., {@code 2026-06-M8}), derived from the run's year/month and the method's rule
     * — never from the day the run actually happened on. A principal-day run and a
     * grace-day retry of the same logical period therefore share one key, preserving the
     * {@code (method_name, window_key)} idempotency guarantee. {@code force=true} skips
     * the window check but still returns the correct period key for the current month.
     *
     * @param methodName the ingestion method name (for the violation message)
     * @param rule the method's resolved publication rule
     * @param now current time in configured timezone
     * @param force if true, bypasses the window check but still returns the key
     * @return stable window key ({@code YYYY-MM-M{principalDay}})
     * @throws WindowViolationException if not on the method's day/time and force=false
     */
    private String validateMonthly(String methodName, MonthlyRule rule, ZonedDateTime now, boolean force) {
        // Stable per-period marker: year/month of the run + the method's own key suffix.
        // Never derived from now.getDayOfMonth() — see F-WP-02 (TECH-111).
        String key = YearMonth.from(now) + "-" + rule.keySuffix();

        if (force)
            return key;

        int day = now.getDayOfMonth();
        LocalTime time = now.toLocalTime();

        if (rule.allowsDay(day) && !time.isBefore(monthlyStart)) {
            return key;
        }

        throw new WindowViolationException(
                "Monthly run outside window for " + methodName
                        + ". Current Day: " + day + " Time: " + time
                        + ". Allowed: day " + rule.principalDay() + " (principal) or day "
                        + rule.graceDay() + " (grace), at or after " + monthlyStart);
    }

    /**
     * Resolves the monthly publication rule for a method, or empty if the method is daily.
     * <p>
     * Matching is by lowercase name fragment, the same convention the scheduler and
     * configuration comments use. Order matters: {@code "promedioAbasSipsaMesMadr"}
     * contains <b>both</b> {@code "abas"} and {@code "mesmadr"}, so {@code "abas"} must be
     * checked first — otherwise the Abastecimientos method would receive the Mayoristas
     * day-8 rule.
     * <p>
     * A method is classified as monthly if and only if a rule resolves for it, so the
     * daily/monthly classification and the per-method rule can never drift apart.
     *
     * @param methodName the ingestion method name
     * @return the method's monthly rule, or empty for daily methods
     */
    private Optional<MonthlyRule> resolveMonthlyRule(String methodName) {
        String name = methodName.toLowerCase();
        if (name.contains("abas")) {
            return Optional.of(ABAS_RULE);
        }
        if (name.contains("mesmadr")) {
            return Optional.of(MES_MADR_RULE);
        }
        return Optional.empty();
    }
}
