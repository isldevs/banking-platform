# Centralized application configuration

Spring Cloud Config Server reads this directory from the Git repository. Files follow Spring Cloud Config naming conventions:

- `application.yml`: common non-secret defaults and management endpoints.
- `auth-service.yml`: auth-service runtime defaults.
- `gateway-service.yml`: gateway routing and JWT validation defaults.

The Config Server exposes this repository on port 8888 and requires HTTP Basic authentication. Clients import configuration with a mandatory `configserver:` location and retry while the server starts.

**Never commit secrets here.** Database passwords, JWT private keys, JWT key file paths when deployment-specific, demo passwords, and internal service credentials are supplied by the runtime environment. Only `auth-service` and `gateway-service` currently consume Config Server settings; other services remain on their existing configuration until migrated.
