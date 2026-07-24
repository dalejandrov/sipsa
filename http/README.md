# HTTP Request Collection

[`sipsa-api.http`](sipsa-api.http) is a ready-to-run request collection for the SIPSA
REST API, compatible with:

- **IntelliJ IDEA / Ultimate** — built-in HTTP Client (open the `.http` file, click the
  green run icon next to a request).
- **VS Code** — [REST Client extension](https://marketplace.visualstudio.com/items?itemName=humao.rest-client).

Full endpoint documentation: [`docs/api/sipsa-rest-api.md`](../docs/api/sipsa-rest-api.md).

---

## Setup

1. Copy the example environment file:

   ```bash
   cp http/http-client.env.json.example http/http-client.private.env.json
   ```

2. Edit `http/http-client.private.env.json` and fill in `accessToken` (and `apiKey` if
   your requests use it). This file is **gitignored** — never commit it.

3. Get a local access token from the mock OIDC service (no AWS needed):

   ```bash
   docker compose up -d oidc

   TOKEN=$(curl -s -X POST http://localhost:9000/default/token \
     -d grant_type=client_credentials \
     -d client_id=local-dev -d client_secret=anything \
     -d scope=sipsa/ingestion.read | jq -r .access_token)

   echo "$TOKEN"
   ```

   Paste the result into `accessToken` in your private environment file. See
   [CONTRIBUTING.md](../CONTRIBUTING.md#local-authentication-mock-oidc) for tokens with
   other scopes (`sipsa/ingestion.execute`, `sipsa/ingestion.cancel`, `sipsa/audit.read`)
   and for the Docker Compose full-stack flow (`--resolve oidc:9000:127.0.0.1`).

4. In your editor, select the `local` environment before running requests (IntelliJ:
   environment dropdown in the HTTP Client gutter; VS Code REST Client: `rest-client.environmentVariables`
   in settings, or select via the status bar).

## Variables

| Variable | Source | Used by |
|---|---|---|
| `baseUrl` | Environment file | Every request |
| `accessToken` | Environment file, from the mock OIDC token endpoint | `/api/internal/**` requests |
| `apiKey` | Environment file (placeholder — see note below) | Public `/api/sipsa/**` requests |
| `runId`, `requestId` | Manual — copy from a previous response | Requests that operate on a specific run or request (cancel, run status, audit trail) |

**`apiKey`** models the future API Gateway tier (per-consumer identification and
throttling, not authentication — see [ADR-002](../docs/adr/ADR-002-internal-endpoint-security.md)).
It is not enforced by the application today; leave it as `replace-me` or omit the header.

**`runId`/`requestId`** are not resolved automatically — the collection doesn't chain
requests. After triggering an ingestion run or calling an endpoint that returns a
`runId`/`requestId`, either edit the placeholder inline in `sipsa-api.http` before
running the next request, or use your client's variable-override UI for a single run.

## Never commit

`http/http-client.private.env.json` is listed in [`.gitignore`](../.gitignore). It is
meant to hold real local tokens — the versioned `http-client.env.json.example` has none.
