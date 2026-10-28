# config-service

Spring Cloud Config Server on port `8888`. Config Service is stateless and has no application database.

## Responsibilities

- Serve non-secret application configuration from the classpath.
- Keep configuration separated by `dev`, `qa`, `stage`, and `prod`.
- Provide a stable bootstrap configuration source for all Config Clients.
- Expose health and metrics endpoints.

Configuration lives in:

```text
config-service/src/main/resources/config/
├── dev/
├── qa/
├── stage/
└── prod/
```

Each environment contains shared `application.yml` plus service-specific files. There is no separate runtime `config-repo` checkout and no Docker Spring profile.

## Security model

The local/demo Config Server does not require username/password credentials. It must remain on a trusted/private network and must never contain secret values. Production deployments require an approved network access policy and/or authentication mechanism before exposing configuration beyond a trusted network.

## Client endpoint pattern

```text
GET /{service}/{environment}
```

Examples:

- `GET /auth-service/dev`
- `GET /account-service/dev`
- `GET /gateway-service/dev`

## Run

```bash
cd infrastructure
docker compose up --build -d config-service
```

Or run the independent Gradle build directly:

```bash
gradle bootRun
```

Check health at `http://localhost:8888/actuator/health`.

See [Configuration](../docs/wiki/Configuration.md) and [Getting Started](../docs/wiki/Getting-Started.md) for the canonical configuration contract.
