# ecs-task module

ECS cluster and Fargate task definition foundation for SIPSA (TECH-140). **No ECS
Service, no ALB, no target group, no listener, no autoscaling exists yet** — this module
defines what a future task *would* run, not a running task. No container is ever started
by this module or this story.

## Cluster and capacity

Fargate only (`capacity_providers = ["FARGATE"]`) — no EC2 capacity provider.
**`FARGATE_SPOT` is deliberately not used** for this task: the single production task
runs scheduled ingestion jobs against a public SOAP endpoint (DANE), with retry logic
tuned for a live, uninterrupted run. A Spot interruption mid-ingestion risks a partial,
hard-to-diagnose ingestion — the cost saving isn't worth that risk for this specific
workload.

**Container Insights defaults to enabled** (`enable_container_insights = true`).
Trade-off: it adds a real per-metric CloudWatch cost beyond the free basic cluster
metrics, in exchange for per-task/per-service CPU, memory, network, and storage
visibility. Judged acceptable here because this workload runs unattended scheduled jobs
(no human watches a dashboard while ingestion runs) and the single-task scale keeps the
absolute cost small. Revisit if cost pressure appears.

## PostgreSQL/instance-sizing validation still required before real deployment

`cpu = 256` (.25 vCPU) and `memory = 512` (MiB) are **proposals, not confirmed
capacities**. Before the first real deployment, validate against:

- Heap and native-memory consumption under a real ingestion run.
- The largest known ingestion (`SipsaParcial`, 229k+ records) — its batch-upsert memory
  footprint specifically.
- GC pause/throughput behavior under that load (G1GC is already configured via
  `-XX:+UseG1GC` in the Dockerfile's entrypoint).
- OOM risk — an under-sized task killed mid-ingestion is a real failure mode for a large
  SOAP payload or large-batch upsert, not a theoretical one.

Both are Terraform variables specifically so this is a config change, not a redesign,
once real numbers exist. **Do not raise them arbitrarily "to be safe"** — an unmeasured
increase just hides whether the real requirement was ever established.

## Image and tag

`ecr_repository_url` (from `modules/ecr`) plus a required `image_tag` variable —
**`"latest"` is rejected by the module's own variable validation**, since it is not a
valid contract against an `IMMUTABLE`-tag ECR repository. For offline `terraform
validate`/`test` only, a placeholder tag (e.g. `"unreleased"`) may be used; never for a
real deployment. No image is ever pushed by this story.

## Port and health

**Port 8080**, confirmed directly from this repository (`application.yaml`:
`server.port: ${PORT:8080}`; `Dockerfile`: `EXPOSE 8080`) — not assumed. Only this one
port is exposed in `portMappings`.

No target group or ALB health check exists yet (out of TECH-140's scope), but the future
one should use:

| | |
|---|---|
| Health path | `/actuator/health` |
| Port | 8080 (same as the app) |
| Expected status | `200` when healthy |
| Startup grace | Actuator's liveness/readiness probe groups are already enabled (`management.endpoint.health.probes.enabled: true`) — the health check should allow enough startup grace for Spring context initialization before the first check counts against the task, not assume an instant-ready container |

## Architecture: X86_64, not ARM64

`cpu_architecture = "X86_64"` by default. ARM64 (Graviton) was evaluated but **not
chosen without evidence**: this repository's CI (`ci.yml`, GitHub-hosted `ubuntu-latest`
runners) builds x86_64 images today, with no multi-arch build pipeline, no ARM64
compatibility check for Java 25, and no native-dependency audit performed (CXF/Woodstox
and other SOAP-stack dependencies may or may not ship ARM64-compatible native
components — not verified). ARM64 is a real, valid future cost optimization once that
evidence exists — not assumed here.

## Container security

- **`readonlyRootFilesystem = true`.** No evidence the application writes to disk
  anywhere outside the JVM's own needs (confirmed by inspection — no
  `java.io.File`/`Files.write`/`createTempFile` usage in `src/main/java`). A small
  **tmpfs mount at `/tmp`** (128 MiB) is provided regardless, since the JVM and its
  libraries (JAXB/CXF schema handling, reflection scratch space) may still want
  writable temp space — this avoids risking an unvalidated fully-read-only filesystem
  breaking the application on its first real run.
