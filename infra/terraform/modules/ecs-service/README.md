# ecs-service module

Internal ALB and ECS Service foundation for SIPSA (TECH-141). Bundles the ALB and the
service together deliberately — "internal load-balanced service" is one coherent
responsibility here, used by exactly this one service, so a separate `alb` module would
add file-boundary complexity with no reuse to justify it. **No image is ever run, no
task is ever executed, and no `terraform apply` is ever run by this story.**

## Topology

```
API Gateway (future, TECH-131)
  → VPC Link (future, TECH-131)
    → Internal ALB (this module, internal = true, never internet-facing)
      → ECS Service, private-application subnets (this module)
        → RDS, private-database subnets (TECH-139)
```

The ALB is placed in the **same private-application subnet tier as the ECS tasks**, not
a dedicated ALB tier. Evaluated and rejected: no other workload exists in that tier
today, so a separate subnet/route-table tier would add topology complexity (more
subnets, more route table associations) with no corresponding isolation benefit. Revisit
only if a concrete requirement for ALB/compute network separation emerges.

## Security groups — three relationships, each scoped narrowly

**ALB security group:** **no ingress rule by default.** No VPC Link exists yet
(TECH-131 creates it) — `alb_allowed_ingress_security_group_ids` is the preferred,
empty-by-default mechanism TECH-131 populates once its VPC Link security group exists.
`alb_allowed_ingress_cidr_blocks` is a documented fallback (e.g. a temporary,
explicitly-approved VPC-CIDR allowance for integration testing) — **`0.0.0.0/0` is
rejected outright by variable validation**, since the ALB is internal and API Gateway is
the only intended public entry point (ADR-002). Egress is scoped to exactly the ECS
service security group, on the application port only.

**ECS service security group:** ingress only from the ALB security group, on the
container port (8080). Egress is scoped to exactly what this application needs — no
`0.0.0.0/0` "all traffic" rule:
- PostgreSQL (5432) to the RDS security group.
- HTTPS (443) to `0.0.0.0/0` — necessary because the DANE SOAP endpoint
  (`SOAP_ENDPOINT`) is a public internet URL and AWS service APIs (Secrets Manager,
  CloudWatch Logs, ECR) are reached the same way; this is an **egress-only** rule (the
  security group has no matching ingress from the internet), consistent with TECH-138's
  NAT Gateway rationale.
- DNS (53, TCP and UDP) scoped to the VPC's own CIDR only, not `0.0.0.0/0`.

**RDS security group:** this module adds the actual ECS→RDS ingress rule
(`aws_security_group_rule.rds_ingress_from_ecs_service`), scoped to the ECS service
security group only. `modules/database` deliberately creates its security group with no
ingress rule of its own (see its README) — this is the first module with both security
groups available to reference each other, so the rule lives here.

## ALB

