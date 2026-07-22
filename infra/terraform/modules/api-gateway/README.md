# api-gateway module

Private API Gateway REST API foundation for SIPSA (TECH-131). **No custom domain, ACM
certificate, Route 53 record, or WAF is created by this module or this story. No real
consumer, real token, or real API key value is ever requested. `terraform apply` is
never run.**

## Architecture correction: REST API VPC Links are NLB-only

ADR-010's original diagram ("API Gateway → VPC Link → ALB interno") undersimplified a
real AWS constraint, confirmed against the provider's own resource docs, not assumed:
`aws_api_gateway_vpc_link` (the **classic REST API** VPC Link, as opposed to
`aws_apigatewayv2_vpc_link` for HTTP APIs) accepts **only a Network Load Balancer ARN**
in `target_arns` — never an Application Load Balancer directly, and only one NLB per
VPC Link.

This module therefore creates a small NLB (`aws_lb`, `load_balancer_type = "network"`)
solely to front the VPC Link, and registers the **existing internal ALB** (TECH-141,
`modules/ecs-service`) as that NLB's single target, using AWS's documented
"Application Load Balancer as a target of a Network Load Balancer" pattern
(`aws_lb_target_group` with `target_type = "alb"` + `aws_lb_target_group_attachment`).
Real topology:

```
Client → API Gateway REST API → VPC Link → NLB → (target_type=alb) → internal ALB → ECS Service
```

Constraints this module respects, all AWS-documented, not invented: exactly one ALB per
`alb`-type target group; the target group's port must match the ALB listener's port
exactly (both `80` here); health checks are forwarded to the ALB and then to its own
target group, using the same `/actuator/health` path already used at the ALB→ECS layer
— not a new health-check contract.

**No security group on the NLB.** AWS's own documentation does not state whether an
`alb`-type NLB target group preserves the original caller's source IP when forwarding to
the ALB, so this module does not attempt to scope NLB-level ingress by IP. The real
access-control boundary remains the ALB's own security group
(`modules/ecs-service`) — this module's design does not widen it (see "Wiring the ALB's
ingress" below).

## Responsibility separation (unchanged from ADR-002 §3)

```
Cognito:          identity and authorization (who is this caller, what can they do)
API key:           operational identification of a /api/sipsa/** consumer, for metering
Usage plan:         throttling and quota, never authentication
Spring Security:    final, defense-in-depth validation of the access token and its scopes
```

API keys are never authentication — a request without an API key is simply
un-metered/un-attributable, not implicitly trusted (`/api/sipsa/**` is public
functionally regardless of key). The Cognito authorizer at the gateway does not replace
Spring Security's own re-validation (`SecurityConfig`, `TokenUseValidator`,
`AllowedClientIdsValidator`) — both layers run, independently, on every
`/api/internal/**` request, per ADR-002's defense-in-depth design: a gateway bypass
(direct access to the ALB from inside the VPC) still hits a backend that re-validates
the token itself.

## Endpoint inventory (grep-sourced, not invented)

Built from `grep -RIn --exclude-dir=.git -e '@RequestMapping' -e '@GetMapping' -e
'@PostMapping' -e '@PutMapping' -e '@DeleteMapping' src/main/java`, cross-referenced
against `SecurityConfig`'s own authorization matchers:

| Ruta | Método | Scope | API key | Throttling | Público |
|---|---|---|---:|---|---:|
| `GET /api/sipsa` (root discovery) | GET | — | Sí | general | Sí |
| `GET /api/sipsa/{proxy+}` (ciudad, mayoristas/mensual, parcial, mayoristas/semanal, abastecimientos/mensual) | GET | — | Sí | general | Sí |
| `POST /api/internal/ingestion/run` | POST | `sipsa/ingestion.execute` | No | **ingestion (1/2)** | No |
| `GET /api/internal/ingestion/methods` | GET | `sipsa/ingestion.read` | No | general | No |
| `POST /api/internal/ingestion/cancel/{runId}` | POST | `sipsa/ingestion.cancel` | No | **ingestion (1/2)** | No |
| `GET /api/internal/ingestion/running` | GET | `sipsa/ingestion.read` | No | general | No |
| `GET /api/internal/ingestion/runs` | GET | `sipsa/ingestion.read` | No | general | No |
| `GET /api/internal/ingestion/runs/{runId}` | GET | `sipsa/ingestion.read` | No | general | No |
| `GET /api/internal/audit/request/{requestId}` | GET | `sipsa/audit.read` | No | general | No |
| `GET /api/internal/audit/run/{runId}` | GET | `sipsa/audit.read` | No | general | No |
| `GET /api/internal/audit/recent` | GET | `sipsa/audit.read` | No | general | No |
| `GET /api/internal/audit/all` | GET | `sipsa/audit.read` | No | general | No |
| `GET /actuator/health` | GET | — | — | — | **never routed through the gateway at all** (ADR-002 §5) |

