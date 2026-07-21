# ADR-010 — AWS Infrastructure-as-Code Tooling and Initial Production Topology

**Status:** Accepted (2026-07-21) — Terraform approved as the IaC tool; the initial
production topology, ownership model, and operational limits below are approved and
ready for phased implementation. No AWS resource exists yet; this ADR records the
decisions, not the resources themselves — those are created story by story, starting
with [TECH-137](../backlog/technical-backlog.md#tech-137) (bootstrap, Done) and
[TECH-138](../backlog/technical-backlog.md#tech-138) (VPC foundation, Done — Terraform
code only, no `apply` run).
**Date:** 2026-07-21 (Proposed) · 2026-07-21 (Accepted, same day — decisions confirmed by
the repository owner) · 2026-07-21 (revised — toolchain corrections applied before the
first `apply`: S3-native state locking replaces the originally-provisioned DynamoDB lock
table, Terraform pinned to 1.15.7 / AWS provider to 6.55.0, Trivy replaces tfsec, OIDC
trust-policy contract and role separation documented, API Gateway REST API decided
outright rather than deferred — see Terraform Toolchain and API Gateway sections below)
**Backlog:** [TECH-130](../backlog/technical-backlog.md#tech-130),
[TECH-131](../backlog/technical-backlog.md#tech-131),
[TECH-132](../backlog/technical-backlog.md#tech-132) (In progress),
[TECH-137](../backlog/technical-backlog.md#tech-137) (Terraform bootstrap, Done),
[TECH-138](../backlog/technical-backlog.md#tech-138) (VPC foundation, Done)
**Depends on:** [ADR-002](ADR-002-internal-endpoint-security.md) (Option E, layered
security model — this ADR provisions the infrastructure ADR-002 already assumes; it does
not revisit that decision), [aws-production-readiness.md](../architecture/aws-production-readiness.md)
(the audit this ADR builds on)

---

## Context

`aws-production-readiness.md` (2026-07-20 audit) confirmed by grep that this repository
had **zero existing IaC tooling and zero prior tooling decision**. A first version of
this ADR (2026-07-21, `Proposed`) recommended Terraform conditional on confirming
ownership, and left 14 blocking decisions open, deliberately not inventing answers to
organizational questions the repository alone could not resolve.

The repository owner has since confirmed all of the decisions this ADR needed. This
revision records them, resolves every previously-open blocking decision from the
`Proposed` version, and moves the ADR to `Accepted`.

---

## Alternatives Considered (unchanged from the `Proposed` version, kept for the record)

| Criterion | Terraform | AWS CDK | CloudFormation (raw) | Externally-managed (platform/infra team) |
|---|---|---|---|---|
| Team experience (this repo) | None found | None found | None found | N/A — ownership decision below rules this out |
| Language | HCL (declarative, AWS-agnostic syntax) | TypeScript/Python/Java (imperative, generates CFN) | YAML/JSON (declarative, AWS-only) | N/A |
| Reusability across providers | High | Low (AWS-only) | None (AWS-only) | N/A |
| Remote state | Built-in (S3 + locking) — set up explicitly (see Bootstrap Strategy below) | CDK-managed bootstrap bucket | CloudFormation is its own state store | N/A |
| CI/CD fit | Mature, matches this repo's existing GitHub Actions pattern (TECH-120) | Mature, same CI shape | Mature but verbose | N/A |
| Complexity to start | Low–Medium | Medium (construct-library learning curve) | Low, but verbose for this resource set | Lowest for this repo, highest coordination cost |
| Ownership fit | Matches this repo's existing single-team, single-repo pattern | Ties infra to a language this repo doesn't otherwise use for infra | Same fit as Terraform, weaker ergonomics | Ruled out — see Ownership decision |

---

## Decision — Approved

**Terraform is approved** as the IaC tool. Infrastructure code lives **in this
repository** (`infra/terraform/`), not a separate repository, unless a real constraint
discovered during implementation forces a split — none is known today, and none should be
assumed preemptively.

All decisions below are **Accepted**, not recommendations awaiting approval:

### Ownership

The owner of this repository is the initial owner of the AWS infrastructure: creation,
maintenance, operation, change approval, Cognito administration (including app clients),
cost accountability, and first-line incident response. This is a single-person
responsibility today — the implementation must not assume a larger team exists (no
multi-approver workflows beyond what GitHub Environment protection rules already give a
single approving owner).

### IaC tool and repository location

Terraform, in this repository, under `infra/terraform/`. Re-evaluate only if
implementation surfaces a concrete blocker (e.g., a platform team requirement discovered
later) — not preemptively.

### AWS account

**A single AWS account.** Only `production` exists initially. Multi-account separation
(e.g., a distinct account per environment) is explicitly **not implemented** in this
phase — Terraform's environment structure (`environments/production/`) exists so that a
future environment can be added without restructuring, but no `dev`/`staging` account or
workspace is created now.

### Region

**`us-east-1`**, parameterized as a Terraform variable (`aws_region`), never hardcoded
into individual resource blocks.

### Environments

**`production` only.** Modules and variables are structured to accept an `environment`
value so a future environment does not require duplicating module code — but no `dev` or
`staging` environment is created in this or the immediately following stories.

### Network

A **new, dedicated VPC** (the AWS account is new — there is nothing to share yet).

- **2 Availability Zones** — public and private subnets in each, even though some
  cost-bearing resources (see NAT Gateway below) are not duplicated per AZ yet. Using 2
  AZs from the start avoids a subnet-layout rewrite when high availability is added later.
- Internal ALB, ECS tasks in **private subnets only** — ECS is never placed in a public
  subnet to avoid needing a NAT Gateway; that would remove the private-network boundary
  ADR-002 (Option E, layer 4) requires.
- **One NAT Gateway initially** (not one per AZ). This is a deliberate cost/availability
  trade-off:
  - **Benefit:** roughly halves NAT cost (one hourly charge instead of two) at a stage
    with no production traffic yet and a single ECS task.
  - **Accepted risk:** the single NAT Gateway's AZ is a point of failure for all outbound
    internet egress (DANE SOAP calls, Cognito JWKS fetches) — if that AZ's NAT fails, the
    ECS tasks in the *other* AZ lose egress even though the AZ itself may be healthy.
    There is **no full high-availability egress path** in this initial topology.
  - **Future migration:** add a NAT Gateway per AZ (one in each public subnet, private
    subnets in each AZ routing to the NAT in the same AZ) once traffic, criticality, or
    incident history justifies the doubled cost. This is a route-table change plus one
    additional NAT Gateway resource — not a VPC redesign — precisely because the subnet
    layout is already 2-AZ from the start.

### Compute

**ECS Fargate.** Initial sizing, oriented at low/near-zero traffic:

- `desired_count = 1`, minimum healthy tasks = 1 (no redundancy yet — a single-task
  outage is an accepted risk at this stage, consistent with the single-NAT trade-off
  above).
- CPU and memory are **Terraform variables**, not hardcoded. Proposed starting values —
  **256 CPU units (.25 vCPU) / 512 MiB memory**, the smallest valid Fargate combination —
  are a starting point only and **must be verified against real ingestion-job memory/CPU
  consumption** before the first production deploy; SOAP response parsing and batch
  upserts are the parts of this workload most likely to need more than the Fargate
  minimum.
- **No autoscaling** in this phase beyond declaring `desired_count` as a variable capable
  of being raised manually. A minimal autoscaling policy (e.g., target-tracking on CPU)
  is deferred until there is real utilization data to size thresholds against — adding one
  now would be tuned on guesses, not evidence.

### Database

**Amazon RDS for PostgreSQL.**

- **Single-AZ** initially (no Multi-AZ) — documented explicitly as a future upgrade, not
  a permanent decision: promote to Multi-AZ once usage or criticality justifies the
  roughly-doubled instance cost Multi-AZ carries.
- Encryption at rest **enabled**.
- Automated backups **enabled**, retention **7 days** initially.
- `publicly_accessible = false` — reachable only from the private subnets (ECS tasks),
  never from the internet, consistent with ADR-002's layered model.
- **Deletion protection enabled** in production.
- Instance class, allocated storage, and PostgreSQL engine version are **Terraform
  variables**, not hardcoded — defaults must be small/inexpensive (e.g., `db.t4g.micro`
  class as a starting default, not a larger class), never a costly default chosen for
  headroom that hasn't been justified by real usage.

### Identity — Cognito

Two separate, non-overlapping contracts — **no app client is shared between them**:

**Machine-to-machine:**
- OAuth2 `client_credentials` grant.
- Confidential app clients (client secret required).
- Scopes drawn from the resource server this repo's `SecurityConfig` already enforces
  (`sipsa/ingestion.execute`, `sipsa/ingestion.cancel`, `sipsa/ingestion.read`,
  `sipsa/audit.read`).
- One app client per integration where practical, so a single integration's credential
  can be rotated or revoked without affecting others.

**Human users:**
- Authorization Code grant **with PKCE**.
- Public app client — **no client secret** (PKCE replaces the confidential-client secret
  for a flow where the client can't keep a secret safe, e.g. a browser or CLI).
- **Implicit flow is not used** (deprecated, unnecessary with PKCE available).
- Hosted UI (or an equivalent Cognito-managed login surface) — exact integration depth
  is scoped to what's actually needed when TECH-130 is implemented, not decided in full
  here.

The repository owner administers Cognito and its app clients initially (see Ownership,
above) — no separate identity team exists to hand this off to yet.

### Domain

**No custom domain and no ACM certificate exist.** The first version uses **API
Gateway's own managed endpoint** (the default `execute-api` URL). Route 53 and ACM are
**not created** in this phase. The architecture must not preclude adding a custom domain
later (API Gateway custom domain names attach without requiring a redesign of the
underlying REST/HTTP API or VPC Link).

### API Gateway and protection

**Decision: API Gateway REST API, not HTTP API.** REST API is required because this
story's protection model depends on **API keys, usage plans, per-consumer quotas, and
usage-plan-associated throttling** — REST API has full, mature support for all four; HTTP
API's support for this combination has historically been partial and should not be
assumed equivalent without re-verifying AWS's current feature matrix at implementation
time, which is exactly the risk choosing REST API now avoids. This is a resolved
decision, not deferred — see Approved Decision 11 below (the `Proposed` version of this
ADR had left it open).

**Purpose: protect against overload and abuse — not restrict legitimate consumers.**

**Responsibility split, restated precisely (do not blur this):**

```
Cognito:              identity and authorization (who is calling, what they're allowed to do)
API key + usage plan: operational identification of the consumer, throttling, and quota
                       (NOT identity, NOT authorization)
```

An API key identifies a *consumer* for metering purposes; it proves nothing about *who*
is using it — anyone holding the key can present it. Cognito remains the sole authority
answering "is this caller allowed to do this." Never describe an API key as
authentication in code, IaC, or runbook documentation.

**Usage-plan throttling is best-effort, not an absolute defense.** Rate/burst/quota
limits reduce the chance of runaway cost or accidental overload from a misbehaving
consumer; they are not a guarantee against cost spikes (a consumer can still be replaced
by many consumers each under-quota, or a determined attacker can still generate billable
requests up to the configured ceiling) and not a substitute for the account-level
throttling safety net or for WAF if that becomes necessary later. Treat these limits as
one layer, not the whole defense.

Initial values (unchanged from the `Proposed` version):

| Scope | Rate limit | Burst | Monthly quota |
|---|---|---|---|
| General, per consumer | 10 req/s | 20 | 100,000 requests |
| Ingestion-triggering endpoints (`POST /api/internal/ingestion/run`, `/cancel/{runId}`) | 1 req/s | 2 | (covered by the general quota; not separately capped) |

These map to distinct, independently-configurable API Gateway mechanisms — implementation
must not collapse them into one setting:

- **Account-level throttling** — a per-region, per-account ceiling across every API;
  a safety net, not the primary control.
- **Stage throttling** — default rate/burst for an entire deployed stage; the fallback
  for any method without a more specific override.
- **Route/method throttling** — per-method overrides (this is where the stricter
  ingestion-trigger limits above are actually enforced).
- **Usage plan** — associates one or more API keys with a rate/burst/quota triple; this
  is the mechanism that implements the "per consumer" values in the table above.
- **API key** — an identifier bound to a usage plan for metering/throttling. **Not an
  authentication mechanism** — Cognito remains the sole authority on identity and
  authorization; the API key controls consumption and operational protection only. A
  distinct API key is issued per M2M consumer where the contract allows it.

**WAF is out of scope for this first implementation.** Documented here as a future
hardening step if the endpoint becomes broadly publicly accessible and abuse patterns
beyond simple rate-limiting emerge (e.g., needing to block by IP reputation, geography, or
request-signature patterns that usage plans can't express).

### Logs

Explicit retention — **no log group is left at infinite retention**:

| Log group | Retention |
|---|---|
| Application logs (ECS/CloudWatch) | 30 days |
| API Gateway access logs | 30 days |
| Infrastructure logs (VPC flow logs, etc., if enabled) | 30 days |
| Audit logs (ingestion audit trail, if/when exported to CloudWatch) | 90 days |

**Known limitation:** this application's audit trail (`IngestionAuditController`,
persisted in PostgreSQL) is not physically separated from application logs at the
infrastructure layer today. Until a genuine separation exists (e.g., a distinct log group
or export pipeline for audit events specifically), the **more conservative 90-day
retention applies to any log stream that could contain audit-relevant content**, rather
than assuming the 30-day application-log retention is safe for all of it.

### Secrets

**AWS Secrets Manager** for:
- RDS credentials.
- Cognito M2M client secrets.
- Any other genuinely sensitive external credential — none is invented here that doesn't
  exist.

**Non-sensitive configuration** goes in environment variables or **SSM Parameter Store**
(`String`/`StringList` types), not Secrets Manager — reserving Secrets Manager for values
that are actually secret keeps cost and rotation scope meaningful.

**Never stored in:** versioned Terraform variables, committed `.tfvars` files, permanent
GitHub Secrets holding long-lived AWS access keys (GitHub Actions uses OIDC instead — see
CI/CD below), plaintext task-definition environment values, or documentation.

**Rotation:**
- RDS credential rotation via Secrets Manager's native rotation **should be evaluated**
  during TECH-132 implementation (native PostgreSQL rotation Lambda is a well-supported
  path) — not committed to here as done, since it hasn't been implemented yet.
- **Cognito M2M client-secret rotation is explicitly not automated** until there is a
  designed mechanism for how each consumer receives the new secret — automatic rotation
  without a distribution plan would silently break every M2M integration at once.

### Costs and availability

**Priority: lowest cost**, consistent with every decision above:

- One ECS task, RDS Single-AZ, one NAT Gateway, no custom domain, no WAF, no additional
  environments, no RDS Multi-AZ, no fully redundant egress path.
- The VPC still spans 2 AZs (subnet layout only) to make future high-availability
  upgrades additive rather than a redesign.

**Before the first `terraform apply` against real resources**, a cost estimate must be
produced — either via Infracost against the actual `terraform plan`, or, if Infracost
isn't wired into CI yet, a manual listing of every fixed-cost resource (NAT Gateway,
internal ALB, RDS instance, ECS Fargate baseline) for human review. **No specific dollar
figures are invented in this ADR** — none exist without real Infracost output or current
AWS pricing data for `us-east-1`.

### Terraform Toolchain

**Terraform:** `required_version = ">= 1.14.0, < 2.0.0"` in every stack; **1.15.7** pinned
concretely in CI and in the Docker commands this repository's docs use for local
validation. The floor of `1.14.0` is not arbitrary — it is the version line where
S3-native state locking (below) becomes available; there is **no deliberate support for
older Terraform clients**.

**AWS provider:** `required_version = ">= 6.0.0, < 7.0.0"`; the committed
`.terraform.lock.hcl` files currently pin **`hashicorp/aws` 6.55.0**. Adopted at TECH-137
time deliberately, since no AWS state or `apply` existed yet — the correct moment to move
onto the v6 major series rather than starting on v5 and migrating state later. The
official v6 upgrade guide documents no breaking change affecting the S3 bucket resource
family this repository currently uses, beyond a deprecated
`s3_us_east_1_regional_endpoint = "legacy"` provider setting this repository never sets.
`terraform validate` was re-confirmed clean against 6.55.0 for both existing stacks;
any future module (starting with TECH-132's network module) must use v6-compatible
argument names from the outset, not v5 patterns carried forward by habit.

**State locking: S3-native, not DynamoDB.** No DynamoDB table exists anywhere in this
repository's Terraform code. Current Terraform documentation marks the DynamoDB-lock
pattern as legacy in favor of the S3-native lockfile (`use_lockfile = true`, declared in
`environments/production/versions.tf`'s partial backend block). Provisioning a DynamoDB
table for a backend that didn't exist yet, on the strength of a mechanism already
superseded in current guidance, would have added infrastructure this repository would
need to un-provision shortly after — so TECH-137's original DynamoDB table was removed
before any stack ever depended on it, not migrated away from later.

**IaC scanning: Trivy, not tfsec.** `trivy config infra/terraform` is the sole
misconfiguration scanner. tfsec's checks have been folded into Trivy upstream; running
both would be redundant tooling for the same class of finding. Documented exceptions use
`# trivy:ignore:<AVD-ID>` comments directly above the resource block, each with an inline
rationale — never a blanket suppression file.

| Finding | Resource | Risk | Justification | Exception |
|---|---|---|---|---|
| AVD-AWS-0089 (Bucket has logging disabled) | `aws_s3_bucket.terraform_state` | Low — no access-logging trail for the state bucket | A dedicated logging bucket + lifecycle policy for a bucket only the manual, single-owner bootstrap process ever touches is complexity disproportionate to the risk at this stage | `# trivy:ignore:AVD-AWS-0089`, revisit if ownership model changes |
| AVD-AWS-0132 (Bucket does not encrypt with a customer-managed key) | `aws_s3_bucket_server_side_encryption_configuration.terraform_state` | Low — AWS-owned key instead of a customer-managed KMS key | A dedicated KMS key/policy for a bucket that only holds Terraform state, applied manually and rarely, is complexity this stage doesn't need; AES256 (AWS-owned key) still encrypts at rest | `# trivy:ignore:AVD-AWS-0132`, revisit if compliance requirements change |

Both exceptions were reassessed for this revision (not carried forward mechanically from
the removed tfsec annotations) — DynamoDB's own two prior exceptions (customer-managed
key, encryption-not-enabled) no longer apply at all, since the DynamoDB table itself no
longer exists.

### CI/CD

**GitHub Actions**, authenticating to AWS via **GitHub Actions OIDC** — no long-lived
AWS access keys stored as GitHub Secrets. Third-party actions in every workflow are
pinned by **immutable commit SHA** (never a tag, never `@main`/`@master`), each with a
comment stating the human-readable version it corresponds to — this applies regardless of
any specific incident, as a standing supply-chain practice for this repository's CI.

Four separate pipelines (not one monolithic workflow):

| Pipeline | Responsibility |
|---|---|
| `infra-plan.yml` | `terraform fmt -check`, `terraform validate`, TFLint, Trivy config scan, `terraform plan` — runs on PRs touching `infra/terraform/`, no apply |
| `infra-apply.yml` | Manual or approval-gated; uses a GitHub Environment named `production` requiring the owner's approval; applies only a previously-reviewed plan where feasible |
| `application-ci.yml` | The existing build/test pipeline (TECH-120) — unchanged by this ADR |
| `application-deploy.yml` | Builds the image, pushes to ECR, updates the ECS service, waits for stability, runs a health smoke test, fails/rolls back if the service doesn't stabilize |

**No automatic production deploy on every push to `main` without approval** — both
`infra-apply.yml` and `application-deploy.yml` are gated, not fire-and-forget.

**OIDC trust-policy contract.** `infra-plan.yml` declares `permissions: { contents: read,
id-token: write }` — never `write-all`. `id-token: write` alone authenticates nothing
without a matching IAM role trust policy on the AWS side, which does not exist yet (see
Consequences) but must, when created, require:

- `token.actions.githubusercontent.com` as the OIDC provider, with audience
  `sts.amazonaws.com` (`aud` claim).
- The `sub` claim restricted to **this exact repository**, never a wildcard such as
  `repo:dalejandrov/*:*`.

GitHub Actions' OIDC token `sub` claim has historically used the format
`repo:OWNER/REPO:ref:refs/heads/BRANCH` (for a branch-triggered run) or
`repo:OWNER/REPO:environment:NAME` (for a run gated by a GitHub Environment). For the
`production` Environment this ADR requires on `infra-apply.yml`, the expected subject is
conceptually:

```
repo:dalejandrov/sipsa:environment:production
```

GitHub has also introduced immutable repository/owner-ID-based claims as an alternative
to the name-based subject above (names can be renamed; IDs cannot). **Before creating any
real IAM role**, decode an actual OIDC token from a run in this repository (or consult
GitHub's current OIDC documentation at that time) to confirm which claim shape this
repository's tokens actually carry, and write the trust policy's `sub` condition against
that confirmed shape — not against whichever format seems more modern. Do not configure
both formats speculatively; configure the one the real token uses.

**Role separation — three distinct roles, never one administrator role:**

| Role (name only — no ARN exists yet) | Purpose | Access shape |
|---|---|---|
| `terraform-plan` | `infra-plan.yml`'s `plan` job | Primarily read-only: read access to the resources being planned, plus read/write to the Terraform state backend (`plan` needs to read current state; S3-native locking needs write access to acquire/release the lockfile) |
| `terraform-apply` | `infra-apply.yml` | Read/write on the resource types this repository's modules actually manage (S3, VPC/networking, ECS, RDS, Cognito, API Gateway, IAM for resources it creates) — scoped to those services, not `AdministratorAccess` |
| `application-deploy` | `application-deploy.yml` | ECR push, ECS service update, nothing else — no Terraform state access at all |

No ARN for any of these three roles is invented in this document — creating them is a
prerequisite for whichever of TECH-130/TECH-132 first needs a real `plan`/`apply`, and
each must be created with the trust-policy contract above, scoped to its own purpose.

### Alerts

**Amazon SNS → email** as the initial channel. **Only alerts that require human
intervention** — avoid alerting on isolated, transient events; use windows/thresholds
that suppress noise rather than raw instantaneous breaches.

Minimum alert set:
- ECS: zero healthy tasks.
- ALB: zero healthy targets.
- Application health (`/actuator/health`) reporting `DOWN`.
- Sustained elevated 5xx rate.
- RDS connection failures.
- RDS free storage running low.
- Sustained CPU > 85%.
- Sustained memory > 85%.
- Ingestion jobs failing repeatedly.
- Consecutive SOAP call failures.
- Async executor saturated / task rejections.
- Abnormal growth in `429` responses.
- An expected ingestion window that produced no run (silence, not just failure).

**Slack, Teams, and PagerDuty are explicitly not integrated initially** — a single email
channel is the full initial surface; add a chat/paging integration only once the alert
volume or on-call structure justifies it.

---

## Target Architecture (reference, unchanged from the readiness audit)

```
Cliente → API Gateway → VPC Link → ALB interno → ECS privado (Fargate) → aplicación Spring Boot
Identidad: Cliente M2M → Cognito (client_credentials) → access token → API Gateway / Spring Security
           Usuario humano → Cognito (Authorization Code + PKCE) → access token
Dependencias externas: ECS privado → NAT Gateway (único, us-east-1) → SOAP público DANE
Datos: ECS privado → RDS PostgreSQL (Single-AZ, privado, cifrado, backups 7 días)
Red: VPC nueva, 2 AZ, subnets públicas/privadas, 1 NAT Gateway (riesgo aceptado)
```

See `aws-production-readiness.md` §5 for the full ASCII diagram this summarizes.

---

## Approved Decisions (resolves every Blocking Decision from the `Proposed` version)

| # | Decision | Approved value | Owner | Unblocks |
|---|---|---|---|---|
| 1 | IaC tool + ownership | Terraform, in this repository; this team owns provisioning | Repository owner | All of TECH-130/131/132/137 |
| 2 | AWS account(s) | Single account, production only | Repository owner | All stories |
| 3 | Region | `us-east-1`, parameterized | Repository owner | VPC, Cognito, API Gateway |
| 4 | Environments | `production` only; structure supports future environments | Repository owner | State layout, naming |
| 5 | VPC | New, dedicated | Repository owner | TECH-132 |
| 6 | ECS launch type | Fargate | Repository owner | TECH-132 |
| 7 | Database | RDS for PostgreSQL, Single-AZ | Repository owner | TECH-132, data layer |
| 8 | Cognito ownership | Repository owner, initially | Repository owner | TECH-130 |
| 9 | Domain + ACM | None yet; API Gateway managed endpoint | Repository owner | TECH-131 (deferred, not blocking) |
| 10 | NAT Gateway | One initially (accepted risk); NAT per AZ deferred | Repository owner | TECH-132 (resolved, not blocking) |
| 11 | API Gateway REST vs. HTTP API | **REST API** — required for full API key/usage-plan/quota/throttling support | Repository owner | TECH-131 (resolved, not blocking) |
| 12 | Logging retention | 30 days (app/API GW/infra), 90 days (audit-adjacent) | Repository owner | TECH-131, TECH-132 |
| 13 | Secrets management | AWS Secrets Manager (sensitive) + SSM Parameter Store (non-sensitive) | Repository owner | TECH-130, TECH-132 |
| 14 | Operational ownership | Repository owner, first-line | Repository owner | Go-live readiness |

---

## Implementation Phases

Unchanged in sequence from the `Proposed` version, refined with the approved specifics
above. **Each phase is implemented as small, independently reviewable branches — no
attempt to land TECH-130/131/132 whole in a single branch.**

**Fase 0 — Bootstrap y estado Terraform.** Terraform project structure, S3 backend with
S3-native locking (no DynamoDB), provider/version constraints (Terraform `~> 1.15`, AWS
provider `~> 6.x`), common variables/tags, GitHub Actions `fmt`/`validate`/Trivy/plan-only
CI, OIDC contract preparation (trust-policy shape documented, no real role created).
**This is TECH-137**, the subject of the first implementation branch
(`infra/terraform-bootstrap`). No Cognito, ECS, ALB, RDS, API Gateway, or NAT Gateway is
created in this phase.

**Fase 1 — TECH-132 (parcial): base de red y cómputo mínimo.** VPC, 2 AZ, public/private
subnets, route tables, single NAT Gateway, S3 Gateway VPC Endpoint, VPC Flow Logs —
**done, [TECH-138](../backlog/technical-backlog.md#tech-138), 2026-07-21,
`infra/production-vpc-foundation`**. Baseline security groups, ECR repository, and a
minimal ECS cluster remain unimplemented — no ALB/service traffic exists yet, just the
network substrate.

**Fase 2 — TECH-130: Cognito.** User pool, resource server, scopes, M2M app client(s),
human-user app client (Authorization Code + PKCE), secrets storage for M2M client
secrets, manual token-issuance verification.

**Fase 3 — TECH-132 (completar): ECS, ALB, RDS.** ECS service (Fargate, `desired_count=1`)
in the Fase 1 private subnets, internal ALB (security group scoped to the future VPC
Link), RDS PostgreSQL (Single-AZ, encrypted, 7-day backups, `publicly_accessible=false`,
deletion protection), CloudWatch log groups per the retention table above, secrets wired
via Secrets Manager/SSM.

**Fase 4 — TECH-131: API Gateway y VPC Link.** API Gateway **REST API** (Approved
Decision 11), Cognito JWT authorizer wired to the Fase 2 pool, API keys + usage plans
(general and ingestion-trigger tiers from the table above), access logging, VPC Link to
the Fase 3 ALB.

**Fase 5 — CI/CD, observabilidad y E2E.** `infra-apply.yml` and `application-deploy.yml`
pipelines completed and gated; SNS email alerting wired for the minimum alert set above;
full end-to-end verification (real token issuance, 401/403/429/2xx matrix against the
deployed stack, SOAP egress through the NAT Gateway, gateway-bypass check, health/metrics
reachability).

---

## Acceptance Criteria (restated per story, unchanged from the backlog)

**TECH-130 (Cognito):** user pool created; resource server configured with the scopes
`SecurityConfig` already enforces; M2M app client(s) created per the confirmed integration
count; human-user app client created (Authorization Code + PKCE, no secret); a real token
issued and validated end-to-end (`iss`, `exp`, `token_use=access`, `client_id` allowlist,
scopes — the same checks `SipsaJwtValidatorsTest` already covers against a mock issuer);
JWKS rotation confirmed reachable; no secret committed to the repository.

**TECH-132 (private networking):** ECS service in private subnets with no public IP;
internal ALB with a security group scoped to VPC Link ENIs only; NAT egress confirmed
working for the DANE SOAP call and for Cognito JWKS HTTPS through the single NAT Gateway;
RDS reachable from the private subnet only; CloudWatch logs flowing with the approved
retention; secrets injected via Secrets Manager/SSM, not plaintext; `/actuator/health`
green from the ALB target group; **the ALB is not reachable from the public internet**.

**TECH-131 (API Gateway):** API Gateway created with the Cognito JWT authorizer wired to
the TECH-130 pool; per-consumer API keys + usage plans issued matching the approved
rate/burst/quota table; ingestion-trigger routes carry the stricter 1 req/s limit; access
logs enabled with request-ID propagation; VPC Link integration to the TECH-132 ALB
confirmed working; full 401/403/429/2xx matrix re-verified end-to-end against the real
deployed stack.

**TECH-137 (Terraform bootstrap):** see the backlog entry — structure, backend, CI
validation all in place, no real AWS apply performed.

---

## Costs and Risks

| Resource | Cost type | Notes |
|---|---|---|
| NAT Gateway (×1) | Coste fijo + coste por transferencia | Single NAT — accepted point of failure, see Network decision above |
| Internal ALB | Coste fijo + coste por uso | |
| API Gateway | Coste por uso | Bounded by usage-plan throttling |
| ECS Fargate (1 task, minimal size) | Coste por uso | Sizing must be verified against real ingestion workload |
| CloudWatch Logs | Coste por uso + coste operativo | 30/90-day retention bounds growth, does not eliminate cost |
| RDS (Single-AZ, small class) | Coste fijo + coste por uso | Multi-AZ deferred — doubling this cost is a deliberate future decision, not assumed |
| Egress traffic (DANE SOAP, JWKS) | Coste por transferencia | Bounded by ingestion frequency (daily/monthly jobs) |
| Secrets Manager | Coste fijo (por secreto) | Scoped to genuinely sensitive values only, per the Secrets decision above |

**Accepted risks** (explicit, not oversights): single NAT Gateway (no HA egress); RDS
Single-AZ (no automatic failover); `desired_count=1` ECS (no task redundancy); no WAF; no
custom domain yet; Cognito client-secret rotation not automated. Each has a documented
future-improvement path above rather than being silently deferred.

**No dollar figures are invented anywhere in this document** — an Infracost estimate or
manual fixed-cost-resource review is required before the first real `apply` (see Costs
and availability, above).

---

## Consequences

- No AWS resource, credential, or IaC file existed before this ADR's implementation
  began. **TECH-137 (bootstrap)** and **TECH-138 (VPC foundation)** are both merged and
  both create **no AWS resource requiring `apply`** — only structure, a real (but
  unapplied) network module, validation, tests, and CI.
- TECH-130 and TECH-131 remain `Pending`. **TECH-132 is `In progress`**: its network
  substrate (Fase 1) is done via TECH-138, but ECS, the internal ALB, and RDS (the rest
  of its scope) are not yet implemented — this ADR does not mark it `Done`.
- Every decision in this ADR is revisitable — in particular the single-NAT-Gateway,
  RDS-Single-AZ, and no-autoscaling choices are explicitly framed as initial,
  cost-driven trade-offs with a documented upgrade path, not permanent architectural
  commitments. TECH-138's implementation followed through on the single-NAT-Gateway and
  per-AZ route-table structure exactly as this ADR specified, including the documented
  future migration path.
