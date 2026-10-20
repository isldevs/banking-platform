# Microservices Platform API Reference

> Branch: `architecture/banking-ddd-hexagonal`
>
> This document describes the HTTP API currently implemented by the platform, with emphasis on the access-token / refresh-token lifecycle and the authenticated client APIs.

## 1. Service endpoints

| Service | Local base URL | Purpose |
|---|---|---|
| Gateway | `http://localhost:8080` | Public entry point; JWT resource-server validation |
| Auth | `http://localhost:8081` | Login, access-token issuance, refresh-token rotation, logout, user management |
| Customer | `http://localhost:8082` | Customer and KYC APIs |
| Account | `http://localhost:8083` | Accounts, balances, funding and ledger APIs |
| Transfer | `http://localhost:8084` | Transfer creation and lookup |
| Notification | `http://localhost:8085` | User notifications |
| Audit | `http://localhost:8086` | Transfer audit history |

The application services are stateless from an HTTP-session perspective. Authenticated APIs use the JWT access token in the `Authorization` header.

## 2. Authentication model

### Access token

The login and refresh endpoints return an OAuth-style bearer access token in the JSON field `accessToken`.

```http
Authorization: Bearer <access-token>
```

The access token is an RSA-signed JWT with:

| Claim | Value |
|---|---|
| `iss` | configured JWT issuer |
| `aud` | `api` |
| `sub` | authenticated user UUID |
| `username` | authenticated username |
| `permissions` | permission codes granted to the user |
| `roles` | role names granted to the user |
| `iat` | issue time |
| `exp` | expiration time |

The current implementation issues access tokens for **15 minutes (900 seconds)**. fileciteturn1431file0L2-L2

### Refresh token

Refresh tokens are opaque, URL-safe random values. They are stored server-side as hashes and expire after **7 days**. A successful refresh revokes the presented refresh token and issues a new access-token / refresh-token pair. This is refresh-token rotation. fileciteturn1429file0L2-L2

Clients must therefore:

1. Store the access token for API authorization.
2. Store the refresh token securely.
3. Use the refresh token when the access token expires.
4. Replace the old refresh token with the newly returned refresh token.
5. Never send an access token where a refresh token is expected.
6. Never log either token.

## 3. Token lifecycle

```text
                     +----------------+
                     |   POST /login  |
                     +-------+--------+
                             |
                             v
                 access_token + refresh_token
                    |                    |
                    |                    |
                    v                    v
             authenticated APIs   POST /auth/refresh
                    |                    |
                    |                    v
                    |             old refresh revoked
                    |                    |
                    |                    v
                    |          new access + refresh
                    |                    |
                    +--------------------+
                             |
                             v
                     POST /auth/logout
                             |
                             v
                    refresh token revoked
```

## 4. Authentication endpoints

### POST `/api/auth/login`

Authenticates a username/password and returns a new token pair.

**Authentication:** none.

**Request**

```json
{
  "username": "alice",
  "password": "your-password"
}
```

Both fields are required. fileciteturn1418file0L2-L2

**Response `200 OK`**

```json
{
  "access_token": "<jwt>",
  "refresh_token": "<opaque-refresh-token>",
  "token_type": "Bearer",
  "expires_in": 900
}
```

The Java implementation currently constructs the response with the logical fields `accessToken`, `refreshToken`, `tokenType`, and `expiresIn`; the platform's global Jackson naming configuration is intended to expose REST JSON as snake_case. fileciteturn1429file0L2-L2

**Failure cases**

| Status | Code | Meaning |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | Missing/invalid request field |
| `401` | `INVALID_CREDENTIALS` | Username or password is invalid |
| `403` | `USER_LOCKED_OR_INACTIVE` | User is inactive or temporarily locked |

Five failed password attempts cause the user to be locked for 15 minutes. fileciteturn1429file0L2-L2

---

### POST `/api/auth/refresh`

Exchanges a valid refresh token for a new token pair.

**Authentication:** refresh token in request body; no access token required.

**Request**

