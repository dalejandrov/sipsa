# SIPSA Integration Service

![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18.0-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/github/license/dalejandrov/sipsa)

REST API wrapper for DANE Colombia's SIPSA SOAP web service. Access agricultural price and supply data through a modern REST interface with automatic syncing, pagination, filtering, and timezone support.

## What is SIPSA?

SIPSA (Sistema de Información de Precios y Abastecimiento del Sector Agropecuario) is Colombia's official agricultural market information system maintained by DANE. It tracks prices and supply across different market types:

- **Ciudad**: Daily city-level pricing
- **Parcial**: Municipality market data  
- **Mayoristas**: Weekly and monthly wholesale market aggregates
- **Abastecimientos**: Monthly supply data to wholesale markets

## Quick Start

### Docker (recommended)

```bash
git clone https://github.com/dalejandrov/sipsa.git
cd sipsa
docker-compose up -d
```

API available at `http://localhost:8080`

### Local Development

Requirements: Java 25, PostgreSQL 18, Maven 3.9+

```bash
# 1. Clone and navigate
git clone https://github.com/dalejandrov/sipsa.git
cd sipsa

# 2. Set up environment variables
cp .env.example .env
# Edit .env with your database credentials

# 3. Create database (adjust credentials if needed)
createdb sipsa_db

# 4. Run
./mvnw spring-boot:run
```

## API Usage

### Query Endpoints (Public)

**Base URL**: `/api/sipsa`

- `GET /api/sipsa` - List all available endpoints
- `GET /api/sipsa/ciudad` - Daily city-level prices
- `GET /api/sipsa/parcial` - Municipality market data
- `GET /api/sipsa/mayoristas/semanal` - Weekly wholesale prices
- `GET /api/sipsa/mayoristas/mensual` - Monthly wholesale aggregates
- `GET /api/sipsa/abastecimientos/mensual` - Monthly supply to wholesale markets

### Operations Endpoints (Internal)

**Base URL**: `/api/internal/ingestion`

- `POST /api/internal/ingestion/run` - Trigger manual ingestion (query params: `method`, `force`)
- `GET /api/internal/ingestion/methods` - List available ingestion methods
- `GET /api/internal/ingestion/running` - Get currently active ingestion runs
- `GET /api/internal/ingestion/runs` - List all ingestion runs
- `GET /api/internal/ingestion/runs/{runId}` - Get specific run status
- `POST /api/internal/ingestion/cancel/{runId}` - Cancel an active run

### Audit Endpoints (Internal)

**Base URL**: `/api/internal/audit`

- `GET /api/internal/audit/request/{requestId}` - Get audit trail for specific request
- `GET /api/internal/audit/run/{runId}` - Get audit events for specific run
- `GET /api/internal/audit/recent` - Get most recent audit events (last 100)
- `GET /api/internal/audit/all` - Query all audit events with pagination and filters

## Features

- **REST instead of SOAP**: No more XML wrangling
- **Automatic sync**: Scheduled jobs fetch new data from DANE
- **Timezone support**: Get dates in your timezone via `X-Timezone` header
- **Pagination**: Standard limit/offset pagination
- **Filtering**: Query by date ranges, cities, products
- **Docker ready**: Includes PostgreSQL in docker-compose
- **Health checks**: `/actuator/health` for monitoring
- **Audit trail**: Track all data ingestion runs

## Configuration

All configuration is done via environment variables. Copy `.env.example` to `.env` and adjust values:

```bash
cp .env.example .env
```

Key configurations available in [.env.example](.env.example):
- **Database**: Connection settings (host, port, credentials)
- **Server**: Port and active profiles
- **SOAP Service**: DANE endpoint and timeouts
- **Ingestion**: Batch sizes, schedules, and quality thresholds
- **Logging**: Log levels per package

For advanced configuration, see [application.yaml](src/main/resources/application.yaml).

## Tech Stack

- Java 25
- Spring Boot 4.1.0
- PostgreSQL 18
- Apache CXF (SOAP client)
- Resilience4j (circuit breaker)

## Architecture

```
API Layer (REST controllers)
    ↓
Application Layer (services, business logic)
    ↓
Domain Layer (entities, rules)
    ↓
Infrastructure (repositories, SOAP client, schedulers)
```

**How it works:**

1. Scheduled jobs run based on DANE's publication schedule
2. SOAP client fetches data from DANE's web service
3. Data is validated and stored in PostgreSQL
4. REST API serves the data with filtering and pagination
5. Audit system logs all operations

## Documentation

Detailed technical documentation and diagrams are available in `docs/`:

- **[REST API Documentation](docs/SipsaRestController-API-Documentation.md)**: Public `/api/sipsa` reference with examples
- **[ER Diagram](docs/diagrams/er-diagram.puml)**: Database schema with entities and relationships
- **[Class Diagram](docs/diagrams/class-diagram.puml)**: System architecture organized by layers
- **[Component Diagram](docs/diagrams/component-diagram.puml)**: Component interactions and dependencies
- **Sequence Diagrams** (`docs/diagrams/sequence/`): Process flows for ingestion and API queries

View diagrams using [PlantUML Online](https://www.plantuml.com/plantuml/) or IDE plugins.

## Manual Data Ingestion

Trigger a manual sync via operations API:

```bash
curl -X POST "http://localhost:8080/api/internal/ingestion/run?method=promediosSipsaCiudad&force=false"
```

Available methods:
- `promediosSipsaCiudad` - City-level pricing
- `promediosSipsaParcial` - Municipality markets
- `promediosSipsaSemanaMadr` - Weekly wholesale
- `promediosSipsaMesMadr` - Monthly wholesale
- `promedioAbasSipsaMesMadr` - Monthly supply

Check available methods:
```bash
curl http://localhost:8080/api/internal/ingestion/methods
```

## Contributing

Pull requests welcome. For major changes, open an issue first.

## License

[MIT](LICENSE)

---

Data provided by [DANE - Departamento Administrativo Nacional de Estadística](https://www.dane.gov.co/index.php/estadisticas-por-tema/agropecuario/sistema-de-informacion-de-precios-sipsa/servicio-web-para-consulta-de-la-base-de-datos-de-sipsa)

*This is an unofficial wrapper. For official data access, visit DANE's website.*
