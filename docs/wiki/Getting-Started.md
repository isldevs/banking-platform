# Getting Started

## Prerequisites

- Java 21 JDK.
- Gradle 8.10.x or an approved equivalent build environment.
- Git.
- Docker Engine + Compose for the full local stack, or approved local PostgreSQL/Kafka/Redis services for non-container development.

The repository does not ship a Gradle wrapper. Use an approved Gradle installation or development environment.

## Build and test

From the repository root:

```bash
gradle buildAll
gradle testAll
gradle checkAll
gradle spotlessCheckAll
```

To format all services:

```bash
gradle spotlessApplyAll
```

## Full Docker Compose stack

From the repository root:

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env with approved local values
cd infrastructure
docker compose up --build -d
docker compose ps
```

Compose provides:

| Component | Host endpoint |
|---|---|
| Gateway | `http://localhost:8080` |
| Auth | `http://localhost:8081` |
| Customer | `http://localhost:8082` |
| Account | `http://localhost:8083` |
| Transfer | `http://localhost:8084` |
| Notification | `http://localhost:8085` |
| Audit | `http://localhost:8086` |
| Config | `http://localhost:8888` |
| PostgreSQL | `localhost:5439` |
| Kafka | `localhost:29092` |
| Redis | `localhost:6379` |

Inside the Compose network, services use container DNS names and Kafka uses `kafka:9092`.

## Environment setup

Create `infrastructure/.env` from `.env.example` and populate the required local values. The file is ignored by Git. Docker's JWT signing keystore and public key are mounted from ignored `infrastructure/secrets/` files when supplied by the local setup.

Do not commit secrets, signing keys, database passwords, OAuth client secrets, or internal service keys.

## Run one service without Docker

1. Start PostgreSQL and any required Kafka/Redis services through an approved local setup.
2. Start Config Service on `8888`.
3. Provide the selected service's runtime environment values.
4. Run the service's independent Gradle build, for example:

```bash
cd account-service
gradle bootRun
```

5. Check its health endpoint, for example `http://localhost:8083/actuator/health`.

For direct local runs, service URLs normally point to `localhost`; for Compose runs they point to container DNS names.

## First API workflow

The recommended application flow is:

```text
1. Obtain an OAuth2 access token using Authorization Code + PKCE
2. Create/verify a customer
3. Complete KYC when authorized
4. Open an account with Idempotency-Key
5. If initialDeposit > 0, capture the returned funding instruction
6. Post the settled funding instruction with X-Service-Key + X-External-Reference
7. Verify account becomes ACTIVE and balances are updated
8. Create transfers with Idempotency-Key when retry safety is required
```

Use the Postman collection under `docs/postman/` for a ready-made local request set.

## Troubleshooting

If a service fails to start, check in this order:

1. Config Service health and active profile.
2. Database/Kafka/Redis availability as applicable.
3. Runtime environment variables.
4. JWT issuer/audience/key configuration.
5. Service logs and correlation IDs.

See [[Configuration]], [[Authentication-and-OAuth2]], and [[API-Reference]].
