# AWS Production Deployment Preflight

**This document has two owners, on two separate branches, deliberately kept apart:**

- **TECH-144** (`infra/preflight-local-hardening`, this branch) — everything in this
  document that was validated **locally, without any AWS access**: the Cognito human
  client gate, the database credential strategy design, the Flyway/scheduler decisions,
  and the ECS capacity/health-check-grace-period evidence. **Status: Done.**
- **TECH-143** (`infra/production-deployment-preflight`, kept separate, not merged) —
  everything that requires **real AWS access to the correct SIPSA account**: RDS engine
  availability, the backend bootstrap plan, OIDC trust-policy inspection, a real
  `terraform plan`, and cost estimation. **Status: Blocked / In progress**, unresolved,
  pending SIPSA-specific credentials not available in this environment.

**TECH-144 contains only locally validated hardening. It does not constitute an AWS
deployment preflight or a real Terraform plan.** No `terraform apply` was run by either
story. No AWS resource of any kind exists. No AWS credential was used, stored,
committed, or transmitted by either story.

---

## 1. AWS access — blocked, by explicit user decision (TECH-143, unresolved)

Before touching AWS, TECH-143 checked for configured credentials on this machine.
Findings: no SIPSA/dalejandrov-specific profile exists. Two unrelated profiles are
configured (`incampo`, `trustid`), both using **permanent, long-lived access keys** —
not GitHub OIDC, not a temporary session, and neither name matches this project. **No
AWS command was run with either profile** — `aws sts get-caller-identity` was never
executed. Presented with this finding, the repository owner confirmed: the handling was
correct, do not use those credentials, and do not merge TECH-143 into `main` while it
mixes verifiable local work with blocked AWS results — hence this split into TECH-144
(local, mergeable) and TECH-143 (blocked, kept on its own branch as evidence, not
merged).

**Consequence, still true:** every item requiring real AWS access remains blocked. This
is not re-litigated by TECH-144 — see §2–§4, §8 below, all unchanged, all still owned by
the TECH-143 branch.

## 2. RDS PostgreSQL 18 / `db.t3.micro` availability — blocked (TECH-143, unchanged)

**Not verified.** `modules/database/variables.tf`'s `postgres_engine_version` (`"18"`)
and `instance_class` (`db.t3.micro`) remain explicitly flagged, unchanged proposals — no
Terraform value was altered speculatively. **Not part of TECH-144.**

## 3. Backend bootstrap (`infra/terraform/bootstrap`) — blocked (TECH-143, unchanged)

**No plan generated.** Structural review only (no AWS needed) already confirms
encryption, versioning, public-access-block, and S3-native locking are all present in
`infra/terraform/bootstrap/main.tf` — unchanged from before. **Not part of TECH-144.**

## 4. OIDC roles — blocked (TECH-143, unchanged)

**Trust-policy subject not inspected**; no IAM role was created. **Not part of
TECH-144.**

## 5. Cognito human client — resolved (TECH-144, this branch)

`enable_human_client` (new variable, `modules/cognito`) defaults to `false` — the human
app client is not created in production until a real, approved callback/logout URL
exists. Gated via `count = var.enable_human_client ? 1 : 0` on
`aws_cognito_user_pool_client.human` only — the M2M client, resource server, scopes, and
user pool itself are entirely unaffected. `human_client_id` output and the client's
entry in the SSM allowlist parameter are both absent/`null` when disabled. A validation
rejects enabling the human client with empty callback/logout URLs outright. 2 new
`terraform test` cases (25 total in `modules/cognito`, was 23) — confirmed: no human
client by default; M2M client and SSM allowlist entirely unaffected; enabling without
real URLs is rejected. No `localhost`/`example.com` value was used anywhere.

## 6. Application database credential strategy — designed, not created (TECH-144, this branch)

**Decision:** two PostgreSQL roles, never one shared credential, replacing the master
secret currently wired (temporarily) into the ECS task definition:

