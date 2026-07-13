# Timezone and Locale Strategy Review

**Date:** 2026-07-13
**Scope:** Read-only analysis. No production code was modified.
**Contrast source:** `DANE-webservice-SIPSA.pdf` (Oficina de Sistemas DANE,
**March 2020**). See the currency warning in section D.
**Branch:** `refactor/internal-models-and-api-filter`
**Related:** [ADR-008 (Proposed)](../adr/ADR-008-timezone-locale-and-date-semantics.md)

---

## A. Executive summary

The current design already gets the two most important decisions right: it uses
**`Instant` + `TIMESTAMPTZ`** as the instant representation and **`America/Bogota`** as
the fixed business zone for ingestion windows and date filters, independent of whatever
the client requests. That is correct and should not change.

The real problem is not the server's timezone, but that **four fields DANE documents as
"collection date" or "period start day" are modeled as instants (`Instant`/
`OffsetDateTime`)** instead of calendar dates (`LocalDate`): `fechaCaptura`, `fechaMesIni`
(two different entities), `fechaIni`, and `enmaFecha`. Today this does not corrupt data
because the mapper forces these fields to stay in UTC regardless of the zone the client
requests (`isSystemGenerated = false` on every call to
`TimezoneUtil.convertToOffsetDateTime`) — but that protection is a **convention the type
system does not enforce**, and it is exactly the kind of field where a future accidental
change (flipping that boolean on a single mapper line) would silently reintroduce the risk
described in section 4 of the assignment: a collection date shifting by a day when
converted to a more westward zone.

In addition, two concrete, verifiable defects were found in `WindowPolicy`, not
hypothetical ones:

1. **It does not distinguish day 8 (Mayoristas monthly) from day 10 (Abastecimientos
   monthly)** — it validates both methods against the same `{8, 10}` set of allowed days.
2. **The monthly window key (`windowKey`) does not honor the contract documented in the
   class's own Javadoc** (`YYYY-MM-M8` / `YYYY-MM-M10`): it actually generates
   `YYYY-MM-DD` with the real execution date, which allows multiple "valid" ingestions of
   the same logical period if run on different grace days.

Both are reachable today through the real operational endpoint `POST
/api/internal/ingestion/run?method=...&force=false` (`SipsaOpsController.java:56-69`), not
just in theory.

**Main recommendation:** adopt a refined variant of **Alternative B** (fixed business zone
+ optional `X-Timezone` that only affects system instants), which is, in fact, very close
to what the code already does — with three corrections: (1) model the 4 calendar fields as
`LocalDate` instead of instant, (2) respond with `HTTP 400` on an invalid `X-Timezone`
instead of silently degrading to UTC, and (3) fix the two `WindowPolicy` defects described
above. Localizing messages (i18n) is not recommended in this round — there is no evidence
it is urgent, and the assignment explicitly asks not to implement it yet.

---

## B. Proposed temporal strategy (summary — see ADR-008 for detail)

| Layer | Proposed decision | Current state |
|---|---|---|
| Storage — instants | `Instant` / `TIMESTAMPTZ` | ✅ Already so |
| Storage — calendar/period dates | `LocalDate` / `DATE` | ❌ Currently `Instant`/`TIMESTAMPTZ` |
| Internal processing | UTC for instants; `America/Bogota` for business rules (windows, filters) | ✅ Already so |
| JSON serialization — instants | ISO-8601 with explicit offset (`OffsetDateTime`) | ✅ Already so |
| JSON serialization — calendar dates | `yyyy-MM-dd` (`LocalDate`) | ❌ Currently timestamps with offset |
| Consumer timezone | `X-Timezone` only affects system instants (`fechaSincronizacion`, `startTime`, `endTime`, `occurredAt`) | ✅ Already so |
| Locale / messages | No i18n yet; error codes already stable | ✅ Already meets the precondition; i18n out of scope |
| Ingestion windows | `America/Bogota` zone, **method-specific** day rules | ❌ Currently shared across monthly methods |

