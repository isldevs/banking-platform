# Config Service

Spring Cloud Config Server for the platform. This is a stateless configuration server and has no application database.

## Responsibilities

- Serve configuration from the Git-backed `config-repo/` directory.
- Require HTTP Basic authentication for configuration endpoints.
- Expose health, info, metrics, and Prometheus actuator endpoints.
- Start independently of auth-service so clients can bootstrap before auth is available.

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

The demo's Git repository is public by default, so Git credentials can be omitted. Keep the Config Server reachable only from trusted networks and the local developer machine. All Config Client credentials and application secrets must remain outside Git.

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

Use HTTP Basic credentials from `CONFIG_SERVER_USERNAME` and `CONFIG_SERVER_PASSWORD` to inspect these endpoints. Each client has a mandatory `configserver:` import and bounded startup retry.

## Run locally

Run `bash infrastructure/scripts/init-local.sh`, then from `infrastructure/` run `docker compose up --build -d`. For a standalone run, set the required environment variables and execute `gradle bootRun` from this service directory.
