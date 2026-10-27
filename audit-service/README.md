# audit-service

Independent Java 21 / Spring Boot Kafka consumer on port `8086`, owning the `audit` database.

## Configuration

Uses a mandatory Spring Cloud Config Client import. Shared settings, Kafka and service URLs are in
`config-repo/application.yml` and `config-repo/application-docker.yml`; service settings are in
`config-repo/audit-service.yml` and `config-repo/audit-service-docker.yml`. Database password, JWT verification key
path, and internal service key remain runtime-injected.

## Run

From the repository root: `bash infrastructure/scripts/init-local.sh`, then
`cd infrastructure && docker compose up --build -d`. For standalone `gradle bootRun`, start PostgreSQL, Kafka, and
Config Server; source `infrastructure/.env`; set `CONFIG_SERVICE_URL=http://localhost:8888`, `DATASOURCE_PASSWORD`, and
`SECURITY_JWT_PUBLIC_KEY_PATH`.
