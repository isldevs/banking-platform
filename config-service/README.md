# Config Service

Spring Cloud Config Server for the platform.

## Responsibilities

- Serve centralized application configuration from a Git repository.
- Keep configuration versioned and reviewable in Git.
- Require HTTP Basic authentication for Config Server endpoints.
- Expose health, info, metrics, and Prometheus actuator endpoints.

This service does **not** own a configuration database and does **not** issue OAuth2 tokens. It must remain independent from `auth-service` so configuration can be loaded before the authorization server starts.

## Required environment

```text
SERVER_PORT=8888
CONFIG_REPO_URI=<Git repository URL>
CONFIG_REPO_LABEL=main
CONFIG_REPO_USERNAME=<optional Git username>
CONFIG_REPO_PASSWORD=<optional Git password or token>
CONFIG_SERVER_USERNAME=<internal Config Client username>
CONFIG_SERVER_PASSWORD=<internal Config Client password>
```

For a public configuration repository, `CONFIG_REPO_USERNAME` and `CONFIG_REPO_PASSWORD` can be omitted.

Do not store application secrets in the configuration repository. Keep credentials, private keys, database passwords, and other secrets in the deployment secret store or environment and inject them into the services at runtime.

## Config repository layout

Use the standard Spring Cloud Config naming convention:

```text
application.yml
auth-service.yml
gateway-service.yml
gateway-service-docker.yml
```

A service loads its shared configuration from `application.yml` and its service-specific configuration from `<service-name>.yml`. Environment-specific files override the base configuration. `auth-service` and `gateway-service` are configured as mandatory Config Clients; the other services will be migrated separately.

## Run locally

Set the required environment variables and run:

```bash
gradle bootRun
```

The server listens on `http://localhost:8888` by default.

A Config Client can request configuration with:

```text
GET /<application>/<profile>
GET /<application>/<profile>/<label>
```

For example:

```text
GET /auth-service/default/main
GET /gateway-service/docker/main
```

## Security model

The Config Server uses HTTP Basic authentication for machine-to-machine access. Do not expose it directly to the public internet. OAuth2/JWT authentication is intentionally not used here because `auth-service` will depend on Config Server during startup, which would otherwise create a bootstrap dependency cycle.
