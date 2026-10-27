# account-service

Independent Java 21 / Spring Boot service on port `8083`, owning the `account` database and the atomic ledger boundary.

## Configuration

Uses a mandatory Spring Cloud Config Client import. Common defaults are in `config-repo/application.yml` and
`config-repo/application-docker.yml`; service settings are in `config-repo/account-service.yml` and
`config-repo/account-service-docker.yml`. Database passwords, JWT verification key path, and internal service key stay
in the runtime environment. Demo funding is enabled by Compose only.

## Run

From the repository root: `bash infrastructure/scripts/init-local.sh`, then
`cd infrastructure && docker compose up --build -d`. For standalone `gradle bootRun`, start PostgreSQL and Config
Server, source `infrastructure/.env`, and set `CONFIG_SERVICE_URL=http://localhost:8888`, `DATASOURCE_PASSWORD`, and
`SECURITY_JWT_PUBLIC_KEY_PATH`.
