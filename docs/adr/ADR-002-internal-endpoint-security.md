# ADR-002 — Internal Endpoint Security

**Status:** Accepted (2026-07-15)  
**Date:** 2026-07-13 (proposed) · 2026-07-15 (accepted, superseding the original Option A recommendation)  
**Backlog:** [TECH-001](../backlog/technical-backlog.md#tech-001), [TECH-002](../backlog/technical-backlog.md#tech-002),
[TECH-130](../backlog/technical-backlog.md#tech-130), [TECH-131](../backlog/technical-backlog.md#tech-131),
[TECH-132](../backlog/technical-backlog.md#tech-132)

---

## Context

Two REST controllers expose operational endpoints under `/api/internal/**`
(trigger/cancel/query ingestion runs; read the full audit trail) with no authentication.
The public functional API (`GET /api/sipsa/**`, read-only DANE data) and Actuator complete
the HTTP surface.

The deployment target is **AWS**: API Gateway as the single entry point, Cognito as the
identity provider, and the service on ECS. This supersedes the assumption under which the
original proposal recommended in-application HTTP Basic (former Option A).

---

## Problem

An unauthenticated actor with HTTP access can trigger ingestion jobs against DANE's SOAP
service (including `force=true`, bypassing publication windows), cancel scheduled
ingestion, and read the operational audit trail. Additionally, consumers of the functional
API cannot be identified, metered, throttled, or revoked individually.

---

## Alternatives Considered

- **Option A — HTTP Basic with an in-memory user (original recommendation).** Rejected as
  the definitive solution: single shared credential (no per-consumer identity, quotas, or
  revocation), password lifecycle owned by the application, and redundant once the AWS
  target was confirmed.
- **Option B — Custom API-key filter in Spring.** Rejected: hand-rolled security surface;
  API Gateway provides key validation, usage plans, and throttling natively. Keys are kept
  as an identification/metering mechanism at the gateway — never authentication.
- **Option C — Network-level restriction only.** Insufficient alone (no identity, no
  authorization granularity); adopted as one layer of the accepted model.
- **Option D — mTLS/service mesh.** Overkill at current scale.
- **Option E — Layered model: API Gateway + Cognito JWT + Resource Server (accepted).**

## Decision

**Option E — a layered security model:**

1. **API Gateway** is the only public entry point. Functional endpoints
   (`GET /api/sipsa/**`) require a **per-consumer API key** for identification, usage
   plans, quotas, throttling, revocation, and consumption traceability
   ([TECH-131](../backlog/technical-backlog.md#tech-131)). An API key is **not**
   authentication and grants access to nothing sensitive.
2. **Cognito** authenticates callers of sensitive operations with **JWT access tokens**
   carrying custom scopes from the `sipsa` resource server
   ([TECH-130](../backlog/technical-backlog.md#tech-130)). Machine-to-machine integrations
   use `client_credentials` app clients (one per integration); human operators use the
   authorization-code flow. AWS-native automation may alternatively use **IAM (SigV4)**
   authorizers at the gateway.
3. **Spring Boot is an OAuth 2.0 Resource Server** and re-validates every JWT and its
   scopes as **defense in depth** (implemented by this ADR's acceptance): issuer +
   signature + expiry, `token_use == "access"` (Cognito ID tokens are rejected), an
   optional `client_id` allowlist (`SIPSA_JWT_ALLOWED_CLIENT_IDS`), and per-operation
   scopes:

   | Operation | Scope |
   |---|---|
   | `POST /api/internal/ingestion/run` | `sipsa/ingestion.execute` |
   | `POST /api/internal/ingestion/cancel/{runId}` | `sipsa/ingestion.cancel` |
   | `GET /api/internal/ingestion/**` | `sipsa/ingestion.read` |
   | `GET /api/internal/audit/**` | `sipsa/audit.read` |

   Everything not explicitly declared is **denied**. The chain is stateless: no sessions,
   no CSRF surface, no form login, no HTTP Basic, no cookies. `401`/`403` are JSON in the
   API's `ErrorResponse` shape, with generic messages that never reveal the failure cause.
4. **Private integration** keeps the backend unreachable except through the gateway: ECS
   in private subnets, internal ALB, API Gateway via VPC Link
   ([TECH-132](../backlog/technical-backlog.md#tech-132)). For the IAM path (no
   app-level re-validation possible), this layer is the enforcing control.
5. **Actuator is not part of the public API surface.** It is never routed through API
   Gateway. `/actuator/health` is unauthenticated for container/platform healthchecks
   (reachable only inside the private network); every other Actuator endpoint requires a
   valid access token, on top of the existing per-profile exposure restriction.

**Tier "API key + JWT" is intentionally empty today:** all current functional endpoints
serve public read-only DANE data. The tier exists in the model for future endpoints
exposing per-client or writable data — do not add endpoints to it implicitly.

## Local development

A mock OIDC server (`ghcr.io/navikt/mock-oauth2-server`, compose service `oidc`,
configured by `docker/mock-oidc-config.json`) is the default issuer in the `dev` and
docker-compose environments, so the project runs without AWS connectivity. A dev Cognito
user pool is used for real AWS integration testing by overriding `SIPSA_JWT_ISSUER_URI`.
The base profile has no issuer default and fails fast at startup.

## Consequences

- The application layer (item 3) is implemented and tested in this repository; items 1, 2
  and 4 are infrastructure work tracked as TECH-130/131/132 and do not block it.
- Any client calling `/api/internal/**` must now present a Cognito access token with the
  right scope; previously-unauthenticated operational scripts break by design.
- Prometheus scraping of `/actuator/prometheus` requires a valid token (or scraping via a
  sidecar/CloudWatch inside the VPC — decided in TECH-132).
- The backend holds **no secrets** for this model: the issuer URI and JWKS are public,
  and client ids are identifiers. Client secrets live with each consumer, in AWS.
- The minimum acceptable posture holds: no `/api/internal/**` endpoint is reachable
  without authentication, in any environment, regardless of gateway or network state.
