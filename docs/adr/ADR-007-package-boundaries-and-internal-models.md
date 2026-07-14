# ADR-007 — Package Boundaries and Internal Models

**Status:** Accepted (**partial / scoped** — see "Scope of This Acceptance" below)
**Date:** 2026-07-13 (Proposed) — 2026-07-13 (Accepted, scoped)
**Author:** Structural diagnosis (branch `refactor/package-structure-and-boundaries`)
**Backlog:** [TECH-090](../backlog/technical-backlog.md#tech-090) (**Done**, merged), [TECH-091](../backlog/technical-backlog.md#tech-091) (**Done**, merged), [TECH-095](../backlog/technical-backlog.md#tech-095) (**Done**, merged), [TECH-093](../backlog/technical-backlog.md#tech-093) (Pending — unblocked since TECH-090/TECH-091 merged), [TECH-092](../backlog/technical-backlog.md#tech-092) (Blocked, pending [TECH-094](../backlog/technical-backlog.md#tech-094) SPIKE)
**Related:** [ADR-000](ADR-000-current-architecture.md), [Refactoring Roadmap — RF-01](../architecture/refactoring-roadmap.md#rf-01--move-internal-dtos-from-apidtorequest-to-applicationdto), [Architecture Review — Dependency Violations](../architecture/architecture-review.md#architecture-map)

---

## Scope of This Acceptance

This ADR is accepted **only** for the following findings:

- **F1** — internal ingestion models (`IngestionRequest`, `CreateRunRequest`,
  `AuditEventRequest`) incorrectly placed as HTTP DTOs → **TECH-090**.
- **F2** — `TimezoneFilter` incorrectly placed under `infrastructure/config` →
  **TECH-091**.
- **F4** — Javadoc-only `domain → infrastructure` reference in `SoapGateway` →
  **TECH-095**.
- **F5** — absence of architectural (ArchUnit) rules → **TECH-093**, gated until
  TECH-090 and TECH-091 land.

**This acceptance explicitly does NOT authorize:**
- A general reorganization by feature/functionality (RF-03 — still rejected).
- Moving all internal DTOs, or any DTO beyond the 3 named in F1.
- Moving any mapper — every mapper investigated (`api/mapper/*`,
  `infrastructure/soap/mapper/SipsaIngestionMapper`) was found correctly placed.
- Moving any exception — all 7 `domain/exception/*` classes were confirmed free of
  technical dependencies.
- Separating the JPA model from the domain model.
- Any change to `SipsaParcial` deduplication (TECH-010/TECH-011/ADR-001 remain
  untouched and out of scope here, per `AGENTS.md`).
- A general refactor of `SipsaReadService`, `IngestionRunQueryService`, or
  `AuditTrailService` — their use of `api.mapper`/`api.dto.response` was investigated
  and explicitly kept (see "What Was Investigated and Found Not to Justify a Move"
  below).
- **F3** (CXF-generated SOAP sources sharing a package with manual code) is **not**
  accepted for implementation yet. It requires **TECH-094** (SPIKE) first — see
  "F3 Status" below.

---

## Context

`architecture-review.md` already documented a set of `application → api` compile-time
dependencies and explicitly **discarded** a broad fix (RF-01: move all internal DTOs;
RF-03: reorganize by feature) as disproportionate at this scale.

This ADR does not reopen that broad decision. It re-examines the *same evidence* at
class-level granularity to determine whether a **narrow, low-risk subset** of it is
justified on its own — separately from the parts that remain correctly rejected.

No source code has been moved to produce this ADR. All findings below were obtained with
read-only inspection (`grep`, `find`, manual review) on branch
`refactor/package-structure-and-boundaries`, based on `main` at commit `83e45eb`.

---

## Method

For every class implicated in a cross-layer import, we recorded: current package, real
consumers, actual responsibility, concrete problem (not aesthetic), evidence, and the
cost/risk of moving it. Classes without a *concrete, verifiable* problem were left where
they are, even if their package "looks wrong" at first glance.

---

## Findings

### F1 — Internal ingestion commands stored as HTTP `*Request` DTOs

**Classes:** `IngestionRequest`, `CreateRunRequest`, `AuditEventRequest`
**Current package:** `api/dto/request`
**Real consumers:** `application/ingestion/core/IngestionJob.java`,
`application/ingestion/scheduler/SipsaIngestionScheduler.java`,
`application/service/{IngestionControlService, IngestionTriggerService, IngestionAuditService, AsyncIngestionService}.java`
— **zero controllers**.
**Real responsibility:** internal application commands — they carry state between
services inside the ingestion pipeline and audit subsystem.
**Concrete problem:**
- None of the three is ever bound from an HTTP request (`@RequestBody`/`@ModelAttribute`).
  All instances are built through internal static factories (`IngestionRequest.manual(...)`,
  `CreateRunRequest.from(...)`, `AuditEventRequest.ingestionStarted(...)`, etc.).
- The `Request` suffix implies an HTTP contract that does not exist, which is misleading to
  a new contributor reading `IngestionJob.execute(IngestionRequest request)`.
- Produces 6 files' worth of `application → api` imports (confirmed in
  `architecture-review.md`'s dependency table).
**Evidence:** `grep -RIn "IngestionRequest\|CreateRunRequest\|AuditEventRequest" src/main/java` — every non-declaration hit is inside `application/*`; zero hits inside `api/controller/*`.
**Impact today:** None functionally. It is a naming/layering inconsistency, not a bug.
**Classification:** dependencia indebida + deuda técnica (already known — this is RF-01,
narrowed to the 3 classes with zero HTTP usage instead of RF-01's broader unstated scope).
**Proposal:** create `application/command/`; move the three records there. Keep the field
names as-is (they are not the public HTTP contract). Renaming the classes themselves
(dropping `Request`) is optional and left to the implementer of TECH-090 — do it only if it
does not inflate the diff meaningfully.
**Cost:** ~9 files touched (3 moved classes + import updates in the 6 consumers). Purely
mechanical — no logic changes.
**Risk:** Low. No behavior change; compiler catches every missed import.
**If not done:** No functional harm. The misleading naming and the `application → api`
imports persist; a future contributor may keep copying the `*Request` pattern for genuinely
internal objects, growing the same problem.

---

### F2 — `TimezoneFilter` misplaced in `infrastructure/config`

**Class:** `TimezoneFilter`
**Current package:** `infrastructure/config`
**Real consumers:** none (it's a `@Component` `OncePerRequestFilter`, auto-detected by
Spring Boot's component scan — no explicit registration references its package).
**Real responsibility:** HTTP request boundary concern — it reads the `X-Timezone` header
and manages a request-scoped `ThreadLocal`. This is functionally the same category of
component as `GlobalExceptionHandler` (already correctly placed in `api/controller`).
**Concrete problem:** It imports `api.util.TimezoneUtil`
(`infrastructure/config/TimezoneFilter.java:3`) — the **only** `infrastructure → api`
import in the codebase (confirmed by
`grep -RIn "import .*\.api\." src/main/java/.../infrastructure` → 1 hit, this one).
**Evidence:** `TimezoneFilter.java:3,35,39,45`.
**Impact today:** None functionally — but it is the only concrete, present-tense
`infrastructure → api` violation in the project (as opposed to F1, which is
`application → api`, already an accepted pattern in this codebase for read services).
**Classification:** error de ubicación real (not aesthetic — it is literally an HTTP filter
sitting in a technical config package).
**Proposal:** move `TimezoneFilter` to `api/filter/` (new package; only member for now — the
target architecture reference already anticipates `api/error` as its own package, so a
`api/filter` sibling is consistent). Do **not** move `TimezoneUtil`: it is a shared
ThreadLocal utility genuinely read from `api/mapper` (6 MapStruct expressions) and
`application/service/AuditTrailService.java`. Moving it would only relocate the violation,
not remove it — `TimezoneUtil` has no single "owning" layer today, and inventing one is a
judgment call better made together with F6, not forced here.
**Cost:** 1 file moved, 1 package declaration changed. No consumer imports it via package
path except itself.
**Risk:** Very low. Spring Boot's component scan covers all sub-packages of
`com.dalejandrov.sipsa`; the filter has no explicit `@Order` or registration bean depending
on its current package.
**If not done:** The one confirmed `infrastructure → api` dependency remains, which would
make an ArchUnit rule "infrastructure must not depend on api" fail immediately (see F4/TECH-093).

---

### F3 — CXF-generated SOAP stubs share a package with hand-written code

**Classes (generated, 24 files):** `ObjectFactory`, `package-info`,
`SrvSipsaUpraBeanService`, `SrvSipsaUpraService`, and 20 request/response wrapper classes
(`PromediosSipsaCiudad`, `PromediosSipsaCiudadResponse`, etc.)
**Generated into:** `com.dalejandrov.sipsa.infrastructure.soap.client` (from
`pom.xml:264`, `cxf-codegen-plugin` → `wsdl2java` → `-p com.dalejandrov.sipsa.infrastructure.soap.client`)
**Hand-written class in the same package:** `SoapStreamingClient.java`
(`src/main/java/.../infrastructure/soap/client/SoapStreamingClient.java`)
**Real consumers of the generated classes:** `SoapGatewayImpl.java:5`
(`import com.dalejandrov.sipsa.infrastructure.soap.client.*;` — wildcard) and
`SipsaSoapClientConfig.java:4-5` (`SrvSipsaUpraBeanService`, `SrvSipsaUpraService`).
**Concrete problem:** Generated code and manual code are indistinguishable by package;
nothing prevents a future contributor from adding a hand-written class into
`infrastructure/soap/client` next to the generated stubs, or editing a generated file
in-place after a regeneration overwrites `target/generated-sources` (this has not happened,
but the target architecture in `AGENTS.md` explicitly calls for
`infrastructure/soap/generated` as a distinguishable package for exactly this reason).
**Evidence:** `find target/generated-sources -name "*.java"` (24 files under
`.../infrastructure/soap/client/`); `pom.xml:246-271`.
**Impact today:** None — the generated files live physically under `target/`, never
committed, and are never hand-edited. The risk is purely about future confusion, not a
present bug.
**Classification:** dependencia indebida real, low current impact.
**Proposal:** change the codegen `-p` argument to
`com.dalejandrov.sipsa.infrastructure.soap.generated`; update the 2 files that import
generated classes (`SoapGatewayImpl.java`, `SipsaSoapClientConfig.java`).
**Cost:** 1 line in `pom.xml` + 2 import updates. Verify with
`./mvnw clean generate-sources` that the new package compiles and
`git diff --stat` shows no other regeneration side effects (WSDL and catalog files are
unchanged, so the generated *content* should be identical modulo the package declaration).
**Risk:** Low, but non-zero — codegen changes always carry a small chance of an
unanticipated ripple (e.g., JAXB `@XmlType` namespace bindings that implicitly reference the
package). Must be verified with a full `./mvnw clean verify` before merging, not assumed.
**If not done:** No functional harm today. Documented and deferred is an acceptable
alternative if the codegen verification proves noisier than expected.

**F3 Status (2026-07-13):** **Not accepted for implementation in this round.** The risk
assessment above ("low, but non-zero") is a judgment call, not verified evidence — unlike
F1, F2, and F4, which were confirmed by direct inspection with no open questions. Before
authorizing TECH-092, **TECH-094 (SPIKE)** must confirm: which plugin/version generates the
classes, from which WSDL, the currently configured package, whether the generated classes
are version-controlled, whether `./mvnw clean generate-sources` reproduces them exactly,
the expected diff size, the import impact, CXF compatibility, and whether the relocation is
worth the generated noise. TECH-092 remains `Blocked` until TECH-094 completes.

---

### F4 — `SoapGateway` (domain) imports `SoapGatewayImpl` (infrastructure) for Javadoc only

**Class:** `SoapGateway`
**Current package:** `domain/gateway`
**Concrete problem:** `domain/gateway/SoapGateway.java:3` imports
`infrastructure.soap.gateway.SoapGatewayImpl` solely to support a `@see SoapGatewayImpl`
Javadoc tag. This is the only `domain → infrastructure` import in the codebase (confirmed:
`grep -RIn "import .*\.infrastructure\." src/main/java/.../domain` → 1 hit, this one).
**Evidence:** `SoapGateway.java:3,39`.
**Impact today:** None functionally — but it inverts the dependency direction the interface
exists to enforce (domain defines the contract; infrastructure implements it, not the
reverse), and it is real: the import is compiled, not just a comment.
**Classification:** error objetivo, trivial to fix.
**Proposal:** remove the import; replace `@see SoapGatewayImpl` with a plain-text mention
(`{@code SoapGatewayImpl} (infrastructure layer)`) so the Javadoc still points a reader in
the right direction without a compiled dependency.
**Cost:** 1 line.
**Risk:** None.
**If not done:** A future ArchUnit rule "domain must not depend on infrastructure"
(see F5) fails on day one because of a Javadoc `@see` tag. This fix is a **precondition**
for the ArchUnit story (TECH-093) landing green, but is tracked as its own independent
story, **TECH-095**, XS-sized, implementable together with TECH-090/TECH-091 in the same
iteration.

---

### F5 — ArchUnit coverage does not exist yet

**Finding:** No architectural tests exist (`find src/test -iname "*ArchTest*" -o -iname "*ArchUnit*"` → no results;
`grep -rn archunit pom.xml` → no results). Nothing currently prevents F1–F4 from recurring,
or growing, after this ADR is resolved either way.
**Proposal:** add `com.tngtech.archunit:archunit-junit5` as a test-scope dependency and a
single test class enforcing exactly the three boundaries actually established in this
codebase (not the full target architecture, which is not fully realized yet — see the
"Discarded" section below):
- `api` must not be depended on by `application`, `domain`, or `infrastructure` in ways not
  already accepted (see Consequences).
- `domain` must not depend on `infrastructure` (requires F4 fixed first).
- Controllers (`api.controller..`) must not depend on repositories
  (`infrastructure.persistence.repository..`) — already true today; this rule only
  prevents regression.
**Cost:** 1 dependency + 1 test class.
**Risk:** Low, but the rule set must be written to pass against the **post-F1/F2/F4**
state, not today's state — see Sequencing below.

---

## What Was Investigated and Found *Not* to Justify a Move

To avoid a general reorganization motivated by appearance, the following were checked with
the same rigor and **rejected**:

| Investigated | Why rejected |
|---|---|
| `SipsaReadService`, `IngestionRunQueryService`, `AuditTrailService` importing `api.dto.response.*` / `api.mapper.*` to assemble response-shaped output | This is a **consistent, deliberate pattern** across every read/query service in the codebase, not an isolated mistake. It is the same pattern `architecture-review.md` already evaluated and discarded for `SipsaReadService` specifically ("functional today... cost/benefit does not justify the change at this scale"). No bug, no coupling growth evidence. Moving it would touch every query endpoint (5+ methods) for a purity gain only. **Not recommended**, consistent with existing precedent. |
| `api/mapper/*` (`SipsaCiudadMapper`, `IngestionAuditMapper`, etc.) | Already correctly placed: they convert entity → HTTP response DTO, which is exactly the API-mapper responsibility defined in `AGENTS.md`'s target structure. **No move.** |
| `infrastructure/soap/mapper/SipsaIngestionMapper` | Already correctly placed: SOAP DTO → entity, matches the SOAP-mapper rule. Contains the `computeKeyHash()` business-logic problem (`UUID.randomUUID()`), but that is a **data-integrity bug** (TECH-010/TECH-011/ADR-001), not a package-location issue. Explicitly out of scope here — `AGENTS.md` prohibits touching TECH-010 without the SPIKE. **No move.** |
| `domain/exception/*` (all 7 exception classes) | Checked every file's imports: zero HTTP, SOAP, CXF, Hibernate, or PostgreSQL dependencies. Confirms `architecture-review.md`'s RF-04 conclusion still holds. **No move.** |
| `IngestionTriggerRequest`, `AuditQueryRequest`, and all `*QueryRequest` classes (`CiudadQueryRequest`, `MayoristasMensualQueryRequest`, `ParcialQueryRequest`, `MayoristasSemanalQueryRequest`, `AbastecimientosMensualQueryRequest`) | Genuinely bound from HTTP (constructed from `@RequestParam`/Spring MVC parameter binding in controllers) or carry `@DateTimeFormat`/pagination validation meant for query strings. These are real HTTP DTOs correctly placed in `api/dto/request`. **No move.** |
| `AuditTrailService` → `TimezoneUtil` (application → api, 2 call sites) | Same category as F1/F2 in principle, but a single, low-traffic call site. No owning layer for `TimezoneUtil` has been established (see F2). Bundling this into F1 or F2 would grow their scope without a clean answer for where it should live. **Deferred**, not rejected — revisit together with F2 if `TimezoneUtil` gains more cross-layer consumers. |
| Feature-based package reorganization (RF-03) | Not re-evaluated — no new evidence since `architecture-review.md`'s rejection. Still 5 closely related data types in a single bounded context. **Not recommended**, precedent stands. |
| Splitting `IngestionControlService`, eliminating `AuditTrailService`, moving `batchUpsert` out of repositories, DDD tactical patterns | Not re-evaluated — no new evidence. Prior rejections in `refactoring-roadmap.md` (RF-02, RF-05, RF-06, RF-10) stand unchanged. |

---

## Option Considered — Do Nothing

Leaving F1–F5 as they are has **zero functional cost**: nothing is broken, no bug traces to
any of these findings. The only cost is to future maintainability (misleading naming in F1,
and the absence of a safety net in F5 that would catch a *worse* violation being added
later). Given the project is about to enter an active development phase (Phase 1 of
`implementation-roadmap.md`), the marginal cost of the four narrow, mechanical stories below
is low enough that "do nothing" is not the recommendation — but it remains a valid choice if
the team prefers to prioritize Phase 1/2/3 stories first.

---

## Decision

**Accepted, scoped to F1, F2, F4, F5.** Approved sequence (labels reflect each story's
state at acceptance time; **implementation status 2026-07-13: steps 1–3 Done and merged to
`main`, step 4 unblocked but not started**):

1. **TECH-090** (Ready) — Move `IngestionRequest`, `CreateRunRequest`, `AuditEventRequest`
   to `application/command/` (F1).
2. **TECH-091** (Ready) — Move `TimezoneFilter` to `api/filter/` (F2).
3. **TECH-095** (Ready) — Remove the `SoapGateway` → `SoapGatewayImpl` Javadoc import (F4).
   May be implemented in the same iteration as TECH-090/TECH-091.
4. **TECH-093** (Pending — blocked until TECH-090 and TECH-091 merge) — Add the 3 ArchUnit
   rules (F5): `application` must not depend on `api` is **not** one of them (see
   "Explicitly out of scope" in TECH-093). It must land last so the rules assert the
   *post*-move state instead of failing immediately.

**F3 (TECH-092) is explicitly deferred, not accepted.** It requires TECH-094 (SPIKE) first.
No generated code is to be moved as part of this ADR's current acceptance.

**Explicitly not decided by this ADR:** whether to also rename `IngestionRequest` →
`TriggerIngestionCommand`-style names (left to the implementer of TECH-090; this iteration's
preference is to move packages and keep names, deferring any rename to a separate story if
warranted), and whether `TimezoneUtil` should eventually move (deferred, see table above).

---

## Consequences

**If accepted and all four stories implemented (verified 2026-07-13 after TECH-090,
TECH-091, TECH-095 landed on `refactor/internal-models-and-api-filter`):**
- `application → api` imports drop from **9 files to 5 files**. The correction here
  matters: the "6 files" figure quoted earlier in this ADR (§F1) was the count of files
  importing specifically `IngestionRequest`/`CreateRunRequest`/`AuditEventRequest`, not the
  total `application → api` count across the codebase — a full `grep` found 9 distinct
  files with some `api` import before this change. The 4 files that imported *only* the 3
  moved classes (`SipsaIngestionScheduler`, `IngestionJob`, `IngestionControlService`,
  `AsyncIngestionService`) now import nothing from `api` at all. The 5 remaining files
  (`IngestionTriggerService`, `SipsaReadService`, `IngestionRunQueryService`,
  `IngestionAuditService`, `AuditTrailService`) all import genuinely-kept classes: HTTP
  DTOs (`IngestionTriggerRequest`, `AuditQueryRequest`, `*QueryRequest`), API mappers, API
  response DTOs, or (in `AuditTrailService`'s case) the still-deferred `TimezoneUtil` — see
  "What Was Investigated and Found Not to Justify a Move".
- `infrastructure → api` imports drop from 1 to 0.
- `domain → infrastructure` imports drop from 1 to 0.
- Generated and hand-written SOAP code become visually and physically distinguishable.
- An ArchUnit suite exists to prevent regression on exactly these three boundaries — no
  more, no less. It will **not** assert `application` must never depend on `api` (that
  would fail against the 3 intentionally-kept cases and require the broader RF-01/RF-03
  change this ADR does not recommend).
- No REST route, JSON body, HTTP status code, DB schema, scheduling behavior, or SOAP
  integration changes. All four stories are internal-only renames/moves.

**If rejected:**
- No functional risk. Revisit if `TimezoneUtil`, the internal command DTOs, or the codegen
  package gain more consumers and the current findings compound.

---

## Incremental Strategy

One story per branch, one PR per story, per `AGENTS.md`. Suggested order: TECH-090 and
TECH-091 first (independent, no shared files), TECH-092 next (touches build config, wants
isolated verification), TECH-093 last (depends on F4 fix landing and asserts the end state).

## Acceptance Criteria for This ADR

- [x] Reviewed and moved to `Accepted` (scoped to F1, F2, F4, F5) — 2026-07-13.
- [x] Non-authorized scope explicitly enumerated (see "Scope of This Acceptance").
- [ ] F3 (TECH-092) requires a separate acceptance once TECH-094 (SPIKE) reports back —
      this ADR does not need to be reopened for that; TECH-094's findings determine whether
      TECH-092 is approved, re-scoped, or rejected.
- [x] TECH-090, TECH-091, TECH-095 marked `Ready` in the backlog.
- [x] TECH-093 remains `Pending`, explicitly gated on TECH-090/TECH-091 landing first.
- [x] TECH-092 remains `Blocked`, explicitly gated on TECH-094.
