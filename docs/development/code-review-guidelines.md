# Code Review Guidelines

**Version:** 1.0 | **Date:** 2026-07-13

Guidelines for reviewing Pull Requests on this project.

---

## Review Objective

The goal of code review is not to find all possible problems — it is to confirm that:
1. The story's acceptance criteria are met.
2. The change does not introduce regressions or new risks.
3. The documentation is updated.

A good review is focused and fast. If a PR is small and well-described, the review should take under 15 minutes.

---

## What to Check — In Priority Order

### 1. Acceptance Criteria

- Are all acceptance criteria from the backlog story (`TECH-NNN`) met?
- Is there at least one test that directly verifies the criteria?
- Is the story marked `Done` in the backlog?

### 2. Build

- Is the `./mvnw clean verify` output in the PR description?
- Are all tests passing?
- Are there zero new compilation warnings?

### 3. Scope

- Does the PR contain only the story it claims to implement?
- Are there cosmetic changes (whitespace, import reordering, variable renaming) in unrelated files?
- Are there "while I'm here" changes that were not part of the story?

Cosmetic and out-of-scope changes are acceptable only if they are in files that were already modified by the story — and only if they are trivial (< 3 lines). Otherwise, they should be in a separate PR.

### 4. Architecture

- Is there any SOAP call inside a database transaction? (See [ADR-004](../adr/ADR-004-transaction-boundaries.md).)
- Does a REST contract change exist without an `Accepted` ADR? (See [ADR-003](../adr/ADR-003-error-response-model.md).)
- Is this implementing something that was explicitly deferred in [`refactoring-roadmap.md`](../architecture/refactoring-roadmap.md)?

### 5. Security

- Are there any credentials, passwords, or API keys in the diff?
- Are error responses exposing stack traces, class names, SQL, or SOAP details?
- Is new input from external sources validated before use?

### 6. Tests

- Do the tests cover the acceptance criteria, or just the happy path?
- Are tests deterministic? (No system clock, no network without WireMock.)
- Do tests verify the *behavior* being changed, not just that the code runs?

### 7. Observability

- If a new service or operation was added, is it observable (logged at the right level, with the right MDC context)?
- If a failure path was added, is it logged?

### 8. Documentation

- Is `CHANGELOG.md` updated?
- Is the related ADR updated if needed?
- Is the implementation roadmap updated if a phase is complete?

### 9. Performance

- Are there any new N+1 queries (loops calling the database per item)?
- Are any large collections loaded without pagination?
- Are there unnecessary calls to external services?

### 10. Consistency

- Does the code follow the existing conventions (naming, error handling, logging style)?
- Is the MapStruct mapper used for entity ↔ DTO conversion (not manual field mapping)?
- Are new exceptions derived from the existing exception hierarchy in `domain/exception/`?

---

## What NOT to Block On

Do not block a PR for:
- Stylistic preferences not captured in an existing convention.
- Potential future improvements unrelated to the story.
- Ideas for new features.
- Refactorings that are in the deferred list.

If you notice something that should be tracked, add a comment with `[NOTE]` and optionally
add it to the backlog. Do not block the PR on it.

---

## Review Comments Format

- **[BLOCK]** — Must be fixed before merge. Explain why and suggest a fix.
- **[SUGGEST]** — Optional improvement. The author can accept or decline.
- **[NOTE]** — Observation for future reference. Does not require action.
- **[QUESTION]** — Clarification needed. Not blocking unless the answer reveals a problem.

---

## Approving

Approve the PR when:
- All `[BLOCK]` comments are resolved.
- `./mvnw clean verify` passes.
- The mandatory checklist items from [`pull-request-checklist.md`](pull-request-checklist.md) are complete.

A PR should not wait for more than 2 business days for a review. If it does, it may be auto-merged
if all automated checks pass and no `[BLOCK]` comments were raised.
