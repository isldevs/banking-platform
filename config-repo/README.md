# Centralized application configuration

Spring Cloud Config Server reads this directory from the configured Git repository. Files use the standard Spring Cloud Config naming convention: shared `application.yml`, optional `application-docker.yml`, and service-specific `<service-name>.yml` / `<service-name>-docker.yml` files.

- `application.yml`: shared actuator, JWT validation defaults, local service URLs, and local Kafka settings.
- `application-docker.yml`: container DNS names and Kafka bootstrap address used by the demo Docker Compose profile.
- `auth-service.yml`, `customer-service.yml`, `account-service.yml`, `transfer-service.yml`, `notification-service.yml`, `audit-service.yml`, and `gateway-service.yml`: service ports and safe per-service defaults.
- Matching `*-docker.yml` files: container-specific database addresses and usernames.

All seven application services use mandatory Spring Cloud Config imports with HTTP Basic credentials. They set the `docker` profile in Compose. The Config Server uses the Git `config-repo/` search path and the selected Git label, defaulting to `main`.

**Never commit secrets here.** Database passwords, the internal service key, demo login passwords, and deployment-specific JWT key paths are injected into client containers through environment variables. The JWT issuer and audience are non-secret settings, but the issuer must match what is configured for the running demo.

After changing config, restart the affected service containers. Automatic refresh is not configured in this demo.
