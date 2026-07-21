# AWS Production Readiness — TECH-130 / TECH-131 / TECH-132

**Version:** 1.2 (2026-07-21 — [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md) is
now **Accepted**: the repository owner resolved IaC tool, ownership, account, region,
environments, VPC, ECS launch type, database, Cognito ownership, domain, NAT Gateway,
logging retention, and secrets management. §10 below is annotated per-item with each
resolution; §9 Risks updated accordingly. Still open: exact M2M integration count/scopes
(Q2), API Gateway REST-vs-HTTP-API (Q8, deferred to TECH-131 implementation by design),
IAM/SigV4 path necessity (Q9), and CORS/end-to-end-VPC-TLS — see the annotations below,
not assumed resolved just because most of §10 now is)
**Date:** 2026-07-20
**Author:** Repository audit (branch `docs/production-aws-readiness-plan`), read-only —
no AWS resource was created, no AWS CLI command was run against a real account, no
credential was added.
**Origin:** [ADR-002](../adr/ADR-002-internal-endpoint-security.md) (Accepted, Option E).
The application layer (layer 3, defense-in-depth JWT re-validation) is already
implemented and merged; this document plans the three infrastructure layers ADR-002
deliberately left as separate stories: TECH-130 (Cognito), TECH-131 (API Gateway),
TECH-132 (private networking).
**Explicitly excluded from this document's scope:** TECH-094 (SOAP-generated-package
SPIKE), TECH-092 (SOAP relocation, blocked on TECH-094), TECH-055 (`isMonthly()` SPIKE).
None of the three are production blockers and none are touched here.

---

## 1. Current State (what already exists in this repository)

### 1.1 Application-side Resource Server — implemented and validated

`SecurityConfig` (`infrastructure/config/security/SecurityConfig.java`) is a complete
Spring OAuth 2.0 Resource Server:

- **Issuer:** `spring.security.oauth2.resourceserver.jwt.issuer-uri`
  (`SIPSA_JWT_ISSUER_URI`) — **required**, no default outside `dev`/`docker` profiles;
  `SipsaJwtProperties` fails application startup if it is missing. Not a secret — the
  issuer's JWKS is public by design.
- **Token validators, chained via `DelegatingOAuth2TokenValidator`:**
  1. `JwtValidators.createDefaultWithIssuer(issuerUri)` — Spring's standard validator:
     confirms `iss` matches the configured issuer and checks `exp`/`nbf` timing.
  2. `TokenUseValidator` (custom) — rejects any token whose `token_use` claim is not
     exactly `"access"`. Cognito ID tokens (`token_use=id`) are rejected outright, even
     though they'd otherwise pass issuer/signature checks.
  3. `AllowedClientIdsValidator` (custom, **optional** — only registered when
     `sipsa.security.jwt.allowed-client-ids`/`SIPSA_JWT_ALLOWED_CLIENT_IDS` is
     non-empty) — checks the token's `client_id` claim against a CSV allowlist.
