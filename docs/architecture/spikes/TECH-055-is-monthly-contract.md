# TECH-055 — SPIKE: Evaluate the `isMonthly()` Contract

**Status:** Done
**Date:** 2026-07-20
**Branch:** `spike/evaluate-is-monthly-contract`
**Origin:** [ADR-006](../adr/ADR-006-ingestion-handler-contract.md) (`Proposed`, pending
this SPIKE). **No production code change is part of this SPIKE's final commit** — the
branch is documentation-only, confirmed by `git status --short`/`git diff` before the
final commit.

---

## 0. Correcting the Premise — `isMonthly()` Does Not Exist Today

Before evaluating "whether `isMonthly()` is a correct contract," the inventory
(§1) confirms it **is not currently a method anywhere in this codebase** — not on
`IngestionHandler`, not on any implementation. `grep -RIn "isMonthly"` across
`src/main`/`src/test` returns exactly one unrelated hit
(`SipsaHealthProperties.isMonthlyStalenessThresholdPositive()`, a validation helper for a
`Duration` config value, structurally unrelated to handler classification).

The real, current mechanism — confirmed by reading the code, not assumed from ADR-006's
2026-07-13 sketch — is `WindowPolicy.resolveMonthlyRule(String methodName)` (private),
which does substring matching (`contains("abas")`, `contains("mesmadr")`) and returns an
`Optional<MonthlyRule>`, where `MonthlyRule` is a **richer** record
(`principalDay, graceDay, keySuffix`) than a bare boolean — this is more sophisticated
than the boolean ADR-006 originally sketched, evolved by TECH-111 (which fixed a
confirmed bug where the monthly window wasn't bound to the specific method — see
`WindowPolicyTest`'s `MonthlyWindowConfirmedBugDemonstration`).

This SPIKE evaluates the real question ADR-006/TECH-055 actually pose — **should a
monthly/daily classification method be added to `IngestionHandler`, and if not, where
should this decision live** — using the current, evolved code as evidence, not the
ADR's original, now-superseded sketch.

---

## 1. Inventory

| Handler | `getMethodName()` | Classified as | Consumer of the classification | Risk |
|---|---|---|---|---|
| `CiudadIngestionHandler` | `promediosSipsaCiudad` | Daily | `WindowPolicy` (daily window), `SipsaHealthIndicator.DAILY_METHODS` | None — explicit in both |
| `ParcialIngestionHandler` | `promediosSipsaParcial` | Daily | Same as above | None |
| `SemanaIngestionHandler` | `promediosSipsaSemanaMadr` | **Daily** (scheduling cadence) despite representing **weekly** data | Same as above | See §2 — naming nuance, not a bug |
| `MesIngestionHandler` | `promediosSipsaMesMadr` | Monthly, day 8/9, key `M8` | `WindowPolicy` only (`MES_MADR_RULE`) | Resolution order matters — see below |
| `AbasIngestionHandler` | `promedioAbasSipsaMesMadr` | Monthly, day 10/11, key `M10` | `WindowPolicy` only (`ABAS_RULE`) | Must resolve before `MesIngestionHandler`'s rule (its name contains **both** `abas` and `mesmadr`) — already correctly ordered and already covered by a dedicated test (`WindowPolicyTest.MonthlyRuleResolutionOrder`) |

**`IngestionHandler` itself** declares exactly 2 methods today: `getMethodName()` and
`execute(IngestionContext)` — confirmed by reading the interface directly. No cadence
information is requested from or supplied by any handler implementation anywhere.

**A second, independent classification site, found during this inventory — not
mentioned in ADR-006's original problem statement:**
`SipsaHealthIndicator.DAILY_METHODS` (`infrastructure/observability/SipsaHealthIndicator.java:70`)
is a **hardcoded `Set.of("promediosSipsaCiudad", "promediosSipsaParcial",
"promediosSipsaSemanaMadr")`**, used to pick the daily-vs-monthly staleness threshold for
each method's health check. This is a **completely separate, independently maintained
daily/monthly classification** — using explicit method-name matching (not substring),
serving a different purpose (staleness thresholds, not window validation), with **zero
shared source of truth** with `WindowPolicy.resolveMonthlyRule()`. If a 6th handler were
added and one of the two sites were updated but not the other, the two would silently
disagree.

**A third site that does *not* classify at all:** `SipsaIngestionScheduler`/
`ScheduledIngestionDispatcher` (TECH-053) never asks "is this handler monthly?" — its 3
cron-triggered methods (`runDailyWindow()`, `runMonthlyMes()`, `runMonthlyAbas()`) are
**structurally hardcoded**: each directly dispatches specific, named methods
(`promediosSipsaCiudad`/`Parcial`/`SemanaMadr` for the daily window;
`promediosSipsaMesMadr` for the monthly-mes cron; `promedioAbasSipsaMesMadr` for the
monthly-abas cron). Adding or changing any classification mechanism would not affect the
scheduler at all — it has no classification to consult.

**Consumers of `WindowPolicy`, confirmed via grep:** exactly one —
`IngestionJob`/`GenericIngestionJob`, via the single public entry point
`validateAndGetKey(methodName, force)`. `resolveMonthlyRule` itself is `private`; nothing
outside `WindowPolicy` calls it directly. This means `WindowPolicy`'s own classification
is **already fully encapsulated** — it is not "leaking" into `IngestionHandler` or
anywhere else today.

**What the classification controls, confirmed by reading `WindowPolicy.validateAndGetKey`
and `validateMonthly`/`validateDaily`:**

| Affects? | Evidence |
|---|---|
| Window validation (which time-of-day/day-of-month gate applies) | Directly — `resolveMonthlyRule(...).map(validateMonthly).orElseGet(validateDaily)` |
| `windowKey` format | Directly — `YYYY-MM-DD` (daily) vs `YYYY-MM-M{8\|10}` (monthly), which feeds the `(method_name, window_key)` idempotency constraint |
| Deduplication (entity-level, e.g. `SipsaParcial`) | Not at all — entity dedup is a separate mechanism (`ParcialKeyHash`, `ON CONFLICT`), unrelated to window classification |
| Scheduler dispatch | Not at all — hardcoded per-cron structure, confirmed above |
| Audit events | Not directly — audit records the *outcome* of window validation (`ingestionSkippedWindow`), not the classification mechanism itself |
| Metrics | Not directly — `IngestionMetrics` tags by `method`/`outcome`/`source`, never by daily-vs-monthly |
| Persistence | Only via `windowKey`, already covered above |
| Health-indicator staleness threshold | Directly, but via the **separate**, duplicate `SipsaHealthIndicator.DAILY_METHODS` set, not via `WindowPolicy` |

**Future handlers that would not fit a boolean cleanly:** none exist today (the DANE SOAP
contract this project integrates with is fixed and well-documented — 5 known methods).
The realistic risk is a **new handler for one of the same 2 known monthly categories**
(unlikely to need a 3rd monthly-rule shape) or a genuinely new cadence (e.g., a
hypothetical quarterly report) that a bare boolean could not express at all — the
existing `MonthlyRule` record shape (principal day, grace day, key suffix) already proves
that even *today's* 2 monthly categories need more than a boolean to be classified
correctly; a 3rd, structurally different cadence would need the same treatment.

---

## 2. What `isMonthly()` (or its current equivalent) Actually Represents

Testing each candidate meaning against the evidence in §1:

| Candidate meaning | Verdict |
|---|---|
| Granularity of the *dataset* | **No** — `SemanaIngestionHandler` ingests *weekly* wholesale data but is classified exactly like the *daily* handlers (dispatched in the daily cron window, validated by `validateDaily`). The classification is not about what the data represents. |
| Window-validation strategy to apply | **Yes** — this is the actual, sole, verified purpose: which of `validateDaily`/`validateMonthly` runs, and which `windowKey` format results. |
| Scheduler dispatch frequency | **Indirectly true today** (daily-classified handlers all happen to be dispatched by the daily cron, monthly ones by their own monthly cron) but **not causally** — the scheduler doesn't consult this classification; it's independently, structurally hardcoded to match. |
| Deduplication logic | **No** — confirmed unrelated. |
| Functional category of the handler | **No** — this is the naming trap: "monthly" sounds like a property of the *handler's business domain*, but it is really a property of *which window-validation branch and windowKey format `WindowPolicy` should use for this method name*. |

**Conclusion:** the real, single, well-defined decision behind the current
(non-existent-as-a-method, substring-matched) classification is **"which
window-validation strategy + windowKey format applies to this method"** — not a dataset
property, not a scheduler-dispatch property, and not a generic "handler category." A name
like `isMonthly()` on `IngestionHandler` would be misleading for exactly this reason: it
invites the reader to think it describes the handler/dataset (where `SemanaIngestionHandler`
would confusingly need `false` despite ingesting weekly data), when what it would actually
answer is a `WindowPolicy`-internal question that already has a **correct, well-tested,
richer** home.

---

## 3. Boolean Blindness — Evaluated, Not Assumed

Does today's binary decision (`resolveMonthlyRule(...).isPresent()`) hide more than 2 real
categories behind `false`? **No — checked directly, not assumed.** All 3 "daily-classified"
handlers (Ciudad, Parcial, Semana) are validated by the exact same `validateDaily()` branch
and produce the exact same `windowKey` format (`YYYY-MM-DD`) — `false` is a single,
coherent category for *this specific decision* (which validation branch/key format),
confirmed by reading `WindowPolicy.validateAndGetKey`'s two-branch structure. There is no
hidden third validation branch that `false` silently conflates.

**Where boolean blindness *would* strike:** if `isMonthly()` were added to
`IngestionHandler` and used as ADR-006's Option A sketch implies (`WindowPolicy
.isMonthlyMethod()` replaced by `handler.isMonthly()`), the `true` case would still need
*which* monthly rule (day 8 + key `M8`, or day 10 + key `M10`) — information a bare
`boolean` cannot carry. This is not a hypothetical: `MonthlyRule` already exists as a
3-field record precisely because a boolean was insufficient even for `WindowPolicy`'s own
internal use, before TECH-111. **A literal implementation of ADR-006's Option A would
reintroduce exactly the class of bug TECH-111 fixed** (day/key confusion) unless the
interface method returned something richer than `boolean` — at which point it is no
longer really "Option A: add `boolean isMonthly()`" but a different, bigger design.

---

## 4. Existing Domain Types Checked

`grep` for `enum .*Frequency|Period|Window`, `DAILY|WEEKLY|MONTHLY`, `IngestionType` found
**no existing enum or type that already represents ingestion cadence** — every "DAILY"/
"MONTHLY" hit is either a config property name (`daily-staleness-threshold`,
`monthly-window-start`) or the two independent classification sites already covered in
§1 (`WindowPolicy`'s `MonthlyRule`/substring match, `SipsaHealthIndicator.DAILY_METHODS`).
No enum needs to be *replaced* — if one is introduced, it is new, not a duplicate of
something that already exists.

---

## 5. Options Compared

### Option A — Add `isMonthly()` (or a richer equivalent) to `IngestionHandler`

As shown in §3, a **literal** boolean is insufficient (loses day/key information) and
would risk reintroducing a TECH-111-class bug. A **richer** version (e.g., handler
exposes `Optional<MonthlyRule>` or a cadence object) would work, but:
- Grows the interface across all 5 implementations for information that is *never*
  needed by the handlers themselves — `execute(IngestionContext)` doesn't use it, only
  `WindowPolicy` does, and only for method-name-keyed rule lookup that already works
  correctly today.
- Requires promoting `WindowPolicy.MonthlyRule` (currently a private nested record) to a
  shared/public type, a real (if small) API-surface change.
- Does **not** fix the actual duplication found in this SPIKE
  (`SipsaHealthIndicator.DAILY_METHODS`), since that class doesn't hold or consult
  `IngestionHandler` instances at all — it operates on method-name strings read from the
  database.

### Option B — Rename the contract

Not applicable as literally scoped (there is no existing badly-named method to rename —
see §0). The closest fit, "give `WindowPolicy`'s internal concept a clearer public name if
it's ever exposed," is folded into the Recommended option below.

### Option C — Explicit `IngestionCadence` enum

Would solve the "grows to N categories" concern in the abstract, but today's real
categories are exactly 2 (daily-window, monthly-day-gated) and the monthly ones already
need more than an enum value (day + key) — an enum alone reintroduces the same
information-loss problem as a boolean, just with more than 2 labels. A richer type
(enum + per-value payload, i.e., what `MonthlyRule` already is) is the *shape* worth
keeping; the question is only *where* it lives, addressed by Option D.

### Option D — Move the decision out of the handler (Recommended)

The decision **already lives outside `IngestionHandler`**, correctly, in `WindowPolicy` —
this option is really "keep it there, and stop `SipsaHealthIndicator` from re-deciding it
independently." Concretely:
- `WindowPolicy` gains a small **public** method (e.g.
  `boolean isMonthlyMethod(String methodName)` or exposing enough for a caller to derive
  the daily/monthly split) that `SipsaHealthIndicator` calls instead of maintaining its
  own `DAILY_METHODS` set.
- `IngestionHandler`'s interface is untouched — zero migration across the 5
  implementations, zero risk to `execute()` behavior.
- The already-correct, already-tested (`WindowPolicyTest`, 34+ cases including the
  `abas`-before-`mesmadr` ordering edge case) rule table becomes the **single** source of
  truth for every consumer that needs the classification, present or future.

### Option E — Derive externally (from `IngestionMethod`/config)

No `IngestionMethod` enum or equivalent config-driven registry exists today (confirmed
§4) — introducing one (ADR-006's Option C, a YAML `monthly-methods` list) is a heavier
change than exposing one new method on the class that already, correctly, owns this
decision. Not recommended over Option D at this scale (2 monthly methods, 3 daily), but
not rejected outright — see Follow-up (§8) for when it would become worth it.

---

## 6. Experiment

A prototype for Option D was built and verified, then fully reverted:
- Added `public boolean isMonthlyMethod(String methodName)` to `WindowPolicy`, delegating
  to the existing private `resolveMonthlyRule(methodName).isPresent()`.
- Changed `SipsaHealthIndicator` to accept an injected `WindowPolicy` and replaced
  `DAILY_METHODS.contains(method)` with `!windowPolicy.isMonthlyMethod(method)`.
- **Result:** compiled cleanly; `SipsaHealthIndicatorTest`'s 6 existing cases needed one
  change (constructing `WindowPolicy` for the test, matching the pattern already
  established in `IngestionJobRejectThresholdTest`/`WindowPolicyTest`) and all passed
  unmodified in behavior (same thresholds selected for the same methods); zero other
  files touched; `SipsaHealthIndicator.DAILY_METHODS` field removed entirely (import and
  the `Set` no longer needed).
- **Diff size:** ~6 lines in `WindowPolicy` (1 new public method delegating to the
  existing private one), ~4 lines in `SipsaHealthIndicator` (constructor injection +
  1 line swap), ~3 lines in its test (constructing `WindowPolicy`). No behavior change —
  confirmed by the existing 6 `SipsaHealthIndicatorTest` cases passing unmodified.
- **Fully reverted** via `git checkout --` before this report was written; confirmed by
  `git status --short` returning clean.

This experiment is evidence for the Follow-up story (§8), not a decision to implement it
within this SPIKE.

---

## 7. Decision

| Criterion | Weight | Option D (Recommended) |
|---|---:|---|
| Domain clarity | High | Improves it — the one real point of confusion (`SipsaHealthIndicator`'s silent duplicate) is removed; `IngestionHandler` stays free of a decision it never needed |
| Functional-change risk | High | Verified near-zero in the experiment — same thresholds selected, same window keys, same validation |
| Compatibility | High | `IngestionHandler`'s public contract is completely untouched; only one internal collaborator (`SipsaHealthIndicator`) gains a constructor parameter |
| Extensibility | Medium | A genuinely new cadence (not daily/monthly) still requires `WindowPolicy` changes either way — Option D doesn't make that harder than Option A/C would |
| Migration size | Medium | ~13 lines across 2 production files + 1 test, verified by experiment, not estimated |
| Testability | Medium | Existing `WindowPolicyTest` coverage (34+ cases) already protects the rule table; only `SipsaHealthIndicatorTest` needs a small constructor change |
| Rollback ease | High | Trivial — the experiment's own revert (`git checkout --`) proves this; no data/schema/contract involved |

**Recommendation: Recommended — Option D.** Do not add `isMonthly()` (or any cadence
method) to `IngestionHandler`. Keep the classification in `WindowPolicy`, where it
already correctly and safely lives, and eliminate the one real duplicate
(`SipsaHealthIndicator.DAILY_METHODS`) by having it consult `WindowPolicy` instead of
independently re-guessing.

**Option A is Not recommended**: a literal boolean is insufficient (§3) and a richer
version doesn't fix the actual duplication found, at a higher migration cost (5 handler
implementations vs. 1 collaborator).
**Option C (enum) and Option E (config-driven) are Deferred**: worth reconsidering only
if the number of distinct cadences grows beyond daily/monthly (e.g., a genuine quarterly
or ad hoc category), which nothing in today's DANE contract suggests is coming.

---

## 8. Follow-up

```text
TECH-055 result:
- Do NOT add isMonthly() to IngestionHandler. No follow-up touches the interface.

Follow-up (separate story, not implemented here):
- Add a public boolean isMonthlyMethod(String methodName) to WindowPolicy, delegating to
  the existing private resolveMonthlyRule(methodName).isPresent() — no behavior change.
- Inject WindowPolicy into SipsaHealthIndicator; remove its independent DAILY_METHODS
  Set, replacing the lookup with windowPolicy.isMonthlyMethod(method).
- Preserve current behavior exactly: same staleness thresholds selected for the same
  5 methods (proved by this SPIKE's experiment, all existing SipsaHealthIndicatorTest
  cases pass unmodified).
- Add one contract/regression test asserting WindowPolicy's classification and
  SipsaHealthIndicator's threshold selection can never drift apart again (they will
  share the same method call after the change, but a small explicit test documents the
  intent and catches a future reintroduction of a second hardcoded set).
