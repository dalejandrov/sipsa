# Implementation Guidelines

**Version:** 1.0 | **Date:** 2026-07-13

Rules for implementing any story in the backlog. These rules apply to all contributors.

---

## Before Writing Code

**Read first:**
1. The full story in [`docs/backlog/technical-backlog.md`](../backlog/technical-backlog.md) — evidence, objective, acceptance criteria.
2. Any referenced ADR in [`docs/adr/`](../adr/) — understand the decision context.
3. [`docs/architecture/refactoring-roadmap.md`](../architecture/refactoring-roadmap.md) — confirm you are not about to implement something explicitly deferred.
4. The affected source files — understand the current behavior before changing it.

**Answer these questions in writing:**

```
Story:         TECH-NNN
Problem:       [one sentence]
Files affected:[list specific files]
Tests needed:  [list test cases]
Docs affected: [list documents]
Risks:         [what could go wrong]
```

Post this analysis as the first comment in the PR, or as a planning note. This prevents scope creep.

---

## Scope Rules

### Do implement

- Exactly what the story's acceptance criteria describe.
- Tests that directly verify the acceptance criteria.
- Documentation updates required by the story (CHANGELOG, backlog, ADR).

### Do not implement

- Anything outside the story scope, even if it is a neighboring problem.
- Improvements you notice while reading the code ("while I'm here" changes).
- Deferred refactorings from [`refactoring-roadmap.md`](../architecture/refactoring-roadmap.md).
- New features not in the backlog.

If you find something that needs fixing, add it to the backlog and continue.

---

## Code Quality Rules

### Minimize the diff

A smaller diff is easier to review, less risky, and easier to revert. If you can achieve the
acceptance criteria with 10 lines instead of 50, use 10 lines.

### No cosmetic changes

Do not reformat code, reorder imports, or rename variables in files you are not otherwise modifying.
These changes increase diff noise and make review harder.

### No dead code

Do not leave commented-out code or TODO comments (unless the TODO is an explicit tracking item
with a backlog reference: `// TODO: TECH-NNN`).

### Dependency additions

Adding a new dependency requires:
1. A justification in the PR description.
2. Verification that it is not already available transitively.
3. A CHANGELOG entry.

For significant dependencies, consider whether an ADR is needed.

### No secrets

Never commit credentials, API keys, passwords, or connection strings. Use environment variables
or `.env` files (which are in `.gitignore`).

---

## Testing Rules

Every story that changes production code must include tests. Follow the priority order from
[`docs/architecture/testing-strategy.md`](../architecture/testing-strategy.md):

1. **Unit tests** for changed logic — pure Java, no Spring context, no database.
2. **`@WebMvcTest`** for controller or exception handler changes.
3. **`@DataJpaTest`** for repository changes (future, after Testcontainers decision).

Test naming convention:
```java
@Test
void methodName_condition_expectedOutcome() { ... }
// e.g.: getCiudad_withNullArtiId_doesNotApplyFilter()
```

Each test follows the Arrange / Act / Assert (Given / When / Then) pattern — even if the comments
are not written explicitly.

Tests must not depend on:
- The system clock (use a fixed `Clock` or injected `ZonedDateTime`).
- External network access.
- A running database (unit tests and `@WebMvcTest` must use H2 or mocks).
- Execution order.

---

## Exception and Error Handling

When implementing changes to error handling:
- Verify the HTTP status code is semantically correct.
- Verify the response body does not expose internal details (class names, SQL, stack traces).
- Update `CHANGELOG.md` if the HTTP status or error code changes.
- Ensure TECH-043 (`GlobalExceptionHandler` tests) covers the modified handler.

Reference: [`docs/adr/ADR-003-error-response-model.md`](../adr/ADR-003-error-response-model.md)

---

## Transaction Boundaries

When implementing changes that touch the persistence or ingestion layers:
- Do not add `@Transactional` to `IngestionJob.execute()` or any method in the handler chain.
- Do not place SOAP calls inside a database transaction.
- New methods that write to the database should use `@Transactional(propagation = REQUIRES_NEW)`
  only if they need to commit independently of the caller (following the existing pattern).

Reference: [`docs/adr/ADR-004-transaction-boundaries.md`](../adr/ADR-004-transaction-boundaries.md)

---

## Security Checklist for Every Implementation

Before committing:
- [ ] No credentials in the diff.
- [ ] No stack traces exposed in HTTP responses.
- [ ] Input from external sources is validated before use.
- [ ] Log messages do not include raw SOAP payloads or user-provided content without sanitization.

---

## Documentation Update Checklist

After implementation, before opening the PR:

- [ ] `docs/backlog/technical-backlog.md` — story marked `Done`, PR link added.
- [ ] `CHANGELOG.md` — entry added under `[Unreleased]`.
- [ ] Related ADR — status updated if applicable.
- [ ] `docs/architecture/implementation-roadmap.md` — phase marked complete if applicable.
- [ ] `docs/architecture/technical-debt.md` — item removed if resolved.

---

## What Happens After Phase 1

Once all Phase 1 stories are `Done` and `./mvnw clean verify` passes on `main`:
1. Update `implementation-roadmap.md` — mark Phase 1 complete.
2. Verify Phase 2 entry criterion: Phase 1 merged to `main` and green.
3. Begin Phase 2 stories.

Do not start Phase 2 stories while Phase 1 stories are still `Pending`.
