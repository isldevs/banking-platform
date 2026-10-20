# Microservices Platform — Executable Demo Package

A **learning/demo implementation** derived from `MICROSERVICES_README_SOURCE.md`. It is **not a production-ready banking
platform**.

## Gradle composite build — build all services in one command

This workspace now includes root `settings.gradle`, `build.gradle`, and `gradle.properties`.
It is a **Gradle composite build**, not a multi-module monorepo: each Spring Boot
service keeps its own independent `settings.gradle` and `build.gradle`; database-backed services own their databases.

```bash
# Requires Gradle 8.10.2 and JDK 21
gradle buildAll

# Or use Docker-based Gradle if Gradle is not installed:
bash infrastructure/scripts/build-all.sh

# Windows PowerShell:
./infrastructure/scripts/build-all.ps1

# Build only account-service from workspace root:
gradle :account-service:bootJar
```

See [`docs/GRADLE_COMPOSITE_BUILD.md`](docs/GRADLE_COMPOSITE_BUILD.md) for all tasks,
installation options and independent repository guidance.

## Services (independent standalone Gradle builds)

| Service              | Port | Database     | Responsibility                                         |
|----------------------|-----:|--------------|--------------------------------------------------------|
| gateway-service      | 8080 | None         | Public routing, JWT verification, correlation ID       |
| auth-service         | 8081 | auth         | OAuth2/OIDC Authorization Server + demo login/refresh  |
| customer-service     | 8082 | customer     | Customer profile, KYC approval                         |
| account-service      | 8083 | account      | Account ownership, balance, atomic double-entry ledger |
| transfer-service     | 8084 | transfer     | Idempotency, transfer workflow, recovery, Kafka outbox |
| notification-service | 8085 | notification | Kafka consumer, in-app notices                         |
| audit-service        | 8086 | audit        | Append-only Kafka audit events                         |
| config-service       | 8888 | None         | Git-backed Spring Cloud Config Server                  |

`infrastructure` and `contracts` are separate logical Git repositories. You can initialize all ten Git roots using
`bash infrastructure/scripts/initialize-git.sh`.

## Quick start (Docker + Bash/WSL + OpenSSL + Python 3)

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
docker compose ps
python3 scripts/demo_flow.py
```

The Gradle Docker build downloads dependencies on first build, so internet access or a company Maven mirror is needed.
First startup may require extra time. On Windows use Docker Desktop with WSL2 and execute commands inside WSL or Git
Bash. Postgres is available on local port `5439`; gateway on `8080`; auth-service OAuth2/OIDC endpoints on `8081`. Kafka listener for external tools on `29092`. The
demo secrets and generated RSA key live **only in `infrastructure/.env` and `infrastructure/secrets/`** (both
git-ignored). Do not check them in. In WSL/Linux, auth-service uses your host UID/GID to read the mode-600 private key
bind mount.

## Demonstrated end-to-end flow

1. JWT login (seeded `demo` and `admin` users; read passwords from `infrastructure/.env`).
2. User creates a customer profile (`PENDING_KYC`).
3. Admin approves KYC (`ACTIVE`).
4. User opens two USD accounts; account-service verifies customer and ownership over internal REST.
5. Admin funds first account with `POST /api/accounts/{id}/fund` (explicitly **demo-only**); offsetting debit is posted
   to a system clearing account, preserving balanced ledger entries.
6. User posts `$125.00` from account A to account B with `Idempotency-Key`.
7. Transfer-service creates PENDING transfer and its idempotency record in its own DB, asks account-service to
   atomically debit, credit and post ledger entries in the account DB, then marks COMPLETED and inserts the Kafka outbox
   event in the transfer DB.
8. Outbox publisher sends `transfer.completed.v1` to Kafka; notification and audit consumers each deduplicate `eventId`
   in their own DB.
9. Repeating the same request/key returns the same transfer. Same key, different request is rejected. Balances are
   verified at 375.00 and 125.00.

## Manual API examples

```bash
# Token: log in through gateway; use password from infrastructure/.env
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"REPLACE_FROM_ENV"}'