```json
{
  "refresh_token": "<refresh-token>"
}
```

`refresh_token` is required. fileciteturn1419file0L2-L2

**Response `200 OK`**

```json
{
  "access_token": "<new-jwt>",
  "refresh_token": "<new-refresh-token>",
  "token_type": "Bearer",
  "expires_in": 900
}
```

**Important:** refresh-token rotation means the submitted refresh token becomes invalid after a successful refresh. Reusing it returns an unauthorized response. fileciteturn1429file0L2-L2

**Failure cases**

| Status | Code | Meaning |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | Missing refresh token |
| `401` | `INVALID_REFRESH_TOKEN` | Token is unknown, expired, or already revoked |
| `401` | `USER_INACTIVE` | Token belongs to an inactive user |

---

### POST `/api/auth/logout`

Revokes the supplied refresh token for the authenticated user.

**Authentication:** required access token.

```http
Authorization: Bearer <access-token>
Content-Type: application/json
```

**Request**

```json
{
  "refresh_token": "<refresh-token>"
}
```

**Response `200 OK`**

```json
{
  "status": "LOGGED_OUT"
}
```

The endpoint revokes the refresh token associated with the current JWT subject. fileciteturn1408file0L2-L2 fileciteturn1429file0L2-L2

---

### GET `/api/auth/me`

Returns the identity and authorization information represented by the access token.

**Authentication:** required access token.

```http
Authorization: Bearer <access-token>
```

**Response `200 OK`**

```json
{
  "user_id": "<user-uuid>",
  "username": "alice",
  "roles": ["USER"],
  "permissions": ["CUSTOMER_READ", "ACCOUNT_READ"]
}
```

The endpoint reads `sub`, `username`, `roles`, and `permissions` directly from the validated JWT. fileciteturn1408file0L2-L2

---

## 5. User management

### POST `/api/users`

Creates a user.

**Authentication:** access token with `USER_MANAGE` permission.

**Request**

```json
{
  "username": "alice",
  "password": "at-least-12-characters",
  "email": "alice@example.com"
}
```

The username and password are required; email is optional. fileciteturn1420file0L2-L2

The service additionally requires a password length of at least 12 characters. fileciteturn1429file0L2-L2

**Response `200 OK`**

```json
{
  "user_id": "<user-uuid>"
}
```

---

# 6. Customer API

All `/api/customers/**` endpoints require a valid access token and the permission shown below.

### POST `/api/customers`

**Permission:** `CUSTOMER_CREATE`

```json
{
  "first_name": "Alice",
  "last_name": "Example",
  "date_of_birth": "1990-01-15",
  "phone": "+85512345678",
  "email": "alice@example.com"
}
```

`first_name`, `last_name`, and `date_of_birth` are required; date of birth must be in the past. fileciteturn1432file0L2-L2

### GET `/api/customers/{id}`

**Permission:** `CUSTOMER_READ`

Path parameter `id` is the customer UUID.

### POST `/api/customers/{id}/kyc/approve`

**Permission:** `KYC_APPROVE`

Path parameter `id` is the customer UUID.

### GET `/internal/customers/{id}/status`

**Authentication:** service-to-service `X-Service-Key`, not a user access token.

```http
X-Service-Key: <internal-service-key>
```

This endpoint is intentionally classified as internal and should not be exposed as a public client API. fileciteturn1409file0L2-L2

---

# 7. Account API

### POST `/api/accounts`

**Permission:** `ACCOUNT_OPEN`

```json
{
  "customer_id": "<customer-uuid>",
  "currency": "USD"
}
```

Both fields are required. fileciteturn1433file0L2-L2

### GET `/api/accounts/{id}`

**Permission:** `ACCOUNT_READ`

Returns the authenticated user's accessible account identified by UUID.

### GET `/api/accounts`

**Permission:** `ACCOUNT_READ`

Lists accounts accessible to the authenticated user.

### POST `/api/accounts/{id}/freeze`

**Permission:** `ACCOUNT_FREEZE`

Freezes the specified account.

