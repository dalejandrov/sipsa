# cognito module

Cognito authentication foundation for SIPSA (TECH-130). **No API Gateway, VPC Link, WAF,
Route 53, ACM, custom domain, real AWS integration, or Spring Security change is created
or made by this module or this story.** `terraform apply` is never run — this is
Terraform code only.

## Scope inventory — where each scope is actually used

Built from `grep -RIn -e 'SCOPE_' -e 'hasAuthority' -e 'hasAnyAuthority' -e 'scope'
src/main src/test docs`, not invented:

| Scope | Endpoint / operation | M2M | Humano |
|---|---|---:|---:|
| `sipsa/ingestion.execute` | `POST /api/internal/ingestion/run` | Sí | Sí |
| `sipsa/ingestion.cancel` | `POST /api/internal/ingestion/cancel/{runId}` | Sí | Sí |
| `sipsa/ingestion.read` | `GET /api/internal/ingestion/**` | Sí | Sí |
| `sipsa/audit.read` | `GET /api/internal/audit/**` | Sí | Sí |

All four are enforced today in `SecurityConfig.securityFilterChain`
(`hasAuthority("SCOPE_sipsa/...")`) — confirmed directly from the source, not assumed.
Nothing in the application restricts a scope to a specific *client type* (M2M vs. human)
— the restriction is per-operation, not per-consumer — so both app clients below are
granted the full resource-server scope set. No scope was invented without a matching
endpoint.

## Two contracts, never one shared app client

**M2M (`aws_cognito_user_pool_client.m2m`):** `client_credentials` grant only,
confidential (`generate_secret = true`). No `authorization_code`, no `implicit`, no
password grant. One app client is defined in this story — parameterizing the module
itself already supports "one client per future integration" by instantiating it again
with different inputs; this story does not invent a second, purely-speculative
integration to justify a `for_each` with no real second consumer yet.

**Humano (`aws_cognito_user_pool_client.human`):** `authorization_code` grant only,
public (`generate_secret = false`). **Cognito enforces PKCE automatically for a public
client on this grant** — there is no separate "require PKCE" argument on
`aws_cognito_user_pool_client`; setting `generate_secret = false` is what makes this a
public client, and Cognito's own token endpoint then requires the
`code_challenge`/`code_verifier` exchange for it. No implicit flow, no password grant.

The two clients never share scopes-vs-grant configuration, never share a secret, and are
provisioned as two independent resources — confirmed by a dedicated test
(`client_ids_are_distinct`).

## User pool defaults

- **Sign-in identifier: email** (`username_attributes = ["email"]`) — no separate
  "username" concept exists anywhere else in this system; this is the simplest choice,
  not an invented scheme.
