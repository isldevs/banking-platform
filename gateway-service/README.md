# gateway-service

Independent Java 21 / Spring Boot stateless API proxy on port `8080`.

## Configuration

The service uses the mandatory Spring Cloud Config import and loads local routing defaults from `config-repo/gateway-service.yml`, Docker-network routing overrides from `config-repo/gateway-service-docker.yml` when the `docker` profile is active, plus common settings from `config-repo/application.yml`. Set `CONFIG_SERVER_USERNAME`, `CONFIG_SERVER_PASSWORD`, and `CONFIG_REPO_LABEL` to match Config Server. The JWT public-key path and issuer override remain deployment-supplied environment values.

## Run

For the full container setup:

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
```

For a standalone `gradle bootRun`, start Config Server first and source `infrastructure/.env`. Set `CONFIG_SERVICE_URL=http://localhost:8888`, `SECURITY_JWT_PUBLICKEYPATH` to the generated public key under `infrastructure/secrets/`, and the issuer and internal-key environment variables. The default profile uses localhost routing; the Docker Compose deployment activates the `docker` profile to route to container DNS names.
