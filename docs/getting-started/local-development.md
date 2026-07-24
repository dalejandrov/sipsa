# Local Development (without Docker)

This guide covers running the SIPSA Integration Service directly on your machine, against
a locally installed PostgreSQL. For a full-stack setup (app + PostgreSQL + mock OIDC) via
containers, see [Docker](docker.md) instead.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 25 (LTS) | Eclipse Temurin 25 recommended |
| Maven | 3.9+ | Use the included wrapper (`./mvnw`) — no local Maven install required |
| PostgreSQL | 18 | Required to run the app; unit tests use H2 and don't need it |
| Git | any | |

Verify your Java version:

```bash
java --version   # should show OpenJDK 25.x
./mvnw --version
```

---

## 1. Clone the repository

```bash
git clone https://github.com/dalejandrov/sipsa.git
cd sipsa
```

## 2. Create the local database

```bash
createdb sipsa_db
```

The `dev` Spring profile (active by default) already points at `localhost:5432/sipsa_db`
with the credentials `sipsa_user` / `sipsa_pass` — see
[`application-dev.yaml`](../../src/main/resources/application-dev.yaml). Adjust the
`DB_*` variables below if your local PostgreSQL uses different credentials.

## 3. (Optional) Start the mock OIDC issuer

`/api/internal/**` requires a JWT with the operation's scope (see
[API authentication](../api/README.md#authentication)). Locally, tokens come from the
`oidc` Docker Compose service — you can start just that one service without running the
rest of the stack:

```bash
docker compose up -d oidc
```

If you only need the public `GET /api/sipsa/**` endpoints, this step is not required.

## 4. Build

```bash
# Compile only
./mvnw clean compile

# Package (skip tests)
./mvnw clean package -DskipTests

# Full build with tests (H2 in-memory; PostgreSQL not required for this step)
./mvnw clean verify
```

`./mvnw clean verify` is also what CI runs
([`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)) — a green local build is
the best predictor of a green pipeline. It additionally runs `FlywayMigrationsTest`
against a real PostgreSQL provisioned via Testcontainers; that test **self-skips** when
Docker is unavailable on your machine (CI fails instead of silently skipping it).

## 5. Run

```bash
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080` (override with the `PORT` environment
variable). With no `SPRING_PROFILES_ACTIVE` set, the `dev` profile is active.

## 6. Verify it's up

```bash
curl http://localhost:8080/actuator/health
```

```bash
curl "http://localhost:8080/api/sipsa/ciudad?size=5"
```

The `ciudad` query returns an empty `results` array until a data ingestion run has
populated the database — see [Manual ingestion](../api/sipsa-rest-api.md#post-apiinternalingestionrun).

## 7. Run tests

```bash
# Unit tests and context load test (no database required)
./mvnw test

# Full verify, same tests plus integration/failsafe suites
./mvnw clean verify
```

---

## Environment variables

All configuration is via environment variables, documented in full in
[`.env.example`](../../.env.example) — a versioned reference template only; **no `.env`
file is read at runtime**. Export variables in your shell, or pass them inline
(`DB_PASSWORD=... ./mvnw spring-boot:run`).

The `dev` profile (default) already supplies local defaults for credentials and the JWT
issuer, so none of the variables below are required to get started — override only what
you need to change.

| Variable | Required | Sensitive | Purpose | Safe example |
|---|---:|---:|---|---|
| `PORT` | No | No | HTTP port | `8080` |
| `SPRING_PROFILES_ACTIVE` | No | No | Active Spring profile (`dev` default) | `dev` |
| `SIPSA_JWT_ISSUER_URI` | No (dev/docker only) | No | OIDC issuer whose JWKS signs access tokens; **required** outside dev/docker | `http://localhost:9000/default` |
| `SIPSA_JWT_ALLOWED_CLIENT_IDS` | No | No | CSV allowlist of accepted client ids; empty = any client of the trusted issuer | `` |
| `DB_HOST` | No | No | PostgreSQL host | `localhost` |
| `DB_PORT` | No | No | PostgreSQL port | `5432` |
| `DB_NAME` | No | No | Database name | `sipsa_db` |
| `DB_USERNAME` | Yes (outside dev) | Yes | Database user | `sipsa_user` |
| `DB_PASSWORD` | Yes (outside dev) | Yes | Database password | `sipsa_pass` |
| `DB_MAX_POOL_SIZE` | No | No | HikariCP max pool size | `10` |
| `DB_MIN_IDLE` | No | No | HikariCP min idle connections | `2` |
| `DB_IDLE_TIMEOUT` | No | No | HikariCP idle timeout (ms) | `30000` |
| `SOAP_ENDPOINT` | No | No | DANE SOAP endpoint | `https://appweb.dane.gov.co/sipsaWS/SrvSipsaUpraBeanService` |
| `SOAP_CONNECT_TIMEOUT_MS` | No | No | SOAP connect timeout (ms) | `30000` |
| `SOAP_READ_TIMEOUT_MS` | No | No | SOAP read timeout (ms); kept high — Parcial alone is 619K+ records per full ingestion | `3600000` |
| `SOAP_MAX_RETRIES` | No | No | SOAP retry attempts (0 = no retries) | `3` |
| `SOAP_RETRY_BACKOFF_MS` | No | No | Backoff between retries (ms) | `2000` |
| `SOAP_MAX_CHILD_ELEMENTS` | No | No | XML child-element guard (0 = unlimited) | `0` |
| `SOAP_LOGGING_ENABLED` | No | No | Verbose SOAP request/response logging | `false` |
| `INGESTION_BATCH_SIZE` | No | No | Records per ingestion batch, 1–10000 | `500` |
| `INGESTION_MONTHLY_WINDOW_START` | No | No | Earliest time (`HH:mm`, `America/Bogota`) a monthly run is authorized | `14:00` |
| `MAX_REJECT_COUNT` | No | No | Absolute reject-count gate before a run fails | `5000` |
| `MAX_REJECT_RATE` | No | No | Reject-rate gate, fraction in `[0..1]` | `0.01` |
| `TASK_POOL_SIZE` | No | No | Spring's default task-scheduling pool size | `5` |
| `SIPSA_SCHEDULING_POOL_SIZE` | No | No | SIPSA custom scheduler pool size | `5` |
| `SIPSA_SCHEDULING_AWAIT_TERMINATION` | No | No | Scheduler shutdown grace period (s) | `30` |
| `SIPSA_ASYNC_CORE_POOL_SIZE` | No | No | `ingestionTaskExecutor` core pool size | `2` |
| `SIPSA_ASYNC_MAX_POOL_SIZE` | No | No | `ingestionTaskExecutor` max pool size (>= core) | `10` |
| `SIPSA_ASYNC_QUEUE_CAPACITY` | No | No | `ingestionTaskExecutor` queue capacity (0 = direct handoff) | `25` |
| `SIPSA_ASYNC_KEEP_ALIVE_SECONDS` | No | No | Executor thread keep-alive (s) | `60` |
| `SIPSA_HEALTH_DAILY_STALENESS_THRESHOLD` | No | No | Max age of last successful daily-method run before health reports DOWN | `36h` |
| `SIPSA_HEALTH_MONTHLY_STALENESS_THRESHOLD` | No | No | Same, for monthly methods | `840h` |
| `LOG_LEVEL_ROOT` | No | No | Root log level | `INFO` |
| `LOG_LEVEL_SIPSA` | No | No | `com.dalejandrov.sipsa` log level | `INFO` |

Values fixed by the DANE contract (timezone, SOAP namespace, ingestion cron schedule,
pagination policy) are **not** environment variables — they are hardcoded in
[`application.yaml`](../../src/main/resources/application.yaml).

### Spring profiles

| Profile | When active | Behavior |
|---|---|---|
| `dev` | Default when `SPRING_PROFILES_ACTIVE` is unset | Local DB credentials, verbose logging, SQL formatting, Actuator `loggers` endpoint, JWT issuer defaults to the local mock OIDC (`http://localhost:9000/default`) |
| `docker` | Set by `docker-compose.yml` | Container topology only (`db` host); credentials and issuer still come from the environment |
| Base (no profile / production) | Any other value | No credential defaults — `DB_USERNAME`, `DB_PASSWORD`, and `SIPSA_JWT_ISSUER_URI` are required; the app fails fast at startup if missing |

---

## Frequent errors

| Symptom | Cause | Fix |
|---|---|---|
| App fails at startup: `Failed to configure a DataSource` | No local PostgreSQL running, or `sipsa_db` doesn't exist | Start PostgreSQL and run `createdb sipsa_db` |
| App fails at startup naming `SIPSA_JWT_ISSUER_URI` | Running with a non-dev profile and no issuer configured | Export `SIPSA_JWT_ISSUER_URI`, or unset `SPRING_PROFILES_ACTIVE` to use the `dev` default |
| `401 Unauthorized` on `/api/internal/**` | No `Authorization: Bearer` token, or a token from the wrong issuer | Get a token from the mock OIDC service — see [API authentication](../api/README.md#authentication) |
| `403 Forbidden` on `/api/internal/**` | Token is valid but lacks the operation's scope | Request a token with the required `sipsa/*` scope |
| `FlywayMigrationsTest` skipped locally | Docker not available on your machine | Expected locally; CI fails the build if this happens there (the skip cannot mask a broken migration in CI) |
| `GET /api/sipsa/**` returns an empty `results` array | No ingestion run has populated the database yet | Trigger a manual ingestion — see [Manual ingestion](../api/sipsa-rest-api.md#post-apiinternalingestionrun) |
