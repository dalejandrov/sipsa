# ADR-008 — Timezone, Locale, and Date Semantics Strategy

**Status:** Proposed (**not accepted** — pending explicit decision)
**Date:** 2026-07-13 (Proposed)
**Author:** Temporal/locale strategy review (branch `refactor/internal-models-and-api-filter`)
**Backlog:** TECH-100 (Pending, Proposed), TECH-101 (Pending, Proposed), TECH-102 (Pending, Proposed),
TECH-103 (Pending, Proposed), TECH-104 (Pending, Proposed — SPIKE), TECH-105 (Pending, deliberately
not prioritized), TECH-106 (Pending, Proposed)
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

- **F1 — Calendar-date fields typed as instants.** `fechaCaptura` (`SipsaCiudad`),
  `fechaMesIni` (`SipsaMayoristasMensual`, `SipsaAbastecimientosMensual`), `fechaIni`
  (`SipsaMayoristasSemanal`), and `enmaFecha` (`SipsaParcial`) are all `Instant` in the
  entity and `OffsetDateTime` in the API response, despite DANE documenting them as
  calendar/period-start dates, not instants. The current mapper convention
  (`TimezoneUtil.convertToOffsetDateTime(value, isSystemGenerated=false)`) pins these to
  UTC regardless of the requested `X-Timezone`, which **prevents** the date-shifting bug
  today, but only by convention — the type itself does not guarantee it.
- **F2 — `WindowPolicy` does not bind the monthly run day to the method.**
  `validateMonthly` accepts day 8 **or** day 10 (plus grace days 9/11) for *any* monthly
  method, so `promedioAbasSipsaMesMadr` can pass validation on day 8 and
  `promediosSipsaMesMadr` can pass validation on day 10 — contradicting DANE's documented
  distinct schedule. Reachable today via `POST /api/internal/ingestion/run`.
- **F3 — Monthly `windowKey` does not match its own documented contract.** The class
  Javadoc promises `YYYY-MM-M8`/`YYYY-MM-M10` keys; the implementation actually emits
  `YYYY-MM-DD` (the real run date), which breaks the idempotency guarantee across grace-day
  reruns (day 8 vs. day 9 produce different keys for the same logical period).
  `promediosSipsaSemanaMadr` is confirmed correctly classified as a daily/weekly method
  (2:00 p.m. window), consistent with DANE's text.
- **F4 — `TimezoneFilter` silently downgrades an invalid `X-Timezone` to UTC** instead of
  returning `HTTP 400`, contrary to the desired contract (stable error code + validation
  feedback).
- **F5 — `GlobalExceptionHandler` uses `LocalDateTime.now()`** for error-response
  timestamps (3 records), which is ambiguous (no offset), inconsistent with the
  `OffsetDateTime` used everywhere else in the API contract.
- **F6 — No i18n exists**, and none is being proposed by this ADR. Error codes are already
  stable strings, which is a correct precondition for i18n if it is prioritized later.
- **No violation found** of "do not infer timezone from locale/IP/Accept-Language," and no
  localized date format (`dd/MM/yyyy` etc.) exists anywhere in the JSON contract — both
  confirmed absent, which is correct.

---

## Decision (Proposed — not yet accepted)

Adopt a refined **Alternative B** (international API, business zone fixed internally,
`X-Timezone` converts only system instants, calendar dates are never converted):

1. Keep `America/Bogota` as the fixed business zone for `WindowPolicy`,
   `SpecificationBuilder`, and the scheduler. **No change.**
2. Keep `Instant`/`TIMESTAMPTZ` as the canonical instant representation, and
   `OffsetDateTime` (ISO-8601 with explicit offset) as the JSON representation of
   instants. **No change.**
3. Retype the four calendar/period-start fields identified in F1 as `LocalDate` in the API
   response layer (minimum scope); evaluate — via a SPIKE, not directly — whether to also
   retype the entity/DB column (see TECH-104).
4. Fix `WindowPolicy` so the monthly-day rule is method-aware (F2) and the monthly
   `windowKey` matches its documented `YYYY-MM-M8`/`YYYY-MM-M10` contract (F3). Add an
   injectable `Clock` to make boundary conditions testable.
5. Fix `TimezoneFilter` to return `HTTP 400` with a stable error code
   (`SIPSA_INVALID_TIMEZONE`) on an invalid `X-Timezone` header (F4).
6. Fix `GlobalExceptionHandler`'s three timestamp fields to use an explicit-zone type
   (F5).
7. **Do not implement i18n/locale-aware messages in this round** (F6) — no evidence of
   urgency; explicitly deferred, not rejected.

**This ADR does not authorize implementation of any of the above.** Each numbered item
maps to a backlog story (see review doc, section J) to be scoped, estimated, and
implemented independently, one story per branch/PR, consistent with this repository's
existing convention (see ADR-007).

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

**If accepted (all seven items, each as its own story):**
- Calendar-date fields become impossible to accidentally date-shift by construction, not by
  convention.
- Manual/retried monthly ingestion triggers can no longer cross DANE's documented
  Mayoristas/Abastecimientos publication-day boundary.
- The monthly `windowKey` idempotency guarantee actually holds across grace-day reruns.
- Invalid `X-Timezone` becomes a visible, stable 400 instead of a silent UTC fallback.
- Error-response timestamps become consistent (offset-aware) with the rest of the API.
- No JSON field is renamed, no HTTP route changes, no database migration is mandated by
  this ADR alone (TECH-104 is a SPIKE, not a migration commitment).

**If rejected or partially accepted:**
- No functional regression from today. The latent F1 risk remains contained by convention
  only; F2/F3 remain live, reachable via the manual trigger endpoint; F4/F5 remain minor UX
  inconsistencies.

---

## Explicitly Not Decided by This ADR

- Whether to migrate the four calendar columns from `TIMESTAMPTZ` to `DATE` in the
  database — deferred to TECH-104 (SPIKE).
- Whether/when to implement i18n — deferred, not scheduled.
- The exact wording of any future localized error message.
- Whether `consultarInsumosSipsaMesMadr` will ever be implemented in this codebase (it
  currently is not, and is out of scope here).

## Acceptance Criteria for This ADR

- [ ] Reviewed by the team/owner and moved to `Accepted` (in full or scoped), or
      `Rejected`, with rationale recorded here.
- [ ] If accepted, TECH-100 through TECH-106 (or the accepted subset) added to
      `docs/backlog/technical-backlog.md` with real IDs and status `Ready`.
- [ ] DANE's documented publication schedule (2:00 p.m., day 8, day 10) re-verified against
      a current source before TECH-101 is implemented, or explicitly accepted as
      unverified/best-effort if no current source is available.
