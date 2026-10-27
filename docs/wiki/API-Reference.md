# API Reference

This is the canonical API reference for the current repository state. Local public APIs are normally accessed through the Gateway at `http://localhost:8080`. Direct service URLs are useful for local diagnostics and OAuth2 protocol endpoints.

## Service URLs

| Service | Local URL |
|---|---|
| Gateway | `http://localhost:8080` |
| Auth / Authorization Server | `http://localhost:8081` |
| Customer | `http://localhost:8082` |
| Account | `http://localhost:8083` |
| Transfer | `http://localhost:8084` |
| Notification | `http://localhost:8085` |
| Audit | `http://localhost:8086` |
| Config | `http://localhost:8888` |

## Authentication

Protected application APIs use:

```http
Authorization: Bearer <access-token>
```

New clients should use OAuth 2.0 Authorization Code + PKCE.

### OAuth2/OIDC endpoints

- `GET /.well-known/openid-configuration`
- `GET /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/revoke`
- `GET /oauth2/jwks`
- `GET /userinfo`

Local issuer: `http://localhost:8081`.

Demo clients:

- `demo-browser` — public PKCE client; no refresh token.
- `demo-bff` — confidential PKCE client for trusted BFF/Postman; refresh tokens rotate and expire after 7 days.

Registered demo redirect URI: `https://oauth.pstmn.io/v1/callback`.

### Legacy compatibility

These endpoints remain for existing demo clients and are deprecated for new clients:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/users`

OAuth2 tokens and legacy custom tokens use separate stores and must not be mixed.

## Customer API

### Create customer

`POST /api/customers`

Permission: `CUSTOMER_CREATE`

```json
{
  "first_name": "Demo",
  "last_name": "Customer",
  "date_of_birth": "1995-01-15",
  "phone": "+85512000000",
  "email": "demo.customer@example.com"
}
```

### Read customer

`GET /api/customers/{id}`

Permission: `CUSTOMER_READ`.

### Approve KYC

`POST /api/customers/{id}/kyc/approve`

Permission: `KYC_APPROVE`.

### Internal customer status

`GET /internal/customers/{id}/status`

Requires `X-Service-Key`. Do not expose it to browser/mobile clients.

## Account API

### Open account

`POST /api/accounts`

Permission: `ACCOUNT_OPEN`.

Required header:

```http
Idempotency-Key: <unique-client-key>
```

Request:

```json
{
  "customerId": "<customer-uuid>",
  "productCode": "BASIC-USD",
  "currency": "USD",
  "initialDeposit": 100.00,
  "fundingChannel": "BANK_TRANSFER"
}
```

The account product and currency are validated from Account Service reference data. If `initialDeposit` is greater than zero, the account is returned as `PENDING_FUNDING` and a funding instruction is created. No customer balance is credited at this stage.

### Account response

The response includes the latest funding instruction when one exists:

```json
{
  "id": "<account-uuid>",
  "account_number": "AC...",
  "customer_id": "<customer-uuid>",
  "owner_user_id": "<user-uuid>",
  "currency": "USD",
  "status": "PENDING_FUNDING",
  "type": "CUSTOMER",
  "available_balance": 0,
  "ledger_balance": 0,
  "funding": {
    "instruction_id": "<funding-instruction-uuid>",
    "status": "PENDING_SETTLEMENT",
    "amount": 100.00,
    "currency": "USD",
    "channel": "BANK_TRANSFER",
    "external_reference": null
  }
}
```

### Read/list accounts

- `GET /api/accounts/{id}` — `ACCOUNT_READ`.
- `GET /api/accounts` — `ACCOUNT_READ`.

### Freeze account

`POST /api/accounts/{id}/freeze`

Permission: `ACCOUNT_FREEZE`.

### Fund an active account

`POST /api/accounts/{id}/fund`

Permission: `ACCOUNT_FUND`.

This is the separate post-opening funding/demo operation. It is not the same workflow as settlement-confirmed initial funding.

### Post settled initial funding

`POST /api/accounts/funding/{instructionId}/post`

This route is intentionally under `/api` so the existing Gateway proxy can route it. It is **not a public user operation** and requires:

```http
X-Service-Key: <internal-service-key>
X-External-Reference: <settlement-reference>
```

The caller supplies the external settlement reference. Posting locks the funding instruction/account, validates the settlement source and currency, creates the debit/credit financial operation and ledger entries, marks the instruction `POSTED`, and activates a `PENDING_FUNDING` account in one database transaction.

Retries with the same instruction and external reference are idempotent. A different external reference for an already-posted instruction is rejected.

### Ledger and internal operations

- `GET /api/accounts/ledger/{operationId}` — `ACCOUNT_FUND`.
- `POST /internal/accounts/transfer` — `X-Service-Key`.
- `GET /internal/accounts/operations/{id}` — `X-Service-Key`.

## Transfer API

### Create transfer

`POST /api/transfers`

Permission: `TRANSFER_CREATE`.

Recommended header for retry-safe financial commands:

```http
Idempotency-Key: <unique-client-key>
```

The authenticated user is derived from the JWT subject. Transfer Service owns workflow/idempotency; Account Service owns the financial ledger operation.

### Read transfer

`GET /api/transfers/{id}`

Permission: `TRANSFER_READ`.

## Notification API

`GET /api/notifications`

Permission: `NOTIFICATION_READ` or `TRANSFER_READ`.

Returns recent in-app notifications for the authenticated user.

## Audit API

`GET /api/audit/transfers/{id}`

Permission: `AUDIT_READ`.

Returns transfer-related audit history. Audit history is not the financial source of truth.

## Configuration API

Config Service uses the native Spring Cloud Config HTTP contract. Examples include:

```text
GET http://localhost:8888/auth-service/dev
GET http://localhost:8888/account-service/dev
GET http://localhost:8888/gateway-service/dev
```

Only non-secret configuration belongs in Config Service.

## Gateway rate limits

Current development defaults:

| Request | Limit |
|---|---:|
| `POST /api/auth/login` | 5/minute |
| `POST /api/auth/refresh` | 10/minute |
| `POST /api/accounts...` | 10/minute |
| `POST /api/transfers...` | 10/minute |
| Other `/api/**` | 60/minute |

Authenticated requests are limited by JWT subject; anonymous requests use client IP. Successful responses expose:

```text
X-RateLimit-Limit
X-RateLimit-Remaining
X-RateLimit-Reset
```

A blocked request returns `429 Too Many Requests` with `Retry-After`.

## Common headers

```text
Authorization: Bearer <access-token>
X-Correlation-Id: <optional-correlation-id>
Idempotency-Key: <unique-client-key>
X-Service-Key: <internal-service-key>
X-External-Reference: <settlement-reference>
```

Do not send service credentials from browser/mobile clients.

## API source of truth

When an endpoint changes, update this file first. Service READMEs should summarize and link here rather than duplicating the complete API reference.
