# ADR-002 — Internal Endpoint Security

**Status:** Proposed  
**Date:** 2026-07-13  
**Backlog:** [TECH-001](../backlog/technical-backlog.md#tech-001), [TECH-002](../backlog/technical-backlog.md#tech-002)

---

## Context

Three REST controllers expose operational endpoints under `/api/internal/**`:

- `POST /api/internal/ingestion/run` — triggers an ingestion job that calls the DANE SOAP service.
- `POST /api/internal/ingestion/cancel/{runId}` — cancels an active ingestion run.
- `GET /api/internal/ingestion/runs` — lists all ingestion runs.
- `GET /api/internal/audit/**` — reads ingestion audit trail.

These endpoints have no authentication or authorization. The source code contains an explicit
`TODO` acknowledging this:

```java
// SipsaOpsController.java:33
// TODO: This controller MUST be protected in production environments
// (e.g. Spring Security, IP allowlist, internal network only).
```

The project currently has no `spring-security-web` dependency.

---

## Problem

An unauthenticated actor with HTTP access to the service can:
- Trigger repeated ingestion jobs, generating load on DANE's SOAP service.
- Cancel running ingestion processes, disrupting scheduled data collection.
- Read the complete operational audit trail.

---

## Alternatives Considered

### Option A — HTTP Basic Authentication with Spring Security (Recommended)

Add `spring-boot-starter-security`. Configure a `SecurityFilterChain` that:
- Requires HTTP Basic Auth for `/api/internal/**`.
- Permits all requests to `/api/sipsa/**` and `/actuator/health`.
- Configures credentials via environment variables (`INTERNAL_API_USERNAME`, `INTERNAL_API_PASSWORD`).

**Pros:** Implemented entirely in the application; no infrastructure dependency; standard Spring Boot pattern.  
**Cons:** Credentials must be managed securely in environment variables or a secrets manager.

### Option B — API Key Header

Custom `OncePerRequestFilter` that validates an `X-API-Key` header against a configured value.

**Pros:** Simple implementation without Spring Security; easy for automation scripts.  
**Cons:** Non-standard; less tooling support; credential rotation requires restart.

### Option C — Network-Level Restriction (IP allowlist or private network)

Configure the ingress controller (Kubernetes, nginx, cloud load balancer) to only allow access
to `/api/internal/**` from specific IP ranges or within the cluster network.

**Pros:** No application-level code change; defense-in-depth with Option A.  
**Cons:** Infrastructure-dependent; not enforced if the service is accessed from within the same network by an unauthorized actor; harder to test in development.

### Option D — mTLS (mutual TLS)

Require client certificates for `/api/internal/**`. Managed by the service mesh (e.g., Istio).

**Pros:** Very strong authentication; no application code change.  
**Cons:** Requires infrastructure setup; complex for local development; overkill for current scale.

---

## Decision

**Not yet decided.** This ADR is `Proposed` pending a decision on the deployment environment.

Recommendation: **Option A** as the primary control, combined with **Option C** as defense-in-depth.
Option A can be implemented immediately in application code. Option C is an infrastructure-level
enhancement when the deployment environment is defined.

The minimum acceptable security posture is: no `/api/internal/**` endpoint is accessible
over the public internet without authentication.

---

## Consequences

**If Option A is chosen:**
- `spring-boot-starter-security` dependency added to `pom.xml`.
- A `SecurityConfig` class configures the filter chain.
- Credentials stored in environment variables; never committed to the repository.
- `/actuator/health` remains unauthenticated for Docker/Kubernetes health probes.
- All integration tests that call `/api/internal/**` must provide credentials.

---

*Update this ADR to `Accepted` after the deployment environment is confirmed and TECH-001 is implemented.*
