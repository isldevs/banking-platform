# Config Service

Spring Cloud Config Server for the platform. This service is stateless and has no application database.

## Responsibilities

- Serve shared and service-specific configuration bundled inside the Config Server.
- Use Spring Cloud Config's native classpath backend; there is no separate `config-repo` service or Git clone at runtime.
- Keep configuration physically separated by environment: `dev`, `qa`, `stage`, and `prod`.
- Resolve the requested Config Client profile directly to the matching environment directory.
- Require HTTP Basic authentication for configuration endpoints.
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

The repository's environment branches can therefore promote the exact environment directory independently:

```text
dev → qa → stage → prod
```

## Environment profiles

The application services support exactly these environment profiles:

```text
dev
qa
stage
prod
```

Each service defaults to `dev` when `SPRING_PROFILES_ACTIVE` is not supplied. Deployments set the required environment explicitly. Docker Compose sets `SPRING_PROFILES_ACTIVE=dev`; it does not introduce a Docker profile.

## Required environment

```text
SERVER_PORT=8888
CONFIG_SERVER_USERNAME=<internal Config Client username>
CONFIG_SERVER_PASSWORD=<internal Config Client password>
```

No Git repository credentials are required. Never commit Config Client credentials or application secrets.

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

## Run

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
```

### Run standalone on your machine

The service imports `infrastructure/.env` as a Spring Boot properties source using `spring.config.import` in `application.yml`. The default path is `../infrastructure/.env` when the working directory is `config-service`. Set `INFRASTRUCTURE_ENV_FILE` to an absolute path if you use a different working directory. Docker Compose continues to pass `CONFIG_SERVER_USERNAME` and `CONFIG_SERVER_PASSWORD` as container environment variables.

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
