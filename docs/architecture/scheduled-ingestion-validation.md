# Scheduled Ingestion Validation

**Date:** 2026-07-13
**Branch:** `test/scheduled-ingestion-jobs` (base: `main` @ `83e45eb`)
**Backlog:** [TECH-110](../backlog/technical-backlog.md#tech-110) (this story), supersedes/implements
[TECH-040](../backlog/technical-backlog.md#tech-040) (`WindowPolicy` unit tests)
**Related:** [ADR-005 — Scheduler Execution Model](../adr/ADR-005-scheduler-execution-model.md)
(Proposed), [ADR-006 — Ingestion Handler Contract](../adr/ADR-006-ingestion-handler-contract.md)
(Proposed), [Testing Strategy](testing-strategy.md)

**Scope:** validate — with automated, deterministic tests — that the scheduled ingestion
pipeline (cron → `SipsaIngestionScheduler` → `IngestionJob` → `WindowPolicy` → `windowKey` →
handler) behaves as documented and as configured. No production functional rule was
changed. Two testability-only production changes were made (see §9) and are the only
non-test diffs in this branch.

---

## 1. Inventory of scheduled processes

```
grep -RIn --include="*.java" -e "@Scheduled" -e "@EnableScheduling" -e "TaskScheduler" \
  -e "SchedulingConfigurer" -e "CronExpression" -e "ThreadPoolTaskScheduler" \
  -e "fixedRate" -e "fixedDelay" -e "initialDelay" src/main/java
```

Result: exactly 3 `@Scheduled` methods, all in `SipsaIngestionScheduler`, plus one
`@EnableScheduling` configuration class. No `fixedRate`, `fixedDelay`, `initialDelay`,
`SchedulingConfigurer`, or custom `CronExpression` usage exists anywhere in `src/main`. No
recovery, audit, or cleanup cron job exists — the only scheduled work in the system is
ingestion.

| Job | Java method | Cron expression (default) | Zone | SIPSA methods invoked | Configurable |
|---|---|---|---|---|---|
| Daily window | `SipsaIngestionScheduler.runDailyWindow()` | `${sipsa.ingestion.cron.daily:0 20 14 * * *}` (14:20 daily) | `${sipsa.timezone:America/Bogota}` | `promediosSipsaCiudad`, `promediosSipsaParcial`, `promediosSipsaSemanaMadr` (sequential, in this order) | Yes — cron string and zone are both externalized properties |
| Monthly MesMadr | `SipsaIngestionScheduler.runMonthlyMes()` | `${sipsa.ingestion.cron.monthly-mes:0 30 14 8 * *}` (day 8, 14:30) | `${sipsa.timezone:America/Bogota}` | `promediosSipsaMesMadr` | Yes |
| Monthly AbasMes | `SipsaIngestionScheduler.runMonthlyAbas()` | `${sipsa.ingestion.cron.monthly-abas:0 30 14 10 * *}` (day 10, 14:30) | `${sipsa.timezone:America/Bogota}` | `promedioAbasSipsaMesMadr` | Yes |
| Recovery / audit / cleanup job | — | — | — | none found | n/a |

`SchedulingConfig` (`infrastructure/config/SchedulingConfig.java`) declares `@EnableScheduling`
and a single `ThreadPoolTaskScheduler` bean (`sipsa.scheduling.pool-size`, default 5).

---

## 2. Spring scheduling configuration — validation

| Check | Result |
|---|---|
| `@EnableScheduling` active | Yes — `SchedulingConfig.java:32` (now conditional, see §9) |
| Scheduler bean created | Yes — `taskScheduler()` bean, `ThreadPoolTaskScheduler`, pool size from `sipsa.scheduling.pool-size` (default 5) |
| Cron properties resolve | Yes — confirmed by `SipsaSchedulingContextTest.cronProperties_resolveToRealValues()` against the real `Environment`, not just the annotation default |
| No unresolved placeholders | Confirmed by the same test — resolved values do not contain `${` |
| Zone is `America/Bogota` or correctly parameterized | Yes, on all 3 `@Scheduled` methods and on the scheduler's `@Scheduled(..., zone = "${sipsa.timezone:America/Bogota}")` — never the JVM/container default. Confirmed via reflection in `SipsaSchedulingCronTest.DeclaredZone` and via `Environment` in `SipsaSchedulingContextTest.timezoneProperty_resolvesToBogota()` |
| Cron independent of server/container zone | Yes — the `zone` attribute is explicit on every `@Scheduled` annotation; Spring uses it to build the trigger's calendar, not `TimeZone.getDefault()` |
| Pool size as expected | 5 (default), sufficient for 3 cron triggers plus headroom; not otherwise validated by an automated test (operational sizing, not correctness) |
| Property names consistent across `application.yaml`, env vars, and code | Yes — `sipsa.ingestion.cron.{daily,monthly-mes,monthly-abas}` and `sipsa.timezone` match exactly between `application.yaml`, the `@Value`/`@Scheduled` placeholders, and their env var overrides (`SIPSA_CRON_DAILY`, etc.) |
| Tasks can be disabled in tests | **No, before this story.** See §4 and §9 — this is the one gap this story closes with an explicit, backward-compatible property. |

**Internal documentation drift found (not a behavior bug):** `SchedulingConfig.java`'s
Javadoc (pre-existing, before this story) stated the monthly jobs run "day 8, 06:00 COT" /
"day 10, 06:00 COT". The actual `@Scheduled` cron expressions are `0 30 14 8 * *` / `0 30 14
10 * *` — **14:30**, not 06:00. `06:00` is `WindowPolicy`'s Java-level `@Value` fallback for
`monthly-window-start` (only relevant if that property were entirely absent from
`application.yaml`, which it is not — `application.yaml` sets it to `14:00`). This looks
like a Javadoc copy/confusion between two unrelated defaults, not a functional issue. The
Javadoc was corrected as part of this story's Clock-injection commit (a comment-only
change, no behavior change — see §9).

---

## 3. Cron expression validation (`SipsaSchedulingCronTest`)

Validated with `org.springframework.scheduling.support.CronExpression`, never the system
clock. Reference times used per the assignment's checklist: before/at/after 2:00 p.m., days
7–11, month rollover, December→January, and a leap day (Feb 29, 2028).

| Cron | Reference | `next()` result | Verified |
|---|---|---|---|
| Daily `0 20 14 * * *` | 13:59:59 same day | 14:20:00 same day | ✅ |
| Daily | exactly 14:20:00 | 14:20:00 **next** day (`next()` is exclusive) | ✅ |
| Daily | 18:00:00 same day | 14:20:00 next day | ✅ |
| Daily | days 7–11 | fires every one of them at 14:20 (day-of-month field is `*`) | ✅ |
| Daily | Dec 31 20:00 | Jan 1 14:20 next year | ✅ |
| Daily | Feb 29 2028 (leap day) | fires the same day at 14:20 | ✅ |
| Monthly MesMadr `0 30 14 8 * *` | day 7 | day 8, 14:30, same month | ✅ |
| Monthly MesMadr | day 8, before 14:30 | day 8, 14:30, same day | ✅ |
| Monthly MesMadr | day 8, after 14:30 | day 8 of the **next month** — the cron itself has no grace day (9/10/11 never trigger it) | ✅ |
| Monthly MesMadr | days 9, 10, 11 | day 8 of the following month | ✅ |
| Monthly MesMadr | December reference | January 8 next year | ✅ |
| Monthly AbasMes `0 30 14 10 * *` | day 9 | day 10, 14:30, same month | ✅ |
| Monthly AbasMes | day 10, before/after 14:30 | same day / next month, symmetric to MesMadr | ✅ |
| Monthly AbasMes | days 8, 9, 11 | day 10 (same or next month) | ✅ |
| Monthly AbasMes | December reference | January 10 next year | ✅ |
| Both monthly crons | any reference | never fire on the same calendar day (day 8 ≠ day 10) | ✅ |

**Important distinction, addressed explicitly per the assignment's instruction not to
assume every difference is a bug:** the cron expressions themselves are correctly
method-specific — `runMonthlyMes()` only ever fires on day 8, `runMonthlyAbas()` only ever
fires on day 10, with no cron-level overlap or grace day. The day-8/day-10 ambiguity
documented in §6 below exists **only** inside `WindowPolicy.validateMonthly()`, which is a
second, independent safety check invoked after the cron fires (and also reachable directly
via the manual trigger endpoint, bypassing the cron entirely). The cron scheduling layer is
not implicated in that finding.

---

## 4. Disabling scheduling in tests

**Before this story:** no property existed. `src/test/resources/application.yaml` already
set permissive test cron values (`0 0 0 * * *`, `0 0 0 1 * *`, `0 0 0 2 * *`, all near
midnight) and a small `pool-size: 1`, which made a real firing during a test run
extremely unlikely — but `@EnableScheduling` was still active and nothing formally
prevented a `@Scheduled` method from executing against the mock SOAP endpoint
(`http://localhost:9999/mock`) if a test run happened to straddle midnight.

**Added by this story:** `sipsa.scheduling.enabled` (default `true`, so production behavior
is unchanged). `SchedulingConfig` is now annotated
`@ConditionalOnProperty(prefix = "sipsa.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)`.
When `false`, `@EnableScheduling`'s bean post-processor and the `taskScheduler` bean are
never registered — Spring never even attempts to schedule the 3 `@Scheduled` methods.
`src/test/resources/application.yaml` now sets `sipsa.scheduling.enabled: false` by
default for every test in the suite; `SipsaSchedulingContextTest` explicitly overrides it
back to `true` (via `@SpringBootTest(properties = ...)`) for the one test class that needs
to prove the wiring is correct when active.

Both directions are verified by tests, not assumed:
- `SipsaSchedulingDisabledByDefaultTest` — proves `taskScheduler` bean does **not** exist
  under the default (disabled) test configuration.
- `SipsaSchedulingContextTest` — proves the bean **does** exist, cron/zone properties
  resolve to real values, and (defense in depth) that none of the 3 cron triggers is due
  within the next minute of test execution.

---

## 5. Contrast: cron vs. `WindowPolicy` vs. idempotency

Full pipeline: `Cron fires → SipsaIngestionScheduler.runSafely(methodName) →
IngestionRequest.scheduled(methodName, requestId) → GenericIngestionJob.execute(request) →
WindowPolicy.validateAndGetKey(methodName, force=false) → IngestionControlService.createRun(...)
[(method_name, window_key) uniqueness] → handler`.

| Method | Cron day/time | `WindowPolicy` rule (as implemented) | `windowKey` | Result |
|---|---|---|---|---|
| `promediosSipsaCiudad` | every day, 14:20 | daily, 14:20–23:59 | `YYYY-MM-DD` | Matches — classified daily, correct window |
| `promediosSipsaParcial` | every day, 14:20 | daily, 14:20–23:59 | `YYYY-MM-DD` | Matches |
| `promediosSipsaSemanaMadr` | every day, 14:20 | daily, 14:20–23:59 (classified daily because its name contains neither `"mesmadr"` nor `"abas"`) | `YYYY-MM-DD` | Matches DANE's "diarios y semanales... desde las 2 p.m." rule — **confirmed correct classification** |
| `promediosSipsaMesMadr` | day 8, 14:30 | monthly, days **{8, 9, 10, 11}** accepted (see §6) | `YYYY-MM-DD` (raw run date, **not** `YYYY-MM-M8`) | Cron is correct (day 8 only); `WindowPolicy` is broader than the cron — see §6 |
| `promedioAbasSipsaMesMadr` | day 10, 14:30 | monthly, days **{8, 9, 10, 11}** accepted (identical rule, method-agnostic) | `YYYY-MM-DD` (raw run date, **not** `YYYY-MM-M10`) | Cron is correct (day 10 only); `WindowPolicy` is broader than the cron — see §6 |

Specific questions from the assignment, answered with evidence from
`WindowPolicyTest` (all green, all pinning **current** behavior):

- **Does Mayoristas monthly accept only day 8?** No — it also accepts days 9, 10, 11
  (`WindowPolicyTest.MonthlyWindowCurrentImplementation`, `MonthlyWindowConfirmedBugDemonstration`).
- **Does Abastecimientos monthly accept only day 10?** No — it also accepts days 8, 9, 11.
- **Is there a grace period?** Yes, days 9 and 11 (one day after each documented day) —
  but see the next point.
- **Is the grace period documented?** Partially. `WindowPolicy`'s own Javadoc
  (`WindowPolicy.java:19-20`) says "Monthly methods: Run only on specific days of the month
  (e.g., 8th and 10th)" and `validateMonthly`'s inline comment says "Day 8 06:00 -> Day 9
  23:59 (for M8)" / "Day 10 06:00 -> Day 11 23:59 (for M10/Abas)" — so a grace day *is*
  documented in the source, but **only as a shared, method-agnostic day set**, never
  distinguishing which grace day belongs to which method.
- **Are days 8 and 10 accepted indistinctly?** **Yes, confirmed.** Both
  `promediosSipsaMesMadr` and `promedioAbasSipsaMesMadr` are accepted on day 8 **and** on
  day 10 (and on 9 and 11). See `MonthlyWindowConfirmedBugDemonstration` in
  `WindowPolicyTest.java`.
- **Does `force=true` ignore the window?** Yes, for both daily and monthly, confirmed by
  `forceTrue_bypassesWindow_forBothMethods` — and it still returns a real-date key (does
  not skip key generation).
- **Does the `windowKey` change between grace days?** Yes — day 8 produces `"...-08"`, day 9
  produces `"...-09"` for the *same logical monthly period*, confirmed by
  `retryOnGraceDay_producesDifferentWindowKey_forSameLogicalPeriod`.
- **Do two retries of the same period produce the same `windowKey`?** Only if they occur on
  the exact same calendar day. A retry that lands on the grace day (day 9 after a day 8
  attempt) produces a **different** key — this is the idempotency-breaking half of the
  finding below.
- **Can a monthly execution duplicate due to an incorrect key?** Yes, in principle: since
  `IngestionRun`'s uniqueness is `(method_name, window_key)` and the monthly key is the raw
  run date rather than a stable per-period marker, the same logical monthly ingestion could
  be recorded as up to 4 distinct "runs" (days 8, 9, 10, 11) without the unique constraint
  ever detecting them as duplicates of each other.

---

## 6. Findings — classification

Per the assignment's classification scheme. Evidence file/line references point to the
pre-existing `main` branch state (nothing here was fixed in this story).

### F-WP-01 — `WindowPolicy` does not bind the monthly day to the specific method

**Classification: Bug confirmed.**
**Status: Fixed** (2026-07-14, [TECH-111](../backlog/technical-backlog.md#tech-111),
branch `fix/window-policy-monthly-rules`): `validateMonthly` now receives the method's own
rule — MesMadr is bound to days 8/9 and AbasMes to days 10/11.

**Evidence:**
- `WindowPolicy.java:169-180` (`validateMonthly`) — the day-of-month check never receives
  or inspects the method name.
- `application.yaml:121` — `monthly-run-days: ${MONTHLY_RUN_DAYS:8,10}   # Day 8 (MesMadr), Day 10 (AbasMes)`
  — the configuration comment **itself** documents the intended per-method split that the
  code does not implement.
- `SipsaIngestionScheduler.java:92,107` and `application.yaml:126-135` — the cron layer
  *does* correctly separate day 8 (MesMadr) from day 10 (AbasMes); the ambiguity is
  isolated to `WindowPolicy`'s independent safety check, reachable via
  `POST /api/internal/ingestion/run` regardless of the cron.
- No commit message, ADR, code comment, or planned test case (`testing-strategy.md`'s
  original `WindowPolicyTest` case list) anywhere in the repository defends or anticipates
  this behavior as intentional.
- Reproduced live by `WindowPolicyTest.MonthlyWindowConfirmedBugDemonstration` (2 green
  tests pinning the bug, 2 `@Disabled` tests specifying the fix).

**Impact:** Low likelihood (requires a manual/retry trigger outside the correctly-separated
cron), but real: a manual call to `promedioAbasSipsaMesMadr` on day 8, or
`promediosSipsaMesMadr` on day 10, passes validation it should not.

**Priority:** Medium — not urgent (the automated path is unaffected), but a real gap in a
safety mechanism whose entire purpose is to prevent exactly this kind of out-of-schedule
run.

**Recommendation:** Fix in a dedicated story (**TECH-111**, proposed below), not in this
testing-only story.

**Risk of fixing:** Low. The fix is additive (pass `methodName` or an `isMonthly()`-style
classification into `validateMonthly`); no REST contract, DB schema, or SOAP behavior
changes. Should land together with, or after, **TECH-055** (`ADR-006`'s SPIKE on
`isMonthly()` in `IngestionHandler`), since both touch the same daily/monthly
classification seam — implementing them separately risks two different fixes to adjacent
problems.

### F-WP-02 — Monthly `windowKey` does not match its own documented format, breaking idempotency across grace-day retries

**Classification: Bug confirmed.**
**Status: Fixed** (2026-07-14, [TECH-111](../backlog/technical-backlog.md#tech-111)): the
monthly key is now the stable `YYYY-MM-M8`/`YYYY-MM-M10` period marker; a grace-day retry
reuses the principal day's key. No data migration — historical raw-date keys coexist.

**Evidence:** `WindowPolicy.java:36` (class Javadoc: `Monthly: YYYY-MM-M8 or YYYY-MM-M10`)
vs. `WindowPolicy.java:164` (`String key = now.format(DATE_FMT)` — always `yyyy-MM-dd`, the
literal run date). `IngestionRun.java:37`'s Javadoc repeats the same
`YYYY-MM-M8`/`YYYY-MM-M10` claim. Reproduced by
`WindowPolicyTest.retryOnGraceDay_producesDifferentWindowKey_forSameLogicalPeriod`.

**Impact:** Combined with F-WP-01, a monthly method could be recorded as up to 4 separate
"runs" in one month without the `(method_name, window_key)` unique constraint catching any
of them as duplicates of the same logical period.

**Priority:** Medium — same story as F-WP-01 (**TECH-111**); fixing one without the other
leaves idempotency broken even after F-WP-01 is fixed, since a legitimate day-8 → day-9
retry would still mint a new key.

**Risk of fixing:** Low-medium. Changing the key format is a **behavior change** for any
already-stored `window_key` values (format migration), so the fix must decide whether to
migrate existing rows or only apply the new format going forward — this decision belongs to
TECH-111, not to this story.

### F-WP-03 — Grace days (9, 11) skip the `monthlyStart` time check entirely

**Classification: Bug confirmed (minor).**
**Status: Fixed** (2026-07-14, [TECH-111](../backlog/technical-backlog.md#tech-111)): the
time gate now applies identically to the principal day and the grace day.

**Evidence:** `WindowPolicy.java:174,178` — `(day == 8 && !time.isBefore(monthlyStart)) ||
day == 9` — by operator precedence, `day == 9` alone (any time, including midnight) returns
true, asymmetric with day 8's explicit time gate. Reproduced by
`WindowPolicyTest.day9_anyTime_acceptedForBothMethods_evenAtMidnight`.

**Priority:** Low. Bundle into **TECH-111** since the fix naturally touches the same
conditional block.

### F-SC-01 — `SchedulingConfig` Javadoc claims monthly jobs run at 06:00 COT; actual cron is 14:30 COT

**Classification: Documentation outdated (internal, code-level).**

**Evidence:** `SchedulingConfig.java:24-26` (pre-existing) vs. `SipsaIngestionScheduler.java:92,107`.
06:00 is `WindowPolicy`'s Java-default fallback for `monthly-window-start` (not what
`application.yaml` actually configures, which is 14:00, and not what the cron fires at,
which is 14:30). Not a functional bug — the Javadoc was corrected as a documentation-only
edit in this story's Clock-injection commit.

**Priority:** Low, already fixed as a side effect of touching the file.

### F-SC-02 — No previous mechanism to disable `@Scheduled` execution in tests

**Classification: Configuration gap (test infrastructure), not a production bug.**

**Evidence:** confirmed absent before this story (§4). Closed by this story via
`sipsa.scheduling.enabled` (default `true`, backward-compatible).

**Priority:** Was Medium (test reliability/flakiness risk, near-zero probability but
nonzero and unverified); now Done.

### F-DANE-01 — Daily/weekly window buffer (14:20) vs. DANE's raw 14:00; monthly buffer (14:30) vs. raw publication time

**Classification: Decision deliberate (documented), pending validation with a current DANE source.**

**Evidence:** `application.yaml:118-125` and `SipsaIngestionScheduler.java`'s Javadoc both
explicitly describe the 20–30 minute margins as intentional operational buffers over DANE's
documented times. This is not a discrepancy to "fix" — it is a considered choice already
recorded in the codebase. **Caveat, per this story's assignment:** the DANE document is
from March 2020; the buffer's *target* (2:00 p.m., day 8, day 10) should be reconfirmed
against a current DANE source before treating it as still accurate — see §7.

### F-DANE-02 — `promediosSipsaCiudad` and `promediosSipsaParcial` are not explicitly covered by DANE's 2020 schedule text

**Classification: Documentation gap in the source document, decision pending validation with DANE.**

**Evidence:** DANE's "Generalidades" section (p. 2 of the PDF) names only "Mayoristas"
(daily/weekly/monthly) and "Abastecimientos" (monthly) schedules. `promediosSipsaCiudad`
and `promediosSipsaParcial` are documented elsewhere in the PDF (pp. 3–7) as separate
methods with their own field tables, but their *publication schedule* is never stated. The
code's decision to group them into the same 14:20 daily window as Mayoristas-daily/weekly
is a reasonable operational assumption, consistent across `WindowPolicy`,
`SipsaIngestionScheduler`, and `application.yaml`'s comments — but it is not literally
justified by the 2020 text. Not a bug; flagged as **pending validation with DANE**, per the
assignment's explicit instruction not to assume it is wrong.

---

## 7. DANE matrix — with the mandatory 2020 currency caveat

⚠️ `DANE-webservice-SIPSA.pdf` is dated **March 2020**. The times and days below are
treated as the best available reference, not as a guarantee of current DANE behavior. No
production schedule should be changed on this document alone if a more recent DANE source
becomes available.

| Process | DANE rule (2020) | Current cron | Current `WindowPolicy` rule | Match |
|---|---|---|---|---|
| Mayoristas daily/weekly | Available from 2:00 p.m. | 14:20 daily (20 min buffer) | 14:20–23:59 daily | ✅ Matches, with a documented operational buffer |
| Mayoristas monthly | Updated day 8 | Day 8, 14:30 (cron) | Days 8, 9, 10, 11 accepted (broader than documented — F-WP-01) | ⚠️ Cron matches; `WindowPolicy` is broader |
| Abastecimientos monthly | Updated day 10 | Day 10, 14:30 (cron) | Days 8, 9, 10, 11 accepted (same set, method-agnostic — F-WP-01) | ⚠️ Cron matches; `WindowPolicy` is broader |
| Ciudad / Parcial | Not explicitly scheduled in the 2020 text | Grouped into the 14:20 daily window | Daily, 14:20–23:59 | Pending validation with DANE (F-DANE-02) — not necessarily wrong, just unconfirmed |

---

## 8. Concurrency and overlap — analysis only, no code change

Per the assignment, this section documents risk; it does not change the execution model.

- **Pool size:** 5 threads (`sipsa.scheduling.pool-size`), for 3 cron triggers — headroom
  exists, no starvation expected at current scale.
- **Execution model:** synchronous within each window
  (`SipsaIngestionScheduler.runSafely()` blocks on `ingestionJob.execute(...)`). Already
  identified and analyzed in **ADR-005** (`Proposed`) — this story does not reopen or
  change that decision; TECH-053 (async dispatch) remains a separate, unimplemented story.
- **Overlap between the two monthly jobs:** structurally impossible at the cron level (day
  8 ≠ day 10, confirmed in §3); possible at the `WindowPolicy` level only in the sense that
  both *could* validate successfully on an overlapping day if triggered manually — not a
  concurrency issue, a correctness issue (F-WP-01).
  If one cron's `runSafely()` blocks for a long time (e.g., Parcial's 619K+ records), the
  daily window's 3 methods run sequentially in the same scheduler thread — a pre-existing,
  documented limitation (ADR-005), unrelated to this story.
- **Two instances of the application running concurrently:** no distributed lock (no
  ShedLock or equivalent) exists — confirmed absent (`grep` for `ShedLock`/`@SchedulerLock`
  returns nothing). In a hypothetical multi-instance deployment, both instances' internal
  Spring schedulers would independently fire the same cron at the same wall-clock time, and
  both would call `IngestionControlService.createRun(...)`; the `(method_name, window_key)`
  unique DB constraint would let only one `INSERT` succeed, but the current architecture is
  implicitly single-instance (no evidence anywhere of a multi-instance deployment target).
  Not a finding requiring immediate action — flagged for whoever eventually plans horizontal
  scaling.
- **Application restart during an in-progress ingestion:** a run left in `RUNNING` status
  (never reaching `SUCCEEDED`/`FAILED`) would not automatically resume; `force=true` would
  be needed to retry it once the app restarts, since `IngestionControlService.createRun`
  only auto-restarts runs in `FAILED` status without `force`. Not evaluated further here —
  no evidence this has caused an incident; flagged as a documentation note, not a backlog
  item, unless a concrete failure is observed.
- **Idempotency backstop:** the `(method_name, window_key)` unique constraint on
  `ingestion_runs` (`V1__initial_schema.sql`) is the only concurrency safety net today. It
  is effective for same-key duplicate prevention, and ineffective for the F-WP-02 scenario
  (different keys, same logical period).

**No new story is proposed here** beyond what ADR-005/TECH-053 (async dispatch) and
TECH-111 (window/key fix, below) already cover. If horizontal scaling is planned, a future
story should evaluate distributed locking (e.g., ShedLock) — not proposed now, as there is
no evidence it is needed yet.

---

## 9. Production changes made in this story (testability only)

Per the assignment's explicit allowance (tests, fixtures, test config, docs, `Clock`
injection, and a backward-compatible test-disable property), exactly two production files
were touched, both behavior-preserving:

1. **`WindowPolicy.java`** — added a package-private, test-only `Clock` field (defaults to
   `Clock.system(zone)`, byte-for-byte equivalent to the previous
   `ZonedDateTime.now(zone)`), plus a package-private `setClock(Clock)` seam used only by
   `WindowPolicyTest` (same package). No public API changed; no branch of `validateDaily`
   or `validateMonthly` was touched.
2. **`SchedulingConfig.java`** — added `@ConditionalOnProperty(prefix = "sipsa.scheduling",
   name = "enabled", havingValue = "true", matchIfMissing = true)`, default `true` (i.e.,
   enabled), so production behavior is unchanged when the property is absent. Also
   corrected the Javadoc's 06:00 → 14:30 (F-SC-01, comment-only).
3. **`src/test/resources/application.yaml`** — added `sipsa.scheduling.enabled: false` as
   the test-suite default.

No entity, DTO, controller, repository, migration, or REST contract was modified.

---

## 10. Tests created

| Test class | Targets | Cases | Result |
|---|---|---|---|
| `WindowPolicyTest` | `WindowPolicy` | 25 (23 executed + 2 `@Disabled`, documenting TECH-111's desired post-fix behavior) | ✅ 23/23 passing, 2 intentionally skipped |
| `SipsaSchedulingCronTest` | The 3 production cron expressions + declared `@Scheduled` zone | 18 | ✅ 18/18 passing |
| `SipsaIngestionSchedulerTest` | `SipsaIngestionScheduler` dispatch | 8 | ✅ 8/8 passing |
| `SipsaSchedulingContextTest` | Spring context, scheduling **enabled** | 5 | ✅ 5/5 passing |
| `SipsaSchedulingDisabledByDefaultTest` | Spring context, scheduling **disabled** (default) | 2 | ✅ 2/2 passing |
| **Total (new)** | | **58** | **56 passing, 2 skipped by design** |
| `SipsaApplicationTests` (pre-existing) | context load | 1 | ✅ unaffected |

**Grand total after this story:** 59 tests (was 1), 0 failures, 2 intentional skips.

---

## 11. Maven output

```
$ ./mvnw clean verify
...
[INFO] Results:
[INFO]
[INFO] Tests run: 59, Failures: 0, Errors: 0, Skipped: 2
[INFO] BUILD SUCCESS

$ ./mvnw -Dtest='*Scheduling*Test,*WindowPolicy*Test,*Scheduler*Test' test
...
[INFO] Tests run: 58, Failures: 0, Errors: 0, Skipped: 2
[INFO] BUILD SUCCESS
```

(The 58-vs-59 difference is `SipsaApplicationTests`, which does not match the requested
filter pattern.)

---

## 12. Findings requiring further action

| ID | Finding | Classification | Story |
|---|---|---|---|
| F-WP-01 | `WindowPolicy` accepts day 8 or day 10 for either monthly method | Bug confirmed → **Fixed** (2026-07-14, TECH-111, branch `fix/window-policy-monthly-rules`) | **TECH-111** (Done) |
| F-WP-02 | Monthly `windowKey` is the raw run date, not the documented `YYYY-MM-M8`/`M10`, breaking idempotency across grace-day retries | Bug confirmed → **Fixed** (2026-07-14, TECH-111) | **TECH-111** (Done) |
| F-WP-03 | Grace days 9/11 skip the `monthlyStart` time check | Bug confirmed (minor) → **Fixed** (2026-07-14, TECH-111) | **TECH-111** (Done) |
| F-SC-01 | `SchedulingConfig` Javadoc said 06:00 instead of 14:30 | Documentation outdated | Fixed in this story (comment-only) |
| F-SC-02 | No way to disable `@Scheduled` in tests | Configuration gap | Fixed in this story |
| F-DANE-01 | 14:20/14:30 buffers vs. DANE's raw 14:00 | Decision deliberate | No action — re-verify against a current DANE source before any future schedule change |
| F-DANE-02 | Ciudad/Parcial scheduling not explicit in DANE's 2020 text | Pending validation with DANE | No action — flag for whoever next talks to DANE |

### Proposed follow-up story

**TECH-111 — Fix `WindowPolicy` monthly day-to-method binding and windowKey format**

> **Status update (2026-07-14):** TECH-111 was formalized in the
> [technical backlog](../backlog/technical-backlog.md#tech-111) and **implemented** on
> branch `fix/window-policy-monthly-rules`. All three findings are fixed: monthly
> validation is bound per method (MesMadr days 8/9, AbasMes days 10/11), the
> `monthlyStart` time gate applies to grace days too, and the monthly `windowKey` is the
> stable `YYYY-MM-M8`/`YYYY-MM-M10` period marker. The two `@Disabled` tests were
> re-enabled; `monthly-run-days` was repurposed as a startup sanity check (it no longer
> drives per-run validation). Note: the backlog's approved plan explicitly decided TECH-111
> does **not** depend on TECH-055/ADR-006 (contrary to the "ideally sequenced with
> TECH-055" suggestion below, which is retained for the record).
>
> The original proposal below is kept as written for historical traceability.

- **Type:** Correctiva. **Priority:** Medium. **Depends on:** ideally sequenced with
  TECH-055 (`isMonthly()` SPIKE, ADR-006), since both touch the daily/monthly
  classification seam.
- **Fixes:** F-WP-01, F-WP-02, F-WP-03.
- **Acceptance criteria (draft):**
  - `promedioAbasSipsaMesMadr` is rejected on day 8 (`force=false`).
  - `promediosSipsaMesMadr` is rejected on day 10 (`force=false`).
  - The two `@Disabled` tests in `WindowPolicyTest.MonthlyWindowConfirmedBugDemonstration`
    are re-enabled and pass.
  - Monthly `windowKey` matches the documented `YYYY-MM-M8`/`YYYY-MM-M10` format and is
    stable across a day-8 → day-9 (or day-10 → day-11) retry of the same logical period.
  - A decision is made and documented on whether existing `window_key` values in
    production need a migration, or whether the new format only applies going forward.
  - `./mvnw clean verify` passes, including the now-enabled tests.

---

## 13. Recommendation

- **`WindowPolicy` should be corrected** (F-WP-01/02/03) — but as its own story
  (**TECH-111**), not inside this validation branch, per the assignment's explicit
  instruction to diagnose first and fix separately.
- **The cron expressions should be kept as configured.** They are correctly separated per
  method (day 8 vs. day 10) and already include a deliberate, documented operational
  buffer. No change recommended.
- **The 14:20/14:30 buffers and the underlying 14:00/day-8/day-10 DANE rule should be
  revalidated against a current DANE source** before any future change to
  `application.yaml`'s cron/window defaults — the referenced document is from 2020.
- **The grace-day tolerance (day 9 for MesMadr, day 11 for AbasMes) should be kept**, but
  bound to its own method once TECH-111 lands — it is a reasonable allowance for a delayed
  DANE publication, just not currently scoped correctly.
- **No change to the scheduler's execution model (sync vs. async)** is recommended by this
  story — that remains ADR-005's open decision (TECH-053), independent of this validation.
