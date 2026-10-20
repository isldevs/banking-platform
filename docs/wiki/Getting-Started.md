# Getting Started

## Prerequisites

- Java 21 for building and running the Spring Boot services.
- Git and Bash (Git Bash or WSL is suitable for the local secret-init script).
- OpenSSL for local key and secret generation.
- PostgreSQL and Kafka if running services locally without containers.
- Docker Engine and Docker Compose only if your environment permits container use.

Follow organizational device and container policies. If Docker is blocked by corporate policy, do not attempt to bypass the restriction; use an approved local service installation or approved development environment.

## Quick start with Docker Compose

From the repository root, run:

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
docker compose ps
python3 scripts/demo_flow.py
```

The initialization script creates local secrets in `infrastructure/.env` and generated JWT signing material in `infrastructure/secrets/`. These paths are ignored by Git.

Compose exposes the gateway at `http://localhost:8080`, Auth Service at `http://localhost:8081`, PostgreSQL on host port `5439`, and Kafka's host listener on `29092`.

## Run services locally without Docker

When working under an environment where containers are unavailable:

1. Use an approved PostgreSQL installation or database host and create the service databases/roles required by the project.
2. Use an approved Kafka service if testing asynchronous workflows.
3. Start Config Service first on port 8888.
4. Configure each application with `SPRING_PROFILES_ACTIVE=dev`, the Config Server URL, database connection settings, JWT key paths, and the required service URLs. Config Server credentials are not required.
5. Start Auth Service, then the remaining services, following their dependencies.
6. Run the root Gradle build and tests before testing end-to-end workflows.

The Docker Compose host port `5439` does not automatically apply to a direct local PostgreSQL installation; use the port configured for that approved installation.

Auth Service local environment settings include:

- `CONFIG_SERVICE_URL=http://localhost:8888`
- `SECURITY_JWT_KEYSTOREPATH=<absolute path to infrastructure/secrets/jwt-signing.p12>`
- `SECURITY_JWT_KEYSTOREPASSWORD=<JWT_KEYSTORE_PASSWORD from infrastructure/.env>`
- `SECURITY_JWT_KEYSTOREALIAS=jwt-signing`
- `OAUTH2_DEMO_CLIENT_SECRET=<value from infrastructure/.env>`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auth`
- `SPRING_DATASOURCE_USERNAME=auth_user`
- `SPRING_DATASOURCE_PASSWORD=<DB_PASSWORD_AUTH from infrastructure/.env>`

## Check service health

Actuator health/info endpoints are enabled in application configuration. For example:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8888/actuator/health
```

If a service fails to start, check the Config Server first, verify the active profile and runtime credentials, then check PostgreSQL/Kafka availability and service logs.

## Next steps

- [[Configuration]] — how each service gets environment settings
- [[Authentication-and-OAuth2]] — configure a client and obtain tokens
- [[Development-and-Delivery]] — build and test commands
