# AGENTS.md — Guide for AI Agents and Developers

This file is the authoritative entry point for any AI agent or new developer working on this
repository. Read it before making any change.

---

## What This System Does

SIPSA Integration Service wraps Colombia's DANE SIPSA agricultural price SOAP service behind
a REST API. It ingests data automatically on a cron schedule, stores it in PostgreSQL, and
exposes it through paginated REST endpoints with filtering and timezone support.

The system's primary value is **reliable, idempotent ingestion** and **accessible querying** —
not complex business logic.

Full architecture description: [`docs/adr/ADR-000-current-architecture.md`](docs/adr/ADR-000-current-architecture.md)

---

## Key Documentation — Read Before Acting

| Document | Purpose |
|---|---|
| [`docs/backlog/technical-backlog.md`](docs/backlog/technical-backlog.md) | **Official work queue.** Pick your story here. |
| [`docs/architecture/implementation-roadmap.md`](docs/architecture/implementation-roadmap.md) | Phase order. Phase 1 is next. |
| [`docs/adr/README.md`](docs/adr/README.md) | Architecture decisions. Check before changing anything structural. |
| [`docs/architecture/architecture-review.md`](docs/architecture/architecture-review.md) | Findings, risks, what was rejected and why. |
| [`docs/architecture/refactoring-roadmap.md`](docs/architecture/refactoring-roadmap.md) | What NOT to refactor, and why. Read before any structural change. |
| [`docs/architecture/technical-debt.md`](docs/architecture/technical-debt.md) | Classified debt registry with backlog references. |
| [`docs/architecture/testing-strategy.md`](docs/architecture/testing-strategy.md) | Required tests per layer. |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Build, test, branch, commit, and PR guide. |
| [`CHANGELOG.md`](CHANGELOG.md) | All notable changes. Update under `[Unreleased]` with each PR. |

---

## Technology Stack

- **Java 25** (Temurin LTS), **Spring Boot 4.1.0**, **Spring Framework 7**
- **PostgreSQL 18**, **Hibernate 7**, **Flyway** (schema migrations)
- **Apache CXF 4.2.2** (WSDL codegen), **StAX** (streaming XML parsing)
- **MapStruct 1.6.3**, **Lombok**, **Micrometer + Prometheus**
- **Maven 3.9.9** via `./mvnw`, **Docker** (eclipse-temurin:25)

---

## Current Phase

**Phase 1 — Foundation Cleanup is done** (2026-07-19). **Phase 2 — Contract and
Correctness** is next: TECH-021, TECH-022, TECH-023 close the HTTP error contract;
TECH-053/TECH-054 remain unblocked but are not part of that immediate group. See the
[implementation roadmap](docs/architecture/implementation-roadmap.md) for current
phase status and the [technical backlog](docs/backlog/technical-backlog.md) for
per-story evidence.

Start with the first unblocked story in the current phase from the backlog.

---

## How to Pick a Story

