# Contributing to SIPSA Integration Service

This guide explains how to set up your environment, work with the codebase,
and contribute changes following the project's standards.

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| Java | 25 (LTS) | Eclipse Temurin 25 recommended |
| Maven | 3.9+ | Use `./mvnw` (wrapper included) |
| PostgreSQL | 18 | For integration and full tests |
| Docker | 24+ | For local full-stack testing |
| Git | any | |

Verify your Java version:
```bash
java --version  # should show OpenJDK 25.x
./mvnw --version
```

---

## Build

```bash
# Compile only
./mvnw clean compile

# Package (skip tests)
./mvnw clean package -DskipTests

# Full build with unit tests (H2 in-memory, no PostgreSQL required)
./mvnw clean verify
```

---

## Running Tests

```bash
# Unit tests and context load test (no database required)
./mvnw test

# Full verify (same as above, includes failsafe)
./mvnw clean verify
```

For integration tests that require PostgreSQL (future, after TECH-044):
```bash
docker compose up -d db
./mvnw verify -P integration-tests
docker compose down
```

Note on `FlywayMigrationsTest`: it provisions its own PostgreSQL via Testcontainers and
**self-skips when Docker is unavailable** on your machine. That skip is acceptable locally
but not in CI — see below.

---

## Continuous Integration

Every pull request and every push to `main` runs the CI gate
([`.github/workflows/ci.yml`](.github/workflows/ci.yml), TECH-120):

- `./mvnw clean verify` on Temurin JDK 25 via the Maven Wrapper — the exact command you
  run locally, so a green local build is the best predictor of a green pipeline.
- `FlywayMigrationsTest` (the ADR-009 migration gate) runs against a real PostgreSQL 18
  container using the runner's Docker. A guard step **fails the pipeline if that suite is
  skipped**, so the local-only self-skip can never mask a broken migration in CI.
- Superseded runs of the same branch/PR are cancelled automatically; on failure the
  surefire/failsafe reports are attached to the run as a `test-reports` artifact.
- The workflow uses no secrets, no `.env`, and no credentials, and the `GITHUB_TOKEN` is
  restricted to read-only repository access.

A PR cannot be considered mergeable with a red pipeline. "It passes locally" is not an
override — if CI and your machine disagree, the difference itself is the bug to chase
(usually Docker availability or an environment-dependent test).

---

## Running Locally

```bash
# Start full stack (PostgreSQL + application)
docker compose up -d

# Check health
curl http://localhost:8080/actuator/health

# Query data (after ingestion)
curl "http://localhost:8080/api/sipsa/ciudad?size=5"

# Stop
docker compose down
```

Environment variables used by the application are documented in `.env.example`.

---

## Branch Strategy

All work is done on feature branches created from `main`. The migration branch
`chore/migrate-spring-boot-4-java-25` should be merged first.

```
main
├── fix/internal-endpoint-security         ← Phase 1
├── fix/request-mapping-leading-slash      ← Phase 1
├── test/window-policy                     ← Phase 3
├── fix/parcial-data-integrity             ← Phase 5 (blocked by SPIKE)
└── docs/architecture-decisions            ← Ongoing
```

Branch naming follows **Conventional Branches**:
- `fix/` — bug fixes or error corrections
- `feat/` — new features
- `refactor/` — internal restructuring without behavior change
- `test/` — tests only
- `docs/` — documentation only
- `chore/` — build, config, tooling
- `spike/` — investigation, proof of concept

---

## Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary>

[optional body]

[optional footer: Co-Authored-By: ...]
```

Types: `fix`, `feat`, `refactor`, `test`, `docs`, `chore`, `perf`.

Examples:
```
fix(api): correct HTTP 422 to 404 for not-found resources
feat(observability): add Micrometer timers for ingestion methods
test(window-policy): unit tests covering all execution window scenarios
docs(adr): update ADR-002 status to Accepted after security implementation
```

One logical change per commit. Do not mix unrelated changes.

---

## When to Create an ADR

Create or update an Architecture Decision Record (`docs/adr/`) when:
- You are changing how a major system boundary works (transactions, async, sessions).
- You are choosing between two or more non-obvious technical alternatives.
- A previous ADR needs updating because circumstances changed.
- You are implementing a story whose corresponding ADR is in `Proposed` state.

Do not create ADRs for: library upgrades, naming changes, minor fixes.

See the [ADR index](docs/adr/README.md) for existing records.

---

## When to Update the Backlog

After completing a story:
1. Change its **Status** to `Done` in [technical-backlog.md](docs/backlog/technical-backlog.md).
2. Add the PR link under **Completed**.
3. Update the [implementation roadmap](docs/architecture/implementation-roadmap.md) if a phase is complete.
4. Update [technical-debt.md](docs/architecture/technical-debt.md) if a debt item is resolved.

If you discover a new issue not in the backlog:
- Add it to [technical-backlog.md](docs/backlog/technical-backlog.md) with evidence and acceptance criteria.
- Add it to [technical-debt.md](docs/architecture/technical-debt.md) under the appropriate category.

---

## When to Update Documentation

| Change | Docs to update |
|---|---|
| Architectural decision | Create or update ADR; update architecture review if major |
| New dependency added | CHANGELOG.md (Unreleased) |
| Bug fix | CHANGELOG.md (Unreleased → Fixed) |
| New feature | CHANGELOG.md + API docs if endpoint changes |
| Story completed | technical-backlog.md + implementation-roadmap.md |
| Refactoring deferred | refactoring-roadmap.md |

---

## Minimum Criteria Before Opening a Pull Request

1. `./mvnw clean verify` passes (no test failures, no compilation errors).
2. The implementation matches the acceptance criteria in the backlog story.
3. Any new business logic has at least one unit test.
4. No `System.out.println`, no TODO comments in changed files (unless intentional and tracked).
5. CHANGELOG.md has an entry in `[Unreleased]`.
6. Related backlog story and ADR (if applicable) are updated.
7. The PR description uses the PR template and references the story ID.

---

## Implementing a Backlog Story

Follow this sequence:

1. **Read the story** in [technical-backlog.md](docs/backlog/technical-backlog.md).
   Read the referenced ADR (if any). Understand the acceptance criteria before starting.

2. **Create the branch** from `main`:
   ```bash
   git switch main && git pull
   git switch -c fix/your-story-branch-name
   ```

3. **Implement the minimal change.** One story per PR. Do not fix unrelated issues.

4. **Write or update tests** for the changed code.

5. **Run the full build:**
   ```bash
   ./mvnw clean verify
   ```

6. **Update documentation:**
   - Mark the story `Done` in the backlog.
   - Update the ADR if it transitions from `Proposed` to `Accepted`.
   - Add a CHANGELOG entry.

7. **Create a Pull Request** using the PR template. Reference the story ID (e.g., `TECH-001`).

8. **Do not merge stories from different phases in the same PR** unless they are explicitly
   grouped (e.g., TECH-050 and TECH-051 share the same branch).

See the full [Implementation Roadmap](docs/architecture/implementation-roadmap.md) for phase ordering.

---

## Checking the ADR Status Before Implementing

Some stories are blocked by an ADR in `Proposed` state. Check:

```
TECH-001 → ADR-002 (security mechanism must be decided first)
TECH-010 → ADR-001 (deduplication key must be decided first)
TECH-053 → ADR-005 (sync vs async scheduler must be decided first)
```

Do not implement a blocked story until the corresponding ADR is `Accepted`.
