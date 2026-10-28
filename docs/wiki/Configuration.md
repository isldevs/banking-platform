# Configuration

## Source of configuration

All application configuration is bundled inside Config Service:

```text
config-service/src/main/resources/config/
├── dev/
├── qa/
├── stage/
└── prod/
```

Each environment contains shared `application.yml` plus service-specific files. There is no separate runtime `config-repo` checkout.

Application environment profiles are exactly:

- `dev`
- `qa`
- `stage`
- `prod`

Docker Compose uses `dev`; Docker is a deployment mechanism, not a Spring profile.

## Config Client bootstrap

Application services import Config Server with:

```yaml
spring:
  config:
    import: "configserver:${CONFIG_SERVICE_URL:http://localhost:8888}"
  cloud:
    config:
      fail-fast: true
```

Config Service itself does not depend on Config Server credentials or another configuration repository. It starts independently and serves the other services' configuration.

## Runtime values

The development configuration references runtime environment values for secrets and deployment-specific endpoints. Typical values include:

```text
DATASOURCE_URL
DATASOURCE_USERNAME
DATASOURCE_PASSWORD
SECURITY_JWT_ISSUER
SECURITY_JWT_AUDIENCE
SECURITY_JWT_KEYSTORE_PATH
SECURITY_JWT_KEYSTORE_PASSWORD
SECURITY_JWT_KEYSTORE_ALIAS
SECURITY_JWT_PUBLIC_KEY_PATH
INTERNAL_SERVICE_KEY
OAUTH2_DEMO_CLIENT_SECRET
CUSTOMER_SERVICE_URL
ACCOUNT_SERVICE_URL
AUTH_SERVICE_URL
TRANSFER_SERVICE_URL
NOTIFICATION_SERVICE_URL
AUDIT_SERVICE_URL
SPRING_KAFKA_BOOTSTRAP_SERVERS
REDIS_URL
GATEWAY_RATE_LIMIT_ENABLED
GATEWAY_RATE_LIMIT_FAIL_OPEN
```

Exact variables are service-specific; use the corresponding environment config file as the source of truth.

## Local `.env`

Create the ignored local environment from the committed example:

```text
infrastructure/.env.example -> infrastructure/.env
```

Edit the local `.env` with approved development values. Never commit it or real credentials.

For the Docker stack, Compose reads these values directly and passes service-specific environment variables into containers.

## Docker networking

Inside Compose, services use container DNS names:

```text
config-service:8888
auth-service:8081
customer-service:8082
account-service:8083
transfer-service:8084
notification-service:8085
audit-service:8086
kafka:9092
redis:6379
```

Do not use `localhost` for service-to-service URLs inside containers.

## Gateway rate-limit configuration

The Gateway supports a default fixed-window rule plus endpoint-specific rules. The current development defaults are:

```yaml
gateway:
  rate-limit:
    enabled: true
    fail-open: true
    default-limit: 60
    default-window: 1m
```

Specific rules currently include 5/min for legacy login, 10/min for legacy refresh, 10/min for account-opening POSTs, and 10/min for transfer POSTs. Production-like environments should set fail-open explicitly according to the operational policy.

## REST JSON naming

Shared configuration uses `SNAKE_CASE` at the HTTP boundary. Java properties and record components remain camelCase unless an endpoint has a documented exception.

## Promotion

```text
dev -> qa -> stage -> prod
```

Production secrets must come from the organization's approved secret-management system rather than copied local `.env` files.

Related pages: [[Getting-Started]], [[Security-and-Operations]].