`GET /api/sipsa/**` is a single `{proxy+}` resource, not five near-identical GET
resources — Spring's own `@RequestMapping` already owns the real routing; this module's
job is edge concerns (API key, throttling, logging), not re-declaring routes the
backend already declares. `/api/internal/**` gets one explicit resource per real leaf
route instead, because each one needs a **different, exact** `authorization_scopes`
value — a single catch-all here would either over-grant (any one scope unlocks every
route) or require verifying AWS's AND-vs-OR semantics for multiple
`authorization_scopes` on one method, which this module does not assume.

## Throttling and quota (ADR-010 approved values)

- **General tier** (everything except the two ingestion-trigger routes): 10 req/s,
  burst 20, applied stage-wide via `aws_api_gateway_method_settings` with
  `method_path = "*/*"`.
- **Ingestion-trigger tier** (`POST run`, `POST cancel/{runId}` only): 1 req/s, burst 2,
  via a per-route `aws_api_gateway_method_settings` override.
- **Monthly quota**: 100,000 requests, `period = "MONTH"`, on the general usage plan —
  applies only to API-key-bearing `/api/sipsa/**` consumers. `/api/internal/**` carries
  no quota concept, because usage plans are inherently API-key-scoped and
  `/api/internal/**` never requires a key (ADR-010) — its own throttling is enforced
  purely via `aws_api_gateway_method_settings`, independent of any usage plan.
- **Best-effort, not an absolute cost barrier**: usage plans and method throttling
  protect operational stability; they do not replace real cost monitoring or a WAF-level
  defense (out of scope here).
- **Gap, not verified empirically by this story** (no `terraform apply` is ever run):
  `aws_api_gateway_method_settings`'s `method_path` format for the ingestion-trigger
  override is built as `"{resource_path}/{HTTP_METHOD}"` with no leading slash, per AWS's
  documented format — confirm this against a real deployed API before relying on it.

## API keys

