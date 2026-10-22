# customer-service

Independent Java 21 / Spring Boot service on port `8082`, owning the `customer` database.

## Configuration

Uses a mandatory Spring Cloud Config Client import. Common defaults are in `config-repo/application.yml` and `config-repo/application-docker.yml`; service settings are in `config-repo/customer-service.yml` and `config-repo/customer-service-docker.yml`. Docker Compose supplies Config Server credentials, the database password, JWT verification key path and internal service key from `infrastructure/.env`.

## Run

From the repository root: `bash infrastructure/scripts/init-local.sh`, then `cd infrastructure && docker compose up --build -d`. For standalone `gradle bootRun`, start PostgreSQL and Config Server, source `infrastructure/.env`, and set `CONFIG_SERVICE_URL=http://localhost:8888` plus `SPRING_DATASOURCE_PASSWORD` and `SECURITY_JWT_PUBLIC_KEY_PATH`.
