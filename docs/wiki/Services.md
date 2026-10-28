# Service Catalogue

This is the **implemented service inventory** for `isldevs/banking-platform`. Target global banking capabilities are listed separately below. Detailed API contracts belong in [API Reference](API-Reference.md).

## Gateway — `8080`

Public API entry point.

- JWT resource-server validation at the edge.
- Routing and correlation ID handling.
- Redis-backed API rate limiting (disabled by default in the checked-in profiles; enable explicitly).
- No banking business logic.

## Auth — `8081`

Identity and Spring Authorization Server / OIDC Provider.

- OAuth2/OIDC discovery, authorization, token, revocation and JWKS endpoints.
- Authorization Code + PKCE is the recommended client flow.
- Legacy `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout` remain for compatibility only.

## Customer — `8082`

Owns customer profiles and the **basic** KYC lifecycle. Automated AML, sanctions and document verification are not currently implemented.

| Endpoint | Security |
|---|---|
| `POST /api/customers` | `CUSTOMER_CREATE` |
| `GET /api/customers/{id}` | `CUSTOMER_READ` |
| `POST /api/customers/{id}/kyc/approve` | `KYC_APPROVE` |
| `GET /internal/customers/{id}/status` | `X-Service-Key` |

## Account — `8083`

Owns accounts, balances, funding instructions, financial operations and current double-entry-style ledger records, **not** an enterprise GL.

| Endpoint | Security |
|---|---|
| `POST /api/accounts` | `ACCOUNT_OPEN` + `Idempotency-Key` |
| `GET /api/accounts` | `ACCOUNT_READ` |
| `GET /api/accounts/{id}` | `ACCOUNT_READ` |
| `POST /api/accounts/{id}/freeze` | `ACCOUNT_FREEZE` |
| `POST /api/accounts/{id}/fund` | `ACCOUNT_FUND` |
| `GET /api/accounts/ledger/{operationId}` | `ACCOUNT_READ` + source/destination account ownership or `ADMIN` role |
| `POST /api/accounts/funding/{instructionId}/post` | `X-Service-Key` + `X-External-Reference` |
| `POST /internal/accounts/transfer` | `X-Service-Key` |
| `GET /internal/accounts/operations/{id}` | `X-Service-Key` |

The settlement-posting route uses the Gateway-routed `/api` namespace but requires a demo internal service key. This should become a separate, strongly authenticated settlement boundary in the target. The account `fund` endpoint is a demo clearing-account operation, not verified external funding.

## Transfer — `8084`

Owns transfer workflow, idempotency and outbox state.

| Endpoint | Security |
|---|---|
| `POST /api/transfers` | `TRANSFER_CREATE` + **required** `Idempotency-Key` (8–200 permitted characters) |
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

## Target services and domains (planned; **not deployed**)

- **Financial core:** posting rules, immutable journals, general ledger, holds, reversals and accounting dates.
- **Payment hub:** orchestration, fraud/limits, clearing and settlement adapters, return/reject handling, reconciliation.
- **Compliance:** CDD/KYC, sanctions/PEP, AML monitoring and case management.
- **International banking:** SWIFT/ISO 20022 adapters, FX, correspondent banks, Nostro/Vostro and legal entities.
- **Additional banking:** lending, regulatory reporting and end-of-day operations.

These are bounded contexts, not a requirement to deploy a separate microservice for every box. Keep the financial posting unit atomic.

## Cross-service standards

- Protected APIs use `Authorization: Bearer <access-token>`.
- `X-Correlation-Id` is used for request traceability where supported.
- `Idempotency-Key` is **required** for account opening and transfers in the current application logic.
- `X-Service-Key` is the existing demo internal mechanism; workload identity and mTLS are target controls.
- Database ownership is strict; never query another service's database.

For complete payloads, responses, OAuth2 protocol endpoints, and rate-limit behavior, use [API Reference](API-Reference.md).
