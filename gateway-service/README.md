# gateway-service

Independent Java 21 / Spring Boot stateless API proxy on port `8080`.

## Configuration

The service uses the mandatory Spring Cloud Config import and loads routing and safe defaults from `config-repo/gateway-service.yml`, plus common settings from `config-repo/application.yml`. Set `CONFIG_SERVER_USERNAME`, `CONFIG_SERVER_PASSWORD`, and `CONFIG_REPO_LABEL` to match Config Server. JWT key paths, issuer overrides, and the internal service key remain deployment-supplied environment values.

For the full local setup, run `bash infrastructure/scripts/init-local.sh`, then from `infrastructure/` run `docker compose up --build -d`. Standalone `gradle bootRun` requires a reachable Config Server and the same environment settings used by Compose.