| Role | Purpose | Privileges | Used by |
|---|---|---|---|
| `sipsa_migration` | Schema ownership, Flyway DDL | `ALL PRIVILEGES` on `public` schema, all tables/sequences, `ALTER DEFAULT PRIVILEGES` for future Flyway-created tables | A one-off migration ECS task, at deploy time only — never the running service |
| `sipsa_runtime` | Application runtime | `SELECT`/`INSERT`/`UPDATE`/`DELETE` on the 8 real application tables + sequence `USAGE`/`SELECT`; auto-granted DML on future Flyway-created tables via `ALTER DEFAULT PRIVILEGES` | The running ECS service, always |

Exact `GRANT` statements: `infra/terraform/modules/database/scripts/create-application-users.sql`
(tracked, reference only — **never executed**). Confirmed by inspection: neither role is
granted `SUPERUSER`, `CREATEROLE`, `CREATEDB`, or `REPLICATION` — only the specific
schema/table/sequence grants each needs. **Not idempotent on `CREATE ROLE`,
deliberately** — PostgreSQL's `CREATE ROLE` has no `IF NOT EXISTS`; re-running the full
script against a database where the roles already exist fails loudly rather than
silently masking a mistake (wrong database) or silently no-op'ing an intended password
rotation. The `GRANT`/`ALTER DEFAULT PRIVILEGES` statements alone, without the two
`CREATE ROLE` lines, are safe to re-run.

**Secrets Manager placement:** two dedicated secrets (not the master secret), same
pattern already established for the Cognito M2M client secret — a dedicated
`aws_secretsmanager_secret`/`_version` per role, IAM-gated `secretsmanager:GetSecretValue`
scoped to the specific ARN needed by whichever role reads it.

**Rotation:** not implemented — same posture as the RDS master secret and the Cognito
M2M secret, deferred pending a real distribution mechanism.

**Not created in this story** — requires a live connection to an applied RDS instance,
which does not exist. Prerequisite step in the documented first-deployment order (§10).

## 7. Flyway and rolling deployments — decision made, not implemented (TECH-144, this branch)

