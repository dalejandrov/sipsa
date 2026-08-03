# ADR-011 — Integration and End-to-End Testing Strategy

**Status:** Accepted
**Date:** 2026-08-03
**Backlog:** [TECH-044](../backlog/technical-backlog.md#tech-044) (SPIKE closure), [TECH-150 through TECH-161](../backlog/technical-backlog.md#tech-150)

---

## Context

The unit test suite has grown substantially since [Testing Strategy v1.0](../architecture/testing-strategy.md)
was written (2026-07-13, 10 files / 104 methods): as of this ADR, `main` has **62 test
files and 457 `@Test` methods**. That growth is real, but it is concentrated at the unit
layer — pure-Java logic, `@WebMvcTest` slices, and a handful of Testcontainers-backed
persistence tests. Two layers described in that same document remain exactly where they
started:

1. **Integration tests never happened.** [TECH-044](../backlog/technical-backlog.md#tech-044)
   was opened as a SPIKE to decide the tooling. [ADR-009](ADR-009-database-migration-strategy.md)
   settled half of it (Testcontainers, real PostgreSQL, proven by `FlywayMigrationsTest`
   and later reused by `SpecificationBuilderPostgresTest`). The other half — how to mock
   the DANE SOAP endpoint — was never closed. WireMock 3.13.2 has been on the test
   classpath since the OIDC security work, but only to mock the Cognito JWKS endpoint
   (`CognitoJwtDecoderContractTest`); it has never mocked the SOAP endpoint, which is the
   actual gap TECH-044 exists to close.

2. **No handler is tested through its real transport and real persistence.** Of the 5
   ingestion handlers (`CiudadIngestionHandler`, `ParcialIngestionHandler`,
   `SemanaIngestionHandler`, `MesIngestionHandler`, `AbasIngestionHandler`), only
   `ParcialIngestionHandlerTest` exists, and it deliberately bypasses both the real
   transport (it builds an `InputStream` directly instead of going through `SoapGateway`
   → `SoapStreamingClient`) and the real database (an in-memory Mockito fake stands in for
   `SipsaParcialRepository`). The other 4 handlers have zero dedicated test coverage. No
   test in the suite exercises the full path [ADR-000](ADR-000-current-architecture.md)
   documents as the system's actual value: real SOAP HTTP call → StAX parse → validate →
   persist → audit → metrics, together, against a real broker-shaped endpoint and a real
   PostgreSQL instance.

3. **E2E was explicitly deferred, for a reason that no longer fully holds.** The original
   testing pyramid marked E2E "Not planned (system boundary)". That was a reasonable call
   while AWS deployment (TECH-132/TECH-143) was still theoretical. It is being revisited
   now because the app is approaching real production traffic and has never had a single
   test proving that the HTTP trigger → async dispatch → ingestion pipeline → audit trail
   → query-back chain actually works together inside one running Spring context.

4. **No coverage measurement exists.** The coverage targets already written into
   `testing-strategy.md` (80/60/95/50%) have never been measured — no JaCoCo
   configuration exists in `pom.xml`.

5. **No integration-test build phase exists.** Every existing test, including the
   Testcontainers-backed ones, runs inline through Surefire in the default `./mvnw test`
   run (self-skipping without Docker). There is no `maven-failsafe-plugin`, no `*IT`
   naming convention, and no separate CI job — integration and unit tests are not
   distinguished from each other today.

---

## Decision drivers

- Close TECH-044 with an actual decision, not a second open half.
- Cover the 4 untested handlers, and the real-SOAP/real-DB path, before the app carries
  production traffic.
- Reuse what this repo already has and has already proven, instead of adopting new
  tools: WireMock is already a dependency; Testcontainers-PostgreSQL is already a
  dependency and pattern (`FlywayMigrationsTest`, `SpecificationBuilderPostgresTest`);
  the mock-OIDC-issuer pattern from the security work already proves how to drive the
  real Spring context under authentication in a test.
- Keep integration/E2E tests out of the default `./mvnw test` run (they need Docker)
  without introducing a second build tool.

---

## Options considered

### Integration-test tooling (closes TECH-044)

**Option A — WireMock (SOAP) + Testcontainers (PostgreSQL), combined, one IT per handler — chosen**

Reuses two dependencies already on the classpath and already individually proven in this
repo. A real HTTP round trip through `SoapStreamingClient`, plus a real `batchUpsert`
against real PostgreSQL, is the only way to verify the ADR-000 "Ingestion Flow" as
documented rather than as assumed.

**Option B — WireMock + H2 (`testing-strategy.md`'s original fallback recommendation)**

Rejected. H2 cannot exercise the PostgreSQL-specific `ON CONFLICT (key_hash) DO NOTHING`
upsert ([TECH-117](../backlog/technical-backlog.md#tech-117)) or the `TIMESTAMPTZ`/`DATE`
calendar semantics ([ADR-008](ADR-008-timezone-locale-and-date-semantics.md)) the
persistence layer actually depends on. An H2-backed IT would pass while hiding exactly
the class of defect TECH-011/TECH-117 fixed on real Postgres — the same reasoning that
already made `SpecificationBuilderPostgresTest` choose real Postgres over H2 for its own
DB-dependent cases.

**Option C — A hand-written fake SOAP server (Testcontainers or otherwise) instead of WireMock**

Rejected. No such server exists or is planned; it would mean building and maintaining a
second fake-server implementation when WireMock already does this and is already a
dependency.

### E2E scope

**Option A — Narrow black-box E2E via `@SpringBootTest(webEnvironment = RANDOM_PORT)` + a real HTTP client, WireMock SOAP, Testcontainers PG, reusing the mock-OIDC-issuer pattern — chosen**

Drives the app exactly as a real client would:
`POST /api/internal/ingestion/run` → `202 Accepted` → poll
`GET /api/internal/ingestion/runs/{id}` until `SUCCEEDED` → `GET /api/sipsa/ciudad`
returns the persisted rows → `GET /api/internal/audit/run/{id}` shows the full
`REQUEST_RECEIVED → REQUEST_ACCEPTED → INGESTION_STARTED → INGESTION_RUNNING →
INGESTION_SUCCEEDED → METRICS_UPDATED` sequence. Deliberately narrow: one golden path
(`CiudadIngestionHandler`, the simplest handler) plus one failure path (SOAP 500 →
`INGESTION_FAILED`, audit trail still intact). This is not a duplicate of the per-handler
integration tests — those verify each handler's parsing/persistence contract; the E2E
suite verifies the *wiring* between the HTTP layer, async dispatch, the ingestion
pipeline, and the audit/observability side effects, end to end, once.

**Option B — Keep E2E "not planned" (status quo)**

Rejected now. The app is entering production readiness (TECH-132/TECH-143) with a fully
async-dispatched, fully audited pipeline that has never been proven end-to-end in a
single test. Deferring further does not remove that risk — it only defers discovering
whether the wiring actually works.

---

## Decision

1. **TECH-044 is resolved**: combined WireMock + Testcontainers, one integration test per
   handler.
2. Introduce a Maven `integration-tests` profile bound to the `verify` phase via
   `maven-failsafe-plugin`, using Failsafe's standard `*IT.java` naming convention, kept
   separate from the Surefire unit run. `./mvnw test` stays fast and Docker-optional,
   exactly as every existing test behaves today; `./mvnw verify -P integration-tests`
   requires Docker, matching `FlywayMigrationsTest`'s existing self-skip behavior for
   anyone who runs the full suite without it.
3. A shared SOAP fixture convention: XML fixtures under
   `src/test/resources/fixtures/soap/<HandlerName>/`, served by a shared WireMock support
   base class — mirroring the existing shared-fixture pattern used for MVC exception
   tests (`RequestContextThrowingTestController`).
4. E2E tests live under a new `src/test/java/.../e2e/` package, named `*E2ETest`, and run
   in the same `integration-tests` Failsafe phase — no third tool or phase. They are
   integration tests in Maven's terms, scoped at the HTTP boundary instead of the handler
   boundary.
5. JaCoCo is added report-only initially (no build-breaking `check` goal yet), matching
   this repo's stated preference for incremental, non-disruptive tooling adoption.
6. CI (`ci.yml`) gains a second job, `integration-verify`, running
   `./mvnw verify -P integration-tests`, in parallel with the existing `verify` job — not
   chained after it, so a slow or flaky Testcontainers pull never blocks the fast
   unit-test signal.

None of this changes public REST endpoints, the database schema, scheduled job behavior,
SOAP integration behavior, or external configuration — it only adds tests and a build
profile.

---

## Consequences

- New build dependency: `maven-failsafe-plugin`. No new *library* dependencies — WireMock
  and Testcontainers are already present in `pom.xml`.
- New work breaks down into the TECH-150..161 block (see
  [technical-backlog.md](../backlog/technical-backlog.md#tech-150)): scaffolding, 5
  per-handler ITs, 1 E2E story, 2 remaining unit-test-gap stories carried over from
  `testing-strategy.md`'s "Recommended" section, 1 JaCoCo story, 1 CI story.
- `ParcialIngestionHandlerTest` (existing, mocked-repo, idempotency-focused) is **kept
  as-is** — it stays a fast unit test and is not superseded by the new
  `ParcialIngestionHandlerIT`, which covers exactly the real-transport/real-DB path the
  existing test explicitly avoids.
- CI gets slower in wall-clock terms (a second, Docker-bound job) — mitigated by running
  it in parallel with the existing `verify` job rather than after it.
- [`testing-strategy.md`](../architecture/testing-strategy.md) is updated in the same
  change that introduces this ADR: current-state numbers, the pyramid diagram (E2E moves
  from "not planned" to "planned, narrow scope"), and the integration-test section
  (replacing the old speculative Option A/B with this decision).

---

## Reconsider if

- WireMock or Testcontainers stop being viable in CI (e.g., the CI runner loses Docker
  access) — would force re-evaluating Option B (H2) despite its documented gaps.
- The E2E suite's scope creeps beyond the two paths named above without a matching
  increase in the risk it addresses — the narrow scope is deliberate; the per-handler
  ITs are the layer meant to absorb “more coverage,” not the E2E suite.