- **Password policy:** minimum length 12 (parameterized), upper/lower/number/symbol all
  required, 7-day temporary-password validity (Cognito's own default).
- **MFA: `OPTIONAL`** by default (parameterized) — human users exist, but no operational
  enrollment/recovery flow for MFA exists yet (no frontend, no documented support
  process for a locked-out user); forcing MFA now would create an operational dead end.
  TOTP (`software_token_mfa_configuration`) is the only method wired — no SMS
  configuration, which would need an additional SNS IAM role this story does not build.
  **Document mandatory MFA as a future hardening step**, not adopted preemptively.
- **Advanced security: `AUDIT`** by default (parameterized) — risk-based visibility
  (compromised-credential checks, adaptive signals) without blocking or challenging any
  sign-in. `ENFORCED` adds friction with no evidence yet that it's needed; both `AUDIT`
  and `ENFORCED` carry a real per-MAU cost `OFF` does not — `AUDIT` is the deliberate
  middle ground for observing before deciding whether `ENFORCED` is justified.
- **`deletion_protection = "ACTIVE"`** by default (note: a string, `"ACTIVE"`/`"INACTIVE"`,
  not a bool — confirmed against the provider's own argument type) — consistent with
  this repository's RDS/ALB posture.
- **`allow_admin_create_user_only = true`** by default — SIPSA is an internal
  operational tool for a DANE-integration team, not a public-signup product; there is no
  documented requirement for open self-registration. Revisit explicitly if a real
  self-service sign-up need emerges.
- **Account recovery: verified email only** (`recovery_mechanism { name =
  "verified_email", priority = 1 }`) — the only verified attribute this pool has
  (`auto_verified_attributes = ["email"]`); no phone-based recovery is configured.

## Hosted UI domain — deliberately optional

`create_hosted_ui_domain` defaults to **false**. No frontend and no approved callback URL
exists yet, so a Hosted UI domain would have nothing real to redirect users to. The human
app client's Authorization Code + PKCE configuration is still fully created when this is
false — it simply has no reachable `/oauth2/authorize` endpoint until a domain exists.
Enabling this later is a one-variable-flip (`create_hosted_ui_domain = true`) plus
`cognito_domain_prefix` (required, globally unique, no default invented here — the same
uniqueness class as an S3 bucket name), not a restructuring. **No custom domain, no ACM
certificate, no Route 53 record** — only a Cognito-managed prefix domain is ever created
by this module.

## Callback and logout URLs — no invented placeholders

`human_callback_urls`/`human_logout_urls` are **required variables with no default**. No
frontend exists yet, so no real URL exists either — a real `terraform plan`/`apply` must
not proceed with invented values. Both variables reject any URL containing `localhost` or
`example.com` via validation, specifically so those can never be mistaken for approved
production values; they remain usable only inside this module's own offline tests, which
use a `*.invalid` hostname (RFC 2606-reserved, guaranteed never to resolve) to make the
placeholder nature unambiguous even there.

## Token validity

`access_token_validity_minutes = 60`, `id_token_validity_minutes = 60` (human client
only — an M2M `client_credentials` token carries no ID token),
`refresh_token_validity_days = 30` (human client only — `client_credentials` is not a
refresh-token-based grant; the M2M client requests a fresh access token directly with its
credentials each time one is needed). All three are Terraform variables, not hardcoded —
proposed starting values, not measured against a real operational pattern yet.

## Compatibility with the application's existing JWT validation

Confirmed by reading `SecurityConfig`/`SipsaJwtProperties`/`TokenUseValidator`/
`AllowedClientIdsValidator` (already implemented, already e2e-validated against a mock
OIDC issuer, TECH-001/ADR-002) — **no incompatibility found, no Spring Security change
made by this story**:

- **`iss`** — validated by Spring's standard issuer validator against
  `SIPSA_JWT_ISSUER_URI`. This module's `issuer_url` output
  (`https://<user_pool.endpoint>`) is the value to wire into that environment variable
  for a real deployment.
- **`exp`** — validated by the same standard validator.
- **`token_use = access`** — validated by `TokenUseValidator`; Cognito issues this claim
  on access tokens for both grants used here.
- **`client_id`** — validated by the optional `AllowedClientIdsValidator` when
  `SIPSA_JWT_ALLOWED_CLIENT_IDS` is non-empty; see "Client-ID allowlist" below for how
  this module publishes the value that variable should eventually receive.
- **`scope`** — validated per-operation by `SecurityConfig`'s `hasAuthority` matchers
  against the `SCOPE_sipsa/...` authorities Spring derives from the token's `scope`
  claim, which Cognito populates from `allowed_oauth_scopes` at token-issuance time.
- **`aud` is deliberately not checked by the application** (confirmed in
  `aws-production-readiness.md`) — Cognito `client_credentials` access tokens carry no
  `aud` claim, so this is consistent, not a gap this module needs to work around.

No defect was found, so no fixture/test/commit addressing an incompatibility was needed.

## Client-ID allowlist — design only, not wired into ECS

`publish_client_ids_to_ssm` (default `true`) publishes both app clients' IDs as a CSV in
an SSM Parameter Store **String** parameter (`/  <project>-<environment>/sipsa/jwt-allowed-client-ids`)
— client IDs are identifiers, not secrets, per `AllowedClientIdsValidator`'s own Javadoc.
**This module does not wire that parameter into `modules/ecs-task`'s task definition** —
doing so would modify an already-merged, unrelated module outside this story's scope.
That wiring (adding an SSM-sourced `SIPSA_JWT_ALLOWED_CLIENT_IDS` environment entry, and
`SIPSA_JWT_ISSUER_URI` alongside it) is a documented follow-up for whichever story next
touches the ECS task definition's environment configuration.

## M2M client secret — Secrets Manager, and why

The M2M client's secret is written directly into a dedicated Secrets Manager secret
(`aws_secretsmanager_secret_version`, sourced from
`aws_cognito_user_pool_client.m2m.client_secret`) and **never exposed as a Terraform
output** — only the secret's ARN is (`m2m_client_secret_arn`), the same pattern already
established for the RDS master secret (`modules/database`).

**Provider behavior verified, not assumed:** unlike some AWS secret-generation patterns
(e.g. an IAM access key, which truly cannot be retrieved again after creation), Cognito's
app client secret is **not** a show-once value — it remains retrievable at any time after
creation via the Cognito API (`DescribeUserPoolClient`), and Terraform's own resource
re-reads it as a computed attribute on every refresh. This means the actual risk this
design guards against is **casual exposure via `terraform output` or a state-viewing tool
with insufficient access control** — not "losing an unrecoverable secret." Writing it into
Secrets Manager, gated by IAM (`secretsmanager:GetSecretValue` on this specific ARN,
granted explicitly to whichever principal needs it — e.g. a partner team's own role, or a
future ECS task role once the application-side wiring above is done), is the actual
control; the ARN alone grants nothing.

**Distribution to the consumer:** not automated by this story. Whoever owns the M2M
integration (this repository's own future ECS task role, or a partner team) is granted
read access to this specific secret ARN explicitly, when that consumer is real — never
distributed by copying the value out of Terraform state or console output.

**No automatic rotation is implemented** — evaluate this once a real distribution
mechanism for a rotated secret exists; rotating without one would silently break every
consumer of the M2M client at once (the same reasoning already documented for RDS's
master-password rotation in `modules/database`).

## Testing

`tests/cognito.tftest.hcl` uses Terraform's native `terraform test` with a mocked AWS
provider (`mock_provider "aws" {}`) — no real AWS account or credential is contacted, no
token is ever requested, no Hosted UI is ever reached, and no Cognito user is ever
created. Run with `terraform test` from this module's directory (after `terraform init
-backend=false`).
