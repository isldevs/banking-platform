# Getting Started

## Prerequisites

- Java 21 for building and running the Spring Boot services.
- Git and PowerShell on Windows, or Bash on Linux/macOS.
- OpenSSL is not required for the Windows local environment scripts; a JDK `keytool` is required.
- PostgreSQL and Kafka if running services locally without containers.
- Docker Engine and Docker Compose only if your environment permits container use.

Follow organizational device and container policies. If Docker is blocked by corporate policy, do not attempt to bypass the restriction; use an approved local service installation or approved development environment.

## Local environment configuration

The recommended local development flow uses `infrastructure/.env` as the single source for local secrets and environment-specific values. The file is ignored by Git and should be created from the safe template:

```text
infrastructure/.env.example  ->  infrastructure/.env
```

Do not commit `infrastructure/.env` or real credentials.

The local environment scripts load this file, set the runtime variables required by Config Server and the selected service, and generate local JWT signing material under the ignored `.jwt/` directory when it does not already exist.

### Windows PowerShell

From the repository root:

```powershell
Copy-Item infrastructure\.env.example infrastructure\.env
# Edit infrastructure\.env and replace all placeholders with local values.

. .\scripts\local-env.ps1 auth
.\gradlew.bat :auth-service:bootRun
```

For another service:

```powershell
. .\scripts\local-env.ps1 customer
.\gradlew.bat :customer-service:bootRun
```

Supported services are `auth`, `customer`, `account`, `transfer`, `notification`, `audit`, and `gateway`.

The PowerShell script must be **dot-sourced** (`. .\scripts\local-env.ps1 ...`) so that environment variables remain available in the current shell and are inherited by Gradle/Spring Boot.

### Windows CMD

```cmd
copy infrastructure\.env.example infrastructure\.env
rem Edit infrastructure\.env and replace all placeholders with local values.

call scripts\local-env.cmd auth
gradlew.bat :auth-service:bootRun
```

Use `call` so the variables loaded by the script remain in the current CMD session.

### Environment flow

```text
infrastructure/.env
        |
        v
local-env.ps1 / local-env.cmd
        |
        +-- local secrets
        +-- datasource settings
        +-- JWT settings
        +-- service endpoints
        +-- Kafka settings
        |
        v
Gradle bootRun
        |
        v
Spring Boot service
        |
        v
Config Service :8888
        |
        v
config/{dev}/<service>.yml
```

Config Server remains the source of application configuration structure. The local `.env` supplies runtime values referenced by the Config Server files; secrets are not committed into Config Server YAML.

### Local JWT material

The local scripts create:

```text
.jwt/
├── jwt-signing.p12
└── jwt-public.pem
```

The private signing keystore is used by Auth Service. Resource services use the public key where required. These files are generated locally and are ignored by Git. Production deployments must use an approved managed key/secret-management solution instead.

## Quick start with Docker Compose

From the repository root, initialize the local environment first:

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
docker compose ps
python3 scripts/demo_flow.py
```

The initialization script creates local secrets in `infrastructure/.env` and generated Docker signing material in `infrastructure/secrets/`. These paths are ignored by Git.

Compose exposes the gateway at `http://localhost:8080`, Auth Service at `http://localhost:8081`, PostgreSQL on host port `5439`, and Kafka's host listener on `29092`.

Docker Compose uses the `dev` Spring profile. Docker is a deployment mechanism, not a separate Spring environment profile. Inside the Compose network, services use container DNS names such as `config-service:8888`, `auth-service:8081`, and `kafka:9092`.

## Run services locally without Docker

When working under an environment where containers are unavailable:

1. Create and configure `infrastructure/.env` from `.env.example`.
2. Use an approved PostgreSQL installation or database host and create the service databases/roles required by the project.
3. Use an approved Kafka service if testing asynchronous workflows.
4. Start Config Service first on port 8888.
5. Dot-source `scripts/local-env.ps1 <service>` or call `scripts/local-env.cmd <service>` to load the runtime environment.
6. Start Auth Service, then the remaining services, following their dependencies.
7. Run the root Gradle build and tests before testing end-to-end workflows.

The Docker Compose host port `5439` does not automatically apply to a direct local PostgreSQL installation; use the port configured for that approved installation.

For local services, the scripts provide service-specific values as:

- `DATASOURCE_URL`
- `DATASOURCE_USERNAME`
- `DATASOURCE_PASSWORD`

These names intentionally match the Config Server development configuration contract.

## Check service health

Actuator health/info endpoints are enabled in application configuration. For example:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8888/actuator/health
```

If a service fails to start, check the Config Server first, verify the active profile and runtime environment, then check PostgreSQL/Kafka availability and service logs.

## Next steps

- [[Configuration]] — how each service gets environment settings
- [[Authentication-and-OAuth2]] — configure a client and obtain tokens
- [[Development-and-Delivery]] — build and test commands
