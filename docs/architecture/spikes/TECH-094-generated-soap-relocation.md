# TECH-094 — SPIKE: Evaluate Relocating CXF-Generated SOAP Sources

**Status:** Done
**Date:** 2026-07-20
**Branch:** `spike/evaluate-generated-soap-relocation`
**Origin:** [ADR-007](../adr/ADR-007-package-boundaries-and-internal-models.md) §F3 — the
risk assessment ("low, but non-zero") was a judgment call, not verified evidence. This
SPIKE investigates directly, empirically, before TECH-092 is authorized to proceed.
**No source code change is part of this SPIKE's final commit.** Every experiment
described below (a package retarget, two import edits) was run in the working tree,
verified, and then fully reverted (`git checkout --`) before this report was written —
confirmed by `git status --short` returning clean afterward.

---

## 1. Inventory

`find src -type f | grep -Ei 'soap|wsdl|jaxb|generated'` and a scoped grep for
`@Xml`/`@WebServiceClient`/`jakarta.xml.bind`/`cxf-codegen`/`generated-sources` confirm:
**zero generated class is version-controlled.** Every `.java` file under `src/` that
matches "soap" is hand-written. Generated classes exist only under
`target/generated-sources/cxf/...` (gitignored: `.gitignore:11` — `target/`).

| Package/file | Generated or manual | Source | Consumers | Risk if moved |
|---|---|---|---|---|
| `infrastructure.soap.client.*` (22 files — see §2) | **Generated** (`cxf-codegen-plugin` `wsdl2java`) | `SrvSipsaUpraBeanService.wsdl` + `.xsd` | `SoapGatewayImpl` (wildcard import), `SipsaSoapClientConfig` (2 explicit imports) | Empirically verified low, with one real scope correction — see §5 |
| `SoapStreamingClient.java` | Manual | — | `SoapGatewayImpl` | None — untouched by any relocation option |
| `SipsaSoapClientConfig.java` | Manual | — | Spring `@Configuration`, imports 2 generated classes by name | Only its 2 import lines change |
| `SoapGatewayImpl.java` | Manual | — | `@Component`, imports the generated package via wildcard **and relies on that same wildcard for `SoapStreamingClient`, which stays in the OLD package** (§5 finding) | Needs 1 additional explicit import, not previously identified |
| `SoapProperties.java`, `SipsaSoapClientConfig` validation | Manual | — | No generated-class import | None |
| `infrastructure.soap.dto.*` (5 record types) | Manual | — | Populated by StAX parsers, not JAXB | None — StAX-parsed, not JAXB-generated, confirmed unrelated |
| `infrastructure.soap.parser.*` (6 classes) | Manual | — | Parse raw XML via StAX, independent of the generated JAXB classes | None |
| `infrastructure.soap.mapper.SipsaIngestionMapper` | Manual | — | Maps parsed DTOs to entities | None |
| `domain.gateway.SoapGateway` | Manual | — | Interface only | None (TECH-095 already removed its only cross-layer reference) |

**Test files:** zero test anywhere in `src/test` imports or references
`infrastructure.soap.client.*` by name (confirmed via grep) — every SOAP-related test
(`SoapStreamingClientMetricsTest`, `SoapPropertiesTest`, `ParcialKeyHashTest`,
`XmlParsingUtilDecimalTest`) exercises hand-written code or already-parsed DTOs, never
the raw generated JAXB classes directly. **A relocation requires zero test file changes**
— stronger evidence than assumed in TECH-092's original scope, which didn't address
tests at all.

---

## 2. Current Generation Mechanism

- **Plugin:** `org.apache.cxf:cxf-codegen-plugin`, version `4.2.2` (`${cxf.version}`,
  `pom.xml:307`).
- **Goal/phase:** `wsdl2java`, bound to Maven's `generate-sources` phase (`pom.xml:310-312`).
- **WSDL source:** `src/main/resources/wsdl/SrvSipsaUpraBeanService.wsdl` — a **local
  file**, resolved via `file:` URI, never fetched over the network. The WSDL's own
  `import` of its XSD is resolved through `jax-ws-catalog.xml` (also local, 4 lines,
  simple `systemId`→`uri` mapping to `SrvSipsaUpraBeanService.xsd` in the same
  directory). **Codegen is fully offline-capable** — confirmed by running it with no
  network access implied or required.
