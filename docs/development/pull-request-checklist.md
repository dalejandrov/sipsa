# Pull Request Checklist

**Version:** 1.0 | **Date:** 2026-07-13

Use this checklist for every Pull Request. Also available as the GitHub PR template
at [`.github/PULL_REQUEST_TEMPLATE.md`](../../.github/PULL_REQUEST_TEMPLATE.md).

---

## Before Opening the PR

### Story

- [ ] The story from [`docs/backlog/technical-backlog.md`](../backlog/technical-backlog.md) is referenced in the PR title or description (e.g., `TECH-020`).
- [ ] All acceptance criteria from the story are met.
- [ ] The story status is updated to `Done` in the backlog.
- [ ] The PR link has been added under **Completed** in the story.

### Build

- [ ] `./mvnw clean verify` passes locally with zero failures.
- [ ] The build output is pasted in the PR description.
- [ ] No new compilation warnings in changed files.

### Tests

- [ ] At least one test verifies the acceptance criteria.
- [ ] Existing tests still pass.
- [ ] Tests are deterministic (no clock dependency, no network calls without WireMock).

### Code Quality

- [ ] No TODO comments left without a backlog reference.
- [ ] No commented-out code.
- [ ] No cosmetic changes (whitespace, import reordering) in unrelated files.
- [ ] No credentials, API keys, or connection strings in the diff.
- [ ] No stack traces exposed in HTTP response bodies.

### Documentation

- [ ] `CHANGELOG.md` updated under `[Unreleased]`.
- [ ] Related ADR updated if this story transitions an ADR from `Proposed` to `Accepted`.
- [ ] `docs/architecture/implementation-roadmap.md` updated if a phase is complete.
- [ ] `docs/architecture/technical-debt.md` updated if a debt item is resolved.
- [ ] Any new dependency is documented in `CHANGELOG.md`.

### Architecture

- [ ] No changes to REST API contracts without an `Accepted` ADR.
- [ ] No SOAP calls inside a database transaction (see [ADR-004](../adr/ADR-004-transaction-boundaries.md)).
- [ ] No changes to deferred refactorings from [`refactoring-roadmap.md`](../architecture/refactoring-roadmap.md).
- [ ] ADR required? → [ ] No / [ ] Yes — created/updated: `docs/adr/ADR-NNN-...`

### PR Size

- [ ] The PR is small enough to review in under 15 minutes.
- [ ] Only one story (or a group of XS stories on the same branch).
- [ ] No unrelated changes mixed in.

---

## Mandatory Items (PR cannot be merged without these)

1. `./mvnw clean verify` passes.
2. Story acceptance criteria met and documented.
3. `CHANGELOG.md` updated.
4. Backlog story marked `Done`.
5. No secrets in the diff.
6. PR template checklist completed (this document).

---

## Optional but Recommended

- [ ] Description explains the *why*, not just the *what*.
- [ ] If this is a security-related change, a reviewer with security focus has been requested.
- [ ] If this changes a public API endpoint, affected documentation (API docs) is updated.