One parameterizable `aws_api_gateway_api_key` (`var.api_key_name`, default
`"sipsa-primary-consumer"`) — the same "one client now, extensible later" precedent
`modules/cognito` already established for its M2M app client, not several speculative
consumers invented here. No `value` argument is set; AWS generates it. The provider
schema marks the generated `value` attribute `sensitive = true` (confirmed against the
provider's own Go source, not assumed) — this module never reads or outputs it (see
`outputs.tf`: only `api_key_ids`, never a value). **Retrieval, when a real consumer
exists**: `aws apigateway get-api-key --api-key <id> --include-value`, gated by IAM
(`apigateway:GET` on the specific key resource), never via `terraform output` or state
inspection — the same state-sensitivity posture documented in `modules/cognito/README.md`
for the Cognito M2M secret applies here too (the value lives in Terraform state
regardless of how it's distributed operationally).

## Access logs

Structured JSON (`aws_api_gateway_stage.access_log_settings`), 30-day retention by
default (`var.access_log_retention_days`). Fields: `requestId` (API Gateway's own
correlation ID — **not** the application's `ErrorResponse.requestId`, TECH-023; the two
are never the same value, since only app-originated errors ever reach the application's
own request-ID generation), `extendedRequestId`, `sourceIp`, `requestTime`,
`httpMethod`, `resourcePath`, `status`, `responseLength`, `integrationStatus`,
`integrationLatency`, `authorizer.principalId`/`authorizer.claims.sub` (Cognito subject,
not the token itself), `apiKeyId` (the key's ID, never its value). **Never logged**: the
`Authorization` header, the API key value, or any request/response body
(`data_trace_enabled = false` on every `aws_api_gateway_method_settings`, deliberately,
everywhere).

An account-level CloudWatch role (`aws_api_gateway_account.cloudwatch_role_arn`) is
created and set — a well-known, real-world prerequisite for API Gateway access/execution
logging to deliver anything at all, even though not stated as a hard requirement in the
resource's own Terraform docs. **This is an AWS-account-level singleton** (the resource
has no `rest_api_id` — it configures the account, not this specific API): safe to create
once in this repository's single, dedicated AWS account (ADR-010), but would need to be
shared/imported rather than re-declared if a second API Gateway stack is ever added to
this account.

## Error responses — Gateway-originated vs. application-originated

`aws_api_gateway_gateway_response` covers `UNAUTHORIZED` (401), `ACCESS_DENIED` (403),
`THROTTLED` (429), and `DEFAULT_5XX`, each with a small, consistent JSON body
(`status`/`message`/`requestId` — API Gateway's own `requestId`, not the
application's). **This is deliberately not the same shape as the application's own
`GlobalExceptionHandler.ErrorResponse`** (`timestamp`/`status`/`error`/`code`/`message`/
`requestId`/`instance`) — replicating that exact shape at the gateway would diverge the
moment either side changes independently, and the gateway cannot populate fields like
the application's own `requestId`/`instance` for a request that never reached the
backend. Errors the **application itself** produces (400/404/500/502, its own
`ErrorResponse` shape) pass through the `HTTP_PROXY` integration unmodified — proxy
integrations forward the backend's status and body as-is.

## Timeouts

Confirmed by reading `SipsaOpsController`: `POST /api/internal/ingestion/run` returns
`ResponseEntity.accepted()` (**202**) synchronously and fast — the actual ingestion runs
asynchronously (TECH-053), so API Gateway's own integration timeout is never at risk.
`POST /api/internal/ingestion/cancel/{runId}` is a synchronous, fast DB-status update,
same conclusion. No endpoint in this application waits for a long-running operation
inline — confirmed by inspection, not assumed; no timeout override or workaround was
needed.

## CORS — undecided, natively-limited by design

`var.cors_allowed_origins` defaults to empty (disabled) —
`docs/architecture/aws-production-readiness.md` §1.6 confirms no browser-client
requirement has been established anywhere in this repository. When exactly one origin
is configured, this module adds a static `Access-Control-Allow-Origin` response header
(and `Access-Control-Allow-Credentials` when `var.cors_allow_credentials` is true) to
both public `GET /api/sipsa` methods via `aws_api_gateway_method_response`/
`integration_response`. **Capped at one origin by variable validation**: API Gateway's
native (non-Lambda) response headers only support a fixed value, not a per-request
dynamic echo of the caller's `Origin` header across multiple allowed origins — that would
require a Lambda proxy integration, not adopted speculatively while zero real origin is
confirmed. `cors_allowed_origins` never accepts `"*"` combined with
`cors_allow_credentials = true` — forbidden by the CORS specification itself, not just
this module's policy. No frontend domain is ever invented here.

## Module

`infra/terraform/modules/api-gateway/` (`main.tf`, `variables.tf`, `outputs.tf`,
`versions.tf`, `README.md`, `tests/`). No third-party public module.
`environments/production/main.tf` consumes it, wiring `module.ecs_service.alb_arn`,
`module.network.vpc_id`/`private_app_subnet_ids`, and `module.cognito.user_pool_arn`/
`resource_server_identifier` — this module has no direct dependency on any of those
modules' internals, only their declared outputs.

## Wiring the ALB's ingress (in `environments/production`, not this module)

This module does not touch `modules/ecs-service`'s security group at all. The root
(`environments/production/main.tf`) passes the NLB's own private subnet CIDRs
(`var.private_app_subnet_cidrs`, the same value already passed to `module.network`)
into `module.ecs_service.alb_allowed_ingress_cidr_blocks` — the CIDR fallback TECH-141
already built and documented for exactly this situation, chosen over the
security-group-reference mechanism (`alb_allowed_ingress_security_group_ids`) because
this module deliberately does not give the NLB a security group of its own (see
"Architecture correction" above) and AWS's docs do not confirm source-IP preservation
behavior for `alb`-type NLB targets. `0.0.0.0/0` remains rejected outright by that
variable's own validation (TECH-141) — this module never widens the ALB beyond the
private subnets the NLB itself lives in.

## Outputs

`rest_api_id`, `rest_api_arn`, `execution_arn`, `invoke_url` (the AWS-managed
`execute-api` endpoint — not sensitive, it's the intended public entry point),
`stage_name`, `vpc_link_id`, `usage_plan_id`, `api_key_ids` (IDs only, never values),
`access_log_group_name`, `authorizer_id`.

## Testing

`tests/api-gateway.tftest.hcl` — 21 cases, `terraform test` against a fully mocked AWS
provider (`mock_provider "aws" {}`), no real AWS account contacted, no VPC Link ever
reachable, no token ever requested, no API key ever retrieved. Covers: REST API created;
VPC Link targets the NLB (not the ALB); the NLB's target group is `alb`-typed and its
one target is the given ALB ARN, port-matched; Cognito authorizer configured correctly;
every `/api/internal/**` route requires its exact single scope, never a shared set;
`/api/internal/**` never requires an API key and is always Cognito-authorized;
`/api/sipsa/**` always requires an API key and is never Cognito-authorized; usage plan
matches the ADR-010 general tier (throttle + quota); exactly the two ingestion-trigger
routes carry the strict 1/2 tier; access logs configured with 30-day retention and never
reference the Authorization header, an API key value, or enable `data_trace_enabled`;
all four gateway responses exist with the right status codes; stage is `production`; the
API key output exposes only the ID; common tags applied; CORS disabled by default,
correctly reflects a single configured origin plus credentials flag, and rejects both
more than one origin and a wildcard-plus-credentials combination. Criteria for "no
custom domain, no Route 53, no ACM, no WAF" are confirmed structurally by reading this
module's own source (no `aws_api_gateway_domain_name`/`aws_route53_*`/`aws_acm_*`/
`aws_wafv2_*` resource exists anywhere in it), not as runtime assertions.
