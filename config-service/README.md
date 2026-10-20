# Config Service

Spring Cloud Config Server for the platform. This service is stateless and has no application database.

## Responsibilities

- Serve shared and service-specific configuration bundled inside the Config Server.
- Use Spring Cloud Config's native classpath backend; there is no separate `config-repo` service or Git clone at runtime.
- Keep configuration physically separated by environment: `dev`, `qa`, `stage`, and `prod`.
- Resolve the requested Config Client profile directly to the matching environment directory.
- Serve configuration without HTTP Basic credentials in this local/demo setup.
- Expose health, info, metrics, and Prometheus actuator endpoints.
- Start independently of auth-service so every client can bootstrap before authentication services start.

## Configuration layout

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

There is intentionally **no `base/` directory** and no `*-docker.yml` configuration. Docker is only a deployment mechanism; it does not define a Spring environment.

The native backend uses `classpath:/config/{profile}`. A request such as `/auth-service/dev` loads `config/dev/application.yml` and `config/dev/auth-service.yml`. `/auth-service/qa`, `/auth-service/stage`, and `/auth-service/prod` resolve the corresponding directories.

## Environment profiles

The application services support exactly these environment profiles:

```text
dev
qa
stage
prod
```

Docker Compose sets `SPRING_PROFILES_ACTIVE=dev`; it does not introduce a Docker profile.

## Authentication and network access

The demo Config Server does not require a username or password. Config Clients connect to `http://localhost:8888` in local runs or `http://config-service:8888` inside Compose without setting `spring.cloud.config.username` or `spring.cloud.config.password`.

This is appropriate only while the Config Server serves non-secret configuration on a private/trusted network. Do not put secrets in the bundled YAML files. The Compose host port is bound to `127.0.0.1:8888`; keep it private in other deployments as well. A shared or production environment must add an approved network access policy or authentication mechanism before the service is exposed beyond a trusted network.

## Config Client endpoints

```text
GET /auth-service/dev
GET /customer-service/dev
GET /account-service/dev
GET /transfer-service/dev
GET /notification-service/dev
GET /audit-service/dev
GET /gateway-service/dev
```

For QA, stage, or production, replace `dev` with `qa`, `stage`, or `prod`.

## Run with Docker Compose

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
```

## Run standalone on your machine

**Windows PowerShell** (uses installed Gradle or the repository's Gradle Wrapper; Docker is not used):

```powershell
.\infrastructure\scripts\run-config-service.ps1
```

**Linux / macOS / Git Bash / WSL**:

```bash
bash infrastructure/scripts/run-config-service.sh
```

Config Service no longer requires `infrastructure/.env` or any Config Server credential variables. The launch scripts use port 8888 unless `SERVER_PORT` is already set. Java 21 is required. Check health at `http://localhost:8888/actuator/health`.
