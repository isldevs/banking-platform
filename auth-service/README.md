# auth-service

Independent Java 21 / Spring Boot service on port `8081` with its own `auth` database.

## Configuration

The service uses the mandatory Spring Cloud Config import and loads shared settings from `config-repo/auth-service.yml` plus common defaults from `config-repo/application.yml`. Set `CONFIG_SERVER_USERNAME`, `CONFIG_SERVER_PASSWORD`, and `CONFIG_REPO_LABEL` to match the Config Server. Database credentials, JWT key paths, JWT issuer override, the internal service key, and demo account passwords are injected from the runtime environment, never committed to the config repository.

## Run

For the full container setup:

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
```

For a standalone `gradle bootRun`, start PostgreSQL and Config Server first, source `infrastructure/.env`, and set the datasource variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`) plus `SECURITY_JWT_PUBLICKEYPATH` and `SECURITY_JWT_PRIVATEKEYPATH` to the actual paths under `infrastructure/secrets/`. Use `http://localhost:5439/auth` for the database URL when using the provided Compose infrastructure.
