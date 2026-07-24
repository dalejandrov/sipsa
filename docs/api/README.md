# API Documentation

Entry point for the SIPSA REST API documentation.

- **[Full endpoint reference](sipsa-rest-api.md)** — every endpoint, parameters, request/response examples, and error codes.
- **[HTTP request collection](../../http/sipsa-api.http)** — ready-to-run requests for IntelliJ HTTP Client / VS Code REST Client. See [http/README.md](../../http/README.md) for setup.

## Authentication

The API has three access tiers:

| Tier | Endpoints | Requirement |
|---|---|---|
| Public | `GET /api/sipsa/**` | None |
| Internal, scoped | `/api/internal/**` | Cognito-issued JWT `Bearer` access token with the operation's `sipsa/*` scope |
| Health probe | `GET /actuator/health` | None (never routed through API Gateway) |

Full contract, scopes per operation, and the difference between the AWS production
design and what's available locally: see
[Authentication and security](sipsa-rest-api.md#authentication-and-security) in the
endpoint reference, and [ADR-002](../adr/ADR-002-internal-endpoint-security.md).

For local tokens (mock OIDC, no AWS needed), see
[CONTRIBUTING.md](../../CONTRIBUTING.md#local-authentication-mock-oidc).

## Response codes

Every error response shares one JSON shape (`ErrorResponse`, plus specialized variants
for validation and ingestion-validation errors) — see
[Errors](sipsa-rest-api.md#errors) in the endpoint reference for the full status-code
table.

## Examples

Every endpoint in the [reference](sipsa-rest-api.md) includes a `curl` example. The
[HTTP request collection](../../http/sipsa-api.http) covers the same endpoints as
runnable requests.
