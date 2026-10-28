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

Each environment contains **service-specific YAML** for the seven Config clients. No shared per-environment `application.yml` is present in the checked-in config tree; Config Service uses its own bootstrap `application.yml`. There is no runtime `config-repo` checkout.

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

The checked-in service YAML references environment values for secrets and endpoints. Example variable names are below; always confirm the precise property in the service profile YAML:

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
SPRING_KAFKA_BOOTSTRAPSERVERS
REDIS_URL
GATEWAY_RATE_LIMIT_ENABLED
GATEWAY_RATE_LIMIT_FAIL_OPEN
```

Exact variables are service-specific; use the corresponding environment config file as the source of truth.

## Local `.env`

Create the ignored local environment from the committed example; on Windows the provided `scripts/setup-local.ps1` initializes values and JWT material. No equivalent Linux/macOS setup script is present:

```text
infrastructure/.env.example -> infrastructure/.env
```

Populate every placeholder with local-only secrets. Do not commit `.env`, JWT signing keys or passwords. Note: the PowerShell helper only prepares files, it does not start Docker Compose.

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
postgres:5432
```

The current Compose exposes Gateway, Auth and Config Server to the host; Customer, Account, Transfer, Notification and Audit remain on the internal Compose network.

Do not use `localhost` for service-to-service URLs inside containers.

## Gateway rate-limit configuration

The Gateway supports a default fixed-window rule plus endpoint-specific rules. The current development defaults are:

```yaml
gateway:
  rate-limit:
    enabled: ${GATEWAY_RATE_LIMIT_ENABLED:false}
    fail-open: ${GATEWAY_RATE_LIMIT_FAIL_OPEN:true}
    default-limit: 60
    default-window: 1m
```

**Limits are disabled until explicitly enabled.** When `GATEWAY_RATE_LIMIT_ENABLED=true`, rules include 5/min for legacy login, 10/min for legacy refresh, 10/min for account POST prefixes, 10/min for transfer POST prefixes, and 60/min as the default. QA, stage and prod default to fail-closed on Redis failure, while dev defaults to fail-open. Evaluate these policies and test them before deployment.

## REST JSON naming

Jackson uses `SNAKE_CASE` for DTO/record JSON at the HTTP boundary; Java member names remain camelCase. **Current exception:** Transfer Service constructs Map response keys in camelCase (`sourceAccountId`, `destinationAccountId`). Treat that as a contract inconsistency to resolve in code.

## Promotion

```text
dev -> qa -> stage -> prod
```

Promotion is a target release process, not proof that production infrastructure exists. Secrets for shared or production-grade environments must come from an approved secret manager rather than copied local `.env` files. `X-Service-Key` and seeded system clearing accounts are demo mechanisms, not approved global banking controls.

Related pages: [Getting Started](Getting-Started.md), [Security and Operations](Security-and-Operations.md).
