# SIPSA Integration Service

REST wrapper for DANE Colombia's SIPSA SOAP web service. It exposes official agricultural
price and supply data through a modern REST API, with automatic scheduled syncing,
pagination, filtering, and timezone-aware responses.

[![CI](https://github.com/dalejandrov/sipsa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/dalejandrov/sipsa/actions/workflows/ci.yml)
[![Infra Plan](https://github.com/dalejandrov/sipsa/actions/workflows/infra-plan.yml/badge.svg?branch=main)](https://github.com/dalejandrov/sipsa/actions/workflows/infra-plan.yml)
![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)
![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)
![Terraform](https://img.shields.io/badge/IaC-Terraform-844FBA?logo=terraform&logoColor=white)
![License](https://img.shields.io/github/license/dalejandrov/sipsa)

---

## Description

SIPSA (Sistema de Información de Precios y Abastecimiento del Sector Agropecuario) is
Colombia's official agricultural market information system, maintained by DANE. This
service ingests SIPSA data through DANE's SOAP interface, stores it in PostgreSQL, and
republishes it as a versioned, paginated, filterable REST API — no SOAP/XML handling
required by consumers.

> This is an unofficial wrapper. For official data access, see
> [DANE's SIPSA web service page](https://www.dane.gov.co/index.php/estadisticas-por-tema/agropecuario/sistema-de-informacion-de-precios-sipsa/servicio-web-para-consulta-de-la-base-de-datos-de-sipsa).

## Key features

- **REST instead of SOAP** — no XML wrangling for API consumers.
- **Automatic sync** — scheduled jobs fetch new data on DANE's publication schedule.
- **Timezone support** — request data in any IANA timezone via the `X-Timezone` header.
- **Pagination and filtering** — by date range, product, city, source, and more.
- **Audit trail** — every ingestion run and its events are queryable.
- **Health checks** — `/actuator/health`, including data-staleness indicators.

## Architecture (summary)

```
API Layer (REST controllers)
    ↓
Application Layer (services, business logic)
    ↓
Domain Layer (entities, rules)
    ↓
Infrastructure (repositories, SOAP client, schedulers)
```

Full details, request flow, and design principles:
[Project Architecture](docs/architecture/project-architecture.md).

## Technology

- Java 25 · Spring Boot 4.1.0 · Maven (wrapper included)
- PostgreSQL 18 · Flyway migrations
- Apache CXF (SOAP client) · Resilience4j (circuit breaker)
- Spring Security OAuth2 Resource Server (JWT)
- Docker / Docker Compose for local orchestration
- Terraform for the AWS target infrastructure (declared as code — see
  [Project Status](#project-status))

## Quick start

```bash
git clone https://github.com/dalejandrov/sipsa.git
cd sipsa
docker compose up --build -d
curl http://localhost:8080/actuator/health
```

The API is now available at `http://localhost:8080`. For running without Docker, the
full environment-variable reference, and troubleshooting, see
[Local Development](docs/getting-started/local-development.md). For the full Docker
Compose walkthrough, see [Docker](docs/getting-started/docker.md).

## Documentation

| | |
|---|---|
| [Documentation index](docs/README.md) | Central index of every document in this repository |
| [Local Development](docs/getting-started/local-development.md) · [Docker](docs/getting-started/docker.md) | Getting started guides |
| [API overview](docs/api/README.md) · [API reference](docs/api/sipsa-rest-api.md) | Authentication, endpoints, parameters, examples |
| [HTTP request collection](http/sipsa-api.http) | Runnable requests (IntelliJ HTTP Client / VS Code REST Client) |
| [Architecture](docs/architecture/) | Project architecture, ADRs, AWS production readiness |
| [Operations](docs/operations/) | AWS deployment preflight and readiness evidence |
| [Changelog](CHANGELOG.md) | Notable changes per release |

## Tests

```bash
./mvnw clean verify
```

Runs the full unit/integration suite (H2 in-memory) plus `FlywayMigrationsTest`, which
validates every database migration against a real PostgreSQL 18 container via
Testcontainers. This is the exact command the [CI workflow](.github/workflows/ci.yml)
runs on every pull request and push to `main`; see
[Testing Strategy](docs/architecture/testing-strategy.md) for the full test pyramid.

## Project status

The application and its test suite are implemented and pass CI (`CI` workflow above).
The target AWS production infrastructure (VPC, RDS, ECS, API Gateway, Cognito) is fully
**declared as Terraform code** (validated by the `Infra Plan` workflow — format, lint,
module tests, Trivy scan) but **`terraform apply` has not been run against any AWS
account**. Real-AWS validation (TECH-143) remains blocked pending SIPSA-specific
credentials — see [AWS Production Preflight](docs/operations/aws-production-preflight.md)
for the current evidence and what's still outstanding.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for environment setup, branching/commit
conventions, and the pull request checklist. AI agents should also read
[AGENTS.md](AGENTS.md).

## License

[MIT](LICENSE)

---

Data provided by [DANE — Departamento Administrativo Nacional de Estadística](https://www.dane.gov.co/index.php/estadisticas-por-tema/agropecuario/sistema-de-informacion-de-precios-sipsa/servicio-web-para-consulta-de-la-base-de-datos-de-sipsa).
