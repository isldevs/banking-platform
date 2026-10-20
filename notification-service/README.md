# notification-service

Independent Java 21 / Spring Boot Kafka consumer on port `8085`, owning the `notification` database.

## Configuration

Uses a mandatory Spring Cloud Config Client import. Shared settings, Kafka and service URLs are in `config-repo/application.yml` and `config-repo/application-docker.yml`; service settings are in `config-repo/notification-service.yml` and `config-repo/notification-service-docker.yml`. Database password, JWT verification key path, and internal service key remain runtime-injected.

## Run

From the repository root: `bash infrastructure/scripts/init-local.sh`, then `cd infrastructure && docker compose up --build -d`. For standalone `gradle bootRun`, start PostgreSQL, Kafka, and Config Server; source `infrastructure/.env`; set `CONFIG_SERVICE_URL=http://localhost:8888`, `SPRING_DATASOURCE_PASSWORD`, and `SECURITY_JWT_PUBLICKEYPATH`.