### POST `/api/accounts/{id}/fund`

**Permission:** `ACCOUNT_FUND`

```json
{
  "amount": 1000.00
}
```

`amount` is required. fileciteturn1434file0L2-L2

### GET `/api/accounts/ledger/{operationId}`

**Permission:** `ACCOUNT_FUND`

Returns the ledger information for the supplied operation UUID.

### POST `/internal/accounts/transfer`

**Authentication:** `X-Service-Key`; not a user access-token endpoint.

Request fields:

```json
{
  "transfer_id": "<transfer-uuid>",
  "source_account_id": "<account-uuid>",
  "destination_account_id": "<account-uuid>",
  "amount": 100.00,
  "currency": "USD",
  "requested_by": "<user-uuid>"
}
```

The internal transfer command is intentionally service-to-service. fileciteturn1435file0L2-L2

### GET `/internal/accounts/operations/{id}`

**Authentication:** `X-Service-Key`.

Returns an internal account operation by UUID.

---

# 8. Transfer API

### POST `/api/transfers`

**Permission:** `TRANSFER_CREATE`

**Authentication:** access token.

Optional idempotency header:

```http
Idempotency-Key: <unique-client-key>
```

The request body is the transfer command defined by `TransferService.NewTransfer`. The authenticated user is taken from the JWT subject; clients must not use the request body to impersonate another user. fileciteturn1413file0L2-L2

**Response:** transfer resource/status map returned by the transfer service.

### GET `/api/transfers/{id}`

**Permission:** `TRANSFER_READ`

Returns a transfer visible to the authenticated user. Administrators may receive broader visibility based on the `ADMIN` role. fileciteturn1413file0L2-L2

---

# 9. Notification API

### GET `/api/notifications`

**Permission:** `NOTIFICATION_READ` **or** `TRANSFER_READ`.

**Authentication:** access token.

Returns up to the latest 100 notifications belonging to the authenticated user. fileciteturn1415file0L2-L2

Example response shape:

```json
[
  {
    "id": "<uuid>",
    "type": "TRANSFER_COMPLETED",
    "subject": "Transfer completed",
    "message": "Your transfer has completed.",
    "status": "UNREAD",
    "created_at": "2026-10-20T08:00:00Z"
  }
]
```

---

# 10. Audit API

### GET `/api/audit/transfers/{id}`

**Permission:** `AUDIT_READ`

Returns audit events for the specified transfer UUID. fileciteturn1417file0L2-L2

---

# 11. Authorization summary

| Endpoint | Auth mechanism | Required permission |
|---|---|---|
| `POST /api/auth/login` | None | — |
| `POST /api/auth/refresh` | Refresh token | — |
| `POST /api/auth/logout` | Access token + refresh token | — |
| `GET /api/auth/me` | Access token | — |
| `POST /api/users` | Access token | `USER_MANAGE` |
| `POST /api/customers` | Access token | `CUSTOMER_CREATE` |
| `GET /api/customers/{id}` | Access token | `CUSTOMER_READ` |
| `POST /api/customers/{id}/kyc/approve` | Access token | `KYC_APPROVE` |
| `POST /api/accounts` | Access token | `ACCOUNT_OPEN` |
| `GET /api/accounts/{id}` | Access token | `ACCOUNT_READ` |
| `GET /api/accounts` | Access token | `ACCOUNT_READ` |
| `POST /api/accounts/{id}/freeze` | Access token | `ACCOUNT_FREEZE` |
| `POST /api/accounts/{id}/fund` | Access token | `ACCOUNT_FUND` |
| `GET /api/accounts/ledger/{operationId}` | Access token | `ACCOUNT_FUND` |
| `POST /api/transfers` | Access token | `TRANSFER_CREATE` |
| `GET /api/transfers/{id}` | Access token | `TRANSFER_READ` |
| `GET /api/notifications` | Access token | `NOTIFICATION_READ` or `TRANSFER_READ` |
| `GET /api/audit/transfers/{id}` | Access token | `AUDIT_READ` |
| `/internal/**` | `X-Service-Key` | Service credential |

