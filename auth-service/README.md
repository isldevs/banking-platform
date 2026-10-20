# auth-service

Independent Java 21 / Spring Boot service on port `8081` with its own `auth` database.

## Configuration

The service uses the mandatory Spring Cloud Config import and loads shared settings from `config-repo/auth-service.yml` plus common defaults from `config-repo/application.yml`. Set `CONFIG_SERVER_USERNAME`, `CONFIG_SERVER_PASSWORD`, and `CONFIG_REPO_LABEL` to match the Config Server. Database credentials, JWT key paths, JWT issuer override, and demo account passwords are injected from the local environment, never committed to the config repository.

For the full local setup, run `bash infrastructure/scripts/init-local.sh`, then from `infrastructure/` run `docker compose up --build -d`. Standalone `gradle bootRun` also requires a running Config Server, database connectivity, and the same environment variables used by Compose.
