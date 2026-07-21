# ADR-006 — Ingestion Handler Contract

**Status:** Accepted (2026-07-20) — **Option D**, not Option A (see Decision below;
this reverses this ADR's original tentative recommendation)  
**Date:** 2026-07-13 (Proposed) — 2026-07-20 (Accepted, per TECH-055's SPIKE)  
**Backlog:** [TECH-055](../backlog/technical-backlog.md#tech-055) (**Done**)

---

## Context

`IngestionHandler` is the Strategy interface for all ingestion data sources. It currently declares:

```java
public interface IngestionHandler {
    String getMethodName();
    void execute(IngestionContext context) throws Exception;
}
```

`WindowPolicy` classifies handlers as daily or monthly based on the handler's method name string.
**Note (TECH-055, 2026-07-20): the snippet below reflects this ADR's original 2026-07-13 context and
is now superseded by TECH-111's fix.** The real, current method is `resolveMonthlyRule(String)`,
which returns `Optional<MonthlyRule>` — a 3-field record (`principalDay`, `graceDay`, `keySuffix`),
richer than a boolean, because a bare boolean was insufficient even for `WindowPolicy`'s own
internal use (see TECH-055's SPIKE report, §0 and §3, for why this materially changes the
Decision below):

```java
// WindowPolicy.java, as of 2026-07-13 (original context, now superseded)
private boolean isMonthlyMethod(String methodName) {
    return methodName.toLowerCase().contains("mesmadr")
        || methodName.toLowerCase().contains("abas");
}
```

---

## Problem

The scheduling type (daily vs monthly) is encoded in the method name string, not in the handler
contract. Adding a new monthly handler with a method name that does not contain "mesmadr" or "abas"
(e.g., `promediosSipsaMensualCiudad`) would silently use daily scheduling, potentially running
at incorrect times without any compile-time or startup-time warning.

**Additional finding (TECH-055, 2026-07-20):** this classification is independently
duplicated a second time, with no shared source of truth, in
`SipsaHealthIndicator.DAILY_METHODS` (a hardcoded `Set` of exact method names, used to
pick daily-vs-monthly staleness thresholds) — a real drift risk the original problem
statement did not identify, since it predates that discovery. See the
[SPIKE report](../architecture/spikes/TECH-055-is-monthly-contract.md) §1 for full
evidence.

---

## Alternatives

### Option A — Add `isMonthly()` to `IngestionHandler` interface

```java
public interface IngestionHandler {
    String getMethodName();
    boolean isMonthly();         // ← new method
    void execute(IngestionContext context) throws Exception;
}
```

`WindowPolicy.isMonthlyMethod()` is replaced by `handler.isMonthly()` looked up from the registry.

**Pros:** Contract is explicit; compiler enforces all handlers to declare their type; zero guessing from method names.  
**Cons:** All 5 existing handlers must add the method; interface grows; if `IngestionHandler` is implemented in tests or other contexts, they also need the method.

### Option B — Document the naming convention (current approach)

Add Javadoc to `IngestionHandler.getMethodName()` stating that monthly methods must include "mesmadr" or "abas" in their name.

**Pros:** Zero code change.  
**Cons:** Convention is not enforced; easy to violate; discoverable only by reading Javadoc; relies on developer discipline.

### Option C — Explicit registry in configuration

Move the daily/monthly classification to `application.yaml`:

```yaml
sipsa:
  scheduling:
    monthly-methods:
      - promediosSipsaMesMadr
      - promedioAbasSipsaMesMadr
```

**Pros:** Configuration-driven; no interface change; operational team can modify without code changes.  
**Cons:** Application configuration and code must stay in sync; risk of misconfiguration if a handler is added without updating the config.

---

## Decision

**Accepted: a new Option D — "keep the decision out of `IngestionHandler`, consolidate
it in `WindowPolicy`."** TECH-055's SPIKE (2026-07-20) found that Option A's own premise
no longer holds: `WindowPolicy` already needs *more* than a boolean (a monthly method
needs its principal day, grace day, and window-key suffix — see the `MonthlyRule` record
introduced by TECH-111, after this ADR's original context was written), so a literal
`boolean isMonthly()` on `IngestionHandler` would be insufficient and would risk
reintroducing the exact bug class TECH-111 fixed (day/key confusion) unless the
interface method returned something far richer than a boolean — at which point it is a
much bigger change than Option A as originally scoped, for a benefit (compiler-enforced
declaration on 5 handlers) that doesn't address the actual duplication found:
`SipsaHealthIndicator`'s independent `DAILY_METHODS` set never consults
`IngestionHandler` at all.

**Option D, concretely:** `WindowPolicy`'s existing, already-correct, already-tested
(34+ cases in `WindowPolicyTest`, including the `abas`-before-`mesmadr` resolution-order
edge case) classification stays exactly where it is — private, encapsulated, with a
single existing public entry point (`validateAndGetKey`). It gains **one new public
method**, `isMonthlyMethod(String)`, so the one real duplicate consumer
(`SipsaHealthIndicator`) can query it instead of independently re-guessing. This was
verified with a working, then fully-reverted, prototype (SPIKE report §6): ~13 lines
across 2 production files, zero behavior change (all existing tests passed unmodified).

`IngestionHandler`'s interface is explicitly **not** changed. Options A, B, and C are
**not recommended/deferred** — see the [SPIKE report](../architecture/spikes/TECH-055-is-monthly-contract.md)
§5, §7 for the full comparison and reasoning.

---

## Consequences

**Option D is accepted; the follow-up implementation is a separate story (not yet
scheduled — see the SPIKE report §8):**
- `IngestionHandler` is unchanged — zero migration across the 5 implementations.
- `WindowPolicy` gains `public boolean isMonthlyMethod(String methodName)`, delegating
  to its existing private rule resolution. No behavior change.
- `SipsaHealthIndicator` is refactored to inject `WindowPolicy` and remove its
  independent `DAILY_METHODS` `Set`, closing the one real drift risk TECH-055 found.
- A new contract test asserts the two call sites can no longer silently disagree.
- Future handlers with a new cadence still require a `WindowPolicy` change (a new
  `MonthlyRule`-shaped rule, or a broader rule type if the cadence isn't "monthly") —
  Option D does not make this any harder than Option A or C would have.

**Options A, B, C — not pursued, per TECH-055:**
- Option A would grow `IngestionHandler`'s interface for information only `WindowPolicy`
  ever needs, without fixing the actual duplication, and a literal boolean is
  insufficient for the monthly case (see above).
- Option B (document-only) leaves both the fragility (substring matching) and the real
  duplication (`SipsaHealthIndicator`) completely unaddressed.
- Option C (YAML-driven registry) is heavier than exposing one method on the class that
  already, correctly, owns this decision — deferred unless the number of distinct
  cadences grows beyond today's 2 (daily, monthly).
