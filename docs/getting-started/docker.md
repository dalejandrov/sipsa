# Running with Docker

[`docker-compose.yml`](../../docker-compose.yml) runs the full local stack: the
application, PostgreSQL, and a mock OIDC issuer for exercising `/api/internal/**`
authentication without any AWS dependency. This is the fastest way to get a working
instance; for running the app directly on your machine instead, see
[Local Development](local-development.md).

---

## Prerequisites

- Docker 24+
- Docker Compose (bundled with Docker Desktop, or the `docker compose` CLI plugin)

## Services

| Service | Image | Container | Port | Purpose |
|---|---|---|---|---|
| `app` | built from [`Dockerfile`](../../Dockerfile) | `sipsa-app` | `8080` | The SIPSA REST API |
| `db` | `postgres:18.0-alpine3.22` | `sipsa-db` | `5432` | PostgreSQL data store |
| `oidc` | `ghcr.io/navikt/mock-oauth2-server:5.0.2` | `sipsa-oidc` | `9000` | Local mock OIDC issuer for `/api/internal/**` — dev-only, issues unsigned-trust JWTs, no real secrets |

`app` starts only after `db` reports healthy and `oidc` has started
(`depends_on` with `condition: service_healthy` / `service_started`).

---

## Build and start

```bash
# Build the app image and start every service in the background
docker compose up --build -d

# Start without rebuilding (after the first build)
docker compose up -d
```

The API becomes available at `http://localhost:8080`.

## Logs

```bash
# Follow logs for every service
docker compose logs -f

# Follow logs for a single service
docker compose logs -f app
```

## Stop

```bash
# Stop containers, keep the PostgreSQL volume (data persists)
docker compose down
```

## Clean up

```bash
# Stop containers AND delete the PostgreSQL volume — this deletes all local data
docker compose down -v
```

`docker compose down -v` removes the `sipsa-postgres-data` volume declared in
`docker-compose.yml`. Only use it when you intend to discard locally ingested data.

---

## Health checks

- `db`: `pg_isready` against the configured database/user, checked every 10s.
- `app`: `curl -f http://localhost:8080/actuator/health`, checked every 30s after a 40s
  start period.

```bash
curl http://localhost:8080/actuator/health
```

## Data persistence

PostgreSQL data is stored in the named volume `sipsa-postgres-data`, so ingested data
survives `docker compose down` / `up` cycles. It is only removed by `docker compose down
-v` or `docker volume rm sipsa-postgres-data`.

---

## Configuration

Every environment variable the `app` service reads is declared with a default in
`docker-compose.yml` (`${VAR:-default}` syntax) — the same variables documented in
[Local Development → Environment variables](local-development.md#environment-variables).
Override any of them from the shell without editing the file:

```bash
DB_PASSWORD=another-password INGESTION_BATCH_SIZE=250 docker compose up -d
```

Inside the compose network the app always runs the `docker` Spring profile
(`SPRING_PROFILES_ACTIVE=docker`) against the `db` service — those two facts are fixed by
the compose topology, not overridable.

### Authentication against the mock OIDC service

`SIPSA_JWT_ISSUER_URI` defaults to `http://oidc:9000/default` — the `oidc` service as
seen from inside the compose network. To request a token from your host machine, use
`curl --resolve` so the request's `Host` header matches what the containerized app
validates against:

```bash
TOKEN=$(curl -s --resolve oidc:9000:127.0.0.1 \
  -X POST http://oidc:9000/default/token \
  -d grant_type=client_credentials \
  -d client_id=local-dev -d client_secret=anything \
  -d scope=sipsa/ingestion.read | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/internal/ingestion/runs
```

Full token-request examples for every scope are in
[CONTRIBUTING.md](../../CONTRIBUTING.md#local-authentication-mock-oidc). To point the
stack at a real Cognito user pool instead, export `SIPSA_JWT_ISSUER_URI` before starting
the app — no file changes required.

---

## Differences from AWS production

This Compose stack is a **local development convenience**, not a stand-in for the AWS
production topology defined in [`infra/terraform/`](../../infra/terraform/):

- The `oidc` service is a mock issuer; production uses Amazon Cognito.
- There is no API Gateway, VPC, ALB, or ECS here — `app` is reached directly on
  `localhost:8080`.
- PostgreSQL runs as a container with a local volume, not Amazon RDS.
- The AWS infrastructure is currently **declared as Terraform code but has not been
  applied** — see [AWS Production Readiness](../architecture/aws-production-readiness.md)
  and [AWS Production Preflight](../operations/aws-production-preflight.md).