- **Target package (`-p` argument):** `com.dalejandrov.sipsa.infrastructure.soap.client`
  — the same package as the hand-written `SoapStreamingClient.java`.
- **Extra args:** `-verbose -keep -wsdlLocation classpath:/wsdl/SrvSipsaUpraBeanService.wsdl`.
- **Output count, verified today: 22 `.java` files** (`ConsultarInsumosSipsaMesMadr[Response]`,
  `ObjectFactory`, `package-info`, `PromedioAbasSipsaMesMadr[Response]`,
  `PromediosSipsaCiudad[Response]`, `PromediosSipsaMesMadr[Response]`,
  `PromediosSipsaParcial[Response]`, `PromediosSipsaSemanaMadr[Response]`,
  `SipsaAbastecimientosMesMadr`, `SipsaInsumosMesMadr`, `SipsaMayoristasMesMadr`,
  `SipsaMayoristasSemanaMadr`, `SipsaPromediosMayoristasParcial`,
  `SipsaPromMayoristasCiudad`, `SrvSipsaUpraBeanService`, `SrvSipsaUpraService`).
  **This corrects ADR-007 §F3 and the TECH-092 backlog entry, both of which stated 24
  files** — that earlier count was not re-verified before being repeated across two
  documents; this SPIKE's count is a fresh, direct measurement (`find
  target/generated-sources -name "*.java" | wc -l` → `22`), not a re-assumption of the
  old number. The discrepancy does not change any risk conclusion below — it's a minor
  factual correction, not a red flag.
- **`ObjectFactory` and `package-info`:** both present, both regenerate identically
  (module-level JAXB registration and `@XmlSchema` package annotation respectively) —
  confirmed in the relocation experiment (§4).
- **JAXB context discovery:** `SoapGatewayImpl` builds its `JAXBContext` via
  `JAXBContext.newInstance(PromedioAbasSipsaMesMadr.class, PromediosSipsaCiudad.class,
  ...)` — **explicit `Class` references, not string-based package scanning.** A package
  rename is a compile-time-checked change (the compiler catches every stale reference);
  there is no classpath-scanning or reflection-by-name risk.
- **XML namespace independence, confirmed:** the generated classes' `@XmlType`/`@XmlSchema`
  annotations bind to the WSDL/XSD's **XML target namespace**
  (`http://servicios.sipsa.co.gov.dane/`), which is entirely independent of the **Java**
  package name — JAXB does not derive the XML namespace from the Java package. The
  relocation experiment's diff (§4) confirms this directly: zero XML-namespace-related
  annotation content changed, only the Java `package` declaration and imports.

---

## 3. Reproducibility

Ran `./mvnw clean generate-sources` twice in immediate succession (same WSDL, same
plugin config, unmodified):

- **20 of 22 files are byte-for-byte identical** between the two runs.
- **2 files differ**, and only in one respect:
  `SrvSipsaUpraBeanService.java`/`SrvSipsaUpraService.java` each carry a
  code-generation timestamp in a Javadoc comment (e.g. `* 2026-07-20T12:29:14.611-05:00`),
  which naturally differs between any two runs, regardless of package name or any other
  change — this is standard CXF/JAX-WS codegen behavior (a generation-time stamp), not a
  sign of non-deterministic *content*. Confirmed by diffing the two runs directly: the
  **only** changed lines are the two timestamp lines.

**Conclusion:** generation is deterministic in every way that matters for a relocation —
the only non-determinism is a cosmetic timestamp comment present in both the old and the
new package location identically, so it does not add any *additional* diff noise from a
rename specifically.

---

## 4. Relocation Experiment (Option B), Empirically Verified

Temporarily changed `pom.xml`'s `-p` argument from
`com.dalejandrov.sipsa.infrastructure.soap.client` to
`com.dalejandrov.sipsa.infrastructure.soap.generated`, regenerated, and diffed every one
of the 22 files against the original output — **normalizing only the package
declaration/import line and stripping the known timestamp lines (§3), nothing else**:

```
for f in <original 22 files>; do
  diff <(sed -e 's/soap\.client/soap.generated/g' -e '/^ \* 20[0-9][0-9]-.../d' "$f") \
       <(sed -e '/^ \* 20[0-9][0-9]-.../d' "<new-location>/$f")
done
```