The gateway is configured as a stateless OAuth2 resource server: health/info endpoints are public, while other gateway requests require authentication. JWT permissions are converted into Spring authorities. fileciteturn1425file0L2-L2

# 12. Standard request headers

### Authenticated request

```http
Authorization: Bearer <access-token>
Content-Type: application/json
X-Correlation-Id: <optional-correlation-id>
```

### Transfer creation

```http
Authorization: Bearer <access-token>
Content-Type: application/json
Idempotency-Key: <unique-key>
```

### Internal service call

```http
X-Service-Key: <internal-service-key>
Content-Type: application/json
```

Do not use `X-Service-Key` for browser/mobile user authentication.

# 13. Error response

The Auth Service currently exposes a consistent error envelope:

```json
{
  "timestamp": "2026-10-20T08:00:00Z",
  "status": 401,
  "code": "INVALID_REFRESH_TOKEN",
  "message": "INVALID REFRESH TOKEN",
  "path": "/api/auth/refresh",
  "correlation_id": "<correlation-id>"
}
```

The implementation generates `timestamp`, `status`, `code`, `message`, `path`, and `correlationId`; the global JSON naming policy exposes camelCase properties as snake_case at the HTTP boundary. fileciteturn1437file0L2-L2

Common platform-level errors include:

| HTTP | Code | Meaning |
|---:|---|---|
| `400` | `VALIDATION_ERROR` | Bean validation failed |
| `400` | `INVALID_JSON` | Request body is not valid JSON |
| `401` | `INVALID_CREDENTIALS` | Login credentials failed |
| `401` | `INVALID_REFRESH_TOKEN` | Refresh token invalid/revoked/expired |
| `401` | `USER_INACTIVE` | Refresh-token owner is inactive |
| `403` | `USER_LOCKED_OR_INACTIVE` | Login rejected because account is locked/inactive |
| `409` | `DATA_CONFLICT` | Database uniqueness/integrity conflict |
| `500` | `INTERNAL_ERROR` | Unexpected server failure |

# 14. Client implementation example

## Login

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<password>"}'
```

Save the returned `access_token` and `refresh_token` securely.

## Call an authenticated API

```bash
curl http://localhost:8082/api/customers/<customer-id> \
  -H 'Authorization: Bearer <access-token>'
```

## Refresh

```bash
curl -X POST http://localhost:8081/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refresh_token":"<refresh-token>"}'
```

Replace both stored tokens with the new pair returned by the refresh operation.

## Logout

```bash
curl -X POST http://localhost:8081/api/auth/logout \
  -H 'Authorization: Bearer <access-token>' \
  -H 'Content-Type: application/json' \
  -d '{"refresh_token":"<refresh-token>"}'
```

After logout, discard both local tokens.

# 15. Security rules for clients

- Treat access tokens as bearer credentials.
- Use HTTPS outside local development.
- Keep refresh tokens in a secure platform credential store; do not place them in URLs.
- Never log `Authorization` headers, access tokens, or refresh tokens.
- Rotate the refresh token whenever `/api/auth/refresh` succeeds.
- If refresh fails with `INVALID_REFRESH_TOKEN`, require a new login.
- Do not call `/internal/**` endpoints from browser/mobile clients.
- Do not trust user IDs supplied by a client when the authenticated subject is available in the JWT.
- Send a stable `X-Correlation-Id` for traceability where supported.

# 16. Implementation notes

This is the API reference for the **currently implemented code**, not a hypothetical future OAuth/OIDC specification. Several response bodies are still returned as `Map`/database projections rather than dedicated response DTOs. The endpoint contract should therefore be treated as implementation-backed until explicit response DTOs and an OpenAPI contract are added.

The access-token / refresh-token lifecycle is implemented by `AuthController` and `AuthServiceImpl`; access tokens are JWTs and refresh tokens are persisted as hashes with rotation/revocation. fileciteturn1408file0L2-L2 fileciteturn1429file0L2-L2