---

## C. Temporal inventory

### JPA entities (`domain/entity`)

| Field | Class | Current type | Semantics | Zone required | Risk |
|---|---|---|---|---|---|
| `SipsaCiudad.fechaCaptura` | `Instant` | collection date | none (calendar) | **High** — instant type for a calendar-date field |
| `SipsaCiudad.fechaCreacion` | `Instant` | creation date (DANE DB) — a real instant | UTC | Low — correct type |
| `SipsaCiudad.fechaSincronizacion` | `Instant` | technical ingestion timestamp | converted to client zone | Low — correct type |
| `SipsaMayoristasMensual.fechaMesIni` | `Instant` (`NOT NULL`) | monthly period (start date) | none (calendar) | **High** |
| `SipsaMayoristasMensual.fechaCreacion` | `Instant` (nullable, "usually null from SOAP") | creation date (instant) | UTC | Low |
| `SipsaMayoristasMensual.fechaSincronizacion` | `Instant` | technical timestamp | converted to client zone | Low |
| `SipsaMayoristasSemanal.fechaIni` | `Instant` (`NOT NULL`) | weekly period (start date) | none (calendar) | **High** |
| `SipsaMayoristasSemanal.fechaCreacion` | `Instant` (nullable) | creation date (instant) | UTC | Low |
| `SipsaMayoristasSemanal.fechaSincronizacion` | `Instant` | technical timestamp | converted to client zone | Low |
| `SipsaAbastecimientosMensual.fechaMesIni` | `Instant` | monthly period (start date) | none (calendar) | **High** |
| `SipsaAbastecimientosMensual.fechaCreacion` | `Instant` | creation date — **not listed in the DANE method's field table** (p. 3), but present in the table dictionary (p. 11) | UTC | Medium — validate against the real WSDL |
| `SipsaAbastecimientosMensual.fechaSincronizacion` | `Instant` (`@Builder.Default Instant.now()`) | technical timestamp | converted to client zone | Low |
| `SipsaParcial.enmaFecha` | `Instant` | **collection date** (literally documented as "fecha", not "fecha y hora") | none (calendar) | **High** |
| `SipsaParcial.fechaSincronizacion` | `Instant` | technical timestamp | converted to client zone | Low |
| `IngestionRun.startTime` / `.endTime` | `Instant` | technical execution timestamp | converted to client zone | Low |
| `IngestionAudit.occurredAt` | `Instant` | technical audit timestamp | converted to client zone | Low |
| `IngestionReject.createdAt` | `Instant` | technical timestamp | converted to client zone | Low |

### Request DTOs (`api/dto/request`) — already using the correct type

`CiudadQueryRequest`, `MayoristasMensualQueryRequest`, `MayoristasSemanalQueryRequest`,
`ParcialQueryRequest`, `AbastecimientosMensualQueryRequest`, `AuditQueryRequest`: all of
their filters (`fecha`, `startDate`, `endDate`, `fechaMes`, `fechaIni`, `fechaEncuesta`)
use `LocalDate` with `@DateTimeFormat(iso = ISO.DATE)`. **Low risk — this is the correct
pattern**, and should be the model replicated in entities/responses.

### Response DTOs (`api/dto/response`) — inherit the entity's risk

All 5 records (`SipsaCiudadResponse`, `SipsaMayoristasMensualResponse`,
`SipsaMayoristasSemanalResponse`, `SipsaAbastecimientosMensualResponse`,
`SipsaParcialResponse`) expose **every** temporal field as `OffsetDateTime`, including the
calendar ones (`fechaCaptura`, `fechaMesIni`, `fechaIni`, `enmaFecha`) → **High risk**
(same problem as the entity, propagated to the public contract). `IngestionRunResponse`,
`IngestionRunDetailResponse`, `AuditTrailResponse` use `OffsetDateTime` for technical
timestamps → Low risk, correct type.

