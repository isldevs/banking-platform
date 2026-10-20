# transfer-service

Independent Java 21 / Spring Boot service on port `8084`, owning transfer workflow state and its Kafka outbox.

## Configuration

Uses a mandatory Spring Cloud Config Client import. Shared settings, service URLs, and Kafka defaults are in `config-repo/application.yml` and `config-repo/application-docker.yml`; service settings are in `config-repo/transfer-service.yml` and `config-repo/transfer-service-docker.yml`. Database password, JWT verification key path, and internal service key are runtime-injected, never stored in Git.

## Run

From the repository root: `bash infrastructure/scripts/init-local.sh`, then `cd infrastructure && docker compose up --build -d`. For standalone `gradle bootRun`, start PostgreSQL, Kafka, and Config Server; source `infrastructure/.env`; set `CONFIG_SERVICE_URL=http://localhost:8888`, `SPRING_DATASOURCE_PASSWORD`, and `SECURITY_JWT_PUBLICKEYPATH`.
