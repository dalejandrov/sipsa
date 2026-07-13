# ADR-003 — Error Response Model

**Status:** Proposed  
**Date:** 2026-07-13  
**Backlog:** [TECH-021](../backlog/technical-backlog.md#tech-021), [TECH-022](../backlog/technical-backlog.md#tech-022), [TECH-023](../backlog/technical-backlog.md#tech-023)

---

## Context

The SIPSA API returns errors using a custom `ErrorResponse` record:

```json
{
  "timestamp": "2026-07-13T10:15:30",
  "status": 400,
  "error": "Bad Request",
  "code": "SIPSA_VALIDATION_ERROR",
  "message": "Parameter 'size' must be >= 1"
}
```

This format is consistent across all error responses but lacks:
- `requestId` — for correlating client-side errors with server logs.
- `instance` — the request path that produced the error (RFC 9457 field).
- `type` — a URI identifying the error type (RFC 9457 field).

The standard RFC 9457 ("Problem Details for HTTP APIs") proposes a different format.

---

## Problem

1. **Incorrect HTTP semantics:** Two specific mappings need correction regardless of format:
   - `SipsaParseException` → currently `400`, should be `502` (error from DANE's XML).
   - "Not found" cases via `SipsaBusinessException` → currently `422`, should be `404`.

2. **Missing correlation:** Error responses lack a `requestId` field. When a client reports an error, it is hard to find the corresponding log entry without a shared identifier.

3. **Format compatibility:** The current format differs from RFC 9457. Adopting `ProblemDetail` (Spring's built-in) would be a breaking change for any existing clients.

---

## Alternatives Considered

### Option A — Extend current format (Recommended)

Keep the existing `ErrorResponse` structure. Add:
- `requestId`: extracted from `X-Request-ID` header or generated per-request.
- `instance`: the request URI from `HttpServletRequest.getRequestURI()`.

Fix the incorrect HTTP status mappings.

**Pros:** Backward-compatible (only adds fields); no client breakage; simpler than RFC 9457.  
**Cons:** Not RFC 9457 compliant; `type` URI is absent.

### Option B — Adopt RFC 9457 `ProblemDetail`

Use Spring Boot's `ProblemDetail` class. The response format becomes:

```json
{
  "type": "https://api.example.com/problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Parameter 'size' must be >= 1",
  "instance": "/api/sipsa/ciudad",
  "requestId": "01J..."
}
```

**Pros:** Standard; tooling support (OpenAPI generators, API gateways).  
**Cons:** Breaking change (different field names: `message` → `detail`, `code` → custom extension); requires updating all client code; `type` URIs must be hosted/documented.

### Option C — Keep current format unchanged

Fix only the HTTP status codes (502, 404). Defer correlation ID to future.

**Pros:** Zero risk.  
**Cons:** Missing correlation makes production diagnosis harder.

---

## Decision

**Not yet decided.** This ADR is `Proposed`.

Recommendation: **Option A** — extend the current format. The corrections to HTTP status codes (TECH-021, TECH-022) should proceed regardless of the format decision. Adding `requestId` and `instance` (TECH-023) is the minimum viable improvement for operational support.

RFC 9457 adoption (Option B) should be evaluated only if the team decides to publish a formal API contract for external consumers.

---

## Consequences

**If Option A is chosen:**
- `ErrorResponse` gains `requestId` and `instance` fields.
- Existing clients receive two additional fields but all existing fields remain.
- A strategy for `requestId` generation must be defined (header passthrough vs generated).

**Error code taxonomy (adopted regardless of format decision):**

| Exception | HTTP | Code |
|---|---|---|
| `SipsaValidationException` | 400 | `SIPSA_VALIDATION_ERROR` |
| `SipsaParseException` | 502 | `SIPSA_UPSTREAM_PARSE_ERROR` |
| `SipsaIngestionValidationException` | 400 | `SIPSA_INGESTION_VALIDATION_ERROR` |
| `SipsaNotFoundException` | 404 | `SIPSA_NOT_FOUND` |
| `SipsaBusinessException` | 422 | `SIPSA_BUSINESS_ERROR` |
| `SipsaExternalException` | 502 | `SIPSA_UPSTREAM_ERROR` |
| `SipsaIngestionException` | 500 | `SIPSA_INGESTION_ERROR` |
| `SipsaConfigurationException` | 500 | `SIPSA_CONFIGURATION_ERROR` |
| `Exception` (catch-all) | 500 | `SIPSA_INTERNAL_ERROR` |

**Security rule (non-negotiable):** The public `message` field must never contain:
- Stack traces or class names.
- SQL queries or table names.
- SOAP payloads or DANE endpoint URLs.
- Internal configuration details.

---

*Update this ADR to `Accepted` after TECH-021 and TECH-022 are implemented.*