- **`aud` is deliberately not used.** Cognito access tokens issued via
  `client_credentials` carry no `aud` claim, so the standard audience validator cannot
  pin the caller — `client_id` is the claim that identifies it instead (see
  `AllowedClientIdsValidator`'s Javadoc). This is already correctly implemented; do not
  add an `aud` check.
- **Authorization (scopes), in `SecurityConfig.securityFilterChain`:** per-operation
  `hasAuthority("SCOPE_sipsa/...")` matchers, default-deny (`anyRequest().denyAll()`).

  | Operation | Scope |
  |---|---|
  | `POST /api/internal/ingestion/run` | `sipsa/ingestion.execute` |
  | `POST /api/internal/ingestion/cancel/{runId}` | `sipsa/ingestion.cancel` |
  | `GET /api/internal/ingestion/**` | `sipsa/ingestion.read` |
  | `GET /api/internal/audit/**` | `sipsa/audit.read` |
  | `GET /api/sipsa/**` | none (public, read-only DANE data) |
  | `GET /actuator/health(/**)` | none (container/platform healthcheck) |
  | rest of `/actuator/**` | any valid access token |

- **Key rotation:** handled transparently. `NimbusJwtDecoder.withIssuerLocation(...)`
  fetches and caches the issuer's JWKS; Cognito's JWKS endpoint is the standard rotation
  mechanism and requires no application-side change. The decoder is wrapped in a
  `SupplierJwtDecoder` so the JWKS fetch happens lazily on first token validation, not at
  startup — the app and the test suite boot without network access to the issuer.
- **Validated end-to-end (2026-07-15, post PR #17 merge)** against the local mock OIDC
  server (`ghcr.io/navikt/mock-oauth2-server`, compose service `oidc`): 9/9 checks green
  — token issuance, `token_use=access`, `scope` claim, issuer coherence, `401`
  without/with an invalid token, `403` on a missing scope in both directions,
  `2xx` with the correct scope, `/actuator/health` public while other Actuator endpoints
  require a token, default-deny on undeclared routes.
- **Test coverage:** `SipsaJwtValidatorsTest` (unit, both custom validators),
  `InternalEndpointSecurityTest` (15 MVC cases, full 401/403/2xx matrix, mocked
  `JwtDecoder` — no real issuer contact in the suite).

**What is NOT yet real:** the issuer above has only ever been the local mock OIDC server
or a mocked `JwtDecoder` in tests. No Cognito user pool, resource server, or app client
exists anywhere — this repository holds zero AWS state.

### 1.2 Infrastructure as Code — absent

`grep`-confirmed: zero Terraform (`.tf`), CDK, or CloudFormation files anywhere in the
repository, and zero mentions of any of the three in any doc as a chosen tool. **No IaC
tool has been decided.** This document does not choose one (see §10, Q3) — a Terraform,
CDK, or CloudFormation snippet is not written here per your instruction, since choosing
arbitrarily would misrepresent a decision that hasn't been made.

### 1.3 Containers, ports, health checks

- `Dockerfile`: multi-stage (`eclipse-temurin:25-jdk-noble` build →
  `eclipse-temurin:25-jre-noble` runtime), non-root user (`appuser`), `EXPOSE 8080`,
  `ENTRYPOINT` with `-XX:MaxRAMPercentage=75 -XX:+UseG1GC`. `SPRING_PROFILES_ACTIVE=docker`
  is the image's baked-in default (overridable).
- `server.port` honors the `PORT` env var (`application.yaml:14`, default `8080`) — ECS
  task definitions can remap the container port without a code change, but the
  `Dockerfile`'s `EXPOSE 8080` and `docker-compose.yml`'s healthcheck both hardcode 8080;
  if `PORT` is ever overridden, both must be updated together (currently unused —
  flagging as an operational note, not a current bug).
- Health check: `curl -f http://localhost:8080/actuator/health` (docker-compose), which
  is unauthenticated (`SecurityConfig`) and has `management.endpoint.health.probes.enabled:
  true` — Spring Boot's `liveness`/`readiness` groups are already active, ready for an
  ECS/ALB target-group health check or a Kubernetes-style probe without any change.
- No `application-prod.yaml` exists. The base `application.yaml` is already
  production-safe by construction (every credential/secret-adjacent value has no
  default, fails fast if missing) — an explicit `prod` profile is not required unless a
  future need for prod-only Spring beans/config arises. `SPRING_PROFILES_ACTIVE` can
  simply be left unset (defaults to `dev`, which is unsafe outside local use — **the
  Fargate task definition should either set `SPRING_PROFILES_ACTIVE` to a real profile
  or verify the base profile alone is used with all required env vars supplied** — see
  §10, Q blocking list).

### 1.4 Environment variables and secrets, already defined

`.env.example` documents every variable and its default (versioned reference only, never
read at runtime). Relevant to TECH-130/131/132:

| Variable | Required outside dev/docker? | Secret? |
|---|---|---|
| `SIPSA_JWT_ISSUER_URI` | Yes (fails fast) | No — issuer/JWKS are public |
| `SIPSA_JWT_ALLOWED_CLIENT_IDS` | No (optional allowlist) | No — client ids are identifiers |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Yes (no default outside dev) | No |
| `DB_USERNAME`, `DB_PASSWORD` | Yes (no default outside dev) | **Yes** |
| `PORT` | No (defaults 8080) | No |

**The application itself holds no AWS credentials, no AWS SDK dependency (confirmed:
zero `software.amazon.awssdk`/`com.amazonaws` groupId anywhere in `pom.xml`), and no
Cognito/API Gateway-specific configuration beyond the standard OAuth2 Resource Server
properties above.** It does not need to — Cognito issuance and API Gateway are entirely
external to the Spring Boot process; the app only ever validates tokens it receives.

### 1.5 CI/CD

`.github/workflows/ci.yml`: build-and-test only (`./mvnw clean verify` + a Testcontainers
Flyway-gate assertion). **No deployment step, no AWS credentials, no CD pipeline exists
in this repository at all.** Provisioning ECS/deploying a new image is presently a fully
manual, undefined process.

### 1.6 CORS — not configured, not decided

`grep`-confirmed: zero CORS configuration anywhere in `SecurityConfig` or elsewhere. If
any browser-based client is expected to call `GET /api/sipsa/**` directly (not
server-to-server), CORS will need explicit configuration — either in Spring Security or,
more likely given the target architecture, at API Gateway. **Not yet decided whether any
browser client exists** (see §10, blocking questions).

### 1.7 SOAP egress dependency (relevant to TECH-132's networking design)

`SoapProperties`/`.env.example`: `SOAP_ENDPOINT` defaults to
`https://appweb.dane.gov.co/sipsaWS/SrvSipsaUpraBeanService` — a **public internet**
endpoint, not an AWS service. This is a hard networking constraint: ECS tasks in private
subnets need outbound internet egress (a NAT Gateway/instance) for ingestion to work at
all; VPC endpoints alone (which only cover AWS services) cannot satisfy this dependency.
See §7.

### 1.8 Async ingestion trigger (relevant to TECH-131's timeout design)

TECH-053 (merged) made `POST /api/internal/ingestion/run` return `202 Accepted`
immediately and run the actual ingestion asynchronously on a managed executor. This
matters for API Gateway: the request/response cycle for triggering ingestion is fast
(milliseconds), never the multi-minute SOAP call itself — so **API Gateway's ~29-second
REST API integration timeout is not at risk from this endpoint**, despite
`SOAP_READ_TIMEOUT_MS` defaulting to 3,600,000 ms (60 minutes) for the SOAP call itself.
This was verified by reading `IngestionJob`/`ScheduledIngestionDispatcher`, not assumed.

---

## 2. Classification Matrix

Legend: **A** = implementable now in code/local IaC, **B** = configurable but needs a
real environment value, **C** = requires AWS account access, **D** = requires an
architecture/security decision, **E** = already resolved.

### TECH-130 — Cognito

| Criterion | Class | Note |
|---|---|---|
| Resource Server re-validates `iss` | E | `JwtValidators.createDefaultWithIssuer` |
| Re-validates `exp` | E | Same validator (also `nbf`) |
| Re-validates `token_use=access` | E | `TokenUseValidator` |
| Client identification (not `aud`) | E | `AllowedClientIdsValidator` on `client_id`, optional |
| Per-operation scope authorization | E | `SecurityConfig`, default-deny |
| Issuer URI plumbing (env var, fail-fast) | E | `SipsaJwtProperties` |
| Client-id allowlist plumbing | E | Optional, CSV, fail-fast on malformed input |
| Key rotation | E | Cognito JWKS + `NimbusJwtDecoder`, no app change needed |
| Local/mock validation | E | 9/9 e2e, 2026-07-15 |
| Real Cognito user pool (dev + prod) | C | Needs AWS account access to create |
| `sipsa` resource server + 4 custom scopes | C | AWS action once IaC tool is chosen |
| `client_credentials` app client(s) | C + D | Needs AWS access; **how many integrations and which scopes each gets is undecided** |
| Authorization-code app client + hosted UI | C + D | **Whether human-operator login is needed at all, and its UI/domain, is undecided** |
| Document issuer URI per environment | B | Mechanical once the pool exists — no code change, just documentation |
| Client secret storage strategy | D | Backlog says "consumer's secret store" — **which store (AWS Secrets Manager, Parameter Store, consumer-owned) is undecided** |
| Test against a real Cognito token | C | Needs a real pool; the mock-OIDC e2e already proves the app-side logic |

**TECH-130 cannot be marked Done**: 3 open **C**/**D** items (app clients, hosted-UI
decision, secret storage strategy) beyond needing raw AWS access.

### TECH-131 — API Gateway

| Criterion | Class | Note |
|---|---|---|
| Actuator never routed through the gateway | E (decided) / C (to implement) | ADR-002 §5 is explicit; enforcing it is an AWS config action |
| Async trigger avoids the 29s integration timeout | E | Verified via TECH-053, see §1.8 |
| REST API vs HTTP API | D | **Undecided** — HTTP API is cheaper/simpler with native JWT authorizers; REST API has more mature native API-key/usage-plan support. Needs an explicit choice, not an assumption (AWS's HTTP API feature set for usage plans has evolved — verify current support before deciding, don't assume either way) |
| JWT authorizer wired to the TECH-130 pool | C | Depends on TECH-130 existing first |
| IAM/SigV4 authorizer for admin routes | D | Backlog phrases this as "or" — **whether this path is needed at all, alongside Cognito, is undecided** |
| API keys + usage plans (tiers, rps, quotas) | D + C | Backlog's `basic`/`partner` figures are **illustrative examples, not a decision** — real tiers need a real decision |
| Access logs (`apiKeyId`/`clientId`) | C | Format can be decided now (B), implementing requires AWS |
| CORS | D | **Completely undecided — no browser-client requirement has been established anywhere in the repo** |
| Max payload size / integration timeout | E (verified safe for the async trigger) / D (for any future large-payload endpoint) | See §1.8 |
| Cognito authorizer vs API keys vs private integration vs Spring Security — compatibility | E (no incompatibility found) | See §6 below — these are four independent, non-overlapping responsibilities already documented in ADR-002, not four mechanisms competing for the same route |

**TECH-131 cannot be marked Done**: depends on TECH-130 (C), and REST-vs-HTTP API, IAM
authorizer necessity, real usage-plan tiers, and CORS are all open **D** decisions.

### TECH-132 — Private networking

| Criterion | Class | Note |
|---|---|---|
| App's health-check endpoint is ALB-ready | E | `/actuator/health`, unauthenticated, `probes.enabled: true` |
| App accepts `DB_HOST`/`DB_PORT` pointing anywhere in-VPC | E | Already fully environment-driven |
| App logs to stdout (CloudWatch-Logs-ready) | E | Default Spring Boot behavior, no config needed |
| ECS Fargate vs ECS EC2 | D | **Undecided** — not specified anywhere in the repo |
| Private subnets, NAT Gateway for DANE SOAP egress | C + D-resolved | **Decision already made by evidence, not open**: NAT (or NAT instance) is required — VPC endpoints alone cannot reach the public DANE SOAP endpoint. See §7. |
| VPC endpoints for AWS-service-only traffic (ECR, CloudWatch Logs, Secrets Manager) | D | Optional cost/security optimization, not required — decide independently of the NAT requirement above |
| Internal ALB + target group | C | — |
| Security groups (ALB admits only VPC Link ENIs) | C + D | Design is expressible now (§7), AWS action to implement |
| VPC Link | C | Depends on the ALB and API Gateway (TECH-131) existing |
| PostgreSQL location (RDS vs self-managed) | D + C | **Explicitly undecided** — flagged in blocking questions |
| Cognito/JWKS reachability from the VPC | E (app side, standard HTTPS via NAT) | No separate VPC endpoint needed — Cognito's JWKS is public HTTPS, same NAT path as DANE egress |
| CloudWatch Logs | C | ECS task definition `awslogs` driver config; app side already correct |
| Secrets in the task definition | C + D | Same store decision as TECH-130 |
| Service discovery (Cloud Map) | D | Likely unnecessary for a single API-Gateway-fronted service — confirm, don't assume |
| Gateway-bypass prevention | D (design known) + C (implement) | Security group scoped to VPC Link ENIs only — see §7 |
| TLS between API Gateway, VPC Link, ALB, ECS | D | **Undecided** — TLS-terminated-at-gateway-only vs end-to-end TLS inside the VPC is a real security/compliance decision, not resolved by anything in this repo |

**TECH-132 cannot be marked Done**: ECS launch type, RDS decision, VPC-internal TLS
policy, and service discovery are all open **D** items, plus every **C** item needs a
real AWS account.

---

## 3. Target Architecture (textual diagram)

```
                                    Internet
                                       │
                    ┌──────────────────┴──────────────────┐
                    │                                      │
              (public data)                          (JWT-authenticated
                    │                                   operations)
                    ▼                                      ▼
        ┌───────────────────────────────────────────────────────────┐
        │                      API Gateway                          │
        │  GET /api/sipsa/**        │  /api/internal/**              │
        │  → API key required       │  → Cognito JWT authorizer      │
        │  → usage plan/throttle    │    (or IAM/SigV4 — TBD)        │
        │  → access logs            │  → access logs                 │
        │  /actuator/** — NEVER routed here (ADR-002 §5)             │
        └───────────────────────────────┬───────────────────────────┘
                                         │  VPC Link
                                         ▼
                         ┌───────────────────────────────┐
                         │     Internal ALB (private)     │
                         │  SG: admits only VPC Link ENIs │
                         └───────────────┬─────────────────┘
                                         │
                                         ▼
                         ┌───────────────────────────────┐
                         │   ECS Fargate/EC2 (TBD)        │
                         │   private subnets, no public IP│
                         │   Spring Boot Resource Server  │
                         │   (re-validates JWT — defense  │
                         │    in depth, already merged)   │
                         │   /actuator/health → ALB target│
                         │    group health check          │
                         └───┬───────────────┬────────────┘
                             │               │
                    ┌────────┘               └────────┐
                    ▼                                  ▼
          ┌──────────────────┐              ┌────────────────────┐
          │ PostgreSQL        │              │ NAT Gateway          │
          │ (RDS? — TBD)      │              │ → Cognito JWKS (HTTPS)│
          │ in-VPC            │              │ → DANE SOAP (HTTPS,   │
          └──────────────────┘              │   public internet,   │
                                             │   REQUIRED — §7)     │
                                             └────────────────────┘

  Cognito (identity):
    user pool → resource server "sipsa" → scopes
      sipsa/ingestion.execute, .cancel, .read, sipsa/audit.read
    → app clients: client_credentials (M2M, one per integration — TBD how many)
                   authorization_code (human operators — TBD if needed)
```

**Responsibility matrix** (who enforces what, why there is no duplication):

| Layer | Enforces | Does NOT enforce |
|---|---|---|
| API Gateway (API keys) | Consumer identification, quotas, throttling, revocation for `GET /api/sipsa/**` | Authentication — a key is not a credential |
| API Gateway (JWT/IAM authorizer) | First-pass token/signature check before forwarding to the VPC | Application-level scope semantics (delegated to Spring) |
| Spring Boot (Resource Server) | Full JWT re-validation + per-operation scope, independent of the gateway | Consumer quotas/throttling (gateway's job) |
| Private networking (SG + VPC Link) | The backend is unreachable except through the gateway — the enforcing control for the IAM path, which Spring cannot re-validate | Token/scope semantics |

No two layers duplicate the same responsibility; each closes a gap the others cannot
(gateway bypass → network layer; gateway misconfiguration → Spring re-validation;
IAM-signed requests, which the app cannot verify → network layer is the only control).

---

## 4. Order and Dependencies

**Recommended order — confirmed correct by the actual dependency graph, not just
restated from the prompt:**

```
1. TECH-130 (Cognito)  — nothing else depends on AWS resources yet; can start
                          immediately once the pending decisions (§10) are answered.
2. TECH-132 (private networking) — the ALB/ECS service can be stood up and health-
                          checked internally without API Gateway existing yet; only
                          the VPC Link step at the end needs TECH-131's gateway.
3. TECH-131 (API Gateway) — the JWT authorizer needs TECH-130's pool; the VPC Link
                          integration needs TECH-132's ALB. This is why it lands last.
```

This matches the order in your message. The one refinement: TECH-132's VPC/ECS/ALB
provisioning (subnets, security groups, the ECS service itself, target group, health
check) has **no hard dependency on TECH-130** and could in principle run in parallel with
it — only the final VPC Link step needs TECH-131, and TECH-131's authorizer needs
TECH-130. So the critical path is `TECH-130 → TECH-131`, with `TECH-132`'s early steps
parallelizable alongside TECH-130 and its final step gated on TECH-131 existing.

**DAG:**

```
[Pending decisions, §10]
        │
        ▼
 ┌─────────────┐        ┌──────────────────────┐
 │  IaC choice  │        │ TECH-132 early steps  │
 │ (Terraform/  │        │ (VPC, subnets, SGs,   │
 │  CDK/CFN)    │        │  NAT, ALB, ECS svc,    │
 └──────┬───────┘        │  target group, health) │
        │                └───────────┬────────────┘
        ▼                            │
 ┌─────────────┐                     │
 │  TECH-130    │                    │
 │  (Cognito)   │                    │
 └──────┬───────┘                    │
        │                            │
        └──────────┬─────────────────┘
                    ▼
           ┌─────────────────┐
           │    TECH-131      │
           │ (API Gateway,    │
           │  JWT authorizer, │
           │  VPC Link to the │
           │  TECH-132 ALB)   │
           └────────┬─────────┘
                    ▼
           ┌─────────────────┐
           │   E2E tests      │
           │ (real Cognito    │
           │  token through   │
           │  the full path)  │
           └────────┬─────────┘
                    ▼
           ┌─────────────────┐
           │     Rollout      │
           └─────────────────┘
```

---

## 5. What Can Be Implemented Locally Right Now (Category A/B, no AWS access needed)

- **Nothing on the application side needs to change** — `SecurityConfig`,
  `SipsaJwtProperties`, `TokenUseValidator`, `AllowedClientIdsValidator` are already
  correct for a real Cognito issuer; swapping `SIPSA_JWT_ISSUER_URI` from the mock OIDC
  URL to a real Cognito user pool URL (`https://cognito-idp.<region>.amazonaws.com/<userPoolId>`)
  requires zero code changes, confirmed by reading the code, not assumed.
- Documenting the per-environment issuer URI format (once a pool exists) — B, mechanical.
- Deciding and documenting the CORS policy (D → once decided, implementable without AWS,
  either in `SecurityConfig` or as an API Gateway config value).
- Deciding IaC tooling (D, blocking — see §10 Q3) so that TECH-130/131/132 can actually
  begin producing artifacts.
- Deciding the ECS launch type, RDS-vs-self-managed, secret-storage tool, and
  VPC-internal TLS policy (all D, blocking — see §10) — these are pure decisions, not AWS
  actions, and can be resolved without touching an AWS account.

## 6. What Requires Real AWS Access (Category C)

Every item marked **C** in §2 — creating the user pool, resource server, scopes, app
clients; creating the VPC, subnets, NAT Gateway, ALB, target group, security groups, ECS
cluster/service, VPC Link; creating the API Gateway, authorizers, API keys, usage plans,
access log configuration. None of this was performed, attempted, or simulated in this
audit — no AWS CLI command was run against a real account, and no credential exists in
this repository or this session.

---

## 7. Gateway-Bypass Prevention (TECH-132's explicit requirement)

The backend must not be reachable by a client that skips API Gateway entirely. The
mechanism, expressed in AWS-agnostic terms so it applies regardless of the eventual IaC
tool:

1. ECS tasks run in **private subnets with no public IP** — there is no address for an
   external client to reach directly.
2. The **internal ALB** is also only reachable from within the VPC (no public-facing
   load balancer, no Elastic IP).
3. The ALB's **security group admits inbound traffic only from the VPC Link's ENIs**
   (their security group or CIDR range) — not from `0.0.0.0/0`, not from the whole VPC
   CIDR. This is the actual gateway-bypass control: even another workload inside the same
   VPC cannot reach the ALB unless it is, specifically, the VPC Link.
4. **API Gateway reaches the ALB exclusively via the VPC Link** — there is no other route
   from API Gateway to the private subnets.
5. `/actuator/health` is reachable by the ALB target group health check (same VPC,
   already permitted by the SG rule above) — no separate exception needed.

**NAT Gateway is required, not optional**, specifically because
`SoapStreamingClient`/`SoapGatewayImpl` calls a public internet endpoint
(`appweb.dane.gov.co`) — this is the one deliberate, necessary egress path out of the
private subnets. VPC endpoints (which only cover AWS services — Cognito JWKS, ECR,
CloudWatch Logs, Secrets Manager, RDS if used) can reduce NAT data-transfer costs for
AWS-service traffic but cannot replace NAT for the DANE call.

**TLS inside the VPC** (API Gateway → VPC Link → ALB → ECS) is listed as an open
decision (§10) rather than assumed either way — AWS traffic between these components can
run over TLS end-to-end or terminate at the gateway with plain HTTP inside the VPC
boundary; which is required depends on a compliance/risk decision this document does not
make unilaterally.

---

## 8. Rollback

Since no AWS resource exists yet, "rollback" here means: how to abandon a partially-
provisioned environment without leaving orphaned billable resources or a half-migrated
security posture.

- **TECH-130 rollback:** delete the Cognito user pool (dev first, always) — no
  application code depends on a specific pool existing; the app simply fails fast if
  `SIPSA_JWT_ISSUER_URI` is unset or unreachable, which is the existing, tested,
  safe failure mode (confirmed by `SipsaJwtProperties`).
- **TECH-131 rollback:** API Gateway can be deleted/disabled independently; the ECS
  service (TECH-132) has no dependency on it existing (only the VPC Link step does) — the
  backend keeps running privately, just without a public entry point, which is a safe
  (if non-functional-for-clients) state.
  **rollback:** stop and delete the ECS service. If a `main` deploy needs to be reverted,
  the existing pattern (this repo's `git merge --no-ff` direct-to-`main` workflow, no
  squash) already gives a clean revert point per story — no new process is needed for
  that part.
- **No database rollback risk:** RDS-vs-self-managed is undecided, but either way the
  existing Flyway gate (ADR-009) and this session's `V1`–`V4` migrations are completely
  unaffected by any of TECH-130/131/132 — none of this AWS work touches schema.

---

## 9. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| IaC tool chosen late, blocking all three stories | Resolved | Q3 (§10) resolved by ADR-010 (Accepted, 2026-07-21) — Terraform, this repository |
| Single NAT Gateway is a point of failure for all outbound egress | Medium (accepted) | Deliberate cost trade-off (ADR-010) — documented upgrade path to one NAT per AZ once justified |
| RDS Single-AZ has no automatic failover | Medium (accepted) | Deliberate cost trade-off (ADR-010) — Multi-AZ is a documented future upgrade, not assumed permanent |
| `desired_count=1` ECS gives no task redundancy | Medium (accepted) | Deliberate cost trade-off (ADR-010) — revisit once real traffic/criticality data exists |
| `SPRING_PROFILES_ACTIVE` left unset/wrong in the ECS task definition | Medium | Base `application.yaml` already fails fast on missing secrets regardless of profile — the failure mode is safe, but should be verified explicitly in the first real deploy, not assumed |
| NAT Gateway omitted, ingestion silently fails | High | §7 makes this explicit and evidence-based, not a footnote |
| API keys mistaken for authentication by a future contributor | Medium | Already documented repeatedly (ADR-002, `SecurityConfig` Javadoc) — carry the same language into any IaC/runbook documentation |
| REST vs HTTP API chosen without checking current AWS feature parity for usage plans | Medium | Verify AWS's current documentation at decision time — this audit deliberately does not assume either way, since the feature landscape changes over time |
| CORS decided implicitly by whichever engineer implements TECH-131 first | Low–Medium | Flagged explicitly in §10 as a blocking question, not left to be improvised |
| Secrets management tool decided ad hoc per story (130 vs 132) | Medium | Same decision affects both stories — resolve once (Q6, §10), not twice |

---

## 10. Pending / Blocking Decisions

These do not block *this audit* — everything determinable from the repository has been
determined above — but TECH-130/131/132 cannot be implemented, and should not be marked
`Done`, until they are answered.

1. **¿La autenticación de integraciones máquina-a-máquina será exclusivamente
   `client_credentials`, o también se necesita un flujo `authorization_code` con hosted
   UI para operadores humanos?** — **Resuelto por [ADR-010](../adr/ADR-010-aws-infrastructure-as-code.md)
   (Accepted, 2026-07-21): ambos.** Dos contratos separados, sin app client compartido:
   M2M vía `client_credentials` (app client confidencial, con secreto), y usuarios humanos
   vía Authorization Code + PKCE (app client público, sin secreto, sin implicit flow).
2. **¿Cuántas integraciones M2M distintas existen hoy o están planeadas, y qué scopes
   necesita cada una?** — **Aún abierto.** ADR-010 aprueba "un app client por integración
   cuando sea posible" como principio, pero el conteo real y sus scopes específicos se
   determinan al implementar TECH-130, no aquí.
3. **¿Se usará Terraform, CDK, CloudFormation, o infraestructura administrada por otro
   equipo/repositorio?** — **Resuelto por ADR-010 (Accepted, 2026-07-21): Terraform, en
   este repositorio** (`infra/terraform/`), con el propietario del repositorio como dueño
   del aprovisionamiento AWS.
4. **¿Quién administra Cognito y el dominio hosted UI (si aplica) — este equipo o un
   equipo de plataforma/identidad centralizado?** — **Resuelto: el propietario de este
   repositorio, inicialmente** (ADR-010).
5. **¿Existe ya una VPC objetivo (compartida con otros servicios) o se provisiona una
   nueva exclusiva para SIPSA?** — **Resuelto: VPC nueva y dedicada, 2 AZ** (ADR-010; la
   cuenta AWS es nueva, no hay nada que compartir).
6. **¿Dónde se almacenarán los secretos (`DB_PASSWORD`, cualquier client secret de
   Cognito): AWS Secrets Manager, Parameter Store (SecureString), o un vault externo ya
   adoptado por la organización?** — **Resuelto: AWS Secrets Manager** para credenciales
   RDS y client secrets de Cognito; **SSM Parameter Store** o variables de entorno para
   configuración no sensible (ADR-010).
7. **¿La base de datos PostgreSQL correrá en RDS, o es autoadministrada (EC2/otro)?** —
   **Resuelto: Amazon RDS for PostgreSQL, Single-AZ inicialmente** (Multi-AZ documentado
   como mejora futura, no implementada ahora) (ADR-010).
8. **¿API Gateway será REST API o HTTP API?** — **Deliberadamente diferido a la
   implementación de TECH-131**, no resuelto aquí ni en ADR-010: verificar la paridad de
   características actual de AWS para usage plans/API keys en el momento de implementar,
   ya que el panorama de features cambia con el tiempo.
9. **¿Se requiere el path IAM/SigV4 para automatización AWS-nativa, o Cognito
   `client_credentials` cubre todos los consumidores conocidos hoy?** — **Aún abierto.**
   No mencionado en las decisiones aprobadas; se resuelve al implementar TECH-131 si surge
   un consumidor AWS-nativo real.
10. **¿Existe ya un dominio y certificado ACM para el endpoint público de API Gateway, o
    debe provisionarse?** — **Resuelto para la primera versión: no existe dominio propio
    ni ACM; se usa el endpoint administrado por defecto de API Gateway** (`execute-api`).
    Route 53/ACM no se crean en esta fase; la arquitectura permite agregarlos después
    (ADR-010).

Additional, previously non-numbered:
- **CORS** (si algún cliente basado en navegador llamará `GET /api/sipsa/**`
  directamente): **aún abierto**, no mencionado en las decisiones aprobadas.
- **TLS end-to-end dentro de la VPC**: **aún abierto**, no mencionado en las decisiones
  aprobadas.
- **ECS Fargate vs EC2**: **Resuelto: Fargate** (ADR-010).
- **Throttling/quota tiers reales de TECH-131**: **Resuelto**, valores iniciales
  aprobados — 10 req/s / burst 20 / 100,000 req mensuales por consumidor (general);
  1 req/s / burst 2 para endpoints que disparan ingestiones (ADR-010). Estos reemplazan
  los valores `basic`/`partner` ilustrativos previos.

---

## 11. Acceptance Criteria (restated, unchanged from the backlog — not loosened or tightened here)

See `docs/backlog/technical-backlog.md` for the authoritative acceptance criteria per
story (TECH-130 §2612, TECH-131 §2644, TECH-132 §2677 as of this audit) — this document
does not redefine them, only maps what's needed to satisfy them.

## 12. Tests

- **Already exist and require no change:** `SipsaJwtValidatorsTest`,
  `InternalEndpointSecurityTest` (full 401/403/2xx matrix against a mocked issuer) — both
  remain the regression gate for the application-side logic regardless of which real
  issuer is eventually configured.
- **Still needed, gated on AWS access (Category C, cannot be written yet):** an
  end-to-end test obtaining a real `client_credentials` token from a real (dev) Cognito
  pool and exercising the deployed backend through the full API Gateway → VPC Link → ALB
  → ECS path — this is TECH-130/131's own acceptance criteria, not a new test suite
  requirement invented here.
