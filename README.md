# iSLDevs Microservices Banking Platform

A Java 21 / Spring Boot microservices learning platform that demonstrates banking-oriented backend design: OAuth2/OIDC authentication, customer/KYC lifecycle, account opening and funding, double-entry ledger operations, transfers, idempotency, Kafka events, audit/notification processing, centralized configuration, and Redis-backed gateway rate limiting.

> **Status:** learning/demo project. It is not a production-ready banking platform.

## Architecture

The runtime consists of independent Spring Boot services. Each business service owns its database and migrations; services communicate through APIs or Kafka events and never read another service's database directly.

| Service | Port | Owns | Responsibility |
|---|---:|---|---|
| gateway-service | 8080 | — | Public API entry point, JWT validation, routing, correlation ID, rate limiting |
| auth-service | 8081 | auth | OAuth2/OIDC Authorization Server, identity and token issuance |
| customer-service | 8082 | customer | Customer profile and KYC lifecycle |
| account-service | 8083 | account | Account lifecycle, balances, funding, operations and ledger |
| transfer-service | 8084 | transfer | Transfer workflow, idempotency, recovery and Kafka outbox |
| notification-service | 8085 | notification | Kafka-driven in-app notifications |
| audit-service | 8086 | audit | Kafka-driven append-oriented audit history |
| config-service | 8888 | — | Spring Cloud Config Server using bundled native configuration |

## Key flows

### Authentication

New clients use **OAuth 2.0 Authorization Code + PKCE** through Spring Authorization Server. The older `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout` endpoints remain only as deprecated compatibility endpoints.

### Account opening and initial funding

Account products are data-driven. An opening request requires `Idempotency-Key`; when an initial deposit is requested, the account is created as `PENDING_FUNDING` and a `funding_instruction` is created. The account is credited only when the settlement/posting step is received with an external settlement reference. Posting creates the financial operation and ledger entries atomically, then activates the account.

### Transfers

Transfer Service owns transfer workflow and client idempotency. Account Service remains the financial accounting boundary and owns balances and ledger entries. Kafka outbox events provide durable downstream processing for notification and audit.

### Gateway rate limiting

The Gateway uses Redis-backed fixed-window limits. Authenticated requests are limited by JWT subject; anonymous requests use client IP. Current defaults include 5/min for legacy login, 10/min for legacy refresh, 10/min for account-opening requests, 10/min for transfer creation, and 60/min for other `/api/**` requests. Limits are configurable by environment.

## Configuration

Configuration is bundled in `config-service/src/main/resources/config/` and separated by the application environments `dev`, `qa`, `stage`, and `prod`. There is no runtime `config-repo` checkout and Docker is not a Spring profile.

Secrets and runtime values come from the environment. Never commit database passwords, JWT signing material, OAuth client secrets, or internal service keys.

## Local development

Prerequisites: Java 21, Git, Gradle 8.10.x, and Docker Compose for the full local stack.

```bash
gradle buildAll
gradle testAll
gradle checkAll
```

Full local stack:

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env with local values
cd infrastructure
docker compose up --build -d
docker compose ps
```

Compose exposes PostgreSQL on `127.0.0.1:5439`, Kafka on `127.0.0.1:29092`, Redis on `127.0.0.1:6379`, Config Service on `127.0.0.1:8888`, Auth Service on `127.0.0.1:8081`, and the Gateway on `127.0.0.1:8080`.

The repository does not ship a Gradle wrapper. Use an approved local Gradle installation matching the documented version or an approved build environment.

## Documentation

The versioned documentation has one canonical API reference under `docs/wiki/API-Reference.md`; the older duplicate `docs/API_REFERENCE.md` is intentionally not maintained.

- [Project Wiki](docs/wiki/Home.md)
- [Architecture](docs/wiki/Architecture.md)
- [Services](docs/wiki/Services.md)
- [Getting Started](docs/wiki/Getting-Started.md)
- [Authentication and OAuth2](docs/wiki/Authentication-and-OAuth2.md)
- [API Reference](docs/wiki/API-Reference.md)
- [Configuration](docs/wiki/Configuration.md)
- [Security and Operations](docs/wiki/Security-and-Operations.md)
- [Development and Delivery](docs/wiki/Development-and-Delivery.md)
- [Database ownership and reconciliation](docs/DATABASES.md)
- [Gradle composite build](docs/GRADLE_COMPOSITE_BUILD.md)
- [Architecture/package conventions](docs/architecture/package-structure.md)

Postman assets are under `docs/postman/`.

## Repository structure

```text
microservice-platform-demo/
├── auth-service/
├── customer-service/
├── account-service/
├── transfer-service/
├── notification-service/
├── audit-service/
├── gateway-service/
├── config-service/
├── contracts/
├── infrastructure/
├── docs/
├── build.gradle
├── settings.gradle
└── README.md
```

Each service remains independently buildable. The root Gradle project orchestrates those independent builds through Gradle composite-build `includeBuild` entries.

## Security boundary

The Gateway performs edge validation and rate limiting, but it is not the only security boundary. Downstream services validate JWTs and enforce their own permissions and ownership rules. Service-to-service credentials such as `X-Service-Key` are for trusted internal calls only.

Financial operations must preserve idempotency, transaction boundaries, concurrency controls, and balanced ledger entries. This demo still requires organization-approved identity controls, key management, TLS/service identity, compliance controls, resilience validation, monitoring, reconciliation, and end-to-end integration testing before any real banking use.