1. Open [`docs/backlog/technical-backlog.md`](docs/backlog/technical-backlog.md).
2. Find the first story with `**Status:** Pending` in the current phase (see [Current Phase](#current-phase) above).
3. Check its `**Dependencies:**` — if None, it can start immediately.
4. Check if there is a linked ADR — if `Proposed`, the ADR must be `Accepted` before implementation (not all stories require this; check the story).
5. Confirm the branch name from `**Branch:**`.

---

## Mandatory Workflow for Every Story

Before writing any code, answer these questions explicitly:

```
1. Which story am I implementing? (TECH-NNN)
2. What problem does it solve?
3. Which files will I modify?
4. What are the risks?
5. What tests are needed?
6. What documentation must be updated?
```

Only proceed after these questions are answered.

Then follow this sequence:

```bash
# 1. Start from main
git switch main && git pull
git switch -c <branch-from-story>

# 2. Implement only that story — nothing else

# 3. Write or update tests

# 4. Verify
./mvnw clean verify    # must pass before continuing

# 5. Update documentation (see below)

# 6. Commit with Conventional Commits
# e.g.: fix(api): add leading slash to @RequestMapping on internal controllers

# 7. Open a small Pull Request using .github/PULL_REQUEST_TEMPLATE.md
```

---

## Documentation to Update With Every Story

| Item | File |
|---|---|
| Mark story Done | `docs/backlog/technical-backlog.md` |
| Add entry | `CHANGELOG.md` under `[Unreleased]` |
| Update status if applicable | Related `docs/adr/ADR-NNN-*.md` |
| Update phase if complete | `docs/architecture/implementation-roadmap.md` |
| Add new debt if found | `docs/architecture/technical-debt.md` |

---

## What an Agent MUST NOT Do

- Make large refactorings or touch multiple unrelated stories in one PR.
- Move packages, rename DTOs, or reorganize structure for aesthetic reasons.
- Change REST contracts without a corresponding ADR.
- Change business logic without evidence that existing behavior is wrong.
- Skip tests or mark acceptance criteria met without running `./mvnw clean verify`.
- Leave documentation outdated after completing a story.
- Implement deferred refactorings from [`docs/architecture/refactoring-roadmap.md`](docs/architecture/refactoring-roadmap.md).
- Change decisions that are `Accepted` in an ADR without creating a new ADR.
- Combine stories from different phases in a single PR.
- Push directly to `main`.

---

## When New Technical Debt Appears

Do not implement it. Do this instead:

1. Add it to [`docs/backlog/technical-backlog.md`](docs/backlog/technical-backlog.md) with evidence and acceptance criteria.
2. Add it to [`docs/architecture/technical-debt.md`](docs/architecture/technical-debt.md) under the correct category.
3. Continue with the original story only.

---

## When a New Architectural Decision is Needed

Do not implement it. Do this instead:

1. Pause.
2. Analyze alternatives, trade-offs, and impact.
3. Create or update an ADR in [`docs/adr/`](docs/adr/).
4. Get the ADR to `Accepted` status before implementing.
5. Reference the ADR in the story and in the PR.

---

## Definition of Done

A story is complete only when ALL of the following are true:

- [ ] `./mvnw clean verify` passes (zero test failures).
- [ ] Acceptance criteria from the backlog story are met.
- [ ] New logic has at least one unit test.
- [ ] `CHANGELOG.md` has an entry under `[Unreleased]`.
- [ ] Backlog story is marked `Done`.
- [ ] Related ADR updated if applicable.
- [ ] No stack traces, credentials, or sensitive data in the diff.
- [ ] PR uses the template and references the story ID.

---

## Critical Known Issues

None currently open. Both items originally listed here are resolved:

- **`SipsaParcial` deduplication** — resolved 2026-07-16 (`TECH-010`/`TECH-011`, ADR-001
  `Accepted`; concurrency hardening followed via `TECH-117` on 2026-07-19). See the
  backlog's Phase 5 stories for evidence.
- **Internal endpoint authentication** — resolved 2026-07-15: internal endpoints now
  require a JWT with per-operation scopes (`TECH-001`/`TECH-002`, ADR-002 `Accepted`,
  merged via PR #17, e2e-validated against the mock OIDC issuer). The AWS gateway/network
  layers remain as `TECH-130..132` (infrastructure, not application-code issues).

If a new critical issue is found, add it here with its backlog story ID and remove it
only once the story is `Done` with verified evidence — see
[the reconciliation criteria](docs/backlog/technical-backlog.md) for what counts as
sufficient evidence.

---

## Build Commands

```bash
./mvnw clean verify          # full build + unit tests (self-contained, no DB required)
./mvnw clean package -DskipTests   # build JAR only
docker compose up -d         # start PostgreSQL + application
docker compose down          # stop
```

---

## Commit Convention

```
<type>(<scope>): <summary>
```

Types: `fix`, `feat`, `refactor`, `test`, `docs`, `chore`, `perf`

Examples:
```
fix(api): add leading slash to @RequestMapping on internal controllers
test(window-policy): add unit tests for daily and monthly window validation
docs(adr): update ADR-002 status to Accepted after security implementation
```

One logical change per commit. One story per branch. One PR per story (or group of XS stories on the same branch).
