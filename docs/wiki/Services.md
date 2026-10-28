# Service Catalogue

This page is the concise service inventory. Detailed request/response examples belong in [[API-Reference]].

## Gateway — `8080`

Public API entry point.

- JWT resource-server validation at the edge.
- Routing and correlation ID handling.
- Redis-backed API rate limiting.
- No banking business logic.

## Auth — `8081`

Identity and Spring Authorization Server / OIDC Provider.

- OAuth2/OIDC discovery, authorization, token, revocation and JWKS endpoints.
- Authorization Code + PKCE is the recommended client flow.
- Legacy `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` remain for compatibility only.

## Customer — `8082`

Owns the customer database and KYC lifecycle.

| Endpoint | Security |
|---|---|
| `POST /api/customers` | `CUSTOMER_CREATE` |
| `GET /api/customers/{id}` | `CUSTOMER_READ` |
| `POST /api/customers/{id}/kyc/approve` | `KYC_APPROVE` |
| `GET /internal/customers/{id}/status` | `X-Service-Key` |

## Account — `8083`

Owns accounts, balances, funding instructions, financial operations and the ledger.

| Endpoint | Security |
|---|---|
| `POST /api/accounts` | `ACCOUNT_OPEN` + `Idempotency-Key` |
| `GET /api/accounts` | `ACCOUNT_READ` |
| `GET /api/accounts/{id}` | `ACCOUNT_READ` |
| `POST /api/accounts/{id}/freeze` | `ACCOUNT_FREEZE` |
| `POST /api/accounts/{id}/fund` | `ACCOUNT_FUND` |
| `GET /api/accounts/ledger/{operationId}` | `ACCOUNT_FUND` |
| `POST /api/accounts/funding/{instructionId}/post` | `X-Service-Key` + `X-External-Reference` |
| `POST /internal/accounts/transfer` | `X-Service-Key` |
| `GET /internal/accounts/operations/{id}` | `X-Service-Key` |

The funding-settlement endpoint intentionally uses the existing `/api` Gateway proxy but remains service-authenticated; it is not a public user endpoint.

## Transfer — `8084`

Owns transfer workflow, idempotency and outbox state.

| Endpoint | Security |
|---|---|
| `POST /api/transfers` | `TRANSFER_CREATE`; optional `Idempotency-Key` |
| `GET /api/transfers/{id}` | `TRANSFER_READ` |

Account Service owns the actual financial account mutation and ledger.

## Notification — `8085`

Consumes Kafka events and stores in-app notifications.

- `GET /api/notifications` — `NOTIFICATION_READ` or `TRANSFER_READ`.

## Audit — `8086`

Consumes Kafka events and stores transfer audit history.

- `GET /api/audit/transfers/{id}` — `AUDIT_READ`.

## Config — `8888`

Spring Cloud Config Server using the native classpath backend.

- Configuration is under `config-service/src/main/resources/config/{dev,qa,stage,prod}/`.
- No runtime `config-repo` checkout.
- No Docker-specific Spring profile.
- Local/demo Config Server has no authentication and must remain on a trusted network.

## Cross-service standards

- Protected APIs use `Authorization: Bearer <access-token>`.
- `X-Correlation-Id` is used for request traceability where supported.
- `Idempotency-Key` protects retryable financial commands.
- `X-Service-Key` is reserved for trusted service-to-service calls.
- Database ownership is strict; never query another service's database.

For complete payloads, responses, OAuth2 protocol endpoints, and rate-limit behavior, use [[API-Reference]].
