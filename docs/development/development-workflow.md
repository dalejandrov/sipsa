# Development Workflow

**Version:** 1.0 | **Date:** 2026-07-13

This document describes the complete development cycle from selecting a story to merging a Pull Request. It applies to all contributors — human and AI.

---

## Overview

```
Backlog → Branch → Implement → Test → Verify → Document → PR → Review → Merge
```

Every change follows this cycle without exception. There are no shortcuts.

---

## Step 1 — Select a Story

Open [`docs/backlog/technical-backlog.md`](../backlog/technical-backlog.md).

Select the first story that meets all of the following:
- `**Status:** Pending`
- In the current active phase (see [`implementation-roadmap.md`](../architecture/implementation-roadmap.md))
- `**Dependencies:**` are satisfied (either "None" or all dependent stories are `Done`)
- Linked ADR (if any) is `Accepted`

Do not start a story that is blocked.

---

## Step 2 — Pre-Implementation Analysis

Before writing a single line of code, answer these questions in writing (in the PR description or a comment):

| Question | Why |
|---|---|
| Which story? (TECH-NNN) | Focus: one story at a time |
| What problem does it solve? | Understand intent, not just the task |
| Which files will be modified? | Scope control |
| Which tests are needed? | Safety net before changes |
| Which docs need updating? | Keep documentation live |
| What could go wrong? | Risk awareness |

Read the relevant ADR(s) before starting. If the story references a `Proposed` ADR that has not been accepted, stop and escalate.

---

## Step 3 — Create the Branch

```bash
git switch main
git pull origin main
git switch -c <branch-name>   # from the **Branch:** field in the story
```

Branch names follow [Conventional Branches](branching-strategy.md).

---

## Step 4 — Implement

Implement **only** what the story describes. No more.

If you find something that needs fixing along the way:
- Do not fix it in this PR.
- Add it to the backlog with evidence.
- Continue with the original story.

See [Implementation Guidelines](implementation-guidelines.md) for specific rules.

---

## Step 5 — Write or Update Tests

Every story that changes production code must include at least one test that verifies
the acceptance criteria. See the [Testing Strategy](../architecture/testing-strategy.md)
for what tests are required per layer.

Tests must be deterministic. No tests that depend on the system clock, network, or random values
unless explicitly designed for it (e.g., with fixed clocks or WireMock).

---

## Step 6 — Verify

```bash
./mvnw clean verify
```

This command must succeed with zero failures before proceeding. Do not skip this step.

If it fails:
- Fix the failure.
- Determine if the failure was pre-existing (document it) or introduced by this story (fix it).
- Never mark a story Done if `./mvnw clean verify` fails.

---

## Step 7 — Update Documentation

After a successful build, update the following before committing:

| Document | Update |
|---|---|
| [`docs/backlog/technical-backlog.md`](../backlog/technical-backlog.md) | Mark story `Done`. Add PR link under **Completed**. |
| [`CHANGELOG.md`](../../CHANGELOG.md) | Add entry under `[Unreleased]` in the appropriate section. |
| Related ADR | If the story completes an ADR decision, change its **Status** to `Accepted`. |
| [`docs/architecture/implementation-roadmap.md`](../architecture/implementation-roadmap.md) | If a full phase is complete, update its status. |
| [`docs/architecture/technical-debt.md`](../architecture/technical-debt.md) | Remove the debt item if it is now resolved. |

Documentation updates should be in the same PR as the implementation. They can be in a
separate commit within the PR for clarity.

---

## Step 8 — Commit

Use [Conventional Commits](https://www.conventionalcommits.org/):

```bash
git add <specific files>   # never: git add .
git commit -m "fix(api): add leading slash to @RequestMapping on internal controllers"
```

Rules:
- One commit per logical change. Do not bundle unrelated changes.
- Commit message explains **why**, not just **what**.
- Never use `--no-verify` to skip hooks.
- Reference the story ID in the PR description, not necessarily every commit.

---

## Step 9 — Open a Pull Request

```bash
git push origin <branch-name>
```

Create a PR on GitHub using the [PR template](../../.github/PULL_REQUEST_TEMPLATE.md).

Required in every PR:
- Story reference: `TECH-NNN`
- Build evidence: paste output of `./mvnw clean verify`
- Checklist completed

PR size: **small**. A PR should be reviewable in under 15 minutes. If it is larger,
consider whether you have mixed multiple stories.

---

## Step 10 — Review and Merge

See [Code Review Guidelines](code-review-guidelines.md).

The PR cannot be merged if:
- `./mvnw clean verify` is failing.
- The PR template checklist has unchecked required items.
- Documentation was not updated.
- The story's acceptance criteria are not met.

---

## Phase Transitions

A phase is complete when all its stories are `Done` and `./mvnw clean verify` passes on `main`.

Before starting the next phase:
- Update `implementation-roadmap.md` to mark the phase complete.
- Verify the entry criterion for the next phase is met.

---

## Deviations

If you believe a story needs to be changed (scope, priority, acceptance criteria), do not
change it unilaterally. Add a comment with the proposed change and rationale. The decision
stays in the backlog until reviewed.
