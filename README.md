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

## Postman Native Git workflow

This repository uses Postman as the API development and testing client. The preferred long-term workflow is **Postman Native Git**, where Postman collections and environments are stored as files in the Git repository and synchronized with a shared Postman Cloud workspace when changes are ready to share.

### Current Postman layout

The repository currently contains exported Postman files under `docs/postman/`:

- `docs/postman/iSLDevs-Banking-API.postman_collection.json`
- `docs/postman/iSLDevs-Banking-Local.postman_environment.json`

These are the current portable exports. Do not manually create Postman's Native Git YAML structure. During migration, let the Postman desktop app create `.postman/` and `postman/` and then verify the generated files before removing the legacy JSON exports.

### Native Git setup

Postman Native Git requires the Postman desktop app. For this repository, use the following workflow:

1. Make sure the local repository is on the integration branch and up to date:

   ```bash
   git checkout main
   git pull
   git status
   ```

2. Open the repository folder in the Postman desktop app using **Local Files** / **Open Folder**.

3. Create or open the Postman workspace for the **iSLDevs Microservices Banking Platform** and connect the local repository folder to that workspace.

4. Because this is a multi-service monorepo, Postman normally recommends one workspace per service folder. This project intentionally uses a **single platform-level Postman collection covering multiple services**, so the initial Native Git workspace should be connected to the **repository root**. Revisit this decision only if the collection is later split into service-specific workspaces.

5. If Postman detects the existing collection/environment, connect the existing workspace and pull the existing Postman resources into the local project. Do not overwrite the existing exports until the Native Git version has been verified.

6. Confirm that Postman creates these Native Git files/directories:

   ```text
   .postman/
   postman/
   ```

   The `.postman/resources.yaml` manifest maps local resources to the Postman Cloud workspace. The `postman/` directory contains the Git-managed collections, environments, and other Postman resources.

7. In Postman **Local View**, review the generated collection and environment. Use the Postman **Issues** panel to fix schema/path/file-format problems before syncing.

8. Test the important platform flow locally:

   ```text
   Authentication
     -> Create Customer
     -> Approve Customer KYC
     -> Open Account
     -> Post Settled Initial Funding
     -> Get Account
     -> Create Transfer
     -> Get Transfer
   ```

9. Verify that dynamic values such as `customer_id`, `account_id`, `instruction_id`, `operation_id`, `transfer_id`, and `idempotency_key` are captured/generated correctly before committing the Native Git files.

10. Review the Git diff. Native Git changes should be treated like application source changes:

   ```bash
   git status
   git diff
   git add .postman postman
   git commit -m "chore(postman): migrate API workspace to native git"
   git push -u origin <feature-branch>
   ```

11. Open a pull request and merge through the normal repository workflow. The merged integration branch becomes the source of truth for Git-managed Postman resources.

### Postman Cloud sync

Use **Local View** for normal development. Push to Postman Cloud only after the Git diff has been reviewed and the local requests have been tested. Cloud View is the shared representation for teammates who work from Postman without cloning the repository.

For this project, the intended direction is:

```text
Feature branch
    |
    v
Postman Local View
    |
    v
Git diff -> commit -> pull request -> merge to main
    |
    v
Postman Cloud sync
```

Do not make conflicting manual edits in Cloud View and Local View at the same time. If a Cloud change must be preserved, pull it into the local Git workspace before pushing local changes.

### Secrets and environment values

Never commit real passwords, OAuth client secrets, JWT signing material, service keys, or other credentials in Postman resources. Keep local-only values in Postman and use Postman Vault or another approved secret mechanism for sensitive values. Commit only non-sensitive shared values required by the project.

The committed local environment should contain safe defaults such as local service URLs and empty placeholders for sensitive/runtime values. Review the Git diff before every Postman commit.

### CI/CD: sync `main` to Postman Cloud

After Native Git has been connected successfully and `.postman/resources.yaml` contains the real Postman workspace mapping, the repository can automate Cloud synchronization after a merge to `main` using the Postman CLI.

The CI job should:

1. Check out `main`.
2. Install/authenticate the Postman CLI.
3. Provide a protected `POSTMAN_API_KEY` repository secret.
4. Run `postman workspace push` from the directory containing `.postman/resources.yaml`.
5. Fail the workflow if the Native Git resources cannot be synchronized.

Do not add a real Postman API key or workspace ID to source control. Configure them as repository/organization secrets or protected CI variables.

### Native Git rules for this repository

- **Git is the source of truth for reviewed API changes.**
- **Postman Local View is the normal editing surface.**
- **Postman Cloud is the shared/consumer view.**
- Review Native Git YAML changes in pull requests just like Java/configuration changes.
- Do not manually edit `.postman/resources.yaml` unless there is a specific Postman-supported reason.
- Do not commit secrets.
- Do not delete the legacy `docs/postman/*.json` exports until the Native Git migration is verified and the team agrees to remove them.
- Keep the platform collection aligned with the actual gateway routes and current API contracts.

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
