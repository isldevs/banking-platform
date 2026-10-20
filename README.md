# Microservices Platform — Executable Demo Package

A **learning/demo implementation** derived from `MICROSERVICES_README_SOURCE.md`. It is **not a production-ready banking platform**.

## Services

| Service | Port | Database | Responsibility |
|---|---:|---|---|
| gateway-service | 8080 | None | Public routing, JWT verification, correlation ID |
| auth-service | 8081 | auth | OAuth2/OIDC Authorization Server; legacy demo login/refresh deprecated |
| customer-service | 8082 | customer | Customer profile, KYC approval |
| account-service | 8083 | account | Account ownership, balance, atomic ledger |
| transfer-service | 8084 | transfer | Idempotency, transfer workflow, recovery, Kafka outbox |
| notification-service | 8085 | notification | Kafka consumer, in-app notices |
| audit-service | 8086 | audit | Append-only Kafka audit events |
| config-service | 8888 | None | Native Spring Cloud Config Server |

## Configuration architecture

Configuration is bundled directly inside `config-service`. There is **no separate `config-repo`** and no Git clone at Config Server runtime.

```text
config-service/src/main/resources/config/
├── dev/
│   ├── application.yml
│   ├── auth-service.yml
│   ├── customer-service.yml
│   ├── account-service.yml
│   ├── transfer-service.yml
│   ├── notification-service.yml
│   ├── audit-service.yml
│   └── gateway-service.yml
├── qa/
│   └── same service files
├── stage/
│   └── same service files
└── prod/
    └── same service files
```

There is intentionally **no `base/` directory**, no `*-dev.yml`/`*-qa.yml` filename scheme, and no `docker` Spring profile.

The only application environment profiles are:

```text
dev
qa
stage
prod
```

Docker is a deployment mechanism only. Docker Compose runs the services with `SPRING_PROFILES_ACTIVE=dev` and supplies container endpoints through environment variables; it does not create a Docker profile.

The Config Server native backend maps the requested Config Client profile to the matching directory:

```text
/auth-service/dev    -> config/dev/application.yml + config/dev/auth-service.yml
/auth-service/qa     -> config/qa/application.yml + config/qa/auth-service.yml
/auth-service/stage  -> config/stage/application.yml + config/stage/auth-service.yml
/auth-service/prod   -> config/prod/application.yml + config/prod/auth-service.yml
```

Environment branches can therefore promote configuration cleanly:

```text
dev -> qa -> stage -> prod
```

## Gradle composite build

The workspace includes root `settings.gradle`, `build.gradle`, and `gradle.properties`. It is a Gradle composite build, not a multi-module monorepo: each Spring Boot service keeps its own independent Gradle build.

```bash
gradle buildAll
```

Windows PowerShell:

```powershell
./infrastructure/scripts/build-all.ps1
```

See `docs/GRADLE_COMPOSITE_BUILD.md` for build details. Java 21 is required.

## Quick start

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
docker compose ps
python3 scripts/demo_flow.py
```

Postgres is available on local port `5439`, gateway on `8080`, auth-service on `8081`, and Kafka's host listener on `29092`.
Local demo secrets and generated signing material live only in `infrastructure/.env` and `infrastructure/secrets/`; both are git-ignored.

## GitHub Actions configuration

To sync local demo settings into GitHub Actions Variables and Secrets:

```bash
gh auth login
bash infrastructure/scripts/init-local.sh
bash infrastructure/scripts/sync-github-actions-config.sh
```

The sync script uploads non-sensitive settings such as `JWT_ISSUER`, `APP_UID`, `APP_GID`, `CONFIG_SERVER_USERNAME`, and `DEMO_ADMIN_USERNAME` as Variables. Database passwords, `CONFIG_SERVER_PASSWORD`, `INTERNAL_SERVICE_KEY`, `OAUTH2_DEMO_CLIENT_SECRET`, and demo passwords are uploaded as Secrets.

## Security and data ownership

- `auth-service` persists OAuth2 clients, authorizations, and consents in PostgreSQL using JDBC repositories and Flyway.
- Six application databases are isolated by service; services do not read another service's tables.
- `account-service` is the financial atomicity boundary for balance and ledger updates.
- `transfer-service` owns transfer workflow state and client idempotency and uses a Kafka outbox.
- Internal service credentials and JWT signing material are supplied at runtime and must never be committed.
- This remains a demo: production deployment still requires real service identity/mTLS, key management, TLS, rate limiting/WAF, complete resilience policies, operational controls, and full integration coverage.

## Repo tree

```text
microservices-platform/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── auth-service/
├── customer-service/
├── account-service/
├── transfer-service/
├── notification-service/
├── audit-service/
├── config-service/
├── gateway-service/
├── contracts/
├── infrastructure/
│   ├── docker-compose.yml
│   ├── .env.example
│   └── scripts/
└── MICROSERVICES_README_SOURCE.md
```

## Architecture

The service package conventions, bounded-context boundaries, and banking-specific design guardrails are documented in `docs/architecture/package-structure.md`.
