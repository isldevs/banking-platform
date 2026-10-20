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

For a standalone run, set the required environment variables and execute `gradle bootRun` from this service directory.