**Decision: one-off migration ECS task before service rollout**, chosen over (A) Flyway
on startup accepted as-is (real risk: two tasks briefly racing for the migration lock
during every rolling deployment) and (C) a dedicated migration pipeline (more
infrastructure than this project's scale currently justifies).

**Not implemented in this story** — the actual Terraform resource depends on the shape
of the future deploy pipeline (`application-deploy.yml`, ADR-010 Fase 5). Explicit
follow-up, registered, not built:

- **Migration task definition** — a second `aws_ecs_task_definition` (or a
  `command`-overridden run of the existing one) that runs only the Flyway migration and
  exits.
- **Pipeline step** — whatever CI/CD stage triggers deployment must run this task to
  completion before updating the ECS service.
- **Failure handling** — a non-zero exit from the migration task must abort the
  deployment before the ECS service is ever touched, not proceed regardless.
- **Rollout ordering** — migration task completes and exits 0 → then, and only then,
  update the ECS service.
- **Rollback behavior** — if the ECS service update itself fails/rolls back (the
  circuit breaker already in `modules/ecs-service`), the schema migration already
  applied does NOT automatically roll back — Flyway migrations are forward-only by
  design in this repository; a failed deployment after a successful migration leaves
  the schema at the new version with the old application code potentially still
  running during rollback. This asymmetry is a real, unresolved risk, explicitly
  flagged, not solved here.

## 8. Scheduler and multiple replicas — follow-up registered, not resolved (TECH-144, this branch)

Unchanged: `desired_count` stays at `1`; no autoscaling exists anywhere in this stack.
Registered explicitly as a blocker for any future HA (more than one replica) — **not a
blocker for the initial single-task deployment**, since one task cannot double-fire its
own scheduler. Four follow-up options documented, **none decided**:

- A distributed lock (e.g. `SELECT ... FOR UPDATE SKIP LOCKED`, or a Postgres advisory
  lock) so only one replica's scheduler fires per trigger.
- An external scheduler (EventBridge Scheduler invoking
  `POST /api/internal/ingestion/run` directly, replacing the in-process `@Scheduled`
  trigger).
- Leader election (a lease row in Postgres, or similar).
- Splitting the API and the scheduler into separate services.

No architectural decision is made here.

## 9. ECS capacity and health check grace period — measured locally, re-verified, evidence-based values applied (TECH-144, this branch)

**Method:** `scripts/measure-container-startup.sh` (tracked, reusable, hardened —
`set -euo pipefail`, fails non-zero if any sample never reaches `/actuator/health` 200,
configurable CPU/memory, per-sample timeout, cleans up containers and the compose stack
on exit including on error, no arbitrary sleep as a success criterion, prerequisites
documented in its own header). Builds the real application image and runs it against
the existing local `db`/`oidc` docker-compose services.

**Explicit caveat, unchanged:** measured on local Docker Desktop (macOS/ARM64), **not
real AWS Fargate hardware** — real, reproducible measurements, not a substitute for
confirming against an actual Fargate task at first deployment.

### Memory: 512 MiB (3 samples) vs. 1024 MiB (3 samples, re-verified for TECH-144)

**512 MiB, 0.25 vCPU** — idle memory immediately after startup, before any real
ingestion load: **459.4 MiB used of a 512 MiB limit — 89.73% memory utilization,
10.27% free.** (Corrected phrasing: the earlier TECH-143 draft used the ambiguous word
"idle" without stating which side of the fraction "89.73%" referred to.) No OOM
occurred in any of the three 512 MiB samples, but 10.27% free before any real workload
is real evidence of thin headroom, not a theoretical concern.

**1024 MiB, 0.25 vCPU — re-tested for TECH-144, not just inferred from the 512 MiB
result:**

| Sample | Peak memory observed (periodic ~3s sampling) | Memory utilization | Exit code | OOMKilled | Time to `/actuator/health` 200 |
|---:|---|---:|---:|---:|---:|
| 1 | 560.4 MiB / 1 GiB | 54.72% | 0 (still running) | false | 188s |
| 2 | 483.5 MiB / 1 GiB | 47.22% | 0 (still running) | false | 187s |
| 3 | 549.0 MiB / 1 GiB | 53.61% | 0 (still running) | false | 385s (see grace period section below) |

Peak memory utilization at 1024 MiB across all three samples: **44.89%–55.25%** (final,
post-startup readings) — a comfortable margin below the limit, not merely "no longer
at ~90%." No OOM in any of the three runs. **Task memory: 512 → 1024 MiB** — a valid
Fargate memory value for 256 CPU (.25 vCPU), so no CPU change was needed.

**Explicitly not done, still a real gap:** no real ingestion was run (neither
`promediosSipsaCiudad` nor, especially, the large `promediosSipsaParcial` — 229k+
records) against DANE's real public SOAP endpoint. **1024 MiB is de-risked by real
idle/startup-memory evidence at both 512 MiB and 1024 MiB, not validated under real
ingestion load** — peak memory during a real large ingestion run remains a required
step before treating ECS capacity as fully settled.

### Health check grace period: six real samples, not three

All six real local measurements this preflight produced (three at 512 MiB, three at
1024 MiB — CPU was 0.25 vCPU in every sample, and grace period is a startup-time
concern, not a memory-size concern, so pooling all six for this specific statistic is
reasonable):

| Sample | Config | Seconds to `/actuator/health` 200 | Spring's own "Started SipsaApplication" |
|---:|---|---:|---|
| 1 | 512 MiB | 207 | 190.5s |
| 2 | 512 MiB | 214 | 196.9s |
| 3 | 512 MiB | 221 | 205.5s |
| 4 | 1024 MiB | 188 | 170.9s |
| 5 | 1024 MiB | 187 | 173.5s |
| 6 | 1024 MiB | **385** | 191.6s |

**Min: 187s. Median: ~210.5s. Max: 385s.**

Sample 6 is kept, not discarded as a convenient outlier: Spring Boot's own internal log
for that exact run reported ~192s to "Started SipsaApplication" — closely consistent
with every other sample — so the extra ~193s gap before the **host's** `curl`-based
probe (via Docker Desktop's port forwarding) actually received `200` is most plausibly
local Docker Desktop network/host contention from the large amount of concurrent heavy
Docker/Terraform activity run throughout this same measurement session — a real,
reproduced measurement, not confirmed root-caused, and not assumed away either.

`health_check_grace_period_seconds`: **120 (original, unmeasured) → 300 (TECH-143 draft,
based on only the three 512 MiB samples) → 480 (TECH-144, final for this story)** — set
with ~95s margin over the worst of all six real samples (385s), not the worst of a
convenient subset. Too high a value would hide a genuinely broken startup for that
long, so this is not padded further "to be safe" beyond that margin. **Still requires
confirmation against real AWS Fargate hardware before the first real deployment** — this
remains a local measurement, explicitly not a substitute for one on real infrastructure.

---

## 10. Final validation (TECH-144, this branch — static/local only)

```
terraform fmt -check -recursive infra/terraform         -> clean
terraform test (modules/cognito, ecs-task, ecs-service,
  environments/production, plus full-tree regression)   -> 131/131 passed
tflint --recursive --chdir=infra/terraform                -> 0 issues
trivy config infra/terraform                              -> 0 unresolved findings
./mvnw clean verify                                        -> 338 tests, 0 failures, BUILD SUCCESS
git diff --check                                           -> clean
```

No `terraform apply`, `terraform import`, AWS CLI command, real Docker registry push,
ECS deployment, real token request, or real API key retrieval was ever executed by
either TECH-144 or TECH-143. No AWS credential was used, stored, committed, or
transmitted.

---

## 11. TECH-132 status

**Remains `In progress`.** TECH-144 (this branch) hardens the declarative configuration
with real local evidence; it does not touch AWS and does not advance TECH-132 toward
Done. TECH-143 (the separate, unmerged branch) still owns every AWS-dependent
validation item, all still blocked. TECH-132 cannot be marked Done until: bootstrap
applied, OIDC roles created and verified, network/RDS/ECS applied, a real image
published, migration executed, ECS Service deployed and healthy, Cognito applied, API
Gateway/VPC Link applied and reachable, gateway-bypass confirmed blocked, a real smoke
E2E test passed, logs/metrics/alerts confirmed flowing, and a real rollback tested.

## 12. Order of the first real deployment (documented, not executed)

```
1.  Bootstrap the state bucket (infra/terraform/bootstrap, real apply)
2.  Create the three OIDC roles (terraform-plan/terraform-apply/application-deploy),
    trust policy verified against a real GitHub Actions token's subject claim
3.  Initialize the remote backend (environments/production, real backend config)
4.  Apply the network (modules/network)
5.  Apply RDS (modules/database)
6.  Create the two application database users (sipsa_migration/sipsa_runtime,
    scripts/create-application-users.sql) and their Secrets Manager entries
7.  Apply ECR/ECS task definition/ALB (modules/ecr, modules/ecs-task, modules/ecs-service)
8.  Publish a real, immutable-tagged application image to ECR
9.  Run the one-off migration task to completion (§7 decision), confirm exit code 0
10. Deploy the ECS Service (repointed at sipsa_runtime's secret, not the master secret)
11. Apply Cognito (modules/cognito) — enable_human_client only once real callback/logout
    URLs exist
12. Apply API Gateway/VPC Link (modules/api-gateway)
13. Run a real smoke E2E test (401/403/429/2xx matrix, real token issuance, gateway-bypass
    check)
14. Validate alerts, logs, and metrics are flowing; test a real rollback
```

This order is not executed by TECH-144, TECH-143, or any story before them —
documentation only.