### Utilities / infrastructure

| Element | Observation | Risk |
|---|---|---|
| `TimezoneUtil.REQUEST_TIMEZONE` (`ThreadLocal<ZoneId>`) | `set`/`clear` correctly wrapped in `try/finally` from `TimezoneFilter` | Low — correctly managed |
| `TimezoneFilter.resolveTimezone` | On an invalid header, catches the exception, logs at `DEBUG`, and falls back to UTC **without informing the client** | **Medium-High** — UX/contract: violates the section 7 rule (an invalid `X-Timezone` should be HTTP 400) |
| `WindowPolicy.zone` (`America/Bogota` by default) | Fixed business zone, correct | Low |
| `WindowPolicy.DATE_FMT` (`yyyy-MM-dd`), also used for the monthly key | Contradicts the class's own Javadoc (`YYYY-MM-M8`/`YYYY-MM-M10`) | **High** — see section F |
| `WindowPolicy.validateMonthly` | Does not bind day-of-month to method | **High** — see section F |
| `SpecificationBuilder` (fixed business zone, not the request's zone) | Correct: calendar filters are always interpreted in `America/Bogota`, regardless of `X-Timezone` | Low — correct design, but **not explicitly documented** and could surprise a consumer who assumes `X-Timezone` also affects filters |
| `GlobalExceptionHandler` — `ErrorResponse.timestamp`, `ValidationErrorResponse.timestamp`, `IngestionValidationErrorResponse.timestamp` | `LocalDateTime.now()` — technical timestamp **with no zone/offset** | **High** — ambiguous, inconsistent with the rest of the API (which uses `OffsetDateTime`) |
| `XmlParsingUtil.parseXmlDateTime` | Parses an ISO `ZonedDateTime` with the source XML's offset → epoch millis | Low — correct, preserves the source offset (`-05:00`) |
| `SipsaIngestionScheduler` (`@Scheduled(..., zone = "${sipsa.timezone}")`) | Fixed business zone for the crons | Low — correct |

### Database (`V1__initial_schema.sql`)

All temporal columns are **`TIMESTAMPTZ`**, including the 4 semantically
calendar/period ones: `fecha_captura`, `fecha_mes_ini` (x2 tables), `fecha_ini`,
`enma_fecha`. Consistent with `Instant`, but **does not reflect the semantics DANE
documents** (a date with no time-of-day). No migration is proposed now — see section I.

---

## D. Contrast with the DANE document

⚠️ **The document is from March 2020.** The schedules (2:00 p.m., day 8, day 10) and
method availability may have changed since. No schedule decision should reach production
without confirming it against a current source (current DANE documentation or the actual
live WSDL response).

| SIPSA method | Temporal field | DANE example | Documented semantics | Recommended type | Current type in the code |
|---|---|---|---|---|---|
| `promediosSipsaCiudad` | `fechaCaptura` | `2017-11-23T00:00:00-05:00` | Collection date | `LocalDate` | `Instant`/`OffsetDateTime` ⚠️ |
| `promediosSipsaCiudad` | `fechaCreacion` | `2017-11-23T14:00:00-05:00` | DB registration date (a real instant, with time-of-day) | `Instant`/`OffsetDateTime` | `Instant`/`OffsetDateTime` ✅ |
| `promediosSipsaParcial` | `enmaFecha` | `2017-03-29T00:00:00-05:00` | Collection date | `LocalDate` | `Instant`/`OffsetDateTime` ⚠️ |
| `promediosSipsaSemanaMadr` | `fechaIni`¹ | `2018-01-06T00:00:00-05:00` | Cutoff date / week start (period) | `LocalDate` | `Instant`/`OffsetDateTime` ⚠️ |
| `promediosSipsaMesMadr` | `fechaMesIni` | `2018-01-06T00:00:00-05:00` | Month start day queried (period) | `LocalDate` or a `YearMonth` model | `Instant`/`OffsetDateTime`, `NOT NULL` ⚠️ |
| `promediosSipsaMesMadr` | `fechaCreacion` | `2017-11-23T14:00:00-05:00` | DB registration date (instant) | `Instant`/`OffsetDateTime` | `Instant`/`OffsetDateTime` ✅ |
| `promedioAbasSipsaMesMadr` | `fechaMesIni` | `2016-10-01T00:00:00-05:00` | Month start day queried (period) | `LocalDate` or `YearMonth` | `Instant`/`OffsetDateTime` ⚠️ |
| `promedioAbasSipsaMesMadr` | `fechaCreacion` | not present in the method's output field table (p. 3 of the PDF); present in the `SIPSA_ABASTECIMIENTOS_MES_MADR` table dictionary (p. 11) | Record creation date (if it exists) | `Instant`/`OffsetDateTime` | Present in the entity and DTO — **must be validated against the real WSDL to confirm DANE actually sends it** |

¹ **Inconsistency in the PDF itself (not in the code):** the field table for
`promediosSipsaSemanaMadr` (p. 5) lists the field as `fechaMesIni`, but the XML example
right below it uses `<fechaIni>`. The data dictionary (`SIPSA_MAYORISTA_SEMANA_MADR`, p.
9) confirms the column as `FECHA_INI`. The code
(`SipsaMayoristasSemanal.fechaIni`, `SipsaMayoristasSemanalResponse.fechaIni`) correctly
follows the XML example and the dictionary, **not** the field-table column name — this is
correct, not a finding to fix.

**Assignment-requested validations, confirmed:**
- `fechaCaptura` (Ciudad) is mapped today with `isSystemGenerated = false` → it always
  stays in UTC, never converted to the client's zone. Correct in terms of *behavior*,
  incorrect in terms of *type* (it is still an instant, not a date).
- `fechaCreacion` keeps its instant semantics in all 3 entities where it appears. Correct.
- `fechaMesIni` (Mayoristas monthly and Abastecimientos monthly) is treated as a
  period-start date at the documented semantic level, but the type (`Instant`,
  `NOT NULL`) does not reflect that.
- `fechaIni` weekly: same case as `fechaMesIni`.
- `enmaFecha` is, in fact, treated as a collection date both in the documentation and in
  the code comments (`SipsaParcial.java:85` — "Survey/data collection date"), but the type
  is `Instant`.
- **No calendar field is currently converted into a different date due to a timezone
  difference** anywhere in the public API path — the mapper forces them to UTC
  (`isSystemGenerated = false`), so the risk of "2017-11-23 becomes 2017-11-22" **does not
  materialize today**. It is a latent design risk (wrong type + conventional-only
  protection), not an active bug.

**Out of scope for the current implementation:** `consultarInsumosSipsaMesMadr` has no
handler, entity, or table in this repository — it does not apply to the inventory or to
`WindowPolicy`.

**DANE documentation vs. code gap:** the "Generalidades" paragraph of the PDF (p. 2) only
declares explicit schedules for **Mayoristas** (daily/weekly from 2:00 p.m., monthly on
day 8) and **Abastecimientos** (monthly on day 10). It does not explicitly mention
`promediosSipsaCiudad` or `promediosSipsaParcial`. The code (`WindowPolicy`,
`SipsaIngestionScheduler`) groups Ciudad and Parcial under the same 2:00 p.m. daily window
as Mayoristas-daily/weekly — a reasonable operational assumption but **not literally
justified by the 2020 text**. It must be confirmed against a current source.

---

## E. Evaluation of `TimezoneFilter`

**Recommendation: simplify, do not remove.**

What it does well:
- Resolves `X-Timezone` with a clear precedence (header → UTC), uses `ZoneId.of(...)`
  (real IANA IDs, automatically rejects abbreviations like `EST`/`PST` because they are
  not valid IANA IDs).
- Does not infer the zone from `Accept-Language`, IP, or locale — meets the constraint.
- Correctly clears the `ThreadLocal` in `finally` (no leakage across requests).
- Only affects system technical timestamps, never calendar dates (see section D).

What should change (as a future story, not now):
- On an invalid header, it currently responds **200 with UTC data** instead of **400**.
  The assignment explicitly asks for an invalid `X-Timezone` to return `HTTP 400` with a
  stable, localizable error code and message. This requires the validation to happen at a
  point that can short-circuit with a structured JSON response (today
  `OncePerRequestFilter` does not have that contract) — a design change, not a trivial
  fix.
- It is the only real dependent of `TimezoneUtil` outside the mappers/`AuditTrailService`;
  there is no reason to remove it or to replace the `ThreadLocal` pattern (already
  evaluated in ADR-007 §F2, where it was decided to keep it).

---

## F. Evaluation of `WindowPolicy`

**What is correct:**
- Fixed business zone (`America/Bogota`), correct and consistent with
  `SpecificationBuilder` and the scheduler.
- `promediosSipsaSemanaMadr` is correctly classified as a **daily** method (its name
  contains neither `"mesmadr"` nor `"abas"`), so it uses the 2:20 p.m. window, consistent
  with DANE's text ("daily and weekly data... available from 2:00 p.m."). **This is
  exactly what the assignment asked to verify explicitly — confirmed correct.**
- The 20-30 minute margin over the documented 2:00 p.m. (`daily-window-start: 14:20`,
  `monthly-mes`/`monthly-abas` crons at 14:30) is a reasonable operational decision,
  documented in `application.yaml:118-125` and in the scheduler's Javadoc — not a bug, a
  deliberate buffer.
- `force=true` correctly bypasses window validation but **still generates** a window key
  (it does not skip it).

**Finding 1 — confirmed, exactly what the assignment asked to verify:**
`validateMonthly` (`WindowPolicy.java:156-184`) does not receive the method name, only
`now`/`force`. The check is:
```java
if (monthlyRunDays.contains(day) && !time.isBefore(monthlyStart)) { return key; }
if ((day == 8 && !time.isBefore(monthlyStart)) || day == 9) { return key; }
if ((day == 10 && !time.isBefore(monthlyStart)) || day == 11) { return key; }
```
`monthlyRunDays` defaults to `{8, 10}` — **the same set for `promediosSipsaMesMadr`**
(Mayoristas, should only be day 8) **and `promedioAbasSipsaMesMadr`** (Abastecimientos,
should only be day 10). The result: today it is possible to run
`promedioAbasSipsaMesMadr` on day 8 (or 9) and `promediosSipsaMesMadr` on day 10 (or 11),
both with `force=false`, and `WindowPolicy` will accept them without error. The automated
cron (`SipsaIngestionScheduler`) does trigger each method on its correct day — the problem
is that **the safety validation does not enforce it**, so any manual retry/trigger via
`POST /api/internal/ingestion/run` (`SipsaOpsController.java:56`) can violate DANE's
documented schedule without the system detecting it.

**Finding 2 — new, found during this review, related to Finding 1:**
The class's own Javadoc documents monthly keys as `YYYY-MM-M8` / `YYYY-MM-M10`
(`WindowPolicy.java:36`, mirrored in `IngestionRun.java:37`), but the actual
implementation (`WindowPolicy.java:164`, `String key = now.format(DATE_FMT)`) generates
`YYYY-MM-DD` with the **real execution date**. Consequence: if `promediosSipsaMesMadr`
runs on day 8, and, due to a retry, runs again on day 9 (grace day), two different
`windowKey`s are generated (`2026-01-08` and `2026-01-09`) for the **same logical
period**. The unique constraint `(method_name, window_key)` on `ingestion_runs`
(`V1__initial_schema.sql`) does not catch the duplicate because the keys differ — this
breaks the idempotency guarantee the Javadoc itself promises ("data for 2026-01-02 should
only be ingested once"). Combined with Finding 1, a monthly method could, in the worst
case, produce up to 4 distinct "valid" executions in a single month (days 8, 9, 10, 11).

**Finding 3 — a testing gap, not a behavioral one:**
`WindowPolicy` uses `ZonedDateTime.now(zone)` directly (`WindowPolicy.java:104`), with no
injected `Clock`. No test exists for this class at all (`find src/test -iname
"*WindowPolicy*"` → no results). The test plan requested in section 11 of the assignment
(exact 2:00 p.m. boundaries, day 8, day 9, day 10, day 11, month rollover) **cannot be
implemented deterministically without introducing a `Clock`** — today it would require
manipulating the system's real clock or using `Thread.sleep`, both unacceptable.

---

## G. Alternatives compared

| Alternative | Advantages | Disadvantages | Risk of adopting it | Is this already what exists? |
|---|---|---|---|---|
| **A — Colombia-centered** (fixed zone, no `X-Timezone`, Spanish-only messages) | Simpler; removes all `TimezoneFilter`/`TimezoneUtil` logic | Breaks the current contract for any international consumer; regresses already-built and tested functionality | High — functional regression with no clear benefit | No |
| **B — International with configurable `X-Timezone`** (fixed internal business zone; the header only converts instants; calendar dates are never converted; future messages via `Accept-Language`) | Compatible with international consumers without sacrificing Colombian business semantics; **is, in practice, what is already built** | Requires type discipline (LocalDate vs. Instant) to avoid reintroducing the date-shift risk | Low — a formalization of what exists, not an architecture change | **Yes, approximately** — missing the correct typing of the 4 calendar dates and the 400 on an invalid header |
| **C — Canonical UTC** (every instant in UTC, no `TimezoneFilter`, client converts) | Simpler to maintain; removes the `ThreadLocal` | Removes already-used functionality (converting technical timestamps to the client's zone) with no evidence it needs removing; does not solve the real problem (the 4 calendar fields) | Medium — functional regression for no benefit toward the real problem | No |
| **D — Country-localized format** (`dd/MM/yyyy`, `MM/dd/yyyy`) | None, in a JSON API contract | Ambiguous across a client's regional settings (`03/04/2026` is April 3 or March 4 depending on the client's locale, not the server's); impossible to parse deterministically without knowing the locale out of band; breaks cacheability and string comparability; ISO-8601 already solves this unambiguously | Very high — should never be adopted in the JSON contract | No, and no evidence was found that the code does this anywhere — correct |

**Conclusion:** the code already converges, for the most part, toward Alternative B. No
different alternative is recommended; **completing** it by fixing the calendar-date typing
and the `X-Timezone` error handling is recommended instead.

---

## H. Final recommendation

1. **Keep** `America/Bogota` as the fixed business zone (`WindowPolicy`,
   `SpecificationBuilder`, scheduler) and `UTC`/`Instant` as the canonical instant
   representation. There is no reason to change either of these two decisions.
2. **Keep** `TimezoneFilter`/`TimezoneUtil`, but fix the invalid-header handling to
   respond with `400` and a stable code (e.g., `SIPSA_INVALID_TIMEZONE`) — a separate
   story, not implemented in this review.
3. **Retype** `fechaCaptura`, `fechaMesIni` (both entities), `fechaIni`, and `enmaFecha`
   as `LocalDate` in the API responses (minimum scope), and evaluate whether it is also
   worth doing so in the entity/DB (needs more evidence — see section I). This removes the
   date-shift risk by construction, instead of depending on every mapper continuing to
   pass `isSystemGenerated = false` correctly.
4. **Fix `WindowPolicy`** so the monthly rule depends on the method (day 8 only for
   `promediosSipsaMesMadr`, day 10 only for `promedioAbasSipsaMesMadr`) and so the monthly
   `windowKey` honors its own documented contract (`YYYY-MM-M8`/`YYYY-MM-M10`).
5. **Do not implement i18n yet** — there is no evidence of business urgency, and the
   assignment explicitly excludes it from this round. Do note that the current error
   codes (`VALIDATION_ERROR`, `BUSINESS_ERROR`, etc.) are already stable and would be
   ready to wrap localized messages the day it is decided to do so.
6. **Fix** the 3 timestamps in `GlobalExceptionHandler` (`LocalDateTime.now()`) to a type
   with an explicit zone (`Instant` or `OffsetDateTime`), for consistency with the rest of
   the contract — a minor but concrete finding.
7. **Add an injectable `Clock` to `WindowPolicy`** to be able to write the exact-boundary
   test plan required in section 11 of the assignment.

None of the above items were implemented in this review — they remain as proposed
backlog stories (section J) and as ADR-008 (`Proposed`, not accepted).

---

## I. Note on persistence (no migration proposed)

The 4 calendar columns (`fecha_captura`, `fecha_mes_ini` × 2, `fecha_ini`, `enma_fecha`)
are currently `TIMESTAMPTZ`. Changing them to `DATE` would be more semantically correct,
but it is a schema change with impact on: the 5 mappers, the 5 API responses,
`SpecificationBuilder.addDateFilter` (which currently builds `Instant` ranges), and
potentially on existing indexes/queries. **There is not enough evidence in this review to
propose the column migration as mandatory** — it is proposed as an investigation story
(`TECH-104`, a SPIKE) to determine the real cost before deciding. This respects the
constraint of not proposing destructive migrations without evidence.

---

## J. Proposed backlog stories

IDs confirmed free against `docs/backlog/technical-backlog.md` (the highest existing one
is `TECH-095`).

- **TECH-100** — Define and document the API's canonical date/time representation
  (`LocalDate` for calendar dates vs. `OffsetDateTime` for instants), as an explicit
  contract across the 5 SIPSA data responses.
- **TECH-101** — Fix `WindowPolicy.validateMonthly` to bind the allowed day to the method
  (Finding 1), and fix the monthly `windowKey` generation to honor the documented
  `YYYY-MM-M8`/`YYYY-MM-M10` contract (Finding 2). Includes adding an injectable `Clock`.
- **TECH-102** — Add timezone-conversion tests (instants vs. calendar dates) for the 5
  mappers in `api/mapper`, covering `America/Bogota`, `America/New_York`,
  `America/Los_Angeles`, and `UTC`, including U.S. daylight-saving transitions.
- **TECH-103** — Make `TimezoneFilter` respond with `HTTP 400` and a stable code
  (`SIPSA_INVALID_TIMEZONE`) on an invalid `X-Timezone` header, instead of silently
  degrading to UTC. (Note: localizing the *message* of that error is out of scope until
  i18n is decided on — see TECH-105 below if it is prioritized.)
- **TECH-104** — SPIKE: evaluate the real cost and impact of migrating
  `fecha_captura`/`fecha_mes_ini`/`fecha_ini`/`enma_fecha` from `TIMESTAMPTZ` to `DATE` in
  the DB, entity, and DTO. Do not implement without the SPIKE confirming the scope.
- **TECH-105** — (Explicitly not prioritized in this round) Evaluate the real need for
  message internationalization (`Accept-Language` + `MessageSource`) before implementing
  it — the assignment asks not to implement i18n yet.
- **TECH-106** — Fix `GlobalExceptionHandler`'s timestamps (`ErrorResponse`,
  `ValidationErrorResponse`, `IngestionValidationErrorResponse`) from `LocalDateTime` to a
  type with an explicit zone.

---

## Limitations of this review

- No access was available to a real, current SOAP response from the DANE WSDL; the
  contrast was made exclusively against the 2020 PDF's examples. Fields marked as
  "validate against the real WSDL" must be confirmed before any contract change. The
  source document itself warns of this: the 2:00 p.m., day 8, and day 10 schedules should
  not be assumed current without an up-to-date source.
