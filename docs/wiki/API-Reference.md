# API Reference

This is the **current implementation** API reference for `isldevs/banking-platform`. Target enterprise banking services (AML, FX, general ledger, payment rails, SWIFT, lending) do **not** yet expose APIs here. Call application routes through Gateway at `http://localhost:8080`; OAuth2 protocol endpoints are exposed by Auth Service at `http://localhost:8081`.

## Service URLs

| Service | Local URL |
|---|---|
| Gateway | `http://localhost:8080` |
| Auth / Authorization Server | `http://localhost:8081` |
| Customer | `http://customer-service:8082` (Compose network only) |
| Account | `http://account-service:8083` (Compose network only) |
| Transfer | `http://transfer-service:8084` (Compose network only) |
| Notification | `http://notification-service:8085` (Compose network only) |
| Audit | `http://audit-service:8086` (Compose network only) |
| Config | `http://localhost:8888` |

> The current Docker Compose does not publish Customer/Account/Transfer/Notification/Audit host ports. Direct `localhost:8082`–`:8086` URLs apply only if you run or expose those services separately.

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

Registered demo redirect URI: `https://oauth.pstmn.io/v1/callback` (`demo-browser` additionally allows `/v1/browser-callback`).

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
  "customer_id": "<customer-uuid>",
  "product_code": "BASIC-USD",
  "currency": "USD",
  "initial_deposit": 100.00,
  "funding_channel": "BANK_TRANSFER"
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

This is a **demo-only** post-opening funding operation using seeded system clearing accounts when enabled, not proof of external settlement. Do not expose as a real deposit facility.

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

**Required in current Transfer Service application logic** (although the controller header is declared optional). Permitted idempotency keys are 8–200 characters consisting of letters, digits, `.`, `_`, `:`, or `-`:

```http
Idempotency-Key: <unique-client-key>
```

Example request (snake_case request DTO naming):

```json
{
  "source_account_id": "<source-account-uuid>",
  "destination_account_id": "<destination-account-uuid>",
  "amount": 100.00,
  "currency": "USD",
  "reference": "DEMO-TRANSFER-001"
}
```

Current transfer currencies are **USD** and **KHR**, with maximum values USD 10,000.00 and KHR 40,000,000. The authenticated user is derived from the JWT subject. Transfer Service owns workflow/idempotency; Account Service owns ledger posting. Reusing a key with a different request fingerprint produces a conflict.

**Response caveat:** transfer responses currently come from a Java Map with camelCase keys such as `sourceAccountId` and `destinationAccountId`, while most request DTO JSON is configured for snake_case. This is an existing contract inconsistency to address in code.

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

Configured rule values **only when** rate limiting is enabled:

| Request | Limit |
|---|---:|
| `POST /api/auth/login` | 5/minute |
| `POST /api/auth/refresh` | 10/minute |
| `POST /api/accounts...` | 10/minute |
| `POST /api/transfers...` | 10/minute |
| Other `/api/**` | 60/minute |

Rate limiting is **disabled by default** in committed config (`GATEWAY_RATE_LIMIT_ENABLED=false`). When enabled, authenticated requests use JWT subject and anonymous requests use client IP. Allowed responses then expose:

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

## Planned API domains (not available)

The repository currently exposes no production APIs for general ledger closing, AML/sanctions screening, real domestic clearing, SWIFT/ISO 20022 messaging, correspondent banks, FX, lending or regulatory reporting. These require separately designed contracts, ownership controls and settlement/reconciliation semantics.

## API source of truth

When an endpoint changes, update this file first. Service READMEs should summarize and link here rather than duplicating the complete API reference.
