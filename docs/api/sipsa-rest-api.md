# SIPSA REST API Reference

Complete reference for every HTTP endpoint exposed by the SIPSA Integration Service.
Derived directly from the controllers in
[`src/main/java/com/dalejandrov/sipsa/api/controller/`](../../src/main/java/com/dalejandrov/sipsa/api/controller/):
`SipsaRestController`, `SipsaOpsController`, and `IngestionAuditController`.

For a shorter orientation, see [docs/api/README.md](README.md). For runnable requests,
see [`http/sipsa-api.http`](../../http/sipsa-api.http).

---

## Overview

**Base URL (local):** `http://localhost:8080`

### Pagination envelope

Every paginated endpoint returns the same wrapper:

```json
{
  "count": 150,
  "next": "http://api/endpoint?page=2",
  "prev": null,
  "pages": 15,
  "results": []
}
```

`next`/`prev` are omitted (not `null` in the JSON) when there is no such page. Common
pagination parameters, accepted by every paginated endpoint:

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `page` | integer | `1` | 1-based |
| `size` | integer | `20` | Clamped to `[1, 100]` |
| `sort` | string | endpoint-specific | e.g. `fechaCaptura,desc`; not accepted by `GET /api/internal/ingestion/runs`, whose order is fixed server-side |

### Timezone header

All endpoints honor an optional `X-Timezone` request header (any IANA timezone ID,
e.g. `America/Bogota`). It defaults to UTC when absent or invalid.

- **External/historical dates** (`fechaCaptura`, `fechaCreacion`, `fechaIni`,
  `fechaMesIni`, `enmaFecha`) are **always UTC**, regardless of the header — they
  represent DANE source data and must stay consistent across clients.
- **System timestamps** (`fechaIngestion`, `lastUpdated`, `occurredAt`, `startTime`,
  `endTime`, `firstEvent`, `lastEvent`) are converted to the requested timezone.
  `fechaSincronizacion` is an internal audit timestamp and is never returned by any
  endpoint.

```bash
curl -H "X-Timezone: America/Bogota" "http://localhost:8080/api/sipsa/ciudad?ciudad=ARMENIA"
```

### Filtering conventions

- String filters are case-sensitive exact matches.
- Dates use `YYYY-MM-DD`. Date-range filters use `startDate`/`endDate`; the API does not
  itself enforce `endDate >= startDate` at the controller layer — pass a coherent range.
- ID filters (`artiId`, `fuenId`, `idArtiSemana`) must be positive integers.

---

## Endpoint summary

### Public query endpoints

