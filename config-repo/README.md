# Centralized application configuration

Spring Cloud Config Server reads this directory from the configured Git repository. Files follow Spring Cloud Config naming:

- `application.yml`: shared management and JWT validation defaults plus local service URLs.
- `application-docker.yml`: service DNS names for Docker Compose.
- `auth-service.yml`, `customer-service.yml`, `account-service.yml`, `transfer-service.yml`, `notification-service.yml`, `audit-service.yml`, and `gateway-service.yml`: service-specific ports and safe defaults.
- `auth-service-docker.yml`, `customer-service-docker.yml`, `account-service-docker.yml`, `transfer-service-docker.yml`, `notification-service-docker.yml`, and `audit-service-docker.yml`: Docker-specific datasource addresses; Kafka clients also override the broker address here.

All seven application services use mandatory Spring Cloud Config imports, authenticated with HTTP Basic. Docker Compose activates the `docker` profile. The Config Server uses the Git `config-repo/` search path and the selected Git label, defaulting to `main`.

**Never commit secrets or signing private-key paths here.** Database passwords, the internal service key, demo login passwords, and `security.jwt.private-key-path` are supplied through runtime environment variables. The RSA private key itself must remain in a local ignored file or an approved secret/key-management service. JWT issuer/audience, the public-key path, and routing defaults are configuration, but the issuer must match the running demo.

After changing configuration, restart the affected containers. Automatic refresh is intentionally not configured in this demo.