- **Non-root user:** already enforced at the image level (`Dockerfile`'s `USER
  appuser`) — no redundant ECS-level override needed.
- **`privileged`:** not set, and Fargate does not support privileged containers at all —
  there is no way to enable it even by mistake on this launch type.
- **No `linuxParameters.capabilities`:** no additional Linux capability is granted.
- **`essential = true`, exactly one container definition** — no sidecar exists in this
  task definition.

## Logs

`awslogs` driver, `/ecs/<project>-<environment>` log group created ahead of the task
definition with **explicit 30-day retention** (`log_retention_days`, never infinite) —
ECS's own auto-referenced log group has no retention unless one is created first with it
set. No secret is ever written to a log by this module's own configuration (the
`secrets` block resolves values directly into the container's environment, not through
any logged path).

## Execution role vs. task role — not the same thing

| | Execution role | Task role |
|---|---|---|
| Who assumes it | The ECS agent, to *prepare* the task | The application, at runtime |
| Used for | Pulling the image from ECR, writing to CloudWatch Logs, resolving `secrets` entries | Whatever AWS APIs the application itself calls |
| This module's grant | `AmazonECSTaskExecutionRolePolicy` (AWS-managed, scoped to exactly ECR pull + Logs write) + an inline policy scoped to the exact secret/parameter ARNs referenced in the container definition — never `Resource = "*"`, never `SecretsManagerReadWrite` | **Empty by default** (`task_role_policy_arns = []`) — this application does not call any AWS API directly today (Spring Data JPA against RDS via a database credential, not IAM); no RDS IAM auth is granted here, since the application doesn't use it |

Never `AdministratorAccess`, `PowerUserAccess`, or an indiscriminate wildcard added on
top of the standard execution policy.

## Secrets — a temporary wiring, not the final design

`db_credentials_secret_arn` is wired, **at the root** (`environments/production`), to
`modules/database`'s RDS-managed master secret. **This is explicitly a placeholder for
this story, not the intended final design:**

- No application-specific, minimum-privilege database user is created here — that
  requires a real SQL bootstrap step (creating a role, granting scoped privileges), out
  of TECH-140's scope.
- A real deployment **must** replace this with a dedicated application credential before
  going live. Using the master secret as the standing design would violate
  least-privilege for the running application.
- Using the master secret is acceptable **only** for a later, explicitly-approved,
  temporary smoke test — never adopted silently as the permanent connection path.

No secret value is ever exposed in a Terraform output or a container definition's plain
`environment` block — `DB_USERNAME`/`DB_PASSWORD` resolve via the task definition's
`secrets` block (`valueFrom` referencing the Secrets Manager ARN + JSON key), which ECS
resolves at task startup, never through Terraform state or a log.

## Spring profile

`spring_profile = "docker"` by default — this repository has **no dedicated
`production` Spring profile** (`application-production.yaml` does not exist).
`application-docker.yaml` is the closest existing, already-safe analog: it inherits the
production-safe baseline (every credential-adjacent value has no default, fails fast if
missing) and only overrides container-topology facts (the database host default). It
contains no insecure development configuration and no mock-OIDC wiring. A dedicated
production profile can be introduced later, deliberately, if a real need for
production-only configuration beyond what `docker` already provides arises — not
invented speculatively here.

## Ephemeral storage

No `ephemeral_storage` block is set — Fargate's default (20 GiB) is left as-is.
Expected usage: JVM/library temp files (the `/tmp` tmpfs mount above), transient SOAP
response buffering during ingestion, and application logs (which are not persisted to
disk — they go to stdout, captured by the `awslogs` driver). No evidence of accumulation
risk has been identified; raising this without evidence would just pay for unused
storage.

## Testing

`tests/ecs-task.tftest.hcl` uses Terraform's native `terraform test` with a mocked AWS
provider (`mock_provider "aws" {}`) — no real AWS account or credential is contacted, and
no task ever actually runs. Run with `terraform test` from this module's directory
(after `terraform init -backend=false`).
