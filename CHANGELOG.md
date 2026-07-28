# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [Unreleased]

### Added

- **Production VPC foundation defined as Terraform code** (TECH-138, ADR-010 Fase 1 —
  `infra/terraform/modules/network/`). VPC `10.40.0.0/16` across 2 deterministically
  selected AZs; 2 public + 2 private-application + 2 private-database subnets; a shared
  public route table and per-AZ private-application/database route tables; a single NAT
  Gateway (accepted cost/availability trade-off, documented migration path to one NAT per
  AZ); an S3 Gateway VPC Endpoint (no hourly cost, keeps S3/future-ECR traffic off the
  NAT); configurable VPC Flow Logs (`REJECT` traffic, 30-day retention by default, never
  infinite). No security group is created — those are added alongside the resources that
  consume them, in later stories. `environments/production` now consumes this module.
  Verified with 16 `terraform test` cases against a fully mocked AWS provider — **no real
  AWS account is contacted by the tests, and no `terraform apply` has been run against any
  account.** TECH-132 (private networking) moves to `In progress`: its network substrate
  is done, ECS/ALB/RDS are not.

- **Production RDS PostgreSQL foundation defined as Terraform code** (TECH-139, ADR-010,
  the RDS portion of TECH-132's Fase 3 — `infra/terraform/modules/database/`). DB subnet
  group using TECH-138's two private database subnets exclusively; a security group with
  **no ingress rule yet** (a future ECS security group is added via
  `allowed_security_group_ids` once TECH-132's compute phase creates one — never a CIDR
  rule); a parameter group (`log_connections`/`log_disconnections` only —
  `rds.force_ssl` deliberately not set, since the application's JDBC URL specifies no
  `sslmode` today and forcing it blind risks breaking the future connection outright);
  RDS PostgreSQL, engine version `"18"` (matching this repository's own
  `postgres:18.0-alpine3.22` Testcontainers/docker-compose usage — not yet verified
  against a live AWS account), Single-AZ (a documented cost trade-off, one-line path to
  Multi-AZ later), `gp3`/20 GiB storage (both `instance_class` and
  `postgres_engine_version` are explicitly flagged as proposals needing AWS-side
  availability validation before the first real apply), encrypted, `publicly_accessible
  = false`, 7-day backups, deletion protection, a reproducible-but-always-unique final
  snapshot identifier. Master password is **RDS-managed via Secrets Manager**
  (`manage_master_user_password = true`) — Terraform never sees, stores, or versions it;
  no `password` variable exists anywhere in the module. Backup/maintenance windows
  (`06:00-06:30`/`sun:07:00-sun:08:00` UTC) are explicitly converted from the
  application's `America/Bogota` ingestion schedule to fall outside it. Performance
  Insights and Enhanced Monitoring both disabled by default (cost, no established need
  yet). Verified with 20 `terraform test` cases against a fully mocked AWS provider —
  **no real AWS account is contacted, and no `terraform apply` has been run.** TECH-132
  remains `In progress`: its network and database substrate are both done now, ECS/ALB
  are not.

- **Production ECR and ECS task foundation defined as Terraform code** (TECH-140,
  ADR-010, the ECR/cluster/task-definition portion of TECH-132's Fase 3 —
  `infra/terraform/modules/ecr/` + `infra/terraform/modules/ecs-task/`). ECR: immutable
  tags, scan-on-push, AES256 encryption by default, a two-rule lifecycle policy (untagged
  images expire after 7 days; tagged images capped at the last 20 — a count, not a time
  window, so the currently-deployed image is never deleted out from under a long release
  cycle). ECS: a Fargate-only cluster (no EC2, no `FARGATE_SPOT` — a Spot interruption
  mid-ingestion is a real risk for this single production task) with Container Insights
  on by default; a task definition (`awsvpc`, CPU/memory at 256/512 — explicitly flagged
  as proposals requiring validation against real ingestion load, GC behavior, and OOM risk
  before any real deployment — `X86_64` architecture, since this repository's CI has no
  multi-arch build pipeline or ARM64 compatibility evidence yet) with a single essential
  container, port 8080 (confirmed from `application.yaml`/`Dockerfile`, not assumed),
  `readonlyRootFilesystem = true` with a `/tmp` tmpfs mount, no privileged mode, no extra
  Linux capabilities. A separate execution role (ECR pull + Logs write + scoped secret
  read — never `Resource = "*"`, never `AdministratorAccess`) and task role (empty
  permissions — the application calls no AWS API directly today). Database credentials
  resolve via the task definition's `secrets` block from RDS's master secret — **an
  explicitly temporary wiring**, not the final design; no application-specific,
  minimum-privilege database user is created by this story. No ECS Service, no ALB, no
  image is ever pushed. Verified with 26 `terraform test` cases (9 + 17) against a fully
  mocked AWS provider — **no real AWS account is contacted, and no `terraform apply` has
  been run.** TECH-132 remains `In progress`: its network, database, and ECS-task
  substrate are all done now; the ECS Service and internal ALB are not.

- **Internal ALB and ECS Service foundation defined as Terraform code** (TECH-141,
  ADR-010, the remainder of TECH-132's compute/network scope —
  `infra/terraform/modules/ecs-service/`). Three security-group relationships: the ALB
  security group with **no ingress rule by default** (populated only once TECH-131's VPC
  Link security group exists; a documented CIDR fallback rejects `0.0.0.0/0` outright by
  variable validation); the ECS service security group admitting ingress only from the
  ALB on port 8080, with egress scoped to RDS (5432), HTTPS (443, necessary for the
  public DANE SOAP endpoint), and DNS (53, scoped to the VPC CIDR); and the actual
  ECS→RDS ingress rule, added here since `modules/database`'s security group carries
  none of its own. An internal ALB (`internal = true`, private-application subnets, HTTP
  listener only — no ACM certificate exists, and the ALB is unreachable outside the VPC
  regardless of listener protocol) with deletion protection on by default, invalid-header
  dropping, defensive desync mitigation, and access logs off by default (no S3 bucket
  exists for them). A target group (`target_type = ip`) whose health check
  (`/actuator/health`, matcher `200`) was confirmed safe to call unauthenticated by
  reading `SecurityConfig` and `application.yaml`'s `show-details: when-authorized`, not
  invented. An ECS Service (`desired_count = 1`, Fargate, deployment circuit breaker with
  rollback, `health_check_grace_period_seconds = 120` — an unmeasured, conservative
  proposal) that reuses the existing task definition by ARN rather than rebuilding it.
  Two explicitly documented, unresolved risks carried forward, not fixed by this story:
  a rolling deployment can briefly run two tasks that could both attempt Flyway migration
  (mitigated by Flyway's own database-level lock, not verified empirically here), and the
  application's in-process ingestion scheduler is not safe for more than one replica
  (`desired_count` must stay at 1 until leader election, an external scheduler, or a
  distributed lock exists) — autoscaling is therefore not implemented at all. Two Trivy
  CRITICAL findings addressed with individually-justified exceptions (an HTTP-only
  listener on an ALB with zero internet-facing exposure by construction; a single-port
  HTTPS egress rule to `0.0.0.0/0` that is unavoidable because the destination, DANE's
  SOAP endpoint, is a public third-party URL, not an AWS resource with a scopable range).
  Verified with 17 `terraform test` cases against a fully mocked AWS provider — **no real
  AWS account is contacted, and no `terraform apply` has been run.** TECH-132 remains
  `In progress`: its network, database, ECS-task, and internal-service substrate are all
  done now; API Gateway and VPC Link (TECH-131) are not.

- **Cognito authentication foundation defined as Terraform code** (TECH-130, ADR-002
  layer 2 / ADR-010 — `infra/terraform/modules/cognito/`). A user pool with email as the
  sole sign-in identifier, a 12-character-minimum password policy, `MFA=OPTIONAL` (no
  operational MFA enrollment/recovery flow exists yet — forcing it now would create an
  operational dead end), `advanced_security_mode=AUDIT` (risk-based visibility without
  blocking sign-in, a deliberate middle ground given the real per-MAU cost of any non-OFF
  mode), `deletion_protection=ACTIVE`, admin-only account creation, and account recovery
  via verified email only. A resource server (`sipsa`) declaring exactly the four scopes
  `SecurityConfig` already enforces (`ingestion.execute`, `ingestion.cancel`,
  `ingestion.read`, `audit.read`), inventoried by grepping the real source, not invented.
  Two independent, never-shared app clients: a confidential M2M client
  (`client_credentials` grant only, with a secret) and a public human client
  (Authorization Code grant only — Cognito enforces PKCE automatically for any public
  client on this grant, with no separate Terraform argument for it — no secret, no
  implicit flow). `human_callback_urls`/`human_logout_urls` are required variables with no
  default and reject any `localhost`/`example.com` value, since no real frontend or
  approved callback URL exists yet. A Cognito-managed Hosted UI domain is supported but
  disabled by default (`create_hosted_ui_domain=false`) — enabling it later is a
  one-variable change, not a restructuring. Cognito generates the M2M client's secret;
  Terraform reads it back as a computed, schema-sensitive attribute during creation and
  copies it into a dedicated Secrets Manager secret, never exposed as a Terraform output
  (only its ARN is) — but Terraform state itself, for both this module and the root stack,
  retains the raw value regardless (documentation corrected 2026-07-22: earlier wording
  incorrectly implied Secrets Manager removes the value from state; it does not — state
  always stores the full attribute, `sensitive` only redacts CLI/log output). The module's
  README documents the real control boundary instead: the state backend's own encryption,
  public-access block, and locking (`infra/terraform/bootstrap/main.tf`), plus IAM role
  separation across `terraform-plan`/`terraform-apply`/`application-deploy` (ADR-010). Also
  documented, verified against the real provider behavior, not assumed: unlike an IAM
  access key, a Cognito app client secret *is* retrievable again after creation via the
  Cognito API, independent of anything this module does. Both app client
  IDs (identifiers, not secrets) are optionally published to an SSM Parameter Store
  `String` parameter for a future `SIPSA_JWT_ALLOWED_CLIENT_IDS` wiring into
  `modules/ecs-task` — publishing only, not connected to that module by this story.
  Reviewed against `SecurityConfig`/`SipsaJwtProperties`/`TokenUseValidator`/
  `AllowedClientIdsValidator` (already implemented, already e2e-validated against a mock
  OIDC issuer): no incompatibility found, no Spring Security change made. One Trivy LOW
  finding (Secrets Manager secret using the AWS-owned default key) addressed with a
  documented exception, consistent with the same posture already applied to this
  repository's other Secrets-Manager-adjacent resources. Verified with 21 `terraform test`
  cases against a fully mocked AWS provider — **no real AWS account is contacted, no
  token is ever requested, no Hosted UI is ever reached, no Cognito user is ever created,
  and no `terraform apply` has been run.** TECH-130 is Done as Terraform foundation only —
  no AWS resources have been applied yet; TECH-132 remains `In progress`, unaffected by
  this story (Cognito is not VPC-scoped).

- **Cognito configuration wired into the ECS task definition** (TECH-142, closing
  TECH-130's own documented follow-up). `SIPSA_JWT_ISSUER_URI` (plain env var) and
  `SIPSA_JWT_ALLOWED_CLIENT_IDS` (an SSM-sourced `secrets` entry, never plaintext) now flow
  from `module.cognito`'s `issuer_url`/`allowed_client_ids_parameter_arn` outputs into
  `module.ecs_task`, via two new generic `modules/ecs-task` variables
  (`environment_variables`/`secret_parameters`) — the module still has no direct dependency
  on `modules/cognito`, keeping it reusable; only `environments/production/main.tf` connects
  the two. The execution role's IAM grant uses `modules/ecs-task`'s existing
  `execution_ssm_parameter_arns` extension point (added in TECH-140, which explicitly
  anticipated "a future Cognito client secret once TECH-130 exists") — scoped to exactly
  the one SSM parameter ARN, never a wildcard, no KMS permission added (the parameter is
  `String`, not `SecureString`), task role untouched. The `docker` Spring profile the ECS
  task already used (TECH-140's default) was audited directly against
  `application-docker.yaml`/`application-dev.yaml`/`application.yaml` and confirmed already
  safe for AWS (no mock OIDC, no relaxed logging/actuator exposure — those live exclusively
  in the `dev` profile) — no new profile was created. Both Cognito app clients remain in
  the allowlist: neither the application's per-operation scope authorization nor TECH-130's
  own scope table distinguishes M2M from human callers. Adds
  `CognitoJwtDecoderContractTest` — 6 new Java tests exercising
  `SecurityConfig.jwtDecoder()` end to end against locally-signed, Cognito-shaped JWTs
  (issuer discovery, JWKS signature verification, `token_use`, and the `client_id`
  allowlist together, via a loopback JDK `HttpServer`, no new test dependency) — closing a
  real coverage gap neither the existing hand-built-`Jwt` unit tests nor the
  mocked-`JwtDecoder` MockMvc tests exercised. No incompatibility found; no change to
  `SecurityConfig`/`TokenUseValidator`/`AllowedClientIdsValidator`/`SipsaJwtProperties`.
  Adds this repository's first root-level `terraform test` suite
  (`environments/production/tests/production.tftest.hcl`), stubbing every module except
  `ecs_task` via `override_module` to isolate the wiring itself from each module's
  already-tested internals. 108 `terraform test` cases across the tree (was 102), 338 Java
  tests total (was 332), all green — **no real AWS account is contacted anywhere, and no
  `terraform apply` has been run.** TECH-130's status line now reads "Terraform foundation
  and ECS configuration wiring complete." TECH-132 remains `In progress`; TECH-131 remains
  `Pending`.

- **API Gateway REST API foundation defined as Terraform code** (TECH-131, ADR-002
  layer 1 / ADR-010 Fase 4 — `infra/terraform/modules/api-gateway/`). **Architecture
  correction, not merely an implementation detail**: `aws_api_gateway_vpc_link` (classic
  REST API) accepts only a Network Load Balancer target, never an Application Load
  Balancer directly — confirmed against the provider's own resource docs, not assumed;
  ADR-010's original "VPC Link → ALB" diagram oversimplified this. The real,
  implemented topology chains a purpose-built NLB (no security group of its own — AWS's
  docs don't confirm source-IP preservation for `alb`-type NLB targets) to the existing
  internal ALB (TECH-141) via AWS's documented "Application Load Balancer as a target of
  a Network Load Balancer" pattern (`aws_lb_target_group` with `target_type = "alb"` +
  `aws_lb_target_group_attachment`) — the ALB's own ingress now comes from
  `alb_allowed_ingress_cidr_blocks` (TECH-141's own documented fallback, populated with
  this stack's private-application subnet CIDRs), never a security-group reference. A
  Cognito authorizer (`COGNITO_USER_POOLS`) wired to TECH-130's user pool, with an exact,
  distinct `authorization_scopes` on each of the ten real `/api/internal/**` leaf routes
  (inventoried by grep against `SecurityConfig`'s own matchers — never a shared, looser
  set); `GET /api/sipsa/**` collapsed into a single `{proxy+}` resource requiring an API
  key instead (Spring's own routing already owns that surface). One parameterizable API
  key (AWS-generated value, schema-marked sensitive by the provider, never read or
  output — same posture as TECH-130's Cognito client secret) on a usage plan carrying
  ADR-010's approved general tier (10 req/s / burst 20 / 100k requests per month); the
  two ingestion-trigger routes (`POST run`, `POST cancel/{runId}`) carry the stricter 1
  req/s / burst 2 tier via a per-route `aws_api_gateway_method_settings` override.
  Structured JSON access logs (30-day retention) that never include the Authorization
  header, an API key value, or request/response bodies (`data_trace_enabled = false`
  everywhere); an account-level CloudWatch IAM role, a real, well-known operational
  prerequisite for that logging to deliver anything at all. Four
  `aws_api_gateway_gateway_response` resources (401/403/429/5xx) with a small, consistent
  JSON body distinct from the application's own `GlobalExceptionHandler.ErrorResponse`
  shape — replicating that shape at the gateway would diverge the moment either side
  changes independently, and the gateway can't populate the application's own
  `requestId`/`instance` for a request that never reached the backend. A `cors_allowed_origins`
  variable exists, defaults to empty (disabled — still no browser-client requirement
  confirmed anywhere in this repository), validated to reject a wildcard combined with
  credentials and capped at one origin (API Gateway's native, non-Lambda response headers
  can't dynamically echo the caller's `Origin` across multiple allowed origins). No
  custom domain, ACM certificate, Route 53 record, or WAF. Verified with 21 `terraform
  test` cases against a fully mocked AWS provider (129 across the tree, including two new
  root-wiring assertions in `environments/production/tests/production.tftest.hcl`) —
  **no real AWS account is contacted, no VPC Link is ever reachable, no token is ever
  requested, no API key is ever retrieved, and no `terraform apply` has been run.**
  TECH-131 is Done as Terraform foundation only — no AWS resources have been applied yet;
  TECH-132's status line now reflects this piece landing, but TECH-132 itself remains
  `In progress` (real AWS provisioning across the whole stack is still pending).

- **Deployment configuration hardened from local preflight evidence, kept explicitly
  separate from the blocked AWS validation it was attempted alongside** (TECH-144,
  extracted from TECH-143). A deployment preflight was attempted
  (`infra/production-deployment-preflight`, TECH-143) but found no SIPSA-specific AWS
  credentials in this environment — only two unrelated profiles with permanent access
  keys, neither used, no AWS command ever run with either. Every AWS-touching check
  (RDS engine availability, backend bootstrap plan, OIDC trust-policy inspection, a
  real `terraform plan`, cost estimation) remains **blocked**, kept on TECH-143's own
  branch as evidence, **not merged into `main`**. What was verifiable entirely without
  AWS access was extracted into this story and merged: the Cognito human app client is
  now gated behind a new `enable_human_client` variable (default `false`) —
  `aws_cognito_user_pool_client.human` only exists when `true`, the M2M client and
  resource server are entirely unaffected, and enabling it with empty callback/logout
  URLs is rejected outright (2 new tests, 25 total in `modules/cognito`). ECS task
  memory moved from an unmeasured 512 MiB to 1024 MiB — not just because 512 MiB showed
  89.73% memory utilization (10.27% free) at idle in three local Docker runs of the
  real application image, but because 1024 MiB was **re-verified with three more real
  runs specifically before merging**: peak usage stayed at 44.89%-55.25% utilization,
  no OOM, exit code 0 in all three. `health_check_grace_period_seconds` moved from an
  unmeasured 120 to 480, based on **six** real local startup samples (three at each
  memory size, all under 0.25 vCPU) — 187s/188s/207s/214s/221s/385s. The 385s sample
  was kept and explained, not quietly dropped as a convenient outlier: Spring Boot's
  own internal "Started SipsaApplication" log for that exact run reported ~192s,
  closely consistent with the other five samples, so the extra ~193s gap before the
  host's `curl`-based probe succeeded is most plausibly local Docker Desktop
  network/host contention from the heavy concurrent Docker usage during this
  measurement session — not confirmed, not assumed away either; 480 gives ~95s margin
  over the true worst observed sample, not a favorable subset. A new, hardened,
  reusable measurement script (`scripts/measure-container-startup.sh`) backs this:
  no credentials, no AWS dependency, `set -euo pipefail`, cleans up containers and the
  compose stack on exit (including on error), configurable CPU/memory, a per-sample
  timeout, and — unlike its first draft — **exits non-zero if any sample never reaches
  `/actuator/health` 200**, never treating a fixed sleep as a success signal. An
  application database credential strategy (`sipsa_migration` for Flyway DDL,
  `sipsa_runtime` for DML only, replacing the RDS master secret currently wired
  temporarily into the ECS task) is designed with exact `GRANT` statements in a tracked,
  never-executed reference script — confirmed to grant neither role `SUPERUSER`,
  `CREATEROLE`, or `CREATEDB`, and documented as deliberately non-idempotent on
  `CREATE ROLE` (PostgreSQL has no `IF NOT EXISTS` for it) so a mistaken re-run fails
  loudly instead of silently masking an error. The Flyway rolling-deployment risk got a
  chosen direction (a one-off migration task before service rollout) with an explicit
  four-part follow-up (task definition, pipeline step, failure handling, rollback
  asymmetry — a completed migration does not auto-revert if the subsequent service
  deployment fails); the scheduler's multi-replica risk keeps its four documented,
  undecided options. **Nothing in this story claims to have validated RDS availability,
  the backend, OIDC, a real plan, real costs, real callback URLs, or a real Cognito
  endpoint** — those remain exclusively TECH-143's, unresolved. 131 `terraform test`
  cases across the tree, all green — **no real AWS account is contacted anywhere, no
  AWS credential was used or stored, and no `terraform apply` has been run.** TECH-144
  is Done; TECH-143 remains `Blocked / In progress`, kept on its own branch, not merged;
  TECH-132 stays `In progress`.

### Changed

- **TECH-137 (Terraform bootstrap) corrected against current official documentation
  before its first `apply`** (ADR-010 revision, 2026-07-21). DynamoDB state locking
  removed entirely (table, variable, output, and docs) in favor of Terraform's S3-native
  lockfile (`use_lockfile = true`) — current Terraform documentation marks the
  DynamoDB-lock pattern as legacy, and this repository never had any state depending on
  the DynamoDB table, so nothing needed migrating away from it. Terraform pinned to
  `>= 1.14.0, < 2.0.0` (S3-native locking's floor), `1.15.7` concretely in CI/Docker. AWS
  provider moved to `>= 6.0.0, < 7.0.0` (`6.55.0` in the committed lock files) — adopted
  before any AWS state existed, confirmed against the official v6 upgrade guide as
  carrying no breaking change for this repository's S3 bucket resources. `tfsec` replaced
  outright by `trivy config` (tfsec's checks are now part of Trivy upstream); the two
  surviving S3-only exceptions were re-justified under Trivy's own check IDs
  (`AVD-AWS-0089`, `AVD-AWS-0132`), not copied mechanically — DynamoDB's two prior
  exceptions no longer apply since that resource is gone. `infra-plan.yml`'s third-party
  actions pinned by immutable commit SHA (not tag, not `@main`) with a human-readable
  version comment on each. ADR-010 gained an explicit OIDC trust-policy contract
  (audience, repository-scoped `sub`, three separate roles for plan/apply/deploy — no
  ARN invented) and resolved the API Gateway REST-vs-HTTP-API decision outright (**REST
  API**, required for full API key/usage-plan/quota/throttling support; API keys
  documented explicitly as consumer identification/throttling, never authentication).
  Still no AWS resource created, no `terraform apply` run, no AWS credential added.

- **Consolidated the daily/monthly ingestion-method classification into a single source
  of truth** (TECH-056, closing the drift risk TECH-055's SPIKE found: ADR-006 now
  `Accepted`, Option D). `WindowPolicy` gains a public `isMonthlyMethod(String
  methodName)`, delegating to the same rule table `validateAndGetKey` already used
  internally — no behavior change to window validation. `SipsaHealthIndicator` no longer
  maintains its own independent `DAILY_METHODS` `Set` (a second, undocumented
  classification site with zero shared source of truth with `WindowPolicy`); it now
  injects `WindowPolicy` and calls `isMonthlyMethod` instead. **One deliberate, narrow,
  explicitly-documented behavior note:** an ingestion method name that `WindowPolicy`
  does not recognize at all now gets the *daily* (36h, stricter) staleness threshold
  instead of the previous, untested `SipsaHealthIndicator`-only fallback of the
  *monthly* (840h) threshold — matching `WindowPolicy`'s own "unrecognized method is not
  monthly" convention exactly, per the explicit instruction to align with `WindowPolicy`'s
  current contract. This affects only a method name that has never appeared in this
  system (all 5 real, registered methods classify identically before and after); no
  existing test locked in the old fallback as intentional, and a new test now pins down
  the new one explicitly. All 5 real handlers (`promediosSipsaCiudad`,
  `promediosSipsaParcial`, `promediosSipsaSemanaMadr` → daily;
  `promediosSipsaMesMadr`, `promedioAbasSipsaMesMadr` → monthly) keep their exact prior
  thresholds — verified both by unit tests and a real Docker smoke test aging seeded
  rows to 40h (daily method correctly flagged `STALE` at the 36h threshold; monthly
  method correctly stayed fresh under the 840h threshold). New tests:
  `WindowPolicyTest.IsMonthlyMethodClassification` (7 cases — all 5 real methods, an
  unrecognized name, and cross-consistency with `validateAndGetKey`'s own
  classification) and 4 new cases in `SipsaHealthIndicatorTest` (a genuine
  runtime-dependency proof — the same method/age flips DOWN↔UP purely from
  `WindowPolicy`'s mocked answer, not from comparing two lists; the unrecognized-method
  case; a structural regression test asserting no `Collection`-typed field exists on
  `SipsaHealthIndicator` anymore; a constructor-dependency check). `IngestionHandler`'s
  interface and all 5 implementations are completely untouched — this is a
  source-of-truth consolidation, not a business-rule or contract change. No scheduler,
  window-validation, cron, metrics, audit, endpoint, HTTP contract, SOAP, security, or
  persistence change. No Flyway migration; V1–V4 unchanged.
  **Cambio funcional intencional para métodos no registrados. No afecta los cinco
  métodos actualmente soportados. El nuevo comportamiento coincide con la convención
  explícita de `WindowPolicy`.**

- **CXF-generated SOAP classes relocated to a distinguishable package** ([ADR-007](docs/adr/ADR-007-package-boundaries-and-internal-models.md)
  §F3, TECH-092, unblocked by TECH-094's SPIKE). All 22 `cxf-codegen-plugin`-generated
  JAXB classes moved from `infrastructure.soap.client` (shared with the hand-written
  `SoapStreamingClient`) to `infrastructure.soap.generated` — a single `pom.xml` line
  (`wsdl2java`'s `-p` argument), not 22 hand-edited package declarations; the plugin
  mapping remains the single source of truth, regenerated fresh on every build (generated
  code is never committed, confirmed by TECH-094). `SoapGatewayImpl`'s single wildcard
  import was replaced with 6 explicit imports for the generated request types it actually
  uses, plus one explicit import for `SoapStreamingClient` (which stays in the manual
  package — TECH-094's SPIKE found the wildcard had been silently supplying both).
  `SipsaSoapClientConfig`'s 2 explicit imports updated the same way. WSDL, JAX-WS catalog,
  XML namespaces, `@XmlType`/`@XmlSchema` bindings, QNames, SOAP actions, service/port
  names, and JAXB marshalling/unmarshalling behavior are all unchanged — verified, not
  assumed: a normalized diff (package declaration and the 2 known cosmetic
  generation-timestamp lines excluded) between the old and new package output is empty,
  and a new `SoapGeneratedPackageRelocationTest` (15 cases) proves the relocation
  end-to-end (all 22 classes present in the new package and absent from the old one,
  `SoapStreamingClient` unmoved, JAXB marshal/unmarshal round-trips preserve the DANE
  namespace and field values, `SoapGatewayImpl` builds correct, well-formed SOAP request
  payloads against the relocated types). `PackageBoundaryArchitectureTest` (TECH-093)
  needed no changes and required no new exclusion — none of its 3 rules interact with
  `infrastructure` sub-packages internally. No behavior change: SOAP timeouts, retries,
  metrics, ingestion logic, and every consumer outside the 2 updated files are untouched.
  Verified in Docker: clean startup, SOAP client bean constructed successfully against
  the relocated classes, no `ClassNotFoundException`, no JAXB context errors,
  `/actuator/health` unaffected. No Flyway migration; V1–V4 unchanged.

### Testing

- **TECH-093 — added ArchUnit rules to prevent regression on the three package boundaries
  ADR-007 established (F1/F2/F4, closed by TECH-090/091/095).** New
  `com.tngtech.archunit:archunit-junit5:1.4.2` test-scope dependency (bytecode import, not
  reflection — verified compatible with Java 25). One test class,
  `PackageBoundaryArchitectureTest`, asserting exactly the 3 rules ADR-007 and this story's
  own scope specify, no more: (1) `application` must not depend on `api`, except the 5
  services ADR-007 explicitly accepts as a deliberate pattern (`SipsaReadService`,
  `IngestionRunQueryService`, `AuditTrailService`, `IngestionTriggerService`,
  `IngestionAuditService` — consuming HTTP DTOs/mappers/`TimezoneUtil`); (2) `domain` must
  not depend on `infrastructure`; (3) `api.controller..` must not depend on
  `infrastructure.persistence.repository..`. All 3 pass against the real, freshly-grepped
  current dependency graph (not assumed from ADR-007's original 2026-07-13 snapshot) with
  zero violations. Rule 1 is written as an explicit exclusion of the 5 named accepted
  classes rather than scoped to the 3 already-relocated `TECH-090` classes, since the
  latter would be vacuous today and could never catch a real regression. No exclusion was
  needed for CXF-generated SOAP code (ADR-007 §F3/TECH-094): none of the 3 rules touch
  `infrastructure` internally, and `domain → infrastructure` is confirmed zero of any
  kind. A separate negative-control fixture (`archunitregression.ArchRulePatternRegressionTest`,
  2 cases, entirely outside `com.dalejandrov.sipsa` so it's never part of the real scan)
  proves the rule pattern itself both flags a genuine violation and doesn't false-positive
  against innocent code — no invalid production class was added anywhere to demonstrate
  this. No production code changed: only `pom.xml` (dependency) and new test files. No
  TECH-094, TECH-092, endpoints, scheduler, metrics, persistence, pagination, security, or
  database change. No Flyway migration; V1–V4 unchanged.

- **TECH-041 — added the first tests for `SpecificationBuilder`, which had zero coverage
  anywhere (direct or indirect) before this story.** Read the real production contract
  before writing anything: `withAttribute` is exact-match equality only (no LIKE/partial
  match/case-insensitivity — the class simply doesn't have them); `withDateOrRange` has a
  3-way precedence (exact date beats a start/end range beats no filter) using a fixed
  business timezone; `build()` only supports AND composition, never OR. Confirmed via
  `SipsaReadService` (all 5 call sites) that every attribute name passed to
  `SpecificationBuilder` is a hardcoded literal, never client input — no field-name
  allowlist exists inside the class itself, but no concrete injection/traversal risk
  exists in real usage today; documented, not "fixed," since no test demonstrates an
  actual exploitable path. Split into `SpecificationBuilderTest` (13 cases, mocked JPA
  Criteria API, no database — which builder method fires with which arguments) and
  `SpecificationBuilderPostgresTest` (8 cases, real PostgreSQL via Testcontainers, against
  `SipsaMayoristasSemanalRepository` — real AND composition, real `TIMESTAMPTZ`
  timezone-boundary semantics, and filter+pagination interaction). **Found and documented
  a real, previously-unwritten implementation detail along the way:** the exact-date
  filter's `cb.between` is inclusive on both ends, so the exact next-day-midnight instant
  is itself matched by a same-day filter — negligible in practice, not treated as a
  defect, but now pinned down by a dedicated test rather than left as an undocumented
  assumption. Test-only story: no production code changed. No endpoints, HTTP contract,
  TECH-054 pagination, scheduler, metrics, audit, ingestion repositories, TECH-060, SOAP,
  security, or database schema change. No Flyway migration; V1–V4 unchanged.

- **TECH-042 — completed `IngestionJob.execute()`'s unit test contract, without
  duplicating coverage TECH-032 and TECH-053 had already added.** An audit-first pass
  (grepping every `IngestionJob`-related test and reading its real assertions, not just
  its name) found that `IngestionJobMetricsTest` (TECH-032) and
  `IngestionJobRejectThresholdTest` (TECH-135) already covered 3 of the 9 target cases
  from the testing strategy doc; 6 had zero test evidence anywhere in the suite: both
  duplicate-run cases (`isRunComplete=true` skipped without `force`, proceeds with
  `force=true`), rejected-record persistence (`logReject` called once per record with
  exact arguments), `updateMetrics` DB persistence in `finally` (on both success and
  failure), and the MDC lifecycle (zero `MDC` references existed anywhere in the test
  suite before this story). New `IngestionJobContractTest` (15 cases) targets exactly
  those 6 gaps plus explicit `updateStatus`/audit-event assertions for the RUNNING,
  SUCCEEDED, FAILED (including the `SipsaExternalException` → `httpStatus`/
  `soapFaultCode` extraction and the null-fields case for a non-external exception),
  and CANCELED transitions — previously only provable indirectly through the
  outcome-metric proxy — and an MDC-no-leak-between-executions case. Reuses the
  `ScriptedIngestionJob` subclass-with-mocked-collaborators pattern already established
  by `IngestionJobMetricsTest`; no existing test file was rewritten or consolidated.
  Test-only story: no scheduler, dispatcher, executor, `CallerRunsPolicy`, metric
  names/tags, `IngestionMetrics`, production audit logic, business/threshold logic,
  repository, or API change. No Flyway migration; V1–V4 unchanged.

### Fixed

- **TECH-060 — removed the N+1 query pattern from
  `SipsaMayoristasSemanalRepository.upsertFallbackBatch()`.** Previously issued one
  `findByBusinessKeys(artiId, fuenId, fechaIni)` SELECT per (deduplicated) record in the
  batch — N round trips for N records. Replaced with a single atomic `INSERT … ON
  CONFLICT (arti_id, fuen_id, fecha_ini) DO NOTHING` JDBC batch (new
  `SipsaMayoristasSemanalBatchInsertRepository`/`...Impl`), mirroring
  `SipsaParcialRepository.batchUpsert`'s TECH-117 technique and backed by the existing
  `ux_semana_fallback` unique constraint (V1) — **0 SELECTs, not 1**, since the
  existence check and the insert are now the same statement. This also closes, rather
  than merely narrows, the pre-existing concurrency gap: a lost race between the old
  SELECT and `saveAll` surfaced as an uncaught `DataIntegrityViolationException` that
  discarded the whole batch; the new atomic conflict clause resolves a lost race to a
  per-row "skipped" outcome with no exception, matching TECH-117's guarantee exactly.
  In-batch deduplication and its exact prior counting semantics are unchanged (a
  duplicate key within one batch collapses to its last occurrence and is not separately
  counted as `inserted` or `skipped`); a `null` business-key component still always
  inserts, matching the removed lookup's behavior for an incomplete key exactly. Skip
  is still never an update — an existing row's stored values are untouched. The
  now-dead `findByBusinessKeys` query method was removed (no remaining callers); the
  separate `tmpMayoSemId`-based upsert path (`upsertTmpBatch`/`findByTmpId`) is
  completely untouched. New tests: `SipsaMayoristasSemanalFallbackUpsertTest` (12
  cases, real PostgreSQL via Testcontainers — `ON CONFLICT` and the constraint are
  PostgreSQL-specific — covering empty/new/existing/mixed batches, intra-batch
  duplicates, `null`-key handling, skip-never-updates, rollback, a structural
  zero-Hibernate-query assertion at batch sizes 1/10/100, and a real two-transaction
  concurrency race). Verified in Docker with a real `promediosSipsaSemanaMadr`
  ingestion (229,369 records, 0 rejected, 0 SQL errors) followed by an identical
  re-run that inserted 0 and skipped all 229,369 with the stored row count unchanged
  and zero duplicate business keys. **Follow-up, not modified here:** the identical
  N+1 pattern exists in `SipsaMayoristasMensualRepository` and
  `SipsaAbastecimientosMensualRepository`'s own `upsertFallbackBatch()` methods. No
  scheduler, API, pagination, metrics, audit, SOAP, security, or `SipsaParcial`
  deduplication change. No Flyway migration; V1–V4 unchanged.

### Changed

- **TECH-054 — `GET /api/internal/ingestion/runs` is now paginated; it no longer loads
  every row in `ingestion_runs`.** **Contract change for consumers:** the response body
  changed from a bare JSON array to this API's existing paginated envelope,
  `ApiResponse<IngestionRunDetailResponse>` (`{count, next, prev, pages, results}` —
  `PaginationUtils.toApiResponse`, `next`/`prev` omitted when null), the same shape
  already used by `GET /api/sipsa/*` and `GET /api/internal/audit/all`; consumers
  reading a raw array must switch to reading `.results`. **Parameters:** `page`
  (default 1) and `size` (default 20, maximum 100) — bound via a new
  `IngestionRunQueryRequest` record that clamps out-of-range values rather than
  rejecting them (`page < 1` → 1, `size < 1` → 20, `size > 100` → 100), matching every
  other `*QueryRequest` DTO in the codebase (`CiudadQueryRequest`, `AuditQueryRequest`)
  — a genuinely non-numeric value (e.g. `size=abc`) still produces the existing 400
  `VALIDATION_ERROR` contract (`requestId`, `instance`, `fieldErrors`, no stack trace).
  **Order:** fixed `startTime DESC, runId DESC` (not client-configurable) — `runId` is
  the deterministic tie-breaker for equal `startTime` values, so pagination never
  duplicates or omits a run across pages. **Implementation:** `SipsaOpsController` →
  `IngestionRunQueryService.getAllRuns(IngestionRunQueryRequest)` → the *existing but
  previously unused* `IngestionControlService.findAllRuns(Pageable)` →
  `IngestionRunRepository.findAll(Pageable)` (inherited from `JpaRepository`, no custom
  `@Query`) → `Page<IngestionRun>.map(mapper::toDetailDto)` — a real `Page` end to end,
  never `findAll()` followed by an in-memory sublist. The now-dead, unbounded
  `IngestionControlService.findAllRuns()` (no-arg) was deleted; it had no other
  callers. Reused `PaginationConfig`/`PaginationUtils` as-is (the codebase's existing
  canonical pagination convention, confirmed via diagnosis before implementing) — no
  new pagination format introduced, `PaginationConfig` itself untouched. New tests:
  `IngestionRunQueryServiceGetAllRunsTest` (11 cases — empty/first/intermediate/last
  page, stable two-column order, entity→DTO mapping, `totalElements`/`totalPages`
  propagation, confirms the unpaged `findAllRuns()` overload no longer exists),
  `SipsaOpsControllerRunsPaginationTest` (11 cases — defaults, explicit page/size,
  content, empty page, clamped negative/zero/over-max values, 401/403, the malformed-
  `size` 400 contract, the `ApiResponse` envelope shape), `IngestionRunPaginationTest`
  (6 cases, real PostgreSQL via Testcontainers — only the requested page size is
  fetched, order is stable across repeated calls, total count is correct, pages never
  duplicate or omit a run, equal-timestamp rows tie-break deterministically by
  `runId`, and a paginated fetch issues exactly 2 SQL statements regardless of table
  size, not one that scales with row count). Verified in Docker: 25 runs seeded
  directly via SQL (no real DANE SOAP call needed for a pure read endpoint), page 1/2/3
  of size 10 returned correct, non-overlapping, stably-ordered slices with correct
  `next`/`prev` links; 401 without a token; 403 with `sipsa/audit.read`; health and
  `/actuator/metrics` unaffected; Flyway still at v4, no new migration. No scheduler,
  metrics, audit, run-execution, cancellation, SOAP, security, or database schema
  change.

- **TECH-053 — the scheduler dispatches ingestion asynchronously; scheduler threads no
  longer block for the duration of a run.** `SipsaIngestionScheduler` no longer depends
  on `GenericIngestionJob` at all — each `@Scheduled` method now logs and delegates to
  a new `ScheduledIngestionDispatcher` (`@Async("ingestionTaskExecutor")`, the same
  executor already used by manual-trigger ingestion and audit logging — no new
  executor). **Dispatch is per window, not per method:** the daily window's Ciudad →
  Parcial → Semana sequence still runs sequentially, on one worker thread, inside a
  single async call — dispatching each method as its own independent async call would
  have let them race on the pool, silently breaking the sequential,
  resource-contention-avoiding execution this exact codebase already relied on. This
  is a deliberate refinement of ADR-005's Option A (now **Accepted** — see its
  Resolution section), not the literal `asyncIngestionService.executeAsync(request)`-
  per-method sketch the ADR and original backlog entry described. Request
  construction, `RequestSource.SCHEDULED`, per-method failure containment
  (try/catch/`log.error`, one method's failure doesn't stop the others), and the
  existing overlap-prevention path (the real `uq_ingestion_runs_window` unique
  constraint, translated to a controlled skip by `IngestionControlService.createRun`)
  are all unchanged — only relocated and regression-tested. `CallerRunsPolicy`
  saturation behavior is unchanged and explicitly not oversold: under sustained
  overload the scheduler thread can still end up blocked, same as before. New tests:
  `SipsaIngestionSchedulerTest` (3 cases, rewritten to verify pure delegation),
  `ScheduledIngestionDispatcherTest` (9 cases — the previous scheduler test's logic
  moved here, plus a new overlap-protection regression), `ScheduledIngestionAsyncDispatchTest`
  (3 cases, real Spring context — dispatch-returns-before-completion via a controlled
  latch, `ingestion-async-*` thread name, and the daily window confirmed still
  sequential on one thread even when dispatched asynchronously). Verified in Docker
  with a real cron-fired trigger (daily window bounds and cron time overridden via env
  vars for this one verification run only, no committed config changed): scheduler
  returned immediately, all three methods ran in order on one `ingestion-async-1`
  thread, three runs created with no duplicates, audit events and the
  `sipsa.ingestion.runs` metric each present exactly once per run, health unaffected.
  No cron, window, HTTP, pagination, repository, deduplication, or database change. No
  Flyway migration; V1–V4 unchanged.

### Added

- **TECH-032 — Micrometer metrics for the ingestion pipeline and SOAP calls.** New
  `IngestionMetrics` component (`infrastructure/observability/`) instruments
  `IngestionJob.execute` (every ingestion run, regardless of method — the abstract base
  class every job runs through) and `SoapStreamingClient.stream` (every SOAP call,
  including its internal retry loop). No new dependency: `spring-boot-starter-actuator`
  and `micrometer-registry-prometheus` were already present, just unused.
  **Metrics:** `sipsa.ingestion.duration` (Timer), `sipsa.ingestion.runs` (Counter),
  `sipsa.ingestion.records.seen/inserted/skipped/rejected` (DistributionSummary, one
  value per run from the run's already-existing final counters — never recalculated,
  never double-recorded), `sipsa.soap.calls`/`sipsa.soap.failures`/`sipsa.soap.retries`
  (Counter), `sipsa.soap.duration` (Timer, spans all retries/backoff). Tags are strictly
  `method` (closed catalog: the ~5 registered ingestion methods / SOAP actions),
  `outcome` (`success`/`failure`/`canceled`), and `source` (lowercased
  `RequestSource`) — never `requestId`, `runId`, a raw exception message, or any other
  unbounded value. Every `IngestionMetrics` method swallows and logs registry
  exceptions rather than propagating them: instrumentation must never break an
  ingestion run or a SOAP call. **Bug found and fixed along the way:**
  `micrometer-registry-prometheus` was marked `<optional>true</optional>` in `pom.xml`,
  which Spring Boot Maven Plugin's `repackage` goal excludes from the runnable jar by
  default — the dependency compiled fine and showed in `dependency:tree`, but was never
  actually bundled, so `/actuator/prometheus` 404'd despite being in the exposure list.
  Fixed by removing the flag; verified in Docker with a real successful
  `promediosSipsaParcial` run (677,061 records) — all 10 `sipsa.*` metrics present with
  the designed tags in both `/actuator/metrics` and `/actuator/prometheus`,
  `/actuator/health` unaffected, no sensitive data in any tag. New tests:
  `IngestionMetricsTest` (9 cases, `SimpleMeterRegistry`), `IngestionJobMetricsTest` (4
  cases, verifying exactly one `recordRunCompleted` call per run with the correct
  outcome), `SoapStreamingClientMetricsTest` (3 cases, a real local loopback HTTP
  server — no WireMock, not yet in this repo per TECH-044 — verifying exactly one
  outcome record and the correct retry count per call). No status codes, `ErrorResponse`,
  DTOs, security, retries, thresholds, scheduler, or database logic changed. No Flyway
  migration; V1–V4 unchanged.

### Testing

- **TECH-043 — full `GlobalExceptionHandler` contract coverage.** New
  `GlobalExceptionHandlerContractTest` exercises all 15 `@ExceptionHandler` cases
  through real MVC dispatch, asserting HTTP status, `Content-Type`, `code`, `message`,
  `requestId`, `instance`, a present `timestamp`, no leaked stack trace, and each
  response type's extra fields (`fieldErrors`, `availableMethods`) where applicable.
  Test-and-documentation only: no status code, error code, message, `ErrorResponse`
  shape, or `RequestIdFilter` behavior changed — every handler already behaved exactly
  as documented, no defects found. `RequestContextThrowingTestController` (TECH-023's
  fixture) was extended with 8 new endpoints rather than adding a second, near-duplicate
  controller. `HttpRequestMethodNotSupportedException` (405) and
  `HttpMediaTypeNotSupportedException` (415) are confirmed out of scope — no handler for
  either exists in `GlobalExceptionHandler`. No Flyway migration; V1–V4 unchanged.

### Added

- **TECH-023 — HTTP error responses now include `requestId` and `instance`.**
  Additive change only: every existing field (`timestamp`, `status`, `error`, `code`,
  `message`), every existing status code, and every existing error `code` (including
  TECH-021's `502`/`PARSE_ERROR` and TECH-022's `404`/`NOT_FOUND`) is unchanged.
  `instance` is the request path (`HttpServletRequest.getRequestURI()` — no host,
  scheme, or query string). `requestId` comes from a new `RequestIdFilter`
  (`api/filter/`, mirrors the existing `TimezoneFilter` pattern): no per-HTTP-request
  correlation ID source existed anywhere in this repository before this — the only
  prior "requestId" concept was the ingestion-domain business correlation ID
  (`UUID.randomUUID()` in `IngestionTriggerService`/`SipsaIngestionScheduler`, generated
  only for ingestion-trigger operations, not every request), and MDC
  (`IngestionJob`) is populated only deep inside the async ingestion pipeline, never on
  the synchronous request-handling thread. The new filter honors an incoming
  `X-Request-Id` header if present and non-blank, otherwise generates a UUID — exactly
  once per request, before the rest of the filter chain runs, and echoes it back on the
  `X-Request-Id` response header. Every `@ExceptionHandler` in `GlobalExceptionHandler`
  now produces both new fields consistently (no handler was left without them),
  including the two that build their response DTOs directly rather than through the
  shared `buildErrorResponse` helper (`IngestionValidationErrorResponse`,
  `ValidationErrorResponse`). New `GlobalExceptionHandlerRequestContextTest` covers all
  five required exception shapes (parse/502, not-found/404, business/422,
  validation/400, generic/500 — with an explicit check that no stack trace leaks) plus
  correlation behavior: an incoming header is echoed verbatim, the fallback generates a
  non-blank ID consistent between the response header and body, and a blank incoming
  header is not trusted verbatim. No Flyway migration; V1–V4 unchanged.

### Changed

- **TECH-022 — a missing ingestion run now returns `404 Not Found`, not
  `422 Unprocessable Entity`.** New `SipsaNotFoundException` (mapped by
  `GlobalExceptionHandler` to HTTP 404, error code `NOT_FOUND`) replaces
  `SipsaBusinessException` (422) in exactly two places that were conflating "the
  resource doesn't exist" with "the resource exists but this operation on it is
  invalid": `IngestionRunQueryService.getRunStatus` (a run ID that was never created),
  and `IngestionControlService.cancelRun`'s `run == null` branch only — its sibling
  check ("run exists but isn't `STARTED`/`RUNNING`") stays `SipsaBusinessException` →
  422, since that's a genuine business-rule violation on an existing run, not an
  absent resource. `AuditTrailService`'s two analogous `SipsaBusinessException`
  sites were deliberately left untouched (separate follow-up story). `ErrorResponse`,
  `requestId`, and `instance` were not touched. New tests:
  `IngestionControlServiceCancelRunTest` (service-level), updated
  `IngestionRunQueryServiceGetRunStatusTest` (now expects `SipsaNotFoundException`,
  previously pinned `SipsaBusinessException`), and `SipsaOpsControllerNotFoundTest`
  (real MVC dispatch — missing run → 404, existing run → 200, inactive-run cancel →
  422, active-run cancel → 200, plus a regression check that a downstream
  `SipsaParseException` through the same controller still returns 502, TECH-021
  untouched). No Flyway migration; V1–V4 unchanged.

- **TECH-021 — `SipsaParseException` now maps to `502 Bad Gateway`, not
  `400 Bad Request`** — a contractual (breaking) HTTP status change for any client that
  depended on the previous `400`. This exception fires when DANE's upstream SOAP/XML
  response can't be parsed (`AbstractStaxParser`); the API caller sent nothing wrong, so
  `400` (client error) was the wrong semantics — `502` (this server, acting as a
  gateway, got an invalid response from an upstream server) is correct. Only the status
  argument in `GlobalExceptionHandler.handleParseException` changed
  (`HttpStatus.BAD_REQUEST` → `HttpStatus.BAD_GATEWAY`); the error `code`
  (`"PARSE_ERROR"`), `message`, `timestamp`, and body shape are all unchanged —
  `ErrorResponse` itself was not touched, and the new `SIPSA_UPSTREAM_PARSE_ERROR`
  code ADR-003 proposes was deliberately not adopted (that ADR is still `Proposed`, not
  `Accepted`; it explicitly authorizes this status-code fix to proceed independently).
  New focused `GlobalExceptionHandlerParseExceptionTest` (`@WebMvcTest`, real MVC
  dispatch) covers status, code, message, timestamp, and content type, plus a
  regression check that `SipsaBusinessException` → `422` is untouched. No Flyway
  migration; V1–V4 unchanged.

- **TECH-070 (C-01) — `SoapProperties` now validates all 9 fields at startup**,
  early configuration validation only — no functional change to the SOAP client.
  Previously only 4 fields were checked, imperatively, inside
  `SipsaSoapClientConfig`'s `@Bean` factory method (after the property had already
  bound); `maxRetries` and `retryBackoffMs` were never validated anywhere (a negative
  retry count silently no-ops the retry loop, a negative backoff throws
  `IllegalArgumentException` mid-retry), and neither was `namespace`, despite
  `SoapGatewayImpl` using it verbatim to build every SOAP operation's `QName`. Now
  `@Validated` + Jakarta constraints match exactly what the client already required:
  `@NotBlank` on `endpoint`/`namespace`, `@Positive` on the two timeouts, `@Min(0)` on
  `maxRetries`/`retryBackoffMs`/`loggingLimitBytes`/`maxChildElements` — the last two
  deliberately not `@Positive`, since `0` is a real, documented value for both
  ("unlimited" and "log nothing" respectively). No cross-field rules, no URL-format
  regex (`@NotBlank` is what the client actually needs; a fragile validator risks
  rejecting valid non-standard endpoints like the test suite's
  `http://localhost:9999/mock`). `endpoint` stays unconditionally required — no real
  "SOAP disabled" flag exists in this repository. Found and fixed along the way:
  `docker-compose.yml` passed through **zero** `SOAP_*` variables, so shell overrides
  silently had no effect in Docker; added passthrough for the six properties with an
  established env var name. 16 new binding tests, previously zero coverage. Verified
  in Docker: defaults, valid overrides (confirmed present inside the container), and
  three invalid-value cases each produced `APPLICATION FAILED TO START` naming the
  property. No Flyway migration; V1–V4 unchanged.

- **TECH-031 — `SipsaHealthIndicator` staleness thresholds externalized**, no effective
  value changed. The indicator hardcoded `36` (hours, for the daily-window method
  group) and `35 * 24` (also compared in hours, for every other monitored method) —
  both now live in the new validated `SipsaHealthProperties`
  (`sipsa.health.daily-staleness-threshold` / `monthly-staleness-threshold`, env
  `SIPSA_HEALTH_DAILY_STALENESS_THRESHOLD` / `SIPSA_HEALTH_MONTHLY_STALENESS_THRESHOLD`,
  canonical defaults `36h` / `840h` — kept in hours, the unit the comparison actually
  uses, not converted to a day count). Each threshold must be positive; zero or negative
  values abort startup naming the property (a zero threshold would mark every method
  `STALE` immediately after its own successful run). `SipsaHealthIndicator` now takes
  `SipsaHealthProperties` via constructor injection instead of hardcoded literals, and
  `Instant.now()` became `Instant.now(clock)` with a package-private test-only
  `setClock` seam mirroring `WindowPolicy`'s existing pattern in this codebase. The
  strict `>` comparison, per-method `STALE` detail entries, and `UP`/`DOWN`/`UNKNOWN`
  outcomes are unchanged — new tests pin both method groups at exactly their default
  threshold (stays `UP`) and one hour past it (`DOWN`), previously uncovered. Verified
  in Docker: defaults, valid override, and startup abort on an invalid value. No
  Actuator wiring, security, endpoint, or health-response-shape change; no migration;
  V1–V4 unchanged.

- **TECH-052 — `IngestionControlService.getRun()` now returns
  `Optional<IngestionRun>`**, an explicit internal absence contract instead of a
  nullable return. `findById` already returned `Optional`; the method was discarding it
  with `.orElse(null)` only to force its single caller,
  `IngestionRunQueryService.getRunStatus`, to null-check it back. That caller now uses
  `.map(mapper::toDetailDto).orElseThrow(...)` — no unguarded `.get()`, no
  `.orElse(null)`. **Not an HTTP contract change:** a missing run still produces
  `SipsaBusinessException` → HTTP 422 with the same message (TECH-022's HTTP 404 story
  is separate and untouched). Two unrelated `findById(...).orElse(null)` sites in the
  same class (`isRunCanceled`, `cancelRun` — both return `boolean`/`void`, not
  `IngestionRun`) were reviewed and intentionally left untouched. New tests cover both
  services' present/absent behavior, previously uncovered (both are always mocked in
  existing controller/security tests). No migration; V1–V4 unchanged.

- **TECH-051 — `IngestionAuditMapper.toAuditEventRequest()` renamed to
  `toAuditEventResponse()`** to match its actual, unchanged return type
  (`AuditTrailResponse.AuditEventResponse` — never the unrelated `AuditEventRequest`
  class). Internal contract clarity only: the 4 call sites in `AuditTrailService` were
  updated (method references, no reflection), MapStruct resolves its generated
  implementation by type signature so the rename doesn't affect code generation, and no
  deprecated alias was kept (internal `api/mapper` interface, no external consumer).
  Field mapping is unchanged and now covered by a new `IngestionAuditMapperTest`
  (previously zero coverage, since the mapper's only consumer is always mocked in
  existing tests). Not a public API change; no Flyway migration; V1–V4 unchanged.

- **TECH-050 — removed residual `// ...existing code...` placeholder comments** from
  the `catch` blocks of `CiudadIngestionHandler`, `SemanaIngestionHandler`,
  `AbasIngestionHandler` and `MesIngestionHandler`. Non-functional cleanup only: each
  comment documented nothing and sat directly before a `log.warn(...)` that already
  describes the partial-progress-save behavior. Zero logic, signature, import, or test
  changes — one line removed per file. No Flyway migration; V1–V4 unchanged.

### Fixed

- **TECH-020 — internal controller route mappings normalized to a leading `/`.**
  `SipsaOpsController` and `IngestionAuditController` declared
  `@RequestMapping("api/internal/ingestion")` and `@RequestMapping("api/internal/audit")`
  without a leading slash, inconsistent with `SipsaRestController`'s
  `@RequestMapping("/api/sipsa")`. Spring MVC normalizes the class-level value at startup
  regardless of the leading slash, so the effective routes
  (`/api/internal/ingestion/**`, `/api/internal/audit/**`) were never actually broken —
  this is a declared-contract normalization, not a routing fix. No HTTP method, subroute,
  DTO, security scope, or status code changed. New `InternalControllerRouteMappingTest`
  pins the exact controller/handler method Spring resolves for each path; the
  pre-existing `InternalEndpointSecurityTest` (15 cases: 401/403/2xx across both
  endpoint groups) stays green unchanged. Verified in Docker (clean rebuild, both
  endpoint groups exercised with the local mock-OIDC flow, Flyway V1→V4 unaffected).

### Changed

- **TECH-136 (C-05) — async executor configuration centralized and audit executor made
  explicit.** `AsyncConfig` no longer re-declares `@Value` defaults for
  `sipsa.ingestion.async.*` — the pool geometry binds once in the new validated
  `AsyncExecutorProperties` (core ≥ 1, max ≥ 1 **and ≥ core** via cross-field check,
  queue ≥ 0 — `0` documented as direct handoff, keep-alive ≥ 0 s; invalid values abort
  startup naming the property; resolved geometry logged once). The operational contract
  is unchanged: same prefix, same `SIPSA_ASYNC_*` env vars (now passed through by
  `docker-compose.yml`), same bean (`ingestionTaskExecutor`), thread prefix
  (`ingestion-async-`), `CallerRunsPolicy`, core-thread timeout and canonical
  `2/10/25/60s` — no tuning. **Fixed alongside:** `IngestionAuditService.logEvent`
  carried a bare `@Async` in a context with two `TaskExecutor` beans (the scheduler
  also implements `TaskExecutor`), so Spring logged `More than one TaskExecutor bean
  found` and ran audit events on ad-hoc `SimpleAsyncTaskExecutor` threads (finding from
  the 2026-07-19 CI investigation). The executor is now explicit —
  `@Async("ingestionTaskExecutor")` — so audit events always run on the managed pool;
  still asynchronous, still `REQUIRES_NEW`, still append-only. Evidence-backed: a
  resolution test pins the `ingestion-async-*` insert thread and the absence of the
  warning and of `SimpleAsyncTaskExecutor` from captured output; a deterministic
  latch-based saturation test preserves the CallerRunsPolicy behavior; 12 binding
  cases cover defaults/overrides/boundaries/aborts; the TECH-117 concurrent ingestion
  test stayed green across 50 repetitions. Findings recorded for follow-up (not
  changed here): the executor keeps framework-default shutdown behavior
  (`waitForTasksToCompleteOnShutdown=false` — in-flight audit tasks may be dropped at
  shutdown) and no `TaskDecorator`/MDC propagation exists (audit correlation travels in
  the event payload, not the logging context).

- **TECH-135 (C-04) — ingestion rejection thresholds centralized into a single source
  of truth** (`IngestionProperties`, bound to `sipsa.ingestion.max-reject-rate` /
  `max-reject-count`). `IngestionJob` and `GenericIngestionJob` no longer carry
  duplicated `@Value` bindings with local defaults — the double-source antipattern
  already removed for `batch-size` (TECH-071) and `monthly-window-start` (TECH-133).
  **Effective values unchanged** (rate `0.01`, count `5000` — what `application.yaml`
  always made effective) **and evaluation semantics untouched, now documented and
  test-pinned:** the rate is a fraction of `recordsSeen` in `[0..1]` (0.01 = 1%, not a
  percentage); both gates are evaluated once at the end of each run over its final
  totals (never per batch); they combine by OR (strictly exceeding either fails the
  run with `SipsaIngestionException` → status `FAILED`); values exactly at a threshold
  pass (strict `>`); `seen=0` skips the rate check so only the count gate applies.
  Startup now validates both values (rate outside `[0..1]`, negative count or
  non-numeric input abort with a message naming the property) and logs the resolved
  pair once. `docker-compose.yml` passes `MAX_REJECT_RATE` / `MAX_REJECT_COUNT`
  through (verified in Docker: defaults `0.01/5000`, override `0.05/1234`) and
  `.env.example` documents range, semantics and precedence
  (env var > property > canonical default). No functional change, no migration.

### Fixed

- **TECH-134 — remaining SIPSA decimal annotations aligned with the DDL.**
  `SipsaCiudad.precioPromedio/enviado` and
  `SipsaMayoristasSemanal.minimoKg/maximoKg/promedioKg/enviado` declared
  `precision=15, scale=2` against `NUMERIC(19,2)` columns — the drift TECH-118 closed
  for `SipsaParcial`, resolved with the same criterion: DANE's XSD is unbounded
  `xs:decimal` (`minOccurs=0`) for every field, so the versioned V1 DDL is the storage
  truth and the annotations now mirror it. **Every SIPSA price model now declares
  `19,2`; no migration (V1–V4 unchanged), no behavior change** (`ddl-auto=validate`
  never compared precision; the pipeline is `BigDecimal` end to end with zero
  `double`/`float`). Verified against real PostgreSQL with fresh real DANE loads
  (Ciudad 373,038 rows: 270.00–15,500.00; Semanal 233,866 rows: 182.00–280,000.00 —
  the widest range in the schema, still far inside either bound): boundary matrix
  including the `19,2`-only value `99999999999999999.99` round-trips exactly, scale > 2
  keeps the TECH-118 half-away-from-zero semantics, JSON stays exact unquoted numbers.
  Real-data shapes recorded: Ciudad `enviado` is `0.00` on every observed row and
  Semanal `enviado` is always `NULL` — both fields look vestigial upstream; documented,
  not changed.

- **TECH-118 — `SipsaParcial` decimal precision aligned with PostgreSQL.** The three
  price columns (`promedioKg`, `maximoKg`, `minimoKg`) declared `precision=15, scale=2`
  in JPA while the versioned DDL has been `NUMERIC(19,2)` since V1. Source of truth:
  the DDL — DANE's XSD declares the fields as unbounded `xs:decimal` (`minOccurs=0`),
  so no external contract backs 15 digits, and real data (677,061 rows: 0.00–22,000.00,
  scale ≤ 2, no negatives, no nulls) fits either bound. The annotation now mirrors the
  DDL (`19,2`), as `MayoristasMensual`/`Abastecimientos` already did. **Declarative
  drift only — no migration (V1–V4 unchanged), no behavior change:** Hibernate's
  `ddl-auto=validate` never compared precision, the parser is exact
  (`new BigDecimal(String)`, no `double` anywhere in the pipeline), and Jackson keeps
  serializing stored values exactly (`22000.00` — JSON stays a number; scale is data,
  not monetary formatting). Newly pinned by tests: values that fit `NUMERIC(19,2)` but
  not `DECIMAL(15,2)` round-trip exactly, and scale > 2 input is coerced by the column
  with **explicit, documented half-away-from-zero rounding** (`123.456 → 123.46`) —
  previously this coercion existed but was undocumented. No `@Digits` added: values
  come exclusively from DANE ingestion (no write API) and the XSD is unbounded, so a
  bean-validation cap could reject contract-valid data. Out-of-scope findings recorded:
  the same `15,2` drift exists in `SipsaCiudad` and `SipsaMayoristasSemanal` (follow-up
  story), and price columns carry no non-negativity CHECK constraint.

- **TECH-117 — concurrent `SipsaParcial` ingestions no longer fail on duplicate keys.**
  Two executions processing the same publication could both pass the dedup lookup
  (READ COMMITTED) and both insert the same `key_hash`; the loser died on the
  unique-violation at flush, its whole batch rolled back (losing even non-conflicting
  rows) and the run ended `FAILED`. The insert step of `batchUpsert` is now an atomic
  `INSERT … ON CONFLICT (key_hash) DO NOTHING` executed as a single JDBC batch in a
  dedicated repository fragment (`SipsaParcialBatchInsertRepositoryImpl` — no SQL in
  handlers or the application layer): a lost race resolves inside PostgreSQL to a
  per-row "not inserted" outcome counted as **skipped**, the transaction stays valid,
  non-conflicting rows persist, and both runs end `SUCCEEDED` with coherent metrics
  (`seen = inserted + skipped + rejected` per execution). The dedup lookups (hash +
  legacy-UUID recompute) are preserved as the read-avoidance optimization, the
  `UNIQUE (key_hash)` constraint remains the integrity barrier, and generated IDs are
  deliberately not fetched (ingestion discards entities after each flush). No schema
  change, no new migration; `ON CONFLICT` targets only `key_hash`, never the natural
  key (TECH-122 pending). Deterministic Testcontainers races (uncommitted-insert hold +
  `pg_stat_activity` lock observation) cover single-key, identical-batch,
  partial-overlap, intra-batch-duplicate, legacy-UUID, post-collision and retry
  scenarios, plus two real overlapping `GenericIngestionJob` executions. ADR-001's
  insert-only + skip decision is unchanged — the implementation note records that the
  strategy simply became atomic.

### Added

- **TECH-124 — covering index for the `SipsaParcial` article filter** (migration
  `V4__add_parcial_article_query_index.sql`): `idx_sipsa_parcial_article_date` on
  `(id_arti_semana, enma_fecha DESC) INCLUDE (id)`. Measured on the real DANE dataset
  (677,061 rows, PostgreSQL 18): the per-page count query of
  `GET /api/sipsa/parcial?idArtiSemana=…` — Hibernate emits `count(id)`, so the index
  must cover `id` to be usable index-only — drops from a full-table Parallel Seq Scan
  (~17–28 ms on every page request) to an Index Only Scan (0.5–2.3 ms, `Heap Fetches: 0`);
  a non-existent article drops from ~18 ms to ~0.02 ms; the `enma_fecha DESC` key column
  matches the endpoint's default ordering so every article cardinality gets an ordered
  index walk. Cost: 26 MB (170 MB table), ~0.2 s creation at current volume, ~+0.55 ms
  per 500-row ingestion batch (all-skip reingestion unaffected: zero writes). Alternatives
  `(id_arti_semana)`, `(id_arti_semana, enma_fecha DESC)` without INCLUDE, and
  `(id_arti_semana, muni_id, enma_fecha DESC)` were measured and discarded — evidence and
  re-evaluation thresholds in `docs/diagnostics/tech-124-article-filter-analysis.md`.
  No API contract change: `idArtiSemana` stays canonical, `artiId` stays a validated
  alias, `Page`/count semantics untouched. Deep-page OFFSET cost (~23–31 ms at page 1000)
  is inherent to OFFSET, marginal at current volume, and explicitly out of scope.

### Changed

- **TECH-133 — monthly ingestion window configuration centralized and validated**.
  `WindowPolicy`'s never-effective `@Value` fallback of `06:00` for
  `sipsa.ingestion.monthly-window-start` is gone: the property now binds as a typed
  `LocalTime` in `IngestionProperties` (canonical default **14:00**, the value
  `application.yaml` always made effective — DANE publishes around 14:00 COT and the
  monthly crons fire at 14:30). **Effective behavior is unchanged.** The value is an
  authorization gate (earliest time of day a monthly run may execute on its
  publication/grace day in `America/Bogota`, ADR-008), not the scheduler fire time.
  Invalid formats (`24:00`, `14:99`, free text) abort startup naming the property; an
  explicitly empty value falls back to the canonical default under standard Spring
  binding. Overridable via `INGESTION_MONTHLY_WINDOW_START` (passed through by
  `docker-compose.yml`). Startup logs one safe confirmation pair: window start and
  timezone. Boundary behavior is pinned with `Clock.fixed` tests (gate−1min/gate/gate+1min,
  wrong-day rejection, UTC-vs-Bogota same-instant divergence, `force=true` bypass).

- **TECH-071 — ingestion batch size unified into a single source of truth**
  (`IngestionProperties`, bound to `sipsa.ingestion.batch-size`). The divergent
  `@Value("${sipsa.ingestion.batch-size:2000}")` default that 5 ingestion handlers
  carried is gone: all handlers now inject the same typed, validated configuration.
  Canonical default is **500** — the value used by every recent real-data validation
  (676,210 records, TECH-011 evidence) — overridable via the `INGESTION_BATCH_SIZE`
  environment variable (now passed through by `docker-compose.yml`). Invalid values
  (zero, negative, non-numeric, or above the preventive maximum of 10,000 — chosen to
  keep per-batch dedup `IN` lookups far below the PostgreSQL JDBC limit of 32,767 bind
  parameters) abort startup with a clear validation message instead of failing
  mid-ingestion. The effective size is logged once at startup
  (`Ingestion batch size = …`). No functional change: with the default 500 the
  behavior is identical to what TECH-011 validated.

- **TECH-119 — redundant `SipsaParcial` index removed** (migration
  `V3__drop_redundant_parcial_key_hash_index.sql`). `V1` had created two identical
  B-tree indexes on `sipsa_parcial.key_hash`: the explicit non-unique
  `idx_sipsa_parcial_key_hash` and the backing index of the `UNIQUE (key_hash)`
  constraint. V3 drops the explicit one; **the `UNIQUE` constraint and its index are
  preserved** and now serve every hash lookup (verified by `EXPLAIN` before/after with
  identical cost, and by a full idempotent re-ingestion of 676,210 real records:
  `inserted=0, skipped=676,210`). Storage on the local real-data base: total indexes
  254 MB → 174 MB (−80 MB); every insert also stops paying double index maintenance.
  Transactional `DROP INDEX` (8 ms observed on 676K rows; rationale in the migration and
  in `docs/database/database-changelog.md`). No data changes, no API change; validated
  from empty base (V1→V2→V3) and as a V2→V3 upgrade with data via Testcontainers
  (`ParcialKeyHashIndexMigrationTest`), including a post-V3 duplicate insert rejected by
  the constraint.

### Fixed

- **TECH-113 — `GET /api/sipsa/parcial` filters corrected** (H-2/H-3 from the integrity
  SPIKE). The article filter no longer targets the nonexistent entity attribute `artiId`
  (which produced `IllegalArgumentException` → HTTP 500 whenever used): the canonical
  parameter is now `idArtiSemana`, matching the entity and the DANE contract, with
  `artiId` retained as a validated compatibility alias (same value → one condition;
  conflicting values → `400 VALIDATION_ERROR`). The municipality filter `muniId` is now
  **text** instead of `Long`, preserving DIVIPOLA leading zeros exactly (107,468 real
  rows carry them; `05001` ≠ `5001`) — trimmed, non-blank, max 50 chars (column bound),
  no numeric conversion ever. Filter errors return `400`, never `500`. Verified against
  real PostgreSQL via Testcontainers through the HTTP endpoint
  (`ParcialQueryFilterIntegrationTest`, 9 cases) plus 11 unit cases
  (`ParcialQueryRequestTest`); API documentation updated with the canonical/alias rule
  and leading-zero examples. No schema change needed — `muni_id VARCHAR(50)` was always
  correct; the bug was only in the query contract.

- **TECH-011 — `SipsaParcial` deduplication** ([ADR-001](docs/adr/ADR-001-data-deduplication.md),
  now `Accepted`; closes debt PS-01). `computeKeyHash()` no longer generates a random
  UUID per row: `key_hash` is the deterministic SHA-256 (versioned, unit-separator
  delimited, UTF-8, lowercase hex) of the natural key
  `(muniId, fuenId, futiId, idArtiSemana, enmaFecha)` — confirmed unique against real
  DANE data by the TECH-012 diagnostic. `SipsaParcialRepository.batchUpsert()` is now a
  real skip-first upsert: intra-batch dedupe, one bulk lookup by hash, and one bulk
  lookup by survey date that recomputes natural-key hashes so **legacy UUID rows
  deduplicate too, without any backfill** (no per-record queries). The survey date is
  parsed strictly (ISO-8601 instant with explicit offset/zone) and records with
  unparseable dates are rejected with audit trail instead of persisted with a silent
  null (closes the SPIKE's H-1 risk preventively — 0 occurrences observed in real data).
  `skipped` is now propagated to `IngestionContext` and the completion log.
  **Validated end-to-end against the real DANE endpoint on Docker Compose:** pre-fix,
  two identical runs duplicated the full dataset (676,210 → 1,352,420 rows, every group
  ×2 with identical prices); post-fix, run 1 inserted 676,210, run 2 and run 3 (after a
  container restart) inserted 0 and skipped 676,210 each, table stable with 0 duplicate
  groups. New migration `V2__add_parcial_natural_key_index.sql` (expand-only support
  index; no data changes — see [docs/database/database-changelog.md](docs/database/database-changelog.md)).
  New tests: `ParcialKeyHashTest`, `ParcialIngestionHandlerTest` (idempotent
  re-ingestion, legacy-UUID dedupe, strict date rejection), `ParcialMigrationUpgradeTest`
  (V1→V2 upgrade over duplicated legacy data on real PostgreSQL), extended
  `FlywayMigrationsTest`. Suite: 116 tests. TECH-012's script fixed to use the real
  `start_time` column (was `started_at`).

### Security

- **TECH-001/TECH-002 — application security layer** ([ADR-002](docs/adr/ADR-002-internal-endpoint-security.md),
  now `Accepted` with the layered AWS model: API Gateway API keys + Cognito JWT +
  Resource Server + private networking). Spring Boot is now an OAuth 2.0 Resource Server:
  - `/api/internal/**` requires a Cognito access token with the per-operation scope —
    `sipsa/ingestion.execute` (run), `sipsa/ingestion.cancel` (cancel),
    `sipsa/ingestion.read` (run queries), `sipsa/audit.read` (audit trail). **Breaking for
    previously-unauthenticated operational scripts, by design.**
  - JWT validation: issuer/signature/expiry, `token_use=access` (Cognito ID tokens
    rejected), optional client allowlist via `SIPSA_JWT_ALLOWED_CLIENT_IDS` (fail-fast on
    malformed values). Issuer configured via `SIPSA_JWT_ISSUER_URI` (required — the app
    refuses to start without it; no secrets involved, none versioned).
  - Stateless chain: no sessions, no CSRF surface, no form login, no HTTP Basic, no
    cookies. Default deny for undeclared routes. `401`/`403` are JSON in the
    `ErrorResponse` shape with generic messages (no HTML, no stack traces, no hint of
    which validation failed).
  - `GET /api/sipsa/**` stays public in the application — per-consumer API keys, quotas
    and throttling are API Gateway's job (TECH-131). `/actuator/health` stays public for
    container healthchecks; every other Actuator endpoint now requires a valid token
    (closing TECH-002), and Actuator is excluded from the public gateway surface.
  - Local development runs without AWS: `docker compose up` now includes a mock OIDC
    service (`ghcr.io/navikt/mock-oauth2-server:5.0.2`, config in
    `docker/mock-oidc-config.json`); the `dev` profile defaults the issuer to
    `http://localhost:9000/default`. See CONTRIBUTING.md for token commands.
  - New dependencies: `spring-boot-starter-oauth2-resource-server`;
    `spring-security-test` and `spring-boot-webmvc-test` (test scope — Spring Boot 4
    moved `@AutoConfigureMockMvc` into the dedicated `spring-boot-webmvc-test` module).
  - Infrastructure layers formalized as TECH-130 (Cognito), TECH-131 (API Gateway),
    TECH-132 (private networking) — pending, tracked in the backlog.
  - Merged via PR #17 (2026-07-15). **Post-merge validation (2026-07-15):** manual
    end-to-end check of the full Docker Compose stack against the mock OIDC issuer,
    9/9 green — token issuance, `token_use=access`, `scope` claim, issuer coherence,
    `401` (no/invalid/tampered token), `403` (missing scope, both ingestion↔audit
    directions), `2xx` (correct scope), `/actuator/health` public with
    `/actuator/info`/`/actuator/metrics` token-protected, and default deny on
    undeclared routes. No changes to `docker/mock-oidc-config.json` or the Spring
    Security implementation were required. Evidence recorded in ADR-002 and TECH-001.

### Added

- **TECH-120 — CI pipeline** (`.github/workflows/ci.yml`). Every pull request and every
  push to `main` now runs `./mvnw clean verify` on GitHub Actions (Temurin JDK 25, Maven
  Wrapper, Maven dependency cache). The Testcontainers-based Flyway migration gate
  (`FlywayMigrationsTest`, ADR-009) executes against the runner's Docker, and a dedicated
  guard step fails the pipeline if that suite is skipped — the local-only Docker self-skip
  can no longer void the gate. Superseded runs of the same branch/PR are cancelled;
  `GITHUB_TOKEN` is restricted to `contents: read`; no secrets or `.env` are used; on
  failure the surefire/failsafe reports are uploaded as a `test-reports` artifact.
  Documented in `CONTRIBUTING.md` (Continuous Integration section) and
  `docs/development/development-workflow.md` (Step 6). Closes post-migration
  recommendation #3 of the Spring Boot 4 migration. Merged via PR #16 (2026-07-15);
  first `main` run green with the migration gate confirmed executed (`tests=4`,
  `skipped=0`).

- `src/main/resources/application-dev.yaml` — new `dev` profile holding everything that is
  convenient locally but must not reach production: default database credentials
  (`sipsa_user`/`sipsa_pass`), verbose per-package log levels, `format_sql`, full health
  details (`show-details: always`), and the Actuator `loggers` endpoint.
- **[ADR-009](docs/adr/ADR-009-database-migration-strategy.md)** — database migration
  strategy: Flyway confirmed as the only migration tool (Liquibase evaluated and
  rejected), with binding conventions (immutable applied migrations, strict ordering,
  fix-forward, expand–migrate–contract for destructive changes). Day-to-day workflow in
  [docs/development/database-migrations.md](docs/development/database-migrations.md).
- `FlywayMigrationsTest` — migration gate on Testcontainers: applies the full migration
  chain against a real PostgreSQL 18 container (same image as `docker-compose.yml`) and
  boots the full context with `ddl-auto: validate`, failing on broken SQL, on a missing
  Flyway auto-configuration (the 2026-07-14 regression), and on entity/schema drift.
  Skipped automatically when Docker is unavailable. New test dependencies (managed by
  the Spring Boot BOM): `spring-boot-testcontainers`, `testcontainers-postgresql`,
  `testcontainers-junit-jupiter`. Suite: 69 tests (was 65).
- Flyway hardening in `application.yaml`: `validate-on-migrate: true`,
  `clean-disabled: true`, `out-of-order: false`, and an explicit, documented baseline
  policy (`baseline-version: 1`).
- `src/main/resources/application-docker.yaml` — explicit `docker` profile so
  `docker-compose.yml`'s `SPRING_PROFILES_ACTIVE=docker` points at a real profile instead
  of silently falling back to the base configuration. Sets only container-topology facts
  (database host defaults to the `db` service); credentials still have no defaults.

### Changed

- `src/main/resources/application.yaml` is now a production-safe baseline:
  - `DB_USERNAME`/`DB_PASSWORD` no longer have hardcoded defaults — the application fails
    fast at startup if they are missing outside the `dev` profile.
  - DANE-contractual values are fixed in the file instead of being environment variables
    (property names unchanged): `sipsa.timezone`, `sipsa.soap.namespace`, ingestion
    windows (`daily-window-start/end`, `monthly-run-days`, `monthly-window-start`), cron
    expressions, and the pagination policy.
  - Actuator no longer exposes `loggers` by default (dev-only now; partially addresses
    TECH-002) and `show-details` changed from `always` to `when-authorized`.
  - Baseline logging reduced to production-safe levels; verbose levels moved to the dev
    profile.
- `.env.example` trimmed to the variables that remain configurable (credentials,
  endpoint, timeouts, tuning knobs) and repositioned as a versioned reference template
  only — no `.env` file is read at runtime; variables are provided via the shell,
  `docker-compose`, or the deployment platform. Documents that contractual values now
  live in `application.yaml`.
- `docker-compose.yml` — database name/credentials now use `${VAR:-default}`
  interpolation (overridable from the shell without any `.env` file) and are passed
  consistently to both the `db` and `app` services; the `pg_isready` healthcheck uses the
  same interpolated values.
- `README.md` — configuration section rewritten: documents the `dev`/`docker`/base
  profile split and removes the instruction to copy `.env.example` to `.env`.

### Removed

- `req.xml` (root) — manual SOAP smoke-test artifact with embedded `curl` commands, not
  referenced by any code, build, or documentation.

### Fixed

- **TECH-111 — monthly `WindowPolicy` rules corrected** (F-WP-01/02/03, confirmed by
  TECH-110's validation; merged via PR #15, 2026-07-14). `validateMonthly()` now binds each monthly method to its own
  DANE publication rule — `promediosSipsaMesMadr`: principal day 8, grace day 9;
  `promedioAbasSipsaMesMadr`: principal day 10, grace day 11 — rejecting the cross-method
  acceptance it previously allowed, and the `monthly-window-start` time gate now applies
  to grace days too (day 9/11 at midnight is no longer accepted). The monthly `windowKey`
  is now the stable per-period marker `YYYY-MM-M8`/`YYYY-MM-M10` documented all along in
  the Javadoc, so a grace-day retry reuses the principal day's key and the
  `(method_name, window_key)` idempotency guarantee holds across retries; `force=true`
  still bypasses the window but returns the correct period key instead of the raw forced-on
  date. Rule resolution checks `abas` before `mesmadr` (the Abas method name contains both
  fragments), protected by an explicit test. `sipsa.ingestion.monthly-run-days` is
  repurposed as a startup sanity check (fails fast with `SipsaConfigurationException` if
  days 8 and 10 are missing) and no longer participates in per-run validation; property
  name unchanged. No data migration: historical raw-date monthly keys coexist safely with
  the new format. The two `@Disabled` tests from TECH-110 are re-enabled; daily/weekly
  validation, cron expressions, timezone, REST contracts, and DB schema are untouched.
  **Deployment note:** deploy outside days 8–11 — if a monthly method already succeeded in
  the current month under the old key format, one redundant (upsert-safe) re-ingestion of
  that period can occur during the transition month.

- **Flyway migrations silently never ran on Spring Boot 4** — Spring Boot 4 moved the
  Flyway auto-configuration out of `spring-boot-autoconfigure` into the dedicated
  `spring-boot-flyway` module. With only `flyway-core`/`flyway-database-postgresql` on the
  classpath, the application started against an empty database and failed Hibernate schema
  validation (`missing table [ingestion_audit]`). Undetected until now because the test
  suite disables Flyway (H2 `create-drop`). Added `org.springframework.boot:spring-boot-flyway`;
  verified against a clean PostgreSQL container: all `V1` tables created, app `UP`.
- **Docker build was broken** — the build stage referenced `maven:3.9.9-eclipse-temurin-25`,
  a tag that does not exist on Docker Hub (pending risk #1 of the Spring Boot 4 migration).
  Replaced with `eclipse-temurin:25-jdk-noble`: the Maven Wrapper already pins Maven 3.9.9,
  keeping a single source of truth for the Maven version. Full
  `docker compose build && up` verified: healthcheck `UP`, `GET /api/sipsa/ciudad` → 200.
- `.dockerignore` excluded `docker-compose.yaml` but the real file is `docker-compose.yml`;
  now covers both, plus `CHANGELOG.md`, `CONTRIBUTING.md`, `AGENTS.md`, `.github/`, `.claude/`.

### Docker

- The runtime image now sets `SPRING_PROFILES_ACTIVE=docker` as a default (overridable), so
  a standalone `docker run` fails fast on missing credentials instead of silently starting
  with the `dev` profile defaults.
- Removed the obsolete `-Djava.security.egd=file:/dev/./urandom` JVM flag (legacy workaround,
  unnecessary on Java 25) and added `--no-install-recommends` to the `curl` install.

### Testing

- **TECH-110/TECH-040** — Added a full automated validation suite for scheduled ingestion:
  `WindowPolicyTest` (25 cases, deterministic via injected `Clock`), `SipsaSchedulingCronTest`
  (18 cases validating the 3 production cron expressions with `CronExpression`, no system
  clock dependency), `SipsaIngestionSchedulerTest` (8 cases verifying dispatch, `force`,
  `requestSource`, and per-job exception isolation with `GenericIngestionJob` mocked), and
  two Spring context tests (`SipsaSchedulingContextTest`,
  `SipsaSchedulingDisabledByDefaultTest`) proving the scheduling wiring both when enabled
  and disabled. Total: 59 tests (was 1), 0 failures, 2 tests intentionally `@Disabled`
  pending TECH-111. See `docs/architecture/scheduled-ingestion-validation.md`.
- Two tests in `WindowPolicyTest` were intentionally `@Disabled` at the time of TECH-110,
  documenting the desired behavior of a confirmed-but-unfixed defect in
  `WindowPolicy.validateMonthly()` (see Findings below); they were the acceptance criteria
  for TECH-111 and have since been re-enabled by it (see Fixed above).

### Added

- `WindowPolicy` now accepts an injectable `Clock` via a package-private, test-only seam
  (`setClock`, defaults to `Clock.system(zone)` — behaviorally identical to the previous
  `ZonedDateTime.now(zone)` call). No public API or functional behavior changed.
- `sipsa.scheduling.enabled` property (default `true`, backward-compatible). Set to
  `false` to prevent Spring from registering `@EnableScheduling`'s bean post-processor at
  all, guaranteeing no `@Scheduled` method fires. Used by
  `src/test/resources/application.yaml` to disable real scheduling for the test suite by
  default.

- `com.h2database:h2` test dependency. Enables `SipsaApplicationTests.contextLoads()` to
  run without a PostgreSQL instance, making `./mvnw clean verify` self-contained.
- `src/test/resources/application.yaml` — Test configuration with H2 in-memory database,
  disabled Flyway, and minimal SOAP configuration for context load tests.
- `InternalIngestionCommandsTest` — unit tests for `IngestionRequest`, `CreateRunRequest`,
  and `AuditEventRequest` static factory methods, verifying construction behavior is
  unchanged after their move to `application.command` (TECH-090).

### Findings (not fixed at the time of TECH-110; since fixed by TECH-111 — see Fixed above and `docs/architecture/scheduled-ingestion-validation.md`)

- **Confirmed bug (F-WP-01):** `WindowPolicy.validateMonthly()` does not bind the allowed
  day-of-month to the specific ingestion method — `promedioAbasSipsaMesMadr` (documented
  day 10) currently passes validation on day 8, and `promediosSipsaMesMadr` (documented
  day 8) currently passes on day 10. The cron scheduler itself is correctly separated by
  method; only `WindowPolicy`'s independent safety check is affected.
- **Confirmed bug (F-WP-02):** the monthly `windowKey` is the raw run date
  (`yyyy-MM-dd`), not the `YYYY-MM-M8`/`YYYY-MM-M10` format documented in `WindowPolicy`'s
  own Javadoc, so a retry on the grace day (e.g., day 9 after a day 8 attempt) mints a new
  key instead of reusing the one for the same logical period — breaking the
  `(method_name, window_key)` idempotency guarantee across grace-day retries.
- Both were tracked as TECH-111, formalized in `docs/backlog/technical-backlog.md` with an
  approved implementation plan and since implemented (see the TECH-111 entry under Fixed).

### Changed

- **Java 21 → Java 25 (LTS)**. Runtime updated to Eclipse Temurin 25.0.3. Maven compiler
  plugin updated from `<source>/<target>` to `<release>25</release>` to enforce API boundaries.

- **Spring Boot 3.5.9 → Spring Boot 4.1.0**. Requires Java 17+ (Java 25 used).
  Pulls in Spring Framework 7, Hibernate 7.4.1, and Jackson 3.

- **Spring Framework 6.x → Spring Framework 7.x** (managed by Spring Boot 4.1.0).
  Deprecated `HttpStatus.UNPROCESSABLE_ENTITY` replaced with `HttpStatus.UNPROCESSABLE_CONTENT`
  (RFC 9110 alignment).  
  Deprecated `@NonNull` from `org.springframework.lang` removed from `TimezoneFilter`
  (Spring 7 migrates to JSpecify internally).

- **Spring Cloud 2025.0.0 (Northfields) → Spring Cloud 2025.1.2 (Oakwood)**. Compatible with
  Spring Boot 4.1.x.

- **Apache CXF 4.1.4 → 4.2.2**. Adds Jakarta EE 11 support and Spring Boot 4 / Spring
  Framework 7 compatibility.

- **Hibernate 6.x → Hibernate 7.4.1 Final** (managed by Spring Boot 4.1.0).
  Hibernate 7 requires an actual JDBC connection to auto-detect the database dialect.
  Added `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect` to
  `application.yaml` to provide the dialect explicitly.

- **Jackson 2.x → Jackson 3.x** (managed by Spring Boot 4.1.0).
  The `jackson-annotations` module retains `com.fasterxml.jackson.annotation` package names
  in Jackson 3. No source changes were required for annotation imports.

- **Actuator health package relocated**. `org.springframework.boot.actuate.health.{Health,
  HealthIndicator}` moved to `org.springframework.boot.health.contributor` in Spring Boot 4.
  `SipsaHealthIndicator` updated accordingly.

- **Resilience4j explicit starters removed**. `resilience4j-spring-boot3` and
  `resilience4j-spring6` direct dependencies removed. Resilience4j is now sourced exclusively
  via `spring-cloud-starter-circuitbreaker-resilience4j` managed by Spring Cloud BOM.
  Note: Spring Cloud 2025.1.2 still pulls `resilience4j-spring-boot3:2.3.0` transitively;
  its health indicator auto-configurations are silently skipped by `@ConditionalOnClass` on
  Spring Boot 4 (no runtime impact).

- **Docker images updated**.
  Build stage: `maven:3.9.9-eclipse-temurin-21` → `maven:3.9.9-eclipse-temurin-25`.
  Runtime stage: `eclipse-temurin:21-jre-jammy` → `eclipse-temurin:25-jre-noble`.

- **Maven Wrapper recreated**. `.mvn/wrapper/maven-wrapper.properties` was missing from the
  repository. Recreated pointing to Maven 3.9.9. File removed from `.gitignore`.

- **Internal ingestion commands moved out of the HTTP DTO package** ([ADR-007](docs/adr/ADR-007-package-boundaries-and-internal-models.md),
  TECH-090). `IngestionRequest`, `CreateRunRequest`, and `AuditEventRequest` moved from
  `api.dto.request` to `application.command` — they were never bound from an HTTP request
  and are internal to the ingestion/audit pipeline. Import-only change in the 6 consumer
  classes; no REST route, JSON body, or HTTP status changed.

- **`TimezoneFilter` relocated to the API layer** (ADR-007, TECH-091). Moved from
  `infrastructure.config` to `api.filter` — it is an HTTP request filter, not a technical
  config class, and was the codebase's only `infrastructure → api` dependency. Package
  declaration only; behavior, filter order, headers, `ThreadLocal` lifecycle, and Spring
  bean registration are unchanged.

- **`SoapGateway` no longer references an infrastructure class** (ADR-007, TECH-095).
  Removed the `SoapGatewayImpl` import used only for a Javadoc `@see` tag — the codebase's
  only `domain → infrastructure` dependency. Javadoc-only change.

### Fixed

- `SipsaHealthIndicator` import updated from `org.springframework.boot.actuate.health` to
  `org.springframework.boot.health.contributor` after package relocation in Spring Boot 4.

- `HttpStatus.UNPROCESSABLE_ENTITY` → `HttpStatus.UNPROCESSABLE_CONTENT` in
  `GlobalExceptionHandler` (deprecated in Spring Framework 7 per RFC 9110).

- `@NonNull` annotation from `org.springframework.lang` removed from
  `TimezoneFilter.doFilterInternal()` (deprecated in Spring Framework 7).

- `spring.jpa.database-platform: org.hibernate.dialect.PostgreSQLDialect` added to
  `application.yaml`. Without this, Hibernate 7 cannot start without a database connection
  to auto-detect the dialect (unlike Hibernate 6 which inferred it from the JDBC URL).

- `SchedulingConfig`'s Javadoc incorrectly stated the two monthly ingestion jobs run at
  "06:00 COT"; the actual `@Scheduled` cron expressions fire at 14:30 COT (06:00 was
  `WindowPolicy`'s unrelated Java-level fallback default for `monthly-window-start`, never
  what `application.yaml` or the cron itself configure). Comment-only fix, no behavior
  change. Found during TECH-110's scheduling inventory.

### Documentation

- `docs/architecture/architecture-review.md` — Full architectural review with 25 accepted
  findings, evidence methodology, transaction boundary diagram, and accepted/discarded
  recommendations.
- `docs/architecture/technical-debt.md` — 28-item debt registry classified by area and priority.
- `docs/architecture/refactoring-roadmap.md` — 10 deferred refactorings with justification
  and conditions for revisiting.
- `docs/architecture/implementation-roadmap.md` — 6-phase implementation plan.
- `docs/architecture/testing-strategy.md` — Test pyramid, mandatory test cases, tooling strategy.
  Updated to reflect the `WindowPolicyTest`/`SipsaSchedulingCronTest`/`SipsaIngestionSchedulerTest`/
  scheduling-context tests actually implemented by TECH-110.
- `docs/architecture/scheduled-ingestion-validation.md` — Full validation of the scheduled
  ingestion pipeline: job inventory, cron table, `WindowPolicy` contrast, DANE schedule
  matrix (with 2020 currency caveat), concurrency analysis, and classified findings
  (TECH-110).
- `docs/architecture/timezone-locale-date-strategy-review.md` — Temporal inventory (all
  `Instant`/`LocalDate`/`OffsetDateTime` fields across entities, DTOs, and infrastructure),
  a contrast matrix against DANE's documented SOAP method semantics (with the March-2020
  currency caveat), an evaluation of `TimezoneFilter`/`WindowPolicy`, and a comparison of
  four timezone/locale strategy alternatives. Evidence for ADR-008.
- `docs/adr/ADR-008-timezone-locale-and-date-semantics.md` — Proposed strategy for
  timezone, locale, and date-semantics handling across the API. **Status: Proposed, not
  accepted.** No code changed as part of this documentation.
- `docs/adr/ADR-000-current-architecture.md` — Architecture snapshot after migration.
- `docs/adr/ADR-001` through `ADR-006` — Architecture decision records (ADR-004 accepted;
  ADR-001, ADR-002, ADR-003, ADR-005, ADR-006 proposed).
- `docs/backlog/technical-backlog.md` — Prioritized technical stories with acceptance
  criteria (36 as of 2026-07-13; the count grows as validations produce new stories).
- `docs/migrations/spring-boot-4-java-25.md` — Migration notes, breaking changes, validation.
- `CHANGELOG.md` — This file.
- `CONTRIBUTING.md` — Developer guide for contributions.
- `.github/` — Issue templates and PR template. (The "no CI workflow exists yet" caveat
  originally recorded here was resolved by TECH-120 — see the Added section above.)

### Fixed

- **ADR-008 F1 — `fechaCaptura`, `fechaMesIni` (`SipsaMayoristasMensual` and
  `SipsaAbastecimientosMensual`), `fechaIni`, and `enmaFecha` now serialize as
  `LocalDate`, not `OffsetDateTime`.** These are DANE calendar/period-start dates, not
  instants; the prior mapping (`TimezoneUtil.convertToOffsetDateTime(value,
  isSystemGenerated=false)`) only pinned them to UTC by convention, leaving a latent
  date-shift risk if a stored value was ever anything other than exact Bogotá midnight.
  New `TimezoneUtil.toBusinessLocalDate(Instant)` always resolves the calendar day in a
  fixed `America/Bogota` zone, ignoring both the request's `X-Timezone` and UTC — the
  five affected mappers (`SipsaCiudadMapper`, `SipsaParcialMapper`,
  `SipsaMayoristasMensualMapper`, `SipsaMayoristasSemanalMapper`,
  `SipsaAbastecimientosMensualMapper`) now call it for these fields only;
  `fechaCreacion`/`fechaSincronizacion` are unaffected. **Contract change:** these five
  JSON fields change format from `"2026-07-15T00:00:00Z"` to `"2026-07-15"`. New tests:
  `TimezoneUtilTest`, `SipsaCiudadMapperTest`.

- **ADR-008 F4 — `TimezoneFilter` now rejects an invalid `X-Timezone` header with `400
  SIPSA_INVALID_TIMEZONE`** instead of silently falling back to UTC. An absent header is
  unchanged (still defaults to UTC — the intended behavior for an international API);
  only a header that is present but not a valid IANA zone ID is now rejected, in the
  same `GlobalExceptionHandler.ErrorResponse` JSON shape as every other API error.
  Serialized by hand rather than via a Jackson `ObjectMapper`, since
  `jackson-datatype-jsr310` is `test`-scope only in this repo and a Spring-managed
  `ObjectMapper` bean isn't reliably present in every servlet-filter context. New test:
  `TimezoneFilterValidationTest`.

- **ADR-008 F5 — `GlobalExceptionHandler`'s three error-response `timestamp` fields
  switch from `LocalDateTime` to `OffsetDateTime`**, converted via
  `TimezoneUtil.convertToOffsetDateTime(Instant.now(), true)` like every other
  system-generated timestamp in the API (honors the request's `X-Timezone`). Previously
  ambiguous (no explicit offset), inconsistent with the rest of the JSON contract.

  Implements items 3, 5, and 6 of ADR-008's proposed decision; **ADR-008 itself remains
  `Proposed`, not `Accepted`** — items 4 (`WindowPolicy` F2/F3) and 7 (i18n, explicitly
  deferred) are unimplemented. No TECH-1xx ID is attached to this entry since the
  backlog stories ADR-008 references (TECH-100–TECH-106) haven't been created with real
  IDs yet.
