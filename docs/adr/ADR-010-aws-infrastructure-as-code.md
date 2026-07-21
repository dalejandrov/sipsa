# ADR-010 — AWS Infrastructure-as-Code Tooling

**Status:** Proposed (2026-07-21) — tool choice requires human approval; no AWS resource
exists yet and none is created by this ADR
**Date:** 2026-07-21
**Backlog:** [TECH-130](../backlog/technical-backlog.md#tech-130),
[TECH-131](../backlog/technical-backlog.md#tech-131),
[TECH-132](../backlog/technical-backlog.md#tech-132)
**Depends on:** [ADR-002](ADR-002-internal-endpoint-security.md) (Option E, layered
security model — this ADR provisions the infrastructure ADR-002 already assumes; it does
not revisit that decision), [aws-production-readiness.md](../architecture/aws-production-readiness.md)
(the audit this ADR builds on, §10 Q3 in particular)

---

## Context

`aws-production-readiness.md` (2026-07-20 audit) confirmed by grep that this repository
has **zero existing IaC tooling and zero prior tooling decision** — no `.tf`, no CDK app,
no CloudFormation template anywhere in the tree. That audit deliberately did not choose a
tool, flagging it as its own blocking question (§10 Q3), since it is a decision, not a
fact derivable from the codebase.

TECH-130, TECH-131, and TECH-132 each require provisioning real AWS resources (Cognito,
API Gateway, VPC/ECS/ALB respectively). None of the three can produce a reviewable,
repeatable infrastructure artifact — as opposed to ad hoc console clicks — until this
decision is made. This is why it is addressed here, once, rather than three times.

There is no evidence anywhere in the repository of prior team experience with Terraform,
CDK, or CloudFormation, and no stated organizational standard. That absence is itself
reported transparently below rather than assumed in either direction — the recommendation
is deliberately conservative because of it, not in spite of it.

---

## Alternatives Considered

| Criterion | Terraform | AWS CDK | CloudFormation (raw) | Externally-managed (platform/infra team) |
|---|---|---|---|---|
| Team experience (this repo) | None found | None found | None found | Unknown — depends on that team |
| Language | HCL (declarative, AWS-agnostic syntax) | TypeScript/Python/Java (imperative, generates CFN) | YAML/JSON (declarative, AWS-only) | N/A to this repo |
| Reusability across providers | High (same tool works for non-AWS resources, e.g. any future DNS/CDN outside AWS) | Low (AWS-only, though CDK for Terraform exists) | None (AWS-only) | N/A |
| Remote state | Built-in (S3 + DynamoDB lock, or Terraform Cloud) — must be set up explicitly | CDK bootstrap creates its own S3/ECR staging bucket per account/region | CloudFormation itself is the state store (no separate backend needed) | Owned by that team |
| CI/CD fit | Mature (`terraform plan`/`apply` in GitHub Actions, matches this repo's existing TECH-120 pipeline pattern) | Mature (`cdk diff`/`deploy`, same CI shape) | Mature but verbose (raw template diff is harder to review than plan output) | Depends entirely on that team's own pipeline — this repo would only consume outputs |
| Drift detection | `terraform plan` against real state | `cdk diff` against deployed stack | CloudFormation drift detection (native, but coarser-grained) | Owned by that team |
| Complexity to start (single service, 3 stories) | Low–Medium — one state file, modules optional at this scale | Medium — CDK bootstrap, construct library learning curve on top of AWS concepts | Low, but templates get verbose fast for VPC + ECS + API Gateway + Cognito together | Lowest for this repo (nothing to write) but highest coordination cost |
| Ownership fit | This team owns the code and the infra definition together, in the same repo, matching this repo's existing single-team, single-repo pattern (app + Flyway migrations + ArchUnit + CI already co-located) | Same as Terraform, but ties infra definitions to a programming language this repo doesn't otherwise use for infra (Java is used for the app, not IaC here) | Same ownership fit as Terraform, weaker ergonomics | Only fits if a platform team already exists and already owns AWS account/network boundaries — **not confirmed for this project** |

**Option D — externally-managed infrastructure** is not rejected outright; it is
**contingent on a fact this document cannot verify**: whether a platform/infra team
already owns AWS account and network provisioning for this organization. If yes, TECH-132
in particular (VPC, ECS, ALB) may partly or fully belong to that team's existing tooling,
and this ADR's recommendation would apply only to the resources this team owns directly
(Cognito app-client config, API Gateway routes). This is listed as Blocking Decision 1
below, not assumed.

---

## Decision

**Recommend Terraform**, conditional on Blocking Decision 1 (below) confirming this team
owns its own AWS provisioning rather than consuming a platform team's existing stack.

Rationale, in order of weight:

1. **No prior tooling or team experience exists in either direction** — this is explicitly
   not a case where CDK's "use the language you already know" advantage applies, since
   this repository's infra surface (VPC, ECS, ALB, API Gateway, Cognito) has no existing
   Java-specific reason to prefer CDK, and Terraform's HCL is the more widely-documented
   option for exactly this AWS resource set (VPC + ECS + ALB + API Gateway + Cognito is a
   well-trodden Terraform path with abundant reference modules).
2. **This repository already has a working, team-relevant CI pattern** (TECH-120, GitHub
   Actions) that a `terraform plan`/`apply` step fits into with no new CI platform
   decision required.
3. **Single service, three stories, one AWS account (pending Blocking Decision 2)** — this
   is a small-to-medium infra surface where Terraform's module system is enough structure
   without CDK's additional abstraction layer paying for itself yet.
4. **Explicitly not chosen for "technical preference."** If Blocking Decision 1 reveals an
   existing platform team already standardized on CDK or CloudFormation, or already owns
   the VPC/network layer, that fact overrides this recommendation — this ADR's
   recommendation is scoped to "no other constraint is known," which is the actual,
   verified state of this repository today, not a general endorsement of Terraform over
   the alternatives.

**Status stays `Proposed`, not `Accepted`**, because Blocking Decision 1 is a
organizational fact this document cannot verify from the repository alone, and because no
one with authority over AWS account/tooling standards has confirmed this choice yet.

---

## Target Architecture (reference, unchanged from the readiness audit)

```
Cliente → API Gateway → VPC Link → ALB interno → ECS privado → aplicación Spring Boot
Identidad: Cliente M2M → Cognito → access token → API Gateway / Spring Security
Dependencias externas: ECS privado → NAT Gateway → SOAP público DANE
Datos: ECS privado → PostgreSQL/RDS (RDS vs. externally-managed: open, Blocking Decision 7 below)
```

See `aws-production-readiness.md` §5 for the full ASCII diagram this summarizes.

---

## Blocking Decisions

| # | Decision | Recommendation | Responsible | Blocks | Required value |
|---|---|---|---|---|---|
| 1 | IaC tool + who owns AWS provisioning (this team vs. platform team) | Terraform, if this team owns provisioning (see Decision above) | Engineering lead / platform team (if one exists) | All of TECH-130/131/132 | Tool name + ownership boundary |
| 2 | AWS account(s) | Not determinable here | Whoever owns AWS billing/org today | All three stories | Account ID(s) per environment |
| 3 | Region | Not determinable here | Same as above | VPC, Cognito, API Gateway all region-scoped | AWS region code |
| 4 | Environments (dev/staging/prod, or fewer) | Not determinable here | Engineering lead | State layout, naming, budget | List of environment names |
| 5 | New VPC vs. existing shared VPC | Not determinable here (readiness audit Q5) | Platform/network owner | TECH-132 | New or existing VPC ID |
| 6 | ECS Fargate vs. EC2 launch type | Fargate (no instance management, matches this repo's operationally-light footprint — Docker-based dev/CI already assumes no host management) | Engineering lead | TECH-132 | Launch type |
| 7 | RDS vs. externally-managed PostgreSQL | Not determinable here (readiness audit Q7) — flagged again as still open | DB owner | TECH-132, data layer | RDS or external endpoint |
| 8 | Cognito ownership (this team vs. platform/identity team) | Not determinable here (readiness audit Q4) | Identity owner | TECH-130 | Ownership boundary |
| 9 | Domain + ACM certificate | Not determinable here (readiness audit Q10) | Domain owner | TECH-131 public endpoint, Cognito hosted UI if Q1 needs it | Domain name + cert ARN or provisioning plan |
| 10 | NAT Gateway | Required — already resolved by evidence, not open (readiness audit §7: DANE SOAP endpoint is public internet, VPC endpoints cannot reach it) | N/A | TECH-132 | N/A (confirmed, not blocking) |
| 11 | API Gateway REST vs. HTTP API | Not determinable here (readiness audit Q8 — verify current AWS feature parity for usage plans/API keys before deciding) | Engineering lead | TECH-131 | REST or HTTP |
| 12 | Logging retention (CloudWatch Logs) | Not determinable here — no compliance requirement stated anywhere in the repo | Engineering lead / compliance owner if one exists | TECH-131, TECH-132 log groups | Retention period (days) |
| 13 | Secrets management (Secrets Manager vs. Parameter Store vs. external vault) | Not determinable here (readiness audit Q6) | Engineering lead | TECH-130 (client secrets), TECH-132 (DB credentials) | Tool name |
| 14 | Operational ownership post-deploy (who is on-call, who rotates secrets/certs) | Not determinable here | Engineering lead | Go-live readiness for all three | Named owner or rotation |

---

## Implementation Phases

**Fase 0 — Decisiones.** Resolve all Blocking Decisions above: IaC tool (this ADR), AWS
account(s), region, environment list, resource naming/tagging convention, ownership
boundaries, and budget expectations. No AWS resource is created in this phase.

**Fase 1 — Base de red.** VPC (new or existing, per Blocking Decision 5), public and
private subnets, route tables, NAT Gateway (confirmed required), security groups
(baseline, refined further in Fase 3), and only the VPC endpoints actually needed (none
identified as required beyond standard AWS-service HTTPS reachability via NAT — see
readiness audit §7).

**Fase 2 — Identidad.** Cognito user pool, resource server, scopes (per the existing
Spring Security scope names already enforced in `SecurityConfig`), `client_credentials`
M2M app client(s) (count per Blocking Decision from the earlier readiness audit — how many
M2M integrations exist), secrets storage for any client secret (per Blocking Decision 13),
and a manual token-issuance verification before moving on.

**Fase 3 — Cómputo.** ECR repository, ECS cluster/task definition/service (Fargate, per
Blocking Decision 6) in the private subnets from Fase 1, internal ALB with a security
group admitting only the future VPC Link's ENIs, `/actuator/health` wired as the ALB
target-group health check, CloudWatch log group (retention per Blocking Decision 12),
secrets injected via the tool chosen in Blocking Decision 13 — never as plain task-
definition environment values.

**Fase 4 — Gateway.** API Gateway (REST or HTTP per Blocking Decision 11), Cognito JWT
authorizer wired to the Fase 2 pool, API keys and usage plans for `GET /api/sipsa/**`
consumers, throttling limits, access logging, VPC Link to the Fase 3 ALB.

**Fase 5 — E2E.** Issue a real Cognito token and confirm the full call path
(`API Gateway → VPC Link → ALB → ECS → Spring Security`); confirm 401 (no token), 403
(wrong scope), 429 (throttle exceeded), and 2xx (valid token + scope) all behave as
`InternalEndpointSecurityTest` already asserts against the mock issuer; confirm the SOAP
egress path to DANE works through the NAT Gateway; confirm `/actuator/health` and
`/actuator/metrics` are reachable operationally; confirm the backend is **not** reachable
by bypassing API Gateway (readiness audit §7's gateway-bypass requirement).

---

## Acceptance Criteria (restated per story, unchanged from the backlog)

**TECH-130 (Cognito):** user pool created; resource server configured with the scopes
`SecurityConfig` already enforces; M2M app client(s) created per the confirmed integration
count; a real token issued and validated end-to-end (`iss`, `exp`, `token_use=access`,
`client_id` allowlist, scopes — the same checks `SipsaJwtValidatorsTest` already covers
against a mock issuer); JWKS rotation confirmed reachable; no secret committed to the
repository.

**TECH-132 (private networking):** ECS service in private subnets with no public IP;
internal ALB with a security group scoped to VPC Link ENIs only; NAT egress confirmed
working for the DANE SOAP call and for Cognito JWKS HTTPS; PostgreSQL reachable from the
private subnet; CloudWatch logs flowing; secrets injected via the chosen secrets tool, not
plaintext; `/actuator/health` green from the ALB target group; **the ALB is not reachable
from the public internet** — confirmed by attempting a direct connection and observing it
fail.

**TECH-131 (API Gateway):** API Gateway created with the Cognito JWT authorizer wired to
the TECH-130 pool; API key + usage plan issued for at least one `GET /api/sipsa/**`
consumer; throttling configured and confirmed to return `429` past the limit; access logs
enabled with request-ID propagation into the application's own logs; VPC Link integration
to the TECH-132 ALB confirmed working; full 401/403/429/2xx matrix re-verified end-to-end
against the real deployed stack (not just the existing mock-issuer test suite).

---

## Costs and Risks

Costed by category, not by exact figure — no region or consumption data exists yet to
price this accurately, and inventing numbers here would be misleading rather than useful.

| Resource | Cost type | Notes |
|---|---|---|
| NAT Gateway | Coste fijo + coste por transferencia | Hourly charge regardless of traffic, plus per-GB data processing — required, not optional (§7 of the readiness audit) |
| Internal ALB | Coste fijo + coste por uso | Hourly charge plus LCU-based usage pricing |
| API Gateway | Coste por uso | Per-request pricing; usage-plan throttling limits also bound worst-case cost |
| ECS Fargate | Coste por uso | Billed per vCPU/memory-second while tasks run; scales with desired task count |
| CloudWatch Logs | Coste por uso + coste operativo | Ingestion + storage cost scales with retention (Blocking Decision 12) and log verbosity |
| RDS (if chosen over external DB) | Coste fijo + coste por uso | Instance-hour plus storage; multi-AZ (if required for HA) roughly doubles the instance cost |
| Egress traffic (DANE SOAP, JWKS) | Coste por transferencia | Bounded by ingestion frequency (daily/monthly jobs, not high-volume streaming) |
| High availability (multi-AZ NAT/ALB/RDS) | Coste fijo (multiplies the above) | Not assumed by default; a deliberate decision once environment count (Blocking Decision 4) is known |
| Duplicated environments (dev/staging/prod) | Multiplies every row above | Environment count is itself Blocking Decision 4 — cost scales roughly linearly with environment count, not sub-linearly, since NAT/ALB/ECS/RDS don't share across environments in a typical setup |

**Risks** (in addition to those already listed in `aws-production-readiness.md` §9, not
repeated here): committing to Terraform before Blocking Decision 1 is resolved could waste
setup effort if a platform team already owns provisioning; choosing environment count
before knowing the budget could over- or under-provision fixed-cost resources (NAT, ALB)
that scale per environment.

---

## Consequences

- No AWS resource, credential, or IaC file is created by this ADR — it is a decision
  record and execution plan only.
- TECH-130, TECH-131, TECH-132 remain `Pending` in the backlog; none becomes
  `In progress` as a result of this document.
- Once Blocking Decision 1 (and ideally 2–4) are answered by someone with the authority to
  answer them, this ADR's status can move to `Accepted` and a follow-up story can
  introduce the actual Terraform module skeleton (Fase 0 tail-end / Fase 1 start) as its
  own reviewable change — not bundled into this document.
