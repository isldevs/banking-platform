# Config Service

Spring Cloud Config Server for the platform. This service is stateless and has no application database.

## Responsibilities

- Serve shared configuration and service-specific configuration bundled inside the Config Server.
- Use Spring Cloud Config's native classpath backend; there is no separate `config-repo` service or Git clone at runtime.
- Apply the shared `application.yml` configuration to every client, then overlay the file matching `spring.application.name`.
- Support environment-specific overrides with profile files such as `application-docker.yml` and `auth-service-docker.yml`.
- Require HTTP Basic authentication for configuration endpoints.
- Expose health, info, metrics, and Prometheus actuator endpoints.
- Start independently of auth-service so every client can bootstrap before authentication services start.

## Configuration layout

```text
config-service/src/main/resources/config/
├── application.yml                 # shared/base configuration
├── application-docker.yml          # shared Docker overrides
├── auth-service.yml                # auth-service specific configuration
├── auth-service-docker.yml
├── customer-service.yml
├── customer-service-docker.yml
├── account-service.yml
├── account-service-docker.yml
├── transfer-service.yml
├── transfer-service-docker.yml
├── notification-service.yml
├── notification-service-docker.yml
├── audit-service.yml
├── audit-service-docker.yml
└── gateway-service.yml
```

Spring Cloud Config resolves configuration using the client's `spring.application.name` and `spring.profiles.active`. With the default `base` profile, a service receives the shared `application.yml` plus its specific file, for example `auth-service.yml`. Docker starts services with the `docker` profile, which loads the shared Docker and service Docker overrides as well.

## Required environment

```text
SERVER_PORT=8888
CONFIG_SERVER_USERNAME=<internal Config Client username>
CONFIG_SERVER_PASSWORD=<internal Config Client password>
```

No Git repository credentials are required. Never commit Config Client credentials or application secrets.

## Config Client endpoints

```text
GET /auth-service/base/main
GET /customer-service/base/main
GET /account-service/base/main
GET /transfer-service/base/main
GET /notification-service/base/main
GET /audit-service/base/main
GET /gateway-service/base/main
```

The label is retained in the Config Client protocol for compatibility, but the native backend loads the configuration packaged with this service rather than reading a Git branch.

## Run

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
```

### Run standalone on your machine

This service requires `CONFIG_SERVER_USERNAME` and `CONFIG_SERVER_PASSWORD`; starting `gradle bootRun` from an IDE or terminal does **not** automatically load `infrastructure/.env`.

**Windows PowerShell** (uses installed Gradle or the repository's Gradle Wrapper; Docker is not used):

```powershell
bash infrastructure/scripts/init-local.sh
.\infrastructure\scripts\run-config-service.ps1
```

**Linux / macOS / Git Bash / WSL**:

```bash
bash infrastructure/scripts/run-config-service.sh
```

Both scripts load the local Config Server credentials from `infrastructure/.env` without printing them. On Windows, the PowerShell script uses installed Gradle if available; otherwise it uses the repository's `gradlew.bat` and does not try Docker. Java 21 is still required. The service listens on port 8888; check it at `http://localhost:8888/actuator/health`.

**Docker option**:

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d config-service
docker compose logs -f config-service
```

If it exits during startup, capture the output with `docker compose logs --no-color config-service` (Docker) or rerun the standalone command and share the first `Caused by:` section. Never share the value of `CONFIG_SERVER_PASSWORD` or any other secrets.
