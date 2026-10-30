# Getting Started

## Prerequisites

- **Java 17 JDK**, matching current service Gradle toolchains and CI.
- The committed Gradle wrapper (`gradlew` / `gradlew.bat`), configured for **Gradle 8.13**. GitHub Actions runs the same committed wrapper.
- Git.
- Docker Engine + Compose for the full local stack, or approved local PostgreSQL/Kafka/Redis services for non-container development.

The repository **does include** the Gradle wrapper. Run `./gradlew` on Linux/macOS or `.\gradlew.bat` on Windows PowerShell. The project is a Gradle composite build.

## Build and test

From the repository root:

```bash
./gradlew buildAll
./gradlew testAll
./gradlew checkAll
./gradlew spotlessCheckAll
```

To format all services:

```bash
./gradlew spotlessApplyAll
```

## Full Docker Compose stack

**Prepare secrets before starting the stack.** On Windows PowerShell run `./scripts/setup-local.ps1` (PowerShell syntax: `.\scripts\setup-local.ps1`) from the repository root. For a fresh isolated Linux/macOS demo, run `bash infrastructure/scripts/init-local.sh`; it refuses to overwrite existing credentials. Both helpers generate local `.env` and JWT material; start Compose separately. Existing environments can provision values and key material manually using the committed example and Compose volume paths.

From the repository root:

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env with approved local values
cd infrastructure
docker compose up --build -d
docker compose ps
```

The following components are **published to the host** by the current Compose file:

| Component | Host endpoint |
|---|---|
| Gateway | `http://localhost:8080` |
| Auth | `http://localhost:8081` |
| Config | `http://localhost:8888` |
| PostgreSQL | `localhost:5439` |
| Kafka | `localhost:29092` |
| Redis | `localhost:6379` |

Customer (`8082`), Account (`8083`), Transfer (`8084`), Notification (`8085`) and Audit (`8086`) are **not published on host ports** by the Compose file. Access their `/api/**` routes through Gateway, or reach them via service DNS names within the Compose network. Kafka uses `kafka:9092` inside Compose.

## Environment setup

The ignored `infrastructure/.env` is created from `infrastructure/.env.example`; replace every `replace-with-...` placeholder with suitable development-only secrets. Docker Compose loads this file and mounts JWT signing material under `infrastructure/secrets/`. A standalone Gradle process does **not** automatically receive variables from the Compose env file.

Windows PowerShell helper:

```powershell
.\scripts\setup-local.ps1
```

The helper prepares credentials and JWT signing material without starting Docker Compose. It preserves existing `.env` values and refuses to generate a new password when a JWT keystore exists without its matching `.env`. Run `docker compose up --build -d` from `infrastructure/` after setup.

Do not commit secrets, signing keys, database passwords, OAuth client secrets, or internal service keys.

## Run one service without Docker

1. Start PostgreSQL and any required Kafka/Redis services through an approved local setup.
2. Start Config Service on `8888`.
3. Provide the selected service's runtime environment values.
4. Run the service's independent Gradle build, for example:

```bash
cd account-service
../gradlew bootRun
```

5. Check its health endpoint, for example `http://localhost:8083/actuator/health` when the service is directly hosted. For composite root execution, use `./gradlew :account-service:bootRun` from the repository root.

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
8. Create USD/KHR internal transfers with the **required** Idempotency-Key
9. Read transfer and financial operation records; verify postings and balances
```

**Current repository gap:** the committed tree has no `docs/postman/` collection or Native Git Postman resource directories. Use the [API Reference](API-Reference.md) to construct requests until API assets are added.

## CI and demo limits

GitHub Actions now generates ephemeral CI-only credentials with `infrastructure/scripts/init-local.sh`, then checks Docker Compose configuration and public/internal service health. This is a **startup smoke test**, not a completed end-to-end banking transaction or ledger correctness test. Demo settlement is not a real external payment or correspondent-bank integration.

## Troubleshooting

If a service fails to start, check in this order:

1. Config Service health and active profile.
2. Database/Kafka/Redis availability as applicable.
3. Runtime environment variables.
4. JWT issuer/audience/key configuration.
5. Service logs and correlation IDs.

See [Configuration](Configuration.md), [Authentication and OAuth2](Authentication-and-OAuth2.md), and [API Reference](API-Reference.md).
