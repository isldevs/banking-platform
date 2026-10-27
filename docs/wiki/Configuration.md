# Configuration

## Configuration ownership

All application configuration is bundled inside Config Service. The repository does not use a separate runtime
`config-repo` checkout, and Docker is not a Spring environment profile.

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

Activate one through `SPRING_PROFILES_ACTIVE`. Docker Compose uses `dev` for the demo, but Docker is only the deployment
mechanism. The Config Server itself uses Spring Cloud Config's internal `native` backend profile to load classpath
files; that does not introduce a fifth application environment.

## Config Server

Config Service listens on port 8888 and uses the native classpath search location:

```text
classpath:/config/{profile}
```

For example, a Config Client requesting `auth-service` under `dev` receives the shared `config/dev/application.yml` and
service-specific `config/dev/auth-service.yml` configuration.

Clients import Config Server configuration through:

```yaml
spring:
  config:
    import: "configserver:${CONFIG_SERVICE_URL:http://localhost:8888}"
  cloud:
    config:
      fail-fast: true
```

Config Server does not require client credentials in this local/demo configuration. No `CONFIG_SERVER_USERNAME` or
`CONFIG_SERVER_PASSWORD` variables are needed. Keep Config Server limited to a trusted network and never put secret
values in committed YAML.

## Runtime configuration contract

The development Config Server configuration uses a consistent runtime contract across services. Local or container
orchestration supplies the environment values; Config Server references them rather than storing secret values itself.

For database-backed services, use:

```text
DATASOURCE_URL
DATASOURCE_USERNAME
DATASOURCE_PASSWORD
```

For JWT configuration, use:

```text
SECURITY_JWT_ISSUER
SECURITY_JWT_AUDIENCE
SECURITY_JWT_KEYSTORE_PATH
SECURITY_JWT_KEYSTORE_PASSWORD
SECURITY_JWT_KEYSTORE_ALIAS
SECURITY_JWT_PUBLIC_KEY_PATH
```

Additional runtime values include service URLs, Kafka bootstrap servers, internal service credentials, OAuth2 demo
client secrets, and demo credentials as required by the individual service configuration.

The important rule is that **Config Server owns the configuration structure, while the runtime environment owns secret
values**.

## Local `.env` workflow

For local development, `infrastructure/.env` is the recommended source for local secrets and environment-specific
values. It is ignored by Git and should be created from `infrastructure/.env.example`.

```text
infrastructure/.env.example
        |
        v
infrastructure/.env
        |
        v
scripts/local-env.ps1 / scripts/local-env.cmd
        |
        v
service process -> Config Server -> config/{dev}/<service>.yml
```

The local environment scripts also generate `.jwt/jwt-signing.p12` and `.jwt/jwt-public.pem` when required. The
generated `.jwt/` directory is ignored by Git.

PowerShell:

```powershell
. .\scripts\local-env.ps1 auth
. .\gradlew.bat :auth-service:bootRun
```

CMD:

```cmd
call scripts\local-env.cmd auth
gradlew.bat :auth-service:bootRun
```

The PowerShell script is intentionally dot-sourced and the CMD script intentionally uses `call`, because environment
changes must remain in the caller's shell so Gradle and the Spring Boot process inherit them.

## Docker Compose configuration

Docker Compose also uses the `dev` Spring profile, but supplies container-specific runtime values. For example:

```text
CONFIG_SERVICE_URL=http://config-service:8888
AUTH_SERVICE_URL=http://auth-service:8081
CUSTOMER_SERVICE_URL=http://customer-service:8082
ACCOUNT_SERVICE_URL=http://account-service:8083
TRANSFER_SERVICE_URL=http://transfer-service:8084
NOTIFICATION_SERVICE_URL=http://notification-service:8085
AUDIT_SERVICE_URL=http://audit-service:8086
SPRING_KAFKA_BOOTSTRAPSERVERS=kafka:9092
```

The JWT issuer used inside the Compose network must be reachable by services using container DNS. Do not use `localhost`
for an inter-container issuer; inside a container, `localhost` refers to that same container.

## Global REST JSON naming

Shared environment configuration sets:

```yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE
```

Java properties and record components should remain camelCase; JSON at the REST boundary should use snake_case. Avoid
redundant per-DTO naming annotations unless a specific endpoint documents an exception.

## Runtime settings and secrets

Common runtime configuration includes:

- Config Server URL (no client credentials in this demo)
- Database URLs, usernames, and passwords
- JWT issuer, audience, signing-keystore path/password/alias, and public-key path
- Internal service-to-service key
- OAuth2 confidential demo client secret
- Seed/demo user credentials
- Service URLs and Kafka bootstrap servers

Locally generated values belong in ignored `infrastructure/.env`; local signing material belongs in ignored `.jwt/` for
host-based development and `infrastructure/secrets/` for the Docker initialization flow. Commit only placeholders and
safe examples, never actual credentials, signing keys, or passwords.

## Environment promotion

Promote reviewed configuration through the environment path:

```text
dev -> qa -> stage -> prod
```

Environment-specific values should differ where required, but the service configuration structure should remain
consistent. Production secrets must come from the organization's approved secret-management system rather than copied
local `.env` files.

Related page: [[Getting-Started]].