**Result: zero differences beyond the package declaration and the already-explained
timestamp lines, across all 22 files, including `ObjectFactory.java` and
`package-info.java`.** This directly, empirically confirms TECH-092's stated acceptance
criterion — "Diff of generated output (excluding the package declaration) is empty" — as
**verified fact**, not an assumption carried over from ADR-007's original "low, but
non-zero" judgment call.

---

## 5. Compile Experiment — A Real Finding

With the package retargeted, updated the 2 documented import sites exactly as TECH-092's
original scope specified:

- `SipsaSoapClientConfig.java:4-5` → `import ....soap.generated.SrvSipsaUpraBeanService;` /
  `SrvSipsaUpraService;`
- `SoapGatewayImpl.java:5` → `import com.dalejandrov.sipsa.infrastructure.soap.generated.*;`

**`./mvnw clean verify` failed to compile:**

```
[ERROR] SoapGatewayImpl.java:[48,19] cannot find symbol
  symbol:   class SoapStreamingClient
```

**Root cause, found by running the experiment, not by inspection alone:**
`SoapGatewayImpl`'s single wildcard import (`soap.client.*`) was doing double duty —
pulling in both the generated classes **and** the hand-written `SoapStreamingClient`
(which stays in `soap.client`, unmoved, since it's manual code). Retargeting the wildcard
to the new generated package silently drops the only import path to
`SoapStreamingClient`. **This is a real gap in TECH-092's original 3-line scope**,
undiscovered until this SPIKE actually ran the change.

**Fix, verified:** add one explicit import,
`import com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient;`, alongside
the retargeted wildcard. With that fourth line added:

```
./mvnw clean verify → BUILD SUCCESS, Tests run: 415, Failures: 0, Errors: 0, Skipped: 0
```

All 415 tests passed, including the real-PostgreSQL Testcontainers suites and
`SoapStreamingClientMetricsTest` — full JAXB marshalling and CXF client construction
exercised successfully against the relocated package. `PackageBoundaryArchitectureTest`
(TECH-093) also passed unaffected, confirming no interaction between this relocation and
the package-boundary rules (as already predicted in TECH-093's own documentation).

**Every change made for this experiment (the `pom.xml` line, the 3 import lines across
2 files) was reverted via `git checkout --` before this report was written.**
`git status --short` returned clean afterward.

---

## 6. Options Compared

### Option A — Keep the current package as-is

The generated classes are **already never committed** (confirmed §1); "keep as-is" means
leaving the *package* (`infrastructure.soap.client`, shared with
`SoapStreamingClient.java`) unchanged, not the file-tracking status.
- **Zero immediate risk** — no change at all.
- **Architectural debt persists:** generated and hand-written code remain
  indistinguishable by package; nothing stops a future contributor from adding more
  hand-written classes into the generated package, or hand-editing a generated file
  in-place (it would be silently overwritten on the next `generate-sources`, with no
  warning).
- **Clarity:** low — a new contributor cannot tell `SoapStreamingClient.java` apart from
  the 22 generated files by package alone.

### Option B — Move to a distinguishable generated subpackage (`infrastructure.soap.generated`)

- **Verified empirically in §4–§5**, not just theorized: diff-clean regeneration, one
  real (now-known, now-fixable) import gap, full green build with all 415 tests after
  the fix.
- **Migration size:** 1 `pom.xml` line + 4 import lines across 2 files (the 3rd and 4th
  being the newly-found `SoapStreamingClient` import). Smaller than a schema migration,
  smaller than any story this session has touched.
- **JAXB/CXF impact:** none beyond the package declaration — confirmed, not assumed.
- **Regeneration:** unaffected — same plugin, same WSDL, same catalog, only the `-p`
  argument changes.

### Option C — Generate directly into `target/generated-sources` (no package relocation)

This is already exactly what happens today — Maven's `generate-sources` phase already
writes to `target/generated-sources/cxf`, never to `src/`, and the output is never
committed (confirmed §1). **Option C is not a distinct option from the status quo** — it
describes the *existing* mechanism, and combining it with Option B (regenerate at build
time, into a distinguishable package) is exactly what Option B already is. There is no
separate "generate to target/" story to choose, because that already happens; the only
open question is the **package name** used within that already-build-time-only output,
which is Option A vs. B.

### Option D — Separate Maven module for generated code

- **Isolation:** strongest of all options — a dedicated module makes the generated/manual
  boundary a hard module boundary, not just a package convention.
- **Complexity:** highest — introduces a multi-module Maven structure where none exists
  today (`pom.xml` is currently a single `<project>`, no `<modules>` section, confirmed).
  Every consumer of the generated classes would need an inter-module dependency;
  Spring Boot's classpath-scanning component model would need to include the new
  module's output.
- **Proportionality:** this is a 22-class, single-WSDL, single-consumer-pair (2 files)
  problem. A dedicated module is the kind of structural change ADR-007 itself explicitly
  rejected for broader reorganizations (RF-03) as disproportionate at this scale — the
  same reasoning applies here, even more so given Option B already fully resolves the
  stated problem (F3) at a fraction of the cost.

### Option E — Defer entirely, no package change until a later migration

- Functionally identical to Option A's risk profile (zero change now), but explicitly
  framed as "revisit later" rather than "accepted as-is." Given Option B is now backed by
  a complete, verified experiment with a known, small, fully-scoped diff, deferring adds
  no new information and only delays a low-cost, low-risk cleanup.

---

## 7. Risk Evaluation (against the requested checklist)

| Risk | Finding |
|---|---|
| Package name change | Verified mechanical — compiler-checked, 4 import lines |
| QNames / XML namespaces | Independent of Java package (JAXB binds to WSDL/XSD target namespace) — confirmed via diff, zero XML-content change |
| JAXB context discovery | Explicit `Class` references, not reflection/scanning — compiler catches every stale reference |
| Classes referenced by name (strings) | None found — `grep` confirms no string-literal reference to the generated package anywhere in `src/main` or `src/test` |
| CXF configuration | `SipsaSoapClientConfig`'s CXF-specific config (`HTTPClientPolicy`, XML limits, logging) operates on `Client`/`Bus` instances, not on class names — unaffected |
| Imports in `SoapGatewayImpl` | **The one real finding of this SPIKE** — needs 1 additional explicit import beyond the original 3-line scope (§5) |
| Binary compatibility | N/A — this is a source-level rename inside one Maven module, not a published artifact with external consumers |
| Diff size | 1 `pom.xml` line + 4 import lines — small, verified, not estimated |
| Reproducibility | Deterministic modulo one cosmetic timestamp comment (§3) |
| CI | No CI change needed — `ci.yml` already just runs `./mvnw clean verify`, which regenerates from source on every run; no cached/stale generated code risk |
| Java 25 / Spring Boot 4 | Full `./mvnw clean verify` succeeded end-to-end on the current toolchain during the experiment — not a compatibility concern |
| WSDL maintenance | Untouched — same WSDL, same catalog, same namespace |
| Ownership of generated code | Unclear in the repo today (no CODEOWNERS file) — a documentation gap, not a blocker; worth noting for whoever implements TECH-092 |
| Accidental regeneration overwrite | Already impossible to "lose" hand-edits to generated code today, since none is version-controlled — Option B doesn't change this, it only makes the boundary *visible* by package name |

---

## 8. Recommendation

**Recommended: Option B — retarget the `wsdl2java` output package to
`com.dalejandrov.sipsa.infrastructure.soap.generated`.**

Weighed against the requested criteria:

| Criterion | Weight | Option B |
|---|---:|---|
| Risk of breaking XML/SOAP | High | **Low, verified** — zero XML-content diff, full green build including SOAP marshalling tests |
| Build reproducible | High | **Yes** — deterministic modulo a pre-existing, package-independent timestamp comment |
| CI/JDK compatibility | High | **Confirmed** — full `verify` green on Java 25/Spring Boot 4 during the actual experiment |
| Architectural clarity | Medium | **Resolved** — generated and hand-written code become distinguishable by package, closing ADR-007 §F3 |
| Migration size | Medium | **Small** — 1 `pom.xml` line + 4 import lines, now precisely known instead of estimated |
| Future maintenance | Medium | **Improved** — a future contributor cannot mistake generated code for hand-written code by package alone |
| Rollback ease | High | **Trivial** — a single `pom.xml` line and 4 import lines; this SPIKE itself proved the revert path (`git checkout --`) leaves zero trace |

**Not recommended:** Option A (leaves the F3 debt unresolved for no remaining reason —
the risk that justified caution is now retired by evidence) and Option D (disproportionate
for a 22-class, 2-consumer problem, and ADR-007 already rejected this scale of
reorganization elsewhere in the codebase for the same proportionality reasoning).
**Not applicable as a distinct option:** Option C (already the status quo mechanism;
Option B is "Option C with a better package name," not an alternative to it).

---

## 9. What TECH-092 Should Do Next

TECH-092 should proceed **as scoped, with one addition found by this SPIKE**:

```text
TECH-092 should:
- change pom.xml's wsdl2java -p argument to
  com.dalejandrov.sipsa.infrastructure.soap.generated;
- update SipsaSoapClientConfig.java's 2 explicit imports (unchanged from original scope);
- update SoapGatewayImpl.java's wildcard import AND add one new explicit import for
  SoapStreamingClient (com.dalejandrov.sipsa.infrastructure.soap.client.SoapStreamingClient)
  — this fourth line was NOT in the original 3-line scope and is required for the build
  to compile, per this SPIKE's §5 finding;
- preserve the WSDL, the JAX-WS catalog, and every XML namespace binding unchanged
  (already verified independent of the Java package — no action needed, just don't touch
  them);
- add a regeneration-drift check: the same normalized-diff technique this SPIKE used
  (§4) — regenerate, diff against a known-good snapshot excluding the package line and
  the 2 timestamp lines, assert empty — as an optional CI safeguard, not a hard
  requirement, since TECH-092's own acceptance criteria already include running
  `./mvnw clean verify` end to end;
- leave SoapStreamingClient.java, SoapProperties.java, and every StAX
  parser/mapper/DTO untouched — none of them reference the generated package.
```

This SPIKE's evidence directly satisfies TECH-092's own listed acceptance criteria
(diff-of-generated-output-excluding-package-declaration is empty, `./mvnw clean verify`
passes) — TECH-092's implementer can treat this report as that verification already
having been performed once, and should re-run it as their own story's gate rather than
re-deriving it from scratch.

---

## 10. Answers to the 9-Point Objective (backlog acceptance criteria)

1. **Plugin/version:** `org.apache.cxf:cxf-codegen-plugin` 4.2.2.
2. **WSDL:** `src/main/resources/wsdl/SrvSipsaUpraBeanService.wsdl` (130 lines), catalog
   `src/main/resources/wsdl/jax-ws-catalog.xml` resolving `SrvSipsaUpraBeanService.xsd`
   (183 lines) — both local, no remote fetch.
3. **Current target package:** `com.dalejandrov.sipsa.infrastructure.soap.client`
   (`pom.xml`'s `-p` argument).
4. **Version-controlled?** No — confirmed via `.gitignore:11` (`target/`) and a fresh
   `find src -type f` sweep finding zero generated `.java` file under `src/`.
5. **Reproducible?** Yes, deterministically, modulo one cosmetic generation-timestamp
   comment in 2 of 22 files (§3) — verified by running codegen twice and diffing.
6. **Expected diff size if retargeted:** verified, not estimated — **1 `pom.xml` line +
   4 import lines across 2 manual files** (corrected from the original 3-line estimate;
   §5's finding adds the 4th line). File count corrected from the previously-stated 24 to
   the actual, freshly-measured **22**.
7. **Import impact on `SoapGatewayImpl.java`/`SipsaSoapClientConfig.java`:** both
   confirmed and, for `SoapGatewayImpl`, corrected — see §5.
8. **CXF compatibility:** confirmed via the actual experiment — `cxf-codegen-plugin`
   4.2.2 respects the `-p` argument consistently, and the full build (including SOAP
   marshalling tests) passed on Java 25/Spring Boot 4.
9. **Worth the generated noise?** Yes — the "noise" is a 5-line diff (1 pom.xml + 4
   imports), not a large migration, and it closes a real, if low-severity, architectural
   gap (ADR-007 §F3) with verified, not assumed, evidence.

**Explicit recommendation:** **proceed with TECH-092, with the scope correction in §9
above (the `SoapStreamingClient` import) added to its acceptance criteria.**

**No source code was changed as part of this SPIKE** — every experimental edit was
reverted before this report was committed.
