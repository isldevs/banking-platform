# Configuration

## Configuration ownership

All environment configuration is bundled inside Config Service. The repository does not use a separate runtime `config-repo` checkout, and Docker is not a Spring environment profile.

Folder layout:

```text
config-service/src/main/resources/config/
├── dev/
│   ├── application.yml
│   ├── auth-service.yml
│   ├── customer-service.yml
│   ├── account-service.yml
│   ├── transfer-service.yml
│   ├── notification-service.yml
│   ├── audit-service.yml
│   └── gateway-service.yml
├── qa/
├── stage/
└── prod/
```

Each environment folder contains shared `application.yml` plus files for individual services.

## Application profiles

The only application environment profiles are:

- `dev`
- `qa`
- `stage`
- `prod`

Activate one through `SPRING_PROFILES_ACTIVE`. Docker Compose uses `dev` for the demo, but Docker is only the deployment mechanism. The Config Server itself uses Spring Cloud Config's internal `native` backend profile to load classpath files; that does not introduce a fifth application environment.

## Config Server

Config Service listens on port 8888 and uses the native classpath search location:

```text
classpath:/config/{profile}
```

For example, a Config Client requesting `auth-service` under `dev` receives the shared `config/dev/application.yml` and service-specific `config/dev/auth-service.yml` configuration.

Clients import Config Server configuration through:

```yaml
spring:
  config:
    import: "configserver:${CONFIG_SERVICE_URL:http://localhost:8888}"
  cloud:
    config:
      username: ${CONFIG_SERVER_USERNAME}
      password: ${CONFIG_SERVER_PASSWORD}
      fail-fast: true
```

Config Server client credentials are runtime environment variables. Do not place secret values in committed YAML.

## Global REST JSON naming

Shared environment configuration sets:

```yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
```

Java properties and record components should remain camelCase; JSON at the REST boundary should use snake_case. Avoid redundant per-DTO naming annotations unless a specific endpoint documents an exception.

## Runtime settings and secrets

Common runtime configuration includes:

- Config Server URL and client username/password
- Database URLs, usernames, and passwords
- JWT issuer and signing-keystore path, password, and alias
- JWT public-key path for resource services
- Internal service-to-service key
- OAuth2 confidential demo client secret
- Seed/demo user credentials
- Service URLs and Kafka bootstrap servers

Locally generated values belong in ignored `infrastructure/.env`; signing material belongs in ignored `infrastructure/secrets/`. Commit only placeholders and safe defaults, never actual credentials, signing keys, or passwords.

## Environment promotion

Promote reviewed configuration through the environment path:

```text
dev -> qa -> stage -> prod
```

Environment-specific values should differ where required, but the service configuration structure should remain consistent. Production secrets must come from the organization's approved secret-management system rather than copied local `.env` files.

Related page: [[Getting-Started]].
