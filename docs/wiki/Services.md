# Service Catalogue

This page summarizes the bounded contexts, local ports, principal data ownership, and main API entry points.

## Gateway Service — port 8080

**Role:** public edge entry point and JWT resource-server validation.

- Validates JWT signature, issuer, audience, and expiration using the configured public key.
- Converts JWT `permissions` claims into Spring Security authorities.
- Health and info endpoints are publicly accessible; other requests require authentication.
- Keep routing and edge concerns here; business rules belong in the business services.

## Auth Service — port 8081

**Role:** identity management and Spring Authorization Server / OpenID Connect Provider.

- Stores users and credentials in the auth database.
- Persists OAuth2 client registrations, authorizations, tokens, and consent through JDBC repositories.
- Exposes discovery, authorization, token, revocation, and JWKS endpoints.
- Issues RSA-signed JWT access tokens with `sub`, `username`, `roles`, and `permissions`.
- Retains deprecated `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout` for compatibility only.

Primary entry points: `/.well-known/openid-configuration`, `/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/jwks`, `GET /api/auth/me`, `POST /api/users`.

## Customer Service — port 8082

**Role:** customer profile and KYC lifecycle.

| Endpoint | Permission / security |
|---|---|
| `POST /api/customers` | `CUSTOMER_CREATE` |
| `GET /api/customers/{id}` | `CUSTOMER_READ` |
| `POST /api/customers/{id}/kyc/approve` | `KYC_APPROVE` |
| `GET /internal/customers/{id}/status` | `X-Service-Key`; internal only |

The customer database is owned by Customer Service. Other services should call its API rather than query its tables.

## Account Service — port 8083

**Role:** account state, balances, funding and ledger operations.

| Endpoint | Permission / security |
|---|---|
| `POST /api/accounts` | `ACCOUNT_OPEN` |
| `GET /api/accounts/{id}` | `ACCOUNT_READ` |
| `GET /api/accounts` | `ACCOUNT_READ` |
| `POST /api/accounts/{id}/freeze` | `ACCOUNT_FREEZE` |
| `POST /api/accounts/{id}/fund` | `ACCOUNT_FUND` |
| `GET /api/accounts/ledger/{operationId}` | `ACCOUNT_FUND` |
| `POST /internal/accounts/transfer` | `X-Service-Key`; internal only |
| `GET /internal/accounts/operations/{id}` | `X-Service-Key`; internal only |

The account database and ledger are owned by Account Service.

## Transfer Service — port 8084

**Role:** transfer lifecycle, idempotency and recovery.

| Endpoint | Permission / security |
|---|---|
| `POST /api/transfers` | `TRANSFER_CREATE`; optionally accepts `Idempotency-Key` |
| `GET /api/transfers/{id}` | `TRANSFER_READ` |

The authenticated user is obtained from the JWT subject. Clients should not use request-body identity fields to impersonate another user. Transfer Service owns workflow state; Account Service owns the financial account ledger.

## Notification Service — port 8085

**Role:** user-facing in-app notifications.

- `GET /api/notifications` returns recent notifications for the authenticated user.
- Requires `NOTIFICATION_READ` or `TRANSFER_READ`.
- The current controller limits the result to the latest 100 notifications.
- Notification records belong to the notification database.

## Audit Service — port 8086

**Role:** transfer audit history.

- `GET /api/audit/transfers/{id}` requires `AUDIT_READ`.
- The audit database belongs to Audit Service.
- Audit records should be treated as evidence for traceability, not as a substitute for reconciliation.

## Config Service — port 8888

**Role:** centralized Spring Cloud Config Server using the native classpath backend.

- Configuration is bundled under `config-service/src/main/resources/config/{dev,qa,stage,prod}/`.
- Each environment folder contains `application.yml` and per-service configuration files.
- The server requires Config Client credentials.
- The internal Config Server backend profile is `native`; application environment profiles remain `dev`, `qa`, `stage`, and `prod`.

## Cross-service standards

- Carry `Authorization: Bearer <access-token>` on protected APIs.
- Use `X-Correlation-Id` for request traceability where supported.
- Use `Idempotency-Key` for safe client retries of transfer commands.
- Reserve `X-Service-Key` for internal service-to-service calls only.
- Do not share persistence entities between services.

For request payloads and response shapes, use [[API-Reference]].
