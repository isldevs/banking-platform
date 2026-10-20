# Config Service

Spring Cloud Config Server for the platform. This service is stateless and has no application database.

## Responsibilities

- Serve shared and service-specific configuration from the Git-backed `config-repo/` directory.
- Require HTTP Basic authentication for configuration endpoints.
- Expose health, info, metrics, and Prometheus actuator endpoints.
- Start independently of auth-service so every client can bootstrap before authentication services start.

## Required environment

```text
SERVER_PORT=8888
CONFIG_REPO_URI=<Git repository URL>
CONFIG_REPO_LABEL=main
CONFIG_REPO_SEARCH_PATHS=config-repo
CONFIG_REPO_USERNAME=<optional Git username>
CONFIG_REPO_PASSWORD=<optional Git password or token>
CONFIG_SERVER_USERNAME=<internal Config Client username>
CONFIG_SERVER_PASSWORD=<internal Config Client password>
```

The public demo repository is the default, so Git credentials are optional. Keep the Config Server private to trusted networks and the local developer machine. Never commit Config Client credentials or application secrets.

## Config Client endpoints

```text
GET /auth-service/docker/main
GET /customer-service/docker/main
GET /account-service/docker/main
GET /transfer-service/docker/main
GET /notification-service/docker/main
GET /audit-service/docker/main
GET /gateway-service/docker/main
```

Send HTTP Basic credentials from `CONFIG_SERVER_USERNAME` and `CONFIG_SERVER_PASSWORD` to inspect these endpoints. Each application service uses a mandatory `configserver:` import with bounded startup retry.

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
# Run once from the repository root in Git Bash or WSL to generate infrastructure/.env
bash infrastructure/scripts/init-local.sh

# Return to PowerShell, from the repository root:
.\infrastructure\scripts\run-config-service.ps1
```

**Linux / macOS / Git Bash / WSL**:

```bash
bash infrastructure/scripts/run-config-service.sh
```

Both scripts load the local Config Server credentials from `infrastructure/.env` without printing them. On Windows, the PowerShell script uses installed Gradle if available; otherwise it uses the repository's `gradlew.bat` and does not try Docker. The wrapper downloads Gradle 8.13 into your user profile on first run if the company network permits it. Java 21 is still required. The Bash script requires Gradle locally. The service listens on port 8888; check it at `http://localhost:8888/actuator/health`.

**Docker option** (no local Gradle installation needed; Docker Desktop/Engine is required):

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d config-service
docker compose logs -f config-service
```

If it still exits during startup, capture the output with `docker compose logs --no-color config-service` (Docker) or rerun the standalone command and share the first `Caused by:` section. Never share the value of `CONFIG_SERVER_PASSWORD` or any other secrets.