# Protected example
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# Health check (all containers)
cd infrastructure && docker compose ps
```

## OAuth2 / OIDC browser demo

The Authorization Code + PKCE client is configured for Postman:

- Issuer: `http://localhost:8081`
- Client ID: `demo-browser` (public client, no secret)
- Authorization endpoint: `http://localhost:8081/oauth2/authorize`
- Token endpoint: `http://localhost:8081/oauth2/token`
- Discovery: `http://localhost:8081/.well-known/openid-configuration`
- Redirect URI: `https://oauth.pstmn.io/v1/callback`

In Postman, create an OAuth 2.0 Authorization Code (With PKCE) token, use the client ID above, leave client secret blank, and use the seeded `admin` or `demo` credentials from `infrastructure/.env`. The legacy `/api/auth/login` flow remains available for the automated banking demo.

## Data ownership and transaction guarantees

- Six **separate PostgreSQL databases** and application roles; services never read another service's tables.
- `account-service` is the financial atomicity boundary. It applies deterministic PostgreSQL row locking, balance
  updates, paired `DEBIT`/`CREDIT` entries, and an `account_operations` idempotency record in **one local transaction**.
- `transfer-service` owns workflow state and client idempotency. When account-service times out, transfer becomes
  `PENDING_RECONCILIATION` and retries using the exact **same transferId** (so it cannot debit twice).
- Completion state and Kafka outbox insert share a single local transfer DB transaction. Outbox events are
  at-least-once; consumers commit deduplication markers with their local changes.
- Each app includes Flyway SQL migrations, independent Gradle build, health endpoint, a basic JUnit test, input
  validations, and structured JSON error handling for application errors.

## Review these demo limitations before any real deployment

- `auth-service` now provides Spring Authorization Server Authorization Code + PKCE and OIDC discovery for a local demo
  client. The legacy `POST /api/auth/login` flow remains for the automated demo script. OAuth2 client registration and
  authorization state are in-memory and are not suitable for production persistence.
- Internal service calls use an externally injected shared `X-Service-Key` for **local demo only**; deploy mTLS or
  strong per-service identity with rotation, egress isolation and a real policy at company environments.
- `account-service` has a **demo funding endpoint** backed by a negative-balance clearing account. Disable
  `DEMO_FUNDING_ENABLED` outside local demo; production funding must reconcile to real external settlement.
- `config-service` serves non-secret configuration from `config-repo/` through Spring Cloud Config; all seven
  application services use mandatory Config Client imports with startup retry. Common values, service URLs,
  database locations, and Kafka settings are stored in `config-repo/`; database passwords, private keys, demo login
  passwords, and internal service credentials remain in `infrastructure/.env` or an approved secret store, never Git.
  Config changes do not automatically refresh running clients, so restart affected containers after changes.
- Gateway includes JWT validation/routing but not a production rate limiter, WAF, TLS termination or hardened
  header/CORS policy.
- This demo doesn't implement transfer reversal, maker-checker, full audit coverage, production-grade OAuth client lifecycle, distributed tracing,
  backups, full resilience policies, complete automated integration coverage, or a real UI. These remain milestones from the source
  roadmap, not completed features.
- GitHub Actions runs `gradle buildAll` for all independent services and a Docker Compose end-to-end demo, including
  OAuth2/OIDC discovery, JWKS, login, KYC, accounts, transfers, idempotency, ledger, notification, and audit checks.
  Review the latest Actions run before treating a particular commit as verified; this remains a learning/demo package.

## Repo tree

```text
microservices-platform/
├── settings.gradle                 # includes 8 independent builds
├── build.gradle                    # buildAll, testAll, bootJarAll, cleanAll
├── gradle.properties
├── auth-service/                  # Java, Gradle, Flyway
├── customer-service/
├── account-service/
├── transfer-service/
├── notification-service/
├── audit-service/
├── config-service/
├── config-repo/                   # Git-backed Spring Cloud Config properties
├── gateway-service/
├── contracts/                     # Versioned event contract examples
├── infrastructure/
│   ├── docker-compose.yml
│   ├── .env.example
│   ├── db/init/01_databases.sh
│   └── scripts/
│       ├── init-local.sh
│       ├── demo_flow.py
│       └── initialize-git.sh
└── MICROSERVICES_README_SOURCE.md
```