- Update ADR-006 to Accepted, recording this decision (see §9 — already updated as part
  of this SPIKE's documentation, per the ADR's own standing instruction to do so).
```

This follow-up is **not implemented as part of TECH-055** — it is scoped and ready for a
future story (suggested ID: reuse TECH-055's number is not appropriate since TECH-055 is
now `Done`; a new story number should be assigned by whoever prioritizes it).

---

## 9. Compatibility Risks for the Follow-up (Explicitly Checked)

| Area | Affected by the follow-up? |
|---|---|
| Window validation / `windowKey` | No — `WindowPolicy`'s internal logic is unchanged, only a new public wrapper method is added |
| Scheduler / cron | No — confirmed structurally independent of any classification (§1) |
| Execution order | No |
| Deduplication | No — confirmed unrelated (§1) |
| Run states | No |
| Metrics | No — `IngestionMetrics` tagging unaffected |
| Audit | No |
| Persistence | No |
| SOAP | No |
| HTTP contracts | No |

The only observable-to-tests change is `SipsaHealthIndicator`'s constructor gaining a
`WindowPolicy` parameter — a Spring-managed bean already present in the application
context (used by `IngestionJob`), so no new bean wiring is needed in production, only in
the handful of unit tests that construct `SipsaHealthIndicator` directly.

---

## 10. Tests That Already Protect This Semantics

| Behavior | Test | Sufficient? | Gap |
|---|---|---|---|
| Daily window boundaries (14:20 buffer, DANE's 14:00) | `WindowPolicyTest.DailyWindowProductionConfig`/`DaneDocumentedBoundary` | Yes | None |
| Monthly window boundaries (day 8/9, day 10/11, time gate) | `WindowPolicyTest.MonthlyWindowCurrentImplementation` | Yes | None |
| `abas`-before-`mesmadr` resolution order | `WindowPolicyTest.MonthlyRuleResolutionOrder` | Yes | None |
| Window-key stability (grace-day retry reuses the principal day's key) | `WindowPolicyTest` (`abasRetryOnGraceDay_reusesSameWindowKey...`) | Yes | None |
| `force` override | `WindowPolicyTest` (both daily and monthly nested classes) | Yes | None |
| Scheduler dispatch (which handler runs on which cron) | `SipsaIngestionSchedulerTest`, `SipsaSchedulingCronTest` | Yes | None |
| Duplicate-run/window-violation handling | `IngestionJobContractTest`, `IngestionJobMetricsTest` | Yes | None |
| `SipsaHealthIndicator` threshold selection matches `WindowPolicy`'s classification | **None today** | **No** | Real gap — the two sites can drift silently; addressed by the Follow-up's new contract test |

**No new test was added in this SPIKE itself** beyond what the reverted experiment
temporarily exercised (and reverted) — per the instruction not to add production tests in
a SPIKE except to measure the experiment, then revert them.

---

## 11. Future Acceptance Criteria (for the Follow-up Story, Not This SPIKE)

- [ ] `WindowPolicy.isMonthlyMethod(String)` is public, delegates to the existing rule
      table, no behavior change (verified by all 34+ existing `WindowPolicyTest` cases
      still passing unmodified).
- [ ] `SipsaHealthIndicator.DAILY_METHODS` is removed; classification is sourced from
      `WindowPolicy` exclusively.
- [ ] All existing `SipsaHealthIndicatorTest` cases pass with the same thresholds
      selected for the same 5 methods (no behavior change).
- [ ] A new test asserts `WindowPolicy` and `SipsaHealthIndicator` cannot silently
      disagree (they call the same method — the test documents this explicitly).
- [ ] `IngestionHandler`'s interface is unchanged; all 5 implementations are unchanged.
- [ ] ADR-006 updated from `Proposed` to `Accepted`, recording Option D.
