# Agent Architecture Guide — SIPSA Integration Service

Read this before touching any file. It takes a few minutes. It is a distillation of
[`project-architecture.md`](project-architecture.md) and [`AGENTS.md`](../../AGENTS.md) —
if the two ever disagree, `AGENTS.md` is the authoritative workflow document; this file is
a faster orientation, not a replacement.

---

## The system in 30 seconds

A Spring Boot REST wrapper around DANE Colombia's SIPSA SOAP service. Four packages:
`api` (HTTP) → `application` (orchestration) → `domain` (entities + one gateway interface)
→ `infrastructure` (SOAP client, JPA, config). One template-method ingestion pipeline, five
strategy handlers, one cron scheduler, one window-validation safety net. No DDD, no
hexagonal formalism, no microservices — deliberately. See
[project-architecture.md](project-architecture.md) for the full picture.

---

## Before you read or change anything

1. Read [`project-architecture.md`](project-architecture.md) — current approved structure.
2. Read [`docs/backlog/technical-backlog.md`](../backlog/technical-backlog.md) — is there
   already a story for what you're about to do?
3. Read [`docs/adr/README.md`](../adr/README.md) — is there an ADR governing this area, and
   is it `Accepted` or still `Proposed`?
4. If neither exists, **stop and ask** before writing code. Do not infer a story or a
   decision that isn't written down.

---

## Hard rules — do not do these without an explicit, separate story

- **Never move packages or rename classes for aesthetic reasons.** Every package move in
  this project's history (ADR-007) was justified by a *concrete, evidenced* problem
  (a real compile-time cross-layer import), not by how the tree "looks." "Looks wrong" is
  not evidence.
- **Never introduce a new cross-layer dependency** (`domain → infrastructure`,
  `infrastructure → api`, or a new `application → api` import beyond the ones already
  investigated and accepted in `project-architecture.md`). If your change seems to require
  one, that's a signal to stop and reconsider the design, not to add the import.
- **Never modify a public contract without an ADR.** REST routes, JSON field names/types,
  HTTP status codes: changing any of these is a contract break. [ADR-003](../adr/ADR-003-error-response-model.md)
  exists specifically because someone considered changing the error contract and stopped to
  write it down first. Do the same.
- **Never change `WindowPolicy` without tests proving the new behavior.** It is
  time-critical business logic. Use the injected `Clock` seam (`setClock`, package-private,
  test-only) — never call `LocalDateTime.now()` / `Instant.now()` / `ZonedDateTime.now()`
  without a `Clock` in a test. If you find a bug in it, document it (evidence, reproducing
  test) before proposing a fix — do not fix silently. See TECH-111 for the model to follow:
  a fully-specified, evidence-backed plan written *before* touching the code, because the
  diagnosis and the fix were deliberately kept as separate steps (the fix later shipped as
  its own story, merged via PR #15).
- **Never change a cron expression, timezone, or schedule without evidence.** The current
  cron values are a deliberate operational buffer over DANE's documented publication time,
  confirmed correct by an evidence-backed validation (see
  [`scheduled-ingestion-validation.md`](scheduled-ingestion-validation.md)). "It would be
  cleaner at 14:00" is not evidence. A confirmed discrepancy against a *current* DANE
  source is.
- **Never assume a business rule.** DANE's reference documentation
  (`DANE-webservice-SIPSA.pdf`) is from March 2020. When a rule might have changed, say so
  explicitly instead of treating a 2020 PDF as current truth. This project's own
  [ADR-008](../adr/ADR-008-timezone-locale-and-date-semantics.md) does exactly this — it
  proposes a strategy while explicitly flagging that DANE's documented schedule needs
  reconfirmation.
- **Never mix unrelated stories in one PR/commit set.** One story, one branch, one PR (or a
  tightly related group explicitly sharing a branch, like TECH-090/091/095 under ADR-007).
  This project's own branch history is the model: `test/scheduled-ingestion-jobs` never
  touched `WindowPolicy`'s functional rules while validating it; `refactor/internal-models-and-api-filter`
  never touched scheduling or timezone code while moving packages.
- **Never combine documentation-only and code-only work in the same PR** when they
  represent different initiatives. This project splits them into separate branches even
  when they're related (e.g., ADR-007's decision commit and its implementation commits live
  on two different branches, merged in a deliberate order — see
  [project-architecture.md](project-architecture.md#a-note-on-merge-status)).

---

## When to create an ADR

- You're changing how a major boundary works (transactions, async, package layering,
  timezone/locale contract).
- You're choosing between two or more non-obvious technical alternatives.
- A backlog story's own text says it's blocked on an ADR that's still `Proposed`.

Do **not** create an ADR for: library upgrades, naming fixes, a single-file bug fix with no
architectural implication.

## When to create a backlog story

- You found a confirmed defect (not a suspicion — reproduce it with a test first) and it
  isn't already tracked. Add it to `docs/backlog/technical-backlog.md` with evidence and
  acceptance criteria; add it to `docs/architecture/technical-debt.md` under the right
  category. Then **stop** — implement only the story you were originally asked to do,
  unless told otherwise.
- A validation or review task surfaces a fix that's out of that task's own scope. Document
  the story fully (this is exactly how TECH-111 was born: a fully-specified,
  approved-on-paper story produced by a *validation* task that was explicitly told not to
  fix what it found — and implemented later as its own story).

## When to stop and ask instead of proceeding

- The task asks you to fix something, but you're not certain whether the current behavior
  is a bug or a deliberate decision. Investigate for evidence of intent (commit history,
  code comments, ADRs, test plans) before concluding either way — and say so explicitly if
  no evidence exists either way.
- You're about to touch a file governed by an `Accepted` ADR, and your change would
  contradict that ADR. Stop; either the ADR needs revisiting (new ADR, not a silent
  override) or your change is out of scope.
- You're asked to publish, merge, or push something and any check fails (`./mvnw clean
  verify` doesn't pass, the diff touches files outside the stated scope, a branch isn't
  where you expect it). Report the failure; do not force through it.
- Git housekeeping: before any command that could discard work (`checkout`, `reset`,
  `clean`, switching branches with uncommitted changes), run `git status` first. Never
  `git push --force` or `git push --all` unless explicitly instructed in that exact
  session.

## What "done" looks like here

`./mvnw clean verify` passes with zero failures, the diff matches the story's stated scope
exactly (nothing more), tests exist for new logic, and the backlog/CHANGELOG/relevant ADR
are updated in the same change — not left for later. See `AGENTS.md`'s "Definition of Done"
for the full checklist; it is the same standard for agents and humans.
