# Branching Strategy

**Version:** 1.0 | **Date:** 2026-07-13

---

## Main Branch

`main` is the only long-lived branch. It must always be:
- Compilable (`./mvnw clean package -DskipTests` passes).
- Green (`./mvnw clean verify` passes).
- Deployable (the JAR starts and `/actuator/health` responds).

No direct pushes to `main`. All changes arrive via Pull Request.

---

## Branch Naming — Conventional Branches

```
<type>/<short-description>
```

| Type | Use for |
|---|---|
| `fix/` | Bug fixes, error corrections, wrong HTTP status codes |
| `feat/` | New features or capabilities |
| `refactor/` | Internal restructuring with no behavior change |
| `test/` | Adding or updating tests only |
| `docs/` | Documentation only |
| `chore/` | Build, config, tooling, dependencies |
| `perf/` | Performance improvements |
| `spike/` | Investigations, proof of concepts — never merged directly |
| `security/` | Security-related changes (prefer `fix/` if it is a bug) |

The branch name should match the `**Branch:**` field in the backlog story. If a story does not
have a branch name, derive it from the type and a 3–5 word slug.

### Examples

```
fix/internal-endpoint-security
fix/request-mapping-leading-slash
fix/error-http-semantics
feat/error-correlation-id
test/window-policy
test/ingestion-job
refactor/health-indicator-config
refactor/optional-return-types
docs/architecture-decisions
spike/parcial-deduplication
chore/migrate-spring-boot-4-java-25    ← historical example (merged to main 2026-07-13)
```

---

## Branch Lifecycle

```
main
 └─ fix/request-mapping-leading-slash   [created from main]
     [implement, test, verify, document]
     └─ PR → review → merge to main
         [branch deleted after merge]
```

1. **Create** from the latest `main`.
2. **Work** on a single story (or a small group of XS stories with the same branch name).
3. **Push** when ready for review.
4. **Delete** after merge.

Branches are short-lived. If a branch lives more than a week without a PR, it is a signal
the story is too large or something is blocking it.

---

## Grouping Stories on One Branch

Some backlog stories share the same branch because they are closely related and very small (XS).

| Branch | Stories |
|---|---|
| `fix/internal-endpoint-security` | TECH-001, TECH-002 |
| `fix/error-http-semantics` | TECH-021, TECH-022 |
| `fix/cleanup-placeholder-comments` | TECH-050, TECH-051 |
| `refactor/config-validation` | TECH-070, TECH-071 |

Do not create ad-hoc groups. Only group stories explicitly assigned to the same branch in the backlog.

---

## Merge Strategy

All PRs are merged with **squash merge** or a clean **merge commit** (the project does not
enforce one strategy; follow team convention). The commit message in `main` should be a
clean Conventional Commit representing the story.

Do not use `git rebase -i` on public branches.

---

## Spike Branches

Spike branches (`spike/`) are investigative. They:
- Are **never merged directly** to `main`.
- Produce a written outcome: a decision, an ADR update, or a new backlog story.
- Are deleted after the SPIKE is complete.

---

## Branch Protection (recommended configuration)

For the `main` branch, configure GitHub branch protection:
- Require PR before merging.
- Require `./mvnw clean verify` to pass (when CI is configured).
- Dismiss stale reviews on new pushes.
- Do not allow force-push.
