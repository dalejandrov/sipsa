# ADR-008 — Timezone, Locale, and Date Semantics Strategy

**Status:** Accepted, scoped (items 1–6 implemented; item 7 deliberately deferred, not rejected)
**Date:** 2026-07-13 (Proposed) → 2026-07-27 (Accepted, scoped)
**Author:** Temporal/locale strategy review (branch `refactor/internal-models-and-api-filter`)
**Backlog:** TECH-100 (**Done**), TECH-101 (**Superseded by [TECH-111](../backlog/technical-backlog.md#tech-111)**,
fixed 2026-07-14, one day before this ADR's own backlog section formalized it), TECH-102 (Pending —
real gap, see below), TECH-103 (**Done**), TECH-104 (**Done** — SPIKE only; migration itself
deliberately not implemented), TECH-105 (Deferred, deliberately not prioritized), TECH-106 (**Done**).
TECH-100/103/106 implemented 2026-07-27 on `fix/timezone-calendar-dates-and-invalid-header-400`
(PR #36, merged to `main`). Full status detail in
[technical-backlog.md](../backlog/technical-backlog.md).
**Related:** [Timezone/Locale Strategy Review](../architecture/timezone-locale-date-strategy-review.md),
[ADR-007](ADR-007-package-boundaries-and-internal-models.md) (moved `TimezoneFilter` to `api/filter`,
did not change its behavior)

---

## Context

The API exposes SIPSA/DANE agricultural pricing data. It currently has a working
`TimezoneFilter`/`TimezoneUtil` pair that lets a consumer request system timestamps in a
specific IANA timezone via `X-Timezone`, and a `WindowPolicy` that gates when ingestion
methods may run, based on DANE's documented publication schedule.

This ADR was requested specifically to pause any further structural refactoring of
`TimezoneFilter`, `TimezoneUtil`, `WindowPolicy`, or date/locale handling until a
deliberate strategy is reviewed and accepted. The companion document,
[timezone-locale-date-strategy-review.md](../architecture/timezone-locale-date-strategy-review.md),
contains the full inventory, DANE contrast matrix, and evidence for every claim below. This
ADR summarizes the *decision* to be made; the review document is the *evidence*.

The reference source (`DANE-webservice-SIPSA.pdf`, March 2020) is five years old at the
time of this review. Its documented publication times (2:00 p.m. daily/weekly, day 8
monthly for Mayoristas, day 10 monthly for Abastecimientos) are treated as a **starting
hypothesis**, not a verified current fact. No decision in this ADR should be read as
confirming those times are still accurate in production.

---

## Findings Summary (see review doc for full evidence)

- **F1 — Calendar-date fields typed as instants. ✅ Fixed (TECH-100/103/106, PR #36,
  2026-07-27).** `fechaCaptura` (`SipsaCiudad`), `fechaMesIni` (`SipsaMayoristasMensual`,
  `SipsaAbastecimientosMensual`), `fechaIni` (`SipsaMayoristasSemanal`), and `enmaFecha`
  (`SipsaParcial`) were all `Instant` in the entity and `OffsetDateTime` in the API
  response, despite DANE documenting them as calendar/period-start dates, not instants.
  The API response layer now types these as `LocalDate`, resolved via the new
  `TimezoneUtil.toBusinessLocalDate(Instant)` (always `America/Bogota`, never the
  request's `X-Timezone` or UTC) — the date-shift risk is now prevented by construction,
  not by convention. The entity/DB column is unchanged (`Instant`/`TIMESTAMPTZ`) — see
  TECH-104 below.
- **F2 — `WindowPolicy` does not bind the monthly run day to the method. ✅ Fixed
  (TECH-111, 2026-07-14 — one day after this finding was written, independently of this
  ADR's acceptance).** `validateMonthly` used to accept day 8 **or** day 10 (plus grace
  days 9/11) for *any* monthly method; each method now has its own `MonthlyRule`
  (`ABAS_RULE`/`MES_MADR_RULE`), resolved by `resolveMonthlyRule`.
- **F3 — Monthly `windowKey` does not match its own documented contract. ✅ Fixed
  (TECH-111, 2026-07-14).** The key is now `YearMonth.from(now) + "-" + rule.keySuffix()`
  (`YYYY-MM-M8`/`YYYY-MM-M10`), never derived from the actual run day — TECH-111 also
  added the injectable `Clock` item 7 of the Decision below asked for.
- **F4 — `TimezoneFilter` silently downgrades an invalid `X-Timezone` to UTC. ✅ Fixed
  (TECH-103, PR #36, 2026-07-27).** Now returns `HTTP 400 SIPSA_INVALID_TIMEZONE`; an
  *absent* header is unchanged (still UTC, the intended international-API default).
- **F5 — `GlobalExceptionHandler` uses `LocalDateTime.now()`. ✅ Fixed (TECH-106, PR #36,
  2026-07-27).** The 3 error-response timestamp fields are now `OffsetDateTime`,
  converted via `TimezoneUtil` like every other system timestamp (honors `X-Timezone`).
- **F6 — No i18n exists, and none is being proposed by this ADR. Deferred, not
  rejected (TECH-105).** Error codes are already stable strings, which is a correct
  precondition for i18n if it is prioritized later.
- **No violation found** of "do not infer timezone from locale/IP/Accept-Language," and no
  localized date format (`dd/MM/yyyy` etc.) exists anywhere in the JSON contract — both
  confirmed absent, which is correct.

---

## Decision (Accepted, scoped — 2026-07-27)

Adopt a refined **Alternative B** (international API, business zone fixed internally,
`X-Timezone` converts only system instants, calendar dates are never converted):

1. Keep `America/Bogota` as the fixed business zone for `WindowPolicy`,
   `SpecificationBuilder`, and the scheduler. **No change.** ✅
2. Keep `Instant`/`TIMESTAMPTZ` as the canonical instant representation, and
   `OffsetDateTime` (ISO-8601 with explicit offset) as the JSON representation of
   instants. **No change.** ✅
3. Retype the four calendar/period-start fields identified in F1 as `LocalDate` in the API
   response layer (minimum scope); evaluate — via a SPIKE, not directly — whether to also
   retype the entity/DB column (see TECH-104). **✅ Done (response layer, TECH-100/106,
   PR #36). SPIKE done (TECH-104) — migration itself deliberately not implemented; see
   its recommendation in technical-backlog.md for the proposed follow-up story.**
4. Fix `WindowPolicy` so the monthly-day rule is method-aware (F2) and the monthly
   `windowKey` matches its documented `YYYY-MM-M8`/`YYYY-MM-M10` contract (F3). Add an
   injectable `Clock` to make boundary conditions testable. **✅ Done — TECH-111,
   2026-07-14 (implemented one day before this ADR's own backlog section was written;
   confirmed to satisfy this item's exact scope on re-reading `WindowPolicy.java`).**
5. Fix `TimezoneFilter` to return `HTTP 400` with a stable error code
   (`SIPSA_INVALID_TIMEZONE`) on an invalid `X-Timezone` header (F4). **✅ Done —
   TECH-103, PR #36.**
6. Fix `GlobalExceptionHandler`'s three timestamp fields to use an explicit-zone type
   (F5). **✅ Done — TECH-106, PR #36.**
7. **Do not implement i18n/locale-aware messages in this round** (F6) — no evidence of
   urgency; explicitly deferred, not rejected. **Still deferred — TECH-105 not
   scheduled.**

Items 1, 2, 4, 5, 6 are fully closed. Item 3 is closed at the scope this ADR
authorized (response layer); the entity/DB migration it evaluated (TECH-104) is
recommended but intentionally left as a separate, not-yet-scheduled follow-up story —
this ADR's acceptance does not itself authorize that migration. Item 7 remains
deliberately deferred. **TECH-102** (multi-zone/DST mapper test coverage) also remains
open — it was proposed alongside item 3 but not implemented by PR #36, which added
targeted tests (`TimezoneUtilTest`, `SipsaCiudadMapperTest`) rather than the full
`America/Bogota`/`America/New_York`/`America/Los_Angeles`/`UTC`/DST matrix TECH-102
describes.

---

## Alternatives Considered

See the full comparison table in the review document, section G. Summary:

| Alternative | Verdict |
|---|---|
| A — Colombia-only, no `X-Timezone` | Rejected — would remove already-working, already-tested international support with no corresponding benefit. |
| **B — International, configurable `X-Timezone`, calendar dates never converted** | **Recommended** — closest to what already exists; this ADR proposes completing it, not replacing it. |
| C — Canonical UTC only, remove `TimezoneFilter` | Rejected — would remove existing functionality without addressing the actual problem (F1–F3). |
| D — Country-localized date formats (`dd/MM/yyyy`) | Rejected — inherently ambiguous in a JSON contract; correctly absent from the codebase today. |

---

## Consequences

**Realized (items 1, 2, 4, 5, 6 — and item 3 at its authorized scope):**
- Calendar-date fields are impossible to accidentally date-shift by construction, not by
  convention (response layer; entity/DB still `Instant`/`TIMESTAMPTZ`, see TECH-104).
- Manual/retried monthly ingestion triggers can no longer cross DANE's documented
  Mayoristas/Abastecimientos publication-day boundary.
- The monthly `windowKey` idempotency guarantee actually holds across grace-day reruns.
- Invalid `X-Timezone` is now a visible, stable 400 instead of a silent UTC fallback.
- Error-response timestamps are now consistent (offset-aware) with the rest of the API.
- No JSON field was renamed, no HTTP route changed, no database migration was required
  (TECH-104 confirmed a migration is feasible and recommended, but deliberately left
  unimplemented — a SPIKE conclusion, not a migration commitment).

**Still open:**
- TECH-102 (multi-zone/DST mapper test matrix) — not implemented; the fix's own tests
  cover the specific bug, not the broader matrix this story describes.
- TECH-105 (i18n) — deliberately deferred, no scheduled date.
- The TIMESTAMPTZ→DATE entity/DB migration TECH-104 evaluated and recommended — proposed
  as a future follow-up story, not scheduled by this ADR.
- DANE's documented publication schedule (2:00 p.m., day 8, day 10) has still not been
  re-verified against a current source (see Acceptance Criteria) — accepted as
  best-effort per the ADR's own escape hatch, since TECH-111 already shipped against it.

---

## Explicitly Not Decided by This ADR

- Whether to migrate the four calendar columns from `TIMESTAMPTZ` to `DATE` in the
  database. TECH-104's SPIKE is done (see technical-backlog.md) and recommends
  proceeding, but the migration itself is a separate, not-yet-scheduled follow-up story —
  this ADR's acceptance does not authorize it.
- Whether/when to implement i18n — deferred, not scheduled.
- The exact wording of any future localized error message.
- Whether `consultarInsumosSipsaMesMadr` will ever be implemented in this codebase (it
  currently is not, and is out of scope here).

## Acceptance Criteria for This ADR

- [x] Reviewed by the team/owner and moved to `Accepted` (in full or scoped), or
      `Rejected`, with rationale recorded here. **Accepted, scoped, 2026-07-27** — items
      1, 2, 4, 5, 6 fully closed; item 3 closed at its authorized (response-layer) scope,
      with TECH-104's SPIKE conclusion recorded rather than the migration itself; item 7
      remains deliberately deferred, not rejected.
- [x] If accepted, TECH-100 through TECH-106 (or the accepted subset) added to
      `docs/backlog/technical-backlog.md` with real IDs and status `Ready`. **Done** — all
      7 IDs now have real entries with their actual status (`Done`, `Superseded`,
      `Pending`, or `Deferred` as applicable — not all `Ready`, since most were already
      implemented by the time this checkbox was closed).
- [x] DANE's documented publication schedule (2:00 p.m., day 8, day 10) re-verified against
      a current source before TECH-101 is implemented, or explicitly accepted as
      unverified/best-effort if no current source is available. **Accepted as
      unverified/best-effort** — no current source was available; TECH-111 (which
      superseded TECH-101's scope) already shipped 2026-07-14 against the same 2020 PDF
      hypothesis this ADR itself flags in its Context section. Re-verifying against a
      live/current DANE source remains open, not blocking.