| Method | Path | Description | Auth | Scope |
|---|---|---|---|---|
| GET | [`/api/sipsa`](#get-apisipsa) | List available public endpoints | None | — |
| GET | [`/api/sipsa/ciudad`](#get-apisipsaciudad) | Daily city-level prices | None | — |
| GET | [`/api/sipsa/mayoristas/mensual`](#get-apisipsamayoristasmensual) | Monthly wholesale aggregates | None | — |
| GET | [`/api/sipsa/mayoristas/semanal`](#get-apisipsamayoristassemanal) | Weekly wholesale aggregates | None | — |
| GET | [`/api/sipsa/parcial`](#get-apisipsaparcial) | Municipality market data | None | — |
| GET | [`/api/sipsa/abastecimientos/mensual`](#get-apisipsaabastecimientosmensual) | Monthly supply data | None | — |

### Internal ingestion endpoints (operational)

| Method | Path | Description | Auth | Scope |
|---|---|---|---|---|
| POST | [`/api/internal/ingestion/run`](#post-apiinternalingestionrun) | Trigger a manual ingestion run | Bearer JWT | `sipsa/ingestion.execute` |
| GET | [`/api/internal/ingestion/methods`](#get-apiinternalingestionmethods) | List available ingestion methods | Bearer JWT | `sipsa/ingestion.read` |
| POST | [`/api/internal/ingestion/cancel/{runId}`](#post-apiinternalingestioncancelrunid) | Cancel an active run | Bearer JWT | `sipsa/ingestion.cancel` |
| GET | [`/api/internal/ingestion/running`](#get-apiinternalingestionrunning) | List currently active runs | Bearer JWT | `sipsa/ingestion.read` |
| GET | [`/api/internal/ingestion/runs`](#get-apiinternalingestionruns) | List all runs, paginated | Bearer JWT | `sipsa/ingestion.read` |
| GET | [`/api/internal/ingestion/runs/{runId}`](#get-apiinternalingestionrunsrunid) | Get one run's status | Bearer JWT | `sipsa/ingestion.read` |

### Internal audit endpoints

| Method | Path | Description | Auth | Scope |
|---|---|---|---|---|
| GET | [`/api/internal/audit/request/{requestId}`](#get-apiinternalauditrequestrequestid) | Full audit trail for one request | Bearer JWT | `sipsa/audit.read` |
| GET | [`/api/internal/audit/run/{runId}`](#get-apiinternalauditrunrunid) | Audit events for one run | Bearer JWT | `sipsa/audit.read` |
| GET | [`/api/internal/audit/recent`](#get-apiinternalauditrecent) | Most recent 100 events | Bearer JWT | `sipsa/audit.read` |
| GET | [`/api/internal/audit/all`](#get-apiinternalauditall) | All events, paginated and filterable | Bearer JWT | `sipsa/audit.read` |

### Actuator

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | [`/actuator/health`](#get-actuatorhealth) | Liveness/readiness probe | None |
| GET | `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus` | Operational metadata/metrics | Any valid access token |
| GET | `/actuator/loggers` | Runtime log-level control | Any valid access token — **`dev` profile only** |

---

## Authentication and security

Contract implemented and enforced today by
[`SecurityConfig`](../../src/main/java/com/dalejandrov/sipsa/infrastructure/config/security/SecurityConfig.java)
(defense-in-depth Resource Server layer — see [ADR-002](../adr/ADR-002-internal-endpoint-security.md)):

- **`GET /api/sipsa/**`** is public — no token required.
- **`/api/internal/**`** requires a `Bearer` JWT access token:
  - `token_use` claim must be `access` (Cognito ID tokens are rejected).
  - Issuer must match the configured `SIPSA_JWT_ISSUER_URI`, with a valid signature and
    expiry.
  - An optional `client_id` allowlist (`SIPSA_JWT_ALLOWED_CLIENT_IDS`) may further
    restrict which clients are accepted.
  - The token must carry the operation's scope as a `SCOPE_sipsa/...` authority (table
    above).
- **`GET /actuator/health`** is public (container/platform healthcheck). Every other
  Actuator endpoint requires any validly authenticated token. `loggers` is only exposed
  at all in the `dev` profile.
- Everything else is denied by default.
- No sessions, no CSRF, no cookies, no HTTP Basic, no form login — the chain is fully
  stateless.

### M2M vs. human callers

Machine-to-machine integrations use the OAuth2 `client_credentials` grant (one Cognito
app client per integration); human operators would use the authorization-code flow. Both
produce the same kind of access token this API validates — there is no separate code
path for the two.

### API key

An API key is planned at the API Gateway layer for the public `GET /api/sipsa/**` tier,
for **consumer identification, metering, and throttling — it is not authentication** and
grants access to nothing sensitive. It is not yet wired into this application; the `x-api-key`
header used in the [HTTP request collection](../../http/sipsa-api.http) is a placeholder
for when that gateway tier exists.

### Production contract vs. local configuration

| | Production (target) | Local |
|---|---|---|
| Identity provider | Amazon Cognito | Mock OIDC (`ghcr.io/navikt/mock-oauth2-server`, Docker Compose service `oidc`) |
| Public entry point | API Gateway (API keys, throttling, usage plans) | Direct to the app on `localhost:8080` |
| Network isolation | ECS in private subnets, reachable only via the gateway | None — the app listens directly |

**The AWS side of this contract (API Gateway, Cognito, private networking) is declared
as Terraform code but has not been deployed** — see
[AWS Production Readiness](../architecture/aws-production-readiness.md). Only the
application-level JWT/scope validation described above is live today, and it is fully
exercised locally against the mock OIDC service — see
[CONTRIBUTING.md](../../CONTRIBUTING.md#local-authentication-mock-oidc) for token
commands.

---

## Public query endpoints

### GET /api/sipsa

**Purpose**

Lists all available public query endpoints with their full URLs — a self-describing
root resource.

**Authentication**

Public.

**Parameters**

None.

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "endpoints": [
    {
      "name": "ciudad",
      "description": "City-level pricing data with product and source filters",
      "path": "http://localhost:8080/api/sipsa/ciudad",
      "methods": ["GET"]
    }
  ]
}
```

**Example**

```bash
curl http://localhost:8080/api/sipsa
```

---

### GET /api/sipsa/ciudad

**Purpose**

Daily city-level pricing data: average price per product and capture date.

**Authentication**

Public.

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `fecha` | query | date (`YYYY-MM-DD`) | No | Exact capture date |
| `startDate` | query | date | No | Range start |
| `endDate` | query | date | No | Range end |
| `artiId` | query | positive integer | No | Product ID |
| `fuenId` | query | positive integer | No | Source ID |
| `ciudad` | query | string | No | City name, exact match |
| `producto` | query | string | No | Product name, exact match |
| `page`, `size`, `sort` | query | — | No | See [pagination](#pagination-envelope). Default sort: `fechaCaptura,desc` |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "count": 2,
  "next": null,
  "prev": null,
  "pages": 1,
  "results": [
    {
      "regId": 646199,
      "ciudad": "ARMENIA",
      "codProducto": 13,
      "producto": "Banano*",
      "fechaCaptura": "2026-01-06T05:00:00Z",
      "fechaCreacion": "2026-01-06T19:00:01Z",
      "precioPromedio": 1833,
      "enviado": 0,
      "fechaIngestion": "2026-01-07T00:04:03.33623Z"
    }
  ]
}
```

**Example**

```bash
curl "http://localhost:8080/api/sipsa/ciudad?ciudad=ARMENIA&startDate=2026-01-01&endDate=2026-01-07&page=1&size=20&sort=fechaCaptura,desc"
```

---

### GET /api/sipsa/mayoristas/mensual

**Purpose**

Monthly wholesale market aggregates: minimum, maximum, and average price per kilogram.

**Authentication**

Public.

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `fechaMes` | query | date | No | Month start date |
| `startDate` | query | date | No | Range start |
| `endDate` | query | date | No | Range end |
| `artiId` | query | positive integer | No | Product ID |
| `artiNombre` | query | string | No | Product name, exact match |
| `fuenNombre` | query | string | No | Source name, exact match |
| `page`, `size`, `sort` | query | — | No | Default sort: `fechaMesIni,desc` |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "count": 1,
  "next": null,
  "prev": null,
  "pages": 1,
  "results": [
    {
      "artiId": 351,
      "artiNombre": "Bocadillo veleño",
      "fuenId": 1,
      "fuenNombre": "Bogotá, D.C., Corabastos",
      "fechaMesIni": "2023-02-01T05:00:00Z",
      "minimoKg": 361,
      "maximoKg": 383,
      "promedioKg": 370,
      "lastUpdated": "2026-01-07T00:11:58.424622Z"
    }
  ]
}
```

**Example**

```bash
curl "http://localhost:8080/api/sipsa/mayoristas/mensual?artiNombre=Bocadillo%20veleño&startDate=2023-01-01&endDate=2023-03-01"
```

---

### GET /api/sipsa/mayoristas/semanal

**Purpose**

Weekly wholesale market aggregates: price statistics per product and market.

**Authentication**

Public.

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `fechaIni` | query | date | No | Week start date |
| `startDate` | query | date | No | Range start |
| `endDate` | query | date | No | Range end |
| `artiId` | query | positive integer | No | Product ID |
| `fuenId` | query | positive integer | No | Source ID |
| `artiNombre` | query | string | No | Product name, exact match |
| `fuenNombre` | query | string | No | Source name, exact match |
| `page`, `size`, `sort` | query | — | No | Default sort: `fechaIni,desc` |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "count": 1,
  "next": null,
  "prev": null,
  "pages": 1,
  "results": [
    {
      "artiId": 115,
      "artiNombre": "Kiwi",
      "fuenId": 26,
      "fuenNombre": "Cúcuta, Cenabastos",
      "fechaIni": "2025-05-31T05:00:00Z",
      "minimoKg": 20000,
      "maximoKg": 22000,
      "promedioKg": 20500,
      "lastUpdated": "2026-01-07T00:09:31.411641Z"
    }
  ]
}
```

**Example**

```bash
curl "http://localhost:8080/api/sipsa/mayoristas/semanal?artiNombre=Kiwi&startDate=2025-05-01&endDate=2025-06-15"
```

---

### GET /api/sipsa/parcial

**Purpose**

Detailed market data by municipality: price ranges and product availability.

**Authentication**

Public.

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `fechaEncuesta` | query | date | No | Survey date |
| `startDate` | query | date | No | Range start |
| `endDate` | query | date | No | Range end |
| `muniId` | query | string, max 50 chars | No | DIVIPOLA municipality code, **exact text match**. Leading zeros are significant (`05001` ≠ `5001`). A present-but-blank value returns `400` |
| `fuenId` | query | positive integer | No | Source ID |
| `idArtiSemana` | query | positive integer | No | **Canonical** product/article filter, matches the `idArtiSemana` response field |
| `artiId` | query | positive integer | No | Compatibility alias for `idArtiSemana`. If both are present they must match, or the request returns `400 VALIDATION_ERROR` |
| `muniNombre` | query | string | No | Municipality name, exact match |
| `deptNombre` | query | string | No | Department name, exact match |
| `fuenNombre` | query | string | No | Source name, exact match |
| `artiNombre` | query | string | No | Product name, exact match |
| `grupNombre` | query | string | No | Product group name, exact match |
| `page`, `size`, `sort` | query | — | No | Default sort: `enmaFecha,desc` |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "count": 1,
  "next": null,
  "prev": null,
  "pages": 1,
  "results": [
    {
      "muniId": "11001",
      "muniNombre": "BOGOTÁ, D.C.",
      "deptNombre": "BOGOTÁ, D. C.",
      "fuenId": 1,
      "fuenNombre": "Bogotá, D.C., Corabastos",
      "futiId": 1,
      "idArtiSemana": 14,
      "artiNombre": "Aguacate*",
      "grupNombre": "FRUTAS",
      "enmaFecha": "2020-02-01",
      "promedioKg": 3650,
      "maximoKg": 3800,
      "minimoKg": 3500
    }
  ]
}
```

**Errors**

| Code | Reason |
|---:|---|
| 400 | `muniId` present but blank (`?muniId=` or whitespace only) |
| 400 | `idArtiSemana` and `artiId` both present with different values |

**Example**

```bash
curl "http://localhost:8080/api/sipsa/parcial?muniId=05001&idArtiSemana=14&startDate=2020-01-01&endDate=2020-03-01"
```

---

### GET /api/sipsa/abastecimientos/mensual

**Purpose**

Monthly product supply volumes delivered to wholesale markets.

**Authentication**

Public.

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `fechaMes` | query | date | No | Month start date |
| `startDate` | query | date | No | Range start |
| `endDate` | query | date | No | Range end |
| `artiId` | query | positive integer | No | Product ID |
| `fuenId` | query | positive integer | No | Source ID |
| `artiNombre` | query | string | No | Product name, exact match |
| `fuenNombre` | query | string | No | Source name, exact match |
| `page`, `size`, `sort` | query | — | No | Default sort: `fechaMesIni,desc` |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "count": 1,
  "next": null,
  "prev": null,
  "pages": 1,
  "results": [
    {
      "artiId": 496,
      "artiNombre": "Uchuva",
      "fuenId": 70,
      "fuenNombre": "Barranquilla, Granabastos",
      "futiId": 794,
      "fechaMesIni": "2020-03-01T05:00:00Z",
      "fechaCreacion": null,
      "cantidadTon": 1,
      "enviado": null,
      "fechaIngestion": "2026-01-07T00:06:57.379458Z"
    }
  ]
}
```

**Example**

```bash
curl "http://localhost:8080/api/sipsa/abastecimientos/mensual?artiNombre=Uchuva&startDate=2020-01-01&endDate=2020-06-01"
```

---

## Internal ingestion endpoints

All endpoints below are under `/api/internal/ingestion` and require a `Bearer` JWT — see
[Authentication and security](#authentication-and-security).

### POST /api/internal/ingestion/run

**Purpose**

Triggers a data ingestion run against DANE's SOAP service, asynchronously.

**Authentication**

- Bearer JWT
- Scope: `sipsa/ingestion.execute`

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `method` | query | string | Yes | One of the available ingestion methods (see [`GET /methods`](#get-apiinternalingestionmethods)) |
| `force` | query | boolean | No (default `false`) | Bypass the publication-window check |

**Request**

No body — parameters are query strings.

**Response**

```http
HTTP/1.1 202 Accepted
```

```json
{
  "requestId": "6e6a6e7e-1c1d-4e2a-9c3f-2b6e2a3d4f5a",
  "status": "ACCEPTED",
  "method": "promediosSipsaCiudad",
  "force": false
}
```

**Errors**

| Code | Reason |
|---:|---|
| 400 | `method` missing/blank, or not one of the available methods (`INGESTION_VALIDATION_ERROR`, includes `availableMethods`) |
| 401 | Missing/invalid token |
| 403 | Token lacks `sipsa/ingestion.execute` |
| 422 | A run for the same method/window already succeeded, or is already in progress (`force=true` is required to restart) |

**Available methods**

| Method | Data |
|---|---|
| `promediosSipsaCiudad` | City-level pricing |
| `promediosSipsaParcial` | Municipality market data |
| `promediosSipsaSemanaMadr` | Weekly wholesale |
| `promediosSipsaMesMadr` | Monthly wholesale |
| `promedioAbasSipsaMesMadr` | Monthly supply |

**Example**

```bash
curl -X POST "http://localhost:8080/api/internal/ingestion/run?method=promediosSipsaCiudad&force=false" \
  -H "Authorization: Bearer $TOKEN"
```

---

### GET /api/internal/ingestion/methods

**Purpose**

Lists every ingestion method name the application knows how to run.

**Authentication**

- Bearer JWT
- Scope: `sipsa/ingestion.read`

**Parameters**

None.

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "methods": [
    "promediosSipsaCiudad",
    "promediosSipsaParcial",
    "promediosSipsaSemanaMadr",
    "promediosSipsaMesMadr",
    "promedioAbasSipsaMesMadr"
  ],
  "count": 5
}
```

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/internal/ingestion/methods
```

---

### POST /api/internal/ingestion/cancel/{runId}

**Purpose**

Cancels an active (non-terminal) ingestion run.

**Authentication**

- Bearer JWT
- Scope: `sipsa/ingestion.cancel`

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `runId` | path | integer | Yes | Ingestion run identifier |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "runId": 42,
  "status": "CANCELED"
}
```

**Errors**

| Code | Reason |
|---:|---|
| 401 | Missing/invalid token |
| 403 | Token lacks `sipsa/ingestion.cancel` |
| 404 | `runId` does not exist |
| 422 | Run is not active (already terminal) |

**Example**

```bash
curl -X POST http://localhost:8080/api/internal/ingestion/cancel/42 \
  -H "Authorization: Bearer $TOKEN"
```

---

### GET /api/internal/ingestion/running

**Purpose**

Lists every currently active ingestion run.

**Authentication**

- Bearer JWT
- Scope: `sipsa/ingestion.read`

**Parameters**

None.

**Response**

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "runId": 42,
    "methodName": "promediosSipsaCiudad",
    "windowKey": "2026-01-07",
    "status": "RUNNING",
    "startTime": "2026-01-07T14:20:00-05:00",
    "requestId": "6e6a6e7e-1c1d-4e2a-9c3f-2b6e2a3d4f5a"
  }
]
```

Note: this endpoint returns a bare JSON array, not the paginated envelope — the number
of active runs is small by construction.

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/internal/ingestion/running
```

---

### GET /api/internal/ingestion/runs

**Purpose**

Lists all ingestion runs (any status), paginated, most recent first.

**Authentication**

- Bearer JWT
- Scope: `sipsa/ingestion.read`

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `page` | query | integer | No (default `1`) | 1-based |
| `size` | query | integer | No (default `20`, max `100`) | |

Sort order is fixed server-side (`startTime DESC, runId DESC`) — there is no `sort`
parameter for this endpoint.

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "count": 1,
  "next": null,
  "prev": null,
  "pages": 1,
  "results": [
    {
      "runId": 42,
      "methodName": "promediosSipsaCiudad",
      "windowKey": "2026-01-07",
      "status": "SUCCEEDED",
      "startTime": "2026-01-07T14:20:00-05:00",
      "endTime": "2026-01-07T14:22:10-05:00",
      "requestId": "6e6a6e7e-1c1d-4e2a-9c3f-2b6e2a3d4f5a",
      "requestSource": "SCHEDULED",
      "recordsSeen": 1200,
      "recordsInserted": 1180,
      "recordsUpdated": 20,
      "rejectCount": 0
    }
  ]
}
```

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/internal/ingestion/runs?page=1&size=20"
```

---

### GET /api/internal/ingestion/runs/{runId}

**Purpose**

Gets the full status and metrics of a single ingestion run.

**Authentication**

- Bearer JWT
- Scope: `sipsa/ingestion.read`

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `runId` | path | integer | Yes | Ingestion run identifier |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "runId": 42,
  "methodName": "promediosSipsaCiudad",
  "windowKey": "2026-01-07",
  "status": "SUCCEEDED",
  "startTime": "2026-01-07T14:20:00-05:00",
  "endTime": "2026-01-07T14:22:10-05:00",
  "requestId": "6e6a6e7e-1c1d-4e2a-9c3f-2b6e2a3d4f5a",
  "requestSource": "SCHEDULED",
  "recordsSeen": 1200,
  "recordsInserted": 1180,
  "recordsUpdated": 20,
  "rejectCount": 0
}
```

**Errors**

| Code | Reason |
|---:|---|
| 404 | `runId` does not exist |

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/internal/ingestion/runs/42
```

---

## Internal audit endpoints

All endpoints below are under `/api/internal/audit`, scope `sipsa/audit.read`.

### GET /api/internal/audit/request/{requestId}

**Purpose**

Full audit trail (every event) for one request — the complete timeline from receipt to
completion or failure.

**Authentication**

- Bearer JWT
- Scope: `sipsa/audit.read`

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `requestId` | path | string (UUID) | Yes | Correlation ID assigned when the request was received |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "requestId": "6e6a6e7e-1c1d-4e2a-9c3f-2b6e2a3d4f5a",
  "eventCount": 3,
  "firstEvent": "2026-01-07T14:20:00-05:00",
  "lastEvent": "2026-01-07T14:22:10-05:00",
  "events": [
    {
      "auditId": 101,
      "runId": 42,
      "requestSource": "SCHEDULED",
      "eventType": "REQUEST_RECEIVED",
      "message": "Ingestion request received",
      "occurredAt": "2026-01-07T14:20:00-05:00"
    }
  ]
}
```

**Errors**

| Code | Reason |
|---:|---|
| 422 | No audit events exist for the given `requestId` |

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/internal/audit/request/6e6a6e7e-1c1d-4e2a-9c3f-2b6e2a3d4f5a
```

---

### GET /api/internal/audit/run/{runId}

**Purpose**

All audit events tied to a specific ingestion run.

**Authentication**

- Bearer JWT
- Scope: `sipsa/audit.read`

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `runId` | path | integer | Yes | Ingestion run identifier |

**Response**

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "auditId": 101,
    "runId": 42,
    "requestSource": "SCHEDULED",
    "eventType": "INGESTION_STARTED",
    "message": "Ingestion started for method promediosSipsaCiudad",
    "occurredAt": "2026-01-07T14:20:01-05:00"
  }
]
```

**Errors**

| Code | Reason |
|---:|---|
| 422 | No audit events exist for the given `runId` |

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/internal/audit/run/42
```

---

### GET /api/internal/audit/recent

**Purpose**

The most recent audit events across all requests (maximum 100).

**Authentication**

- Bearer JWT
- Scope: `sipsa/audit.read`

**Parameters**

None.

**Response**

```http
HTTP/1.1 200 OK
```

```json
[
  {
    "auditId": 101,
    "runId": 42,
    "requestSource": "SCHEDULED",
    "eventType": "INGESTION_COMPLETED",
    "message": "Ingestion completed successfully",
    "occurredAt": "2026-01-07T14:22:10-05:00"
  }
]
```

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/internal/audit/recent
```

---

### GET /api/internal/audit/all

**Purpose**

Paginated, filterable query over all audit events.

**Authentication**

- Bearer JWT
- Scope: `sipsa/audit.read`

**Parameters**

| Name | Location | Type | Required | Description |
|---|---|---|---:|---|
| `requestId` | query | string | No | Filter by request correlation ID |
| `fecha` | query | date | No | Exact event date |
| `startDate` | query | date | No | Range start |
| `endDate` | query | date | No | Range end |
| `page`, `size`, `sort` | query | — | No | Default sort: `occurredAt,desc` |

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "count": 1,
  "next": null,
  "prev": null,
  "pages": 1,
  "results": [
    {
      "auditId": 101,
      "runId": 42,
      "requestSource": "SCHEDULED",
      "eventType": "INGESTION_COMPLETED",
      "message": "Ingestion completed successfully",
      "occurredAt": "2026-01-07T14:22:10-05:00"
    }
  ]
}
```

**Example**

```bash
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/internal/audit/all?page=1&size=20"
```

---

## Actuator

### GET /actuator/health

**Purpose**

Liveness/readiness probe, used by the Docker Compose healthcheck and any container
orchestrator. Never routed through API Gateway in the production design.

**Authentication**

Public — the only Actuator endpoint that is.

**Response**

```http
HTTP/1.1 200 OK
```

```json
{
  "status": "UP"
}
```

`show-details` is `when-authorized` in the base configuration and `always` in the `dev`
profile, so an authenticated caller sees per-component details
(`db`, `diskSpace`, custom SIPSA health indicators for daily/monthly staleness).

**Example**

```bash
curl http://localhost:8080/actuator/health
```

Every other Actuator endpoint (`/actuator/info`, `/actuator/metrics`,
`/actuator/prometheus`, and — `dev` profile only — `/actuator/loggers`) requires any
validly authenticated access token; no specific scope is enforced beyond that.

---

## Errors

Every error response (except the two variants noted below) uses this shape:

```json
{
  "timestamp": "2026-01-07T14:22:10",
  "status": 404,
  "error": "Not Found",
  "code": "NOT_FOUND",
  "message": "Ingestion run not found: 42",
  "requestId": "6e6a6e7e-1c1d-4e2a-9c3f-2b6e2a3d4f5a",
  "instance": "/api/internal/ingestion/runs/42"
}
```

`INGESTION_VALIDATION_ERROR` additionally includes `availableMethods`; bean-validation
failures (`VALIDATION_ERROR` from `@Valid`/`@Validated` on request DTOs) additionally
include `fieldErrors` — a map of field name to message.

| Status | Code | When |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | A query parameter fails validation (e.g. `muniId` blank, conflicting `idArtiSemana`/`artiId`) |
| 400 | `INGESTION_VALIDATION_ERROR` | `method` missing or not a recognized ingestion method |
| 400 | `TYPE_MISMATCH` | A parameter can't be converted to its expected type |
| 400 | `INVALID_FORMAT` | Malformed JSON body |
| 400 | `MISSING_PARAMETER` | A required parameter is absent |
| 401 | — | Missing, invalid, expired, or wrong-issuer token on a protected endpoint |
| 403 | — | Valid token, missing required scope |
| 404 | `NOT_FOUND` | Referenced resource (e.g. a run ID) does not exist, or the route itself doesn't exist |
| 422 | `BUSINESS_ERROR` | The resource exists but the operation is invalid in its current state (e.g. cancelling a finished run, duplicate trigger without `force`) |
| 502 | `PARSE_ERROR` | DANE's upstream SOAP response could not be parsed |
| 502 | `EXTERNAL_ERROR` | DANE's SOAP service call failed |
| 500 | `INGESTION_ERROR` | Unexpected failure during ingestion processing |
| 500 | `CONFIGURATION_ERROR` | Invalid application configuration detected at runtime |
| 500 | `INTERNAL_ERROR` | Any other unhandled exception |