`internal = true`, `load_balancer_type = "application"`, subnets = the private
application tier. `enable_deletion_protection` defaults to `true` (consistent with this
repository's RDS posture). `drop_invalid_header_fields = true`,
`desync_mitigation_mode = "defensive"` (AWS's own recommended default — `"strictest"` is
selectable but not adopted without evidence it's needed), `enable_http2 = true`.

**Access logs default to disabled** (`enable_alb_access_logs = false`): enabling them
requires a real S3 bucket with a correct policy, encryption, and retention — none of
which this story creates. Creating an ad hoc bucket just to flip this on would be worse
than leaving it off. **Follow-up:** API Gateway's own access logs are mandatory in
TECH-131 regardless of this setting; revisit ALB-level logging specifically before any
public exposure if gateway-level logs prove insufficient for a specific investigation.

## Listener — HTTP only, and why that's acceptable here

No custom domain and no ACM certificate exist yet (ADR-010) — this module does not
simulate HTTPS against a certificate that doesn't exist. **This is acceptable
specifically because the ALB is internal-only**, reachable solely from within the VPC;
API Gateway (TECH-131) is, and will remain, the only public entry point — the ALB itself
is never exposed to the internet regardless of the listener's protocol. If VPC-internal
TLS is ever decided as a requirement (an open question tracked in
`aws-production-readiness.md`), that is a deliberate future decision requiring its own
certificate strategy — not assumed or worked around here.

## Target group and health check

`target_type = "ip"` (required for `awsvpc`-networked Fargate tasks), `protocol = "HTTP"`,
`port = 8080`. Health check path: **`/actuator/health`** — confirmed safe to use
unauthenticated, not invented:
- `SecurityConfig` permits `/actuator/health` and `/actuator/health/**` outright
  (`permitAll()`).
- `application.yaml` sets `management.endpoint.health.show-details: when-authorized` —
  an unauthenticated caller (the ALB) receives only the top-level `UP`/`DOWN` status,
  never component details. No need to force `show-details: never`; the existing
  configuration already achieves the same effect for exactly this caller.

## ECS Service

`launch_type = "FARGATE"`, `desired_count = 1` (see "Scheduler and multiple replicas"
below for why this must not be raised casually), `network_configuration` uses the
private-application subnets with `assign_public_ip = false`. `enable_execute_command`
defaults to `false` — ECS Exec needs its own IAM permissions, session logging, and audit
posture defined deliberately before enabling; turning it on by default would grant an
interactive shell into the production container with no corresponding control.

**Deployment configuration:** `deployment_minimum_healthy_percent = 100`,
`deployment_maximum_percent = 200`. With `desired_count = 1`, this combination lets ECS
start a **new** task (up to 200% = 2 tasks momentarily) before stopping the old one —
never dropping below 1 healthy task during a deployment. `deployment_circuit_breaker`
is enabled with `rollback = true` — a deployment that can't reach a healthy state
automatically rolls back rather than leaving the service in a broken state. Blue/green
deployment is not used at this stage.

**`health_check_grace_period_seconds` defaults to 120** — a conservative starting
proposal covering RDS connection establishment, Flyway migration, Spring context
initialization, and security configuration loading, but **not a measured value**. Must
be validated against a real application startup before the first real deployment; the
value must not be padded further "to be safe," since an excessively long grace period
would hide a genuinely broken startup for that long.

## Flyway and rolling deployments — a documented risk, not resolved here

This application runs Flyway migrations at startup (`spring-boot-flyway` /
`flyway-database-postgresql`, unconditional on boot). The deployment configuration above
(`minimum_healthy_percent=100` / `maximum_percent=200`) means a rolling deployment can
briefly run **two tasks simultaneously** — the old one still serving traffic, and the new
one starting up. **Both tasks would attempt to run Flyway on startup.**

Flyway's own documented mitigation for exactly this scenario is a database-level lock it
acquires before applying migrations (implemented via a row lock on the schema history
table for PostgreSQL, in the Flyway versions this repository's Spring Boot 4.1.0 parent
manages) — a second concurrent Flyway invocation blocks until the first completes, then
observes the target migrations already applied and does nothing further. This is
Flyway's own standard concurrency-safety mechanism, not something this repository has
customized. **This has not been verified empirically against a real concurrent rolling
deployment in this repository** — TECH-141 does not connect to RDS, run a task, or
exercise this path at all.

**Explicit pre-real-deployment criteria** (none satisfied by this story):
- Validate concurrent-migration behavior against a real two-task rolling deployment (or
  a local simulation with two application instances against the same database).
- Validate the time both tasks take to reach a healthy state under that condition.
- Validate the rollback path if the new task's migration or startup fails.
- If any of the above reveals a real problem, consider a separate, dedicated migration
  job (run once, before the service deployment) instead of migrate-on-boot — not adopted
  preemptively here, since it is unnecessary complexity without evidence Flyway's own
  locking is insufficient.

## Scheduler and multiple replicas — a critical future risk, documented explicitly

SIPSA's ingestion scheduler (the cron-triggered daily/monthly jobs) runs **inside the
application process itself** — there is no leader election, no distributed lock, and no
external scheduler. **`desired_count` must stay at 1** until one of the following exists:

- Leader election among ECS tasks (so only one task's scheduler is ever active), or
- An external scheduler (e.g., EventBridge Scheduler triggering the ingestion endpoint
  directly, bypassing the in-process cron entirely), or
- A distributed lock the scheduler acquires before running each job, or
- A structural split between a "scheduler" process and an "API" process, only one of
  which runs the cron logic.

**This service is explicitly not ready for multiple replicas or autoscaling** — running
`desired_count > 1` today would silently double- (or N-times-) trigger every scheduled
ingestion job, corrupting or duplicating data, not just wasting compute. Autoscaling is
therefore not implemented in this story at all (see below), and this module's own
`desired_count` variable description repeats this warning inline.

## Autoscaling — not implemented, criteria documented for later

No `aws_appautoscaling_target`/`policy` resource exists in this module. Future scaling
criteria to evaluate, once the scheduler risk above is resolved and real usage data
exists: CPU utilization, memory utilization, ALB request count per target, and — specific
to this workload — ingestion duration/queue depth (a request-count-based policy alone
doesn't capture "an ingestion job is running long," which is this application's actual
load pattern). **Never scale to zero** in production: the in-process scheduler must
always have exactly one running instance to fire on schedule.

## RDS master credentials — still only a placeholder

The ECS Service is **not** considered ready for production traffic against the RDS
master user. The task definition's wiring to `modules/database`'s master secret (see
`modules/ecs-task/README.md`) remains exactly what it was in TECH-140: a Terraform-code
placeholder. Explicit gap, unchanged by this story:
- Create an application-specific database user with minimum privileges (not the master
  user).
- Create a dedicated Secrets Manager secret for that user.
- Migrate the task definition's `secrets` block to that new secret.

This requires real database connectivity to execute the `CREATE ROLE`/`GRANT`
statements, and a decision on ordering relative to Flyway ownership — not resolved by
Terraform alone, and not attempted in this story.

## No image published

No image exists in ECR yet (TECH-140 created the repository, not an image). A real
`terraform plan`/`apply` of this module additionally requires, none of which this story
provides:
- A real image pushed to ECR, under an immutable, non-`"latest"` tag.
- The task definition's `image_tag` variable set to that real tag.
- The IAM permissions, secrets, remote backend, and OIDC roles TECH-137/139/140 already
  flagged as outstanding.

## Outputs

`alb_arn`, `alb_dns_name` (marked `sensitive = true` by the same defensive posture as
`modules/database`'s `db_endpoint` — not a credential, but a reachability point),
`alb_zone_id`, `alb_security_group_id`, `target_group_arn`, `listener_arn`,
`ecs_service_name`, `ecs_service_id`, `ecs_service_security_group_id`,
`ecs_desired_count`. No secret value is exposed by any of them.

## Testing

`tests/ecs-service.tftest.hcl` uses Terraform's native `terraform test` with a mocked AWS
provider (`mock_provider "aws" {}`) — no real AWS account or credential is contacted, and
no task is ever run. Run with `terraform test` from this module's directory (after
`terraform init -backend=false`).
