# Microservices Platform API Reference

> Branch: `architecture/banking-ddd-hexagonal`
>
> This page documents the implemented REST APIs and the preferred OAuth 2.0 / OpenID Connect client flow. For new
> clients, use Spring Authorization Server. The older `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout`
> endpoints remain only for compatibility with the current demo.

## 1. Service endpoints

| Service                     | Local base URL          | Purpose                                                               |
|-----------------------------|-------------------------|-----------------------------------------------------------------------|
| Authorization Server / Auth | `http://localhost:8081` | OAuth2/OIDC, token issuance, user identity, legacy auth compatibility |
| Gateway security service    | `http://localhost:8080` | Gateway/security application                                          |
| Customer                    | `http://localhost:8082` | Customer and KYC APIs                                                 |
| Account                     | `http://localhost:8083` | Accounts, balances, funding and ledger APIs                           |
| Transfer                    | `http://localhost:8084` | Transfer creation and lookup                                          |
| Notification                | `http://localhost:8085` | User notifications                                                    |
| Audit                       | `http://localhost:8086` | Transfer audit history                                                |

Use the Authorization Server URL for the OAuth2 protocol endpoints documented below. In a deployed environment,
substitute the configured issuer and service URLs.

## 2. Authentication model

### Preferred: OAuth 2.0 Authorization Code + PKCE

Spring Authorization Server exposes these protocol endpoints:

| Method | Endpoint                            | Purpose                                                     |
|--------|-------------------------------------|-------------------------------------------------------------|
| GET    | `/.well-known/openid-configuration` | OIDC discovery document                                     |
| GET    | `/oauth2/authorize`                 | Begins user authorization; redirects to sign-in when needed |
| POST   | `/oauth2/token`                     | Exchanges an authorization code or refresh token for tokens |
| POST   | `/oauth2/revoke`                    | Revokes an OAuth2 token                                     |
| GET    | `/oauth2/jwks`                      | Public signing keys used to validate JWTs                   |
| GET    | `/userinfo`                         | OIDC UserInfo endpoint                                      |

The configured grant types are `authorization_code` and, for confidential clients, `refresh_token`. **The Resource Owner
Password Credentials (password) grant is not enabled and should not be added.** The user's username and password are
submitted to the Authorization Server's browser sign-in page as part of the authorization-code flow, not to
`/oauth2/token`.

### Registered demo clients

| Client ID      | Type                                       | Suitable for                                    | Refresh tokens                            |
|----------------|--------------------------------------------|-------------------------------------------------|-------------------------------------------|
| `demo-browser` | Public; no client secret; PKCE required    | Authorization-code examples for a public client | Not issued by Spring Authorization Server |
| `demo-bff`     | Confidential; client secret; PKCE required | Trusted backend/BFF or Postman                  | Yes; 7-day lifetime with rotation         |

The `demo-bff` client is registered when `OAUTH2_DEMO_CLIENT_SECRET` is supplied at runtime. Its secret is stored as a
BCrypt hash in the Authorization Server database. The local setup script generates this value in ignored
`infrastructure/.env`.

Never put a confidential client secret or a refresh token in browser JavaScript, a mobile app bundle, a URL, or client
logs. For a browser/mobile application that needs persistent login, keep OAuth credentials and refresh tokens behind a
backend-for-frontend (BFF). Do not treat a public client as confidential by shipping a secret with it.

The demo redirect URI is `https://oauth.pstmn.io/v1/callback`. The registered scopes are `openid`, `profile`, `email`,
`customer.read`, `account.read`, `account.write`, and `transfer.create`.

### Access-token claims

The Authorization Server issues RSA-signed JWT access tokens with these claims:

| Claim         | Meaning                                    |
|---------------|--------------------------------------------|
| `iss`         | Configured issuer                          |
| `aud`         | API audience (`api`)                       |
| `sub`         | Authenticated user's UUID                  |
| `username`    | Authenticated username                     |
| `permissions` | Permission codes used by API authorization |
| `roles`       | User role names                            |
| `iat` / `exp` | Issuance / expiry timestamps               |

Access-token lifetime is 15 minutes (900 seconds). Resource-server APIs expect:

```http
Authorization: Bearer <access_token>
```

The confidential client's refresh tokens expire after 7 days. Refresh-token rotation is enabled: each successful refresh
returns a replacement refresh token, and the previously submitted refresh token must no longer be used.

## 3. OAuth2/OIDC client API reference

### 3.1 Discover the server configuration

**GET** `/.well-known/openid-configuration`

No authentication required.

```bash
curl http://localhost:8081/.well-known/openid-configuration
```

Use the returned issuer and endpoint URLs instead of hard-coding them in production applications.

### 3.2 Start authorization

**GET** `/oauth2/authorize`

This is a browser redirect, not a JSON API. A client initiates Authorization Code + PKCE with query parameters
including:

| Parameter                    | Required                | Description                                                 |
|------------------------------|-------------------------|-------------------------------------------------------------|
| `response_type=code`         | Yes                     | Requests an authorization code                              |
| `client_id`                  | Yes                     | `demo-browser` or `demo-bff`                                |
| `redirect_uri`               | Yes                     | Must exactly match the registered URI                       |
| `scope`                      | Yes                     | Space-delimited registered scopes                           |
| `state`                      | Yes for a robust client | Random value generated by the client; verify it on callback |
| `code_challenge`             | Yes                     | Base64URL(SHA-256(code_verifier))                           |
| `code_challenge_method=S256` | Yes                     | PKCE SHA-256 method                                         |
| `nonce`                      | Recommended for OIDC    | Random value checked against the ID token                   |

Example authorization request (use an OAuth client library or Postman to generate and retain a matching `code_verifier`
and `code_challenge`):

```text
http://localhost:8081/oauth2/authorize?response_type=code&client_id=demo-bff&redirect_uri=https%3A%2F%2Foauth.pstmn.io%2Fv1%2Fcallback&scope=openid%20profile%20email%20customer.read%20account.read%20account.write%20transfer.create&state=<random-state>&nonce=<random-nonce>&code_challenge=<BASE64URL_SHA256_CODE_CHALLENGE>&code_challenge_method=S256
```

The user signs in through the Authorization Server. On successful authorization, the browser is redirected to the
registered URI with a short-lived `code` and the original `state`. Validate `state` before exchanging the code.

### 3.3 Exchange an authorization code for tokens

**POST** `/oauth2/token`

**Content type:** `application/x-www-form-urlencoded`

**Client:** use the confidential `demo-bff` client when the application needs a refresh token. Authenticate the client
with HTTP Basic using the configured client ID and secret.

| Form field      | Value                                             |
|-----------------|---------------------------------------------------|
| `grant_type`    | `authorization_code`                              |
| `client_id`     | `demo-bff`                                        |
| `code`          | Code returned to the registered redirect URI      |
| `redirect_uri`  | The exact registered URI                          |
| `code_verifier` | The verifier matching the original PKCE challenge |

Example:

```bash
curl -X POST http://localhost:8081/oauth2/token \
  -u "demo-bff:${OAUTH2_DEMO_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "client_id=demo-bff" \
  --data-urlencode "code=<authorization-code>" \
  --data-urlencode "redirect_uri=https://oauth.pstmn.io/v1/callback" \
  --data-urlencode "code_verifier=<original-code-verifier>"
```

A successful response follows the OAuth2 token response shape. Typical fields:

```json
{
  "access_token": "<jwt>",
  "refresh_token": "<opaque-refresh-token>",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "openid profile email customer.read account.read account.write transfer.create",
  "id_token": "<oidc-id-token>"
}
```

The `id_token` is returned when the authorization request includes `openid`. Do not assume all fields will be present
for every scope or grant. Store the refresh token only in the trusted client/BFF.

**Important:** the public `demo-browser` client does not receive refresh tokens. Spring Authorization Server does not
issue refresh tokens to public clients, regardless of a refresh-token grant entry in their registration.

### 3.4 Refresh an access token

**POST** `/oauth2/token`

**Content type:** `application/x-www-form-urlencoded`

**Client:** confidential `demo-bff` (HTTP Basic authentication).

| Form field      | Value                             |
|-----------------|-----------------------------------|
| `grant_type`    | `refresh_token`                   |
| `client_id`     | `demo-bff`                        |
| `refresh_token` | The currently valid refresh token |

Example:

```bash
curl -X POST http://localhost:8081/oauth2/token \
  -u "demo-bff:${OAUTH2_DEMO_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "client_id=demo-bff" \
  --data-urlencode "refresh_token=<current-refresh-token>"
```

On success, replace the locally stored access token and refresh token with the returned values. Because refresh-token
reuse is disabled, the submitted refresh token must be treated as consumed. If the refresh request fails, do not retry
indefinitely with the same token; initiate a new authorization flow as appropriate.

### 3.5 Revoke a token

**POST** `/oauth2/revoke`

**Content type:** `application/x-www-form-urlencoded`

Use the confidential client credentials. A client can request revocation of the refresh token when the user signs out or
when credentials must be invalidated.

```bash
curl -X POST http://localhost:8081/oauth2/revoke \
  -u "demo-bff:${OAUTH2_DEMO_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "token=<refresh-token>" \
  --data-urlencode "token_type_hint=refresh_token"
```

OAuth2 revocation responses are protocol responses, not the custom JSON error envelope used by the legacy REST
endpoints. The client should follow the OAuth2 token revocation specification and treat the token as unusable locally
after sign-out.

### 3.6 Call a protected API

After obtaining an access token:

```bash
curl http://localhost:8082/api/customers/<customer-id> \
  -H "Authorization: Bearer <access-token>" \
  -H "X-Correlation-Id: <optional-correlation-id>"
```

Protected application APIs validate the JWT issuer, audience, signature, and expiry. The permission codes in the
`permissions` claim are used for endpoint authorization.

## 4. Legacy authentication compatibility endpoints

These endpoints are deprecated for new clients. They use a separate custom token implementation and refresh-token table;
use the OAuth2/OIDC endpoints above instead.

### POST `/api/auth/login` — legacy

Authenticates username/password in a JSON request and issues the legacy token pair.

```json
{
  "username": "alice",
  "password": "your-password"
}
```

A successful response historically includes `access_token`, `refresh_token`, `token_type`, and `expires_in`. Access
tokens expire after 900 seconds. The refresh token is an opaque random value persisted as a server-side hash with a
7-day expiry.

### POST `/api/auth/refresh` — legacy

Exchanges only a refresh token created by `/api/auth/login`. It does not accept Spring Authorization Server refresh
tokens.

```json
{
  "refresh_token": "<legacy-refresh-token>"
}
```

The refresh token is rotated on success and the old value is revoked.

### POST `/api/auth/logout` — legacy

Requires a bearer access token and the legacy refresh token in JSON. It revokes the refresh token in the legacy store
only. Use `POST /oauth2/revoke` for OAuth2 tokens.

### GET `/api/auth/me`

Requires a bearer access token. Returns the subject, username, roles, and permissions from the validated JWT:

```json
{
  "user_id": "<user-uuid>",
  "username": "alice",
  "roles": ["USER"],
  "permissions": ["CUSTOMER_READ", "ACCOUNT_READ"]
}
```

### POST `/api/users`

Creates a user. Requires the access token authority `USER_MANAGE`.

```json
{
  "username": "alice",
  "password": "at-least-12-characters",
  "email": "alice@example.com"
}
```

Username and password are required; email is optional. The password must be at least 12 characters long. The response
contains `user_id`.

## 5. Customer API

All public customer API endpoints require a valid access token and the listed permission.

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

`first_name`, `last_name`, and `date_of_birth` are required; date of birth must be in the past.

### GET `/api/customers/{id}`

**Permission:** `CUSTOMER_READ`

Path parameter `id` is the customer UUID.

### POST `/api/customers/{id}/kyc/approve`

**Permission:** `KYC_APPROVE`

Path parameter `id` is the customer UUID.

### GET `/internal/customers/{id}/status`

**Authentication:** service-to-service `X-Service-Key`; not a user access-token endpoint.

```http
X-Service-Key: <internal-service-key>
```

Do not expose this internal endpoint to browser/mobile clients.

## 6. Account API

### POST `/api/accounts`

**Permission:** `ACCOUNT_OPEN`

```json
{
  "customer_id": "<customer-uuid>",
  "currency": "USD"
}
```

Both fields are required.

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

`amount` is required.

### GET `/api/accounts/ledger/{operationId}`

**Permission:** `ACCOUNT_FUND`

Returns ledger information for the supplied operation UUID.

### POST `/internal/accounts/transfer`

**Authentication:** `X-Service-Key`; internal service-to-service endpoint.

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

### GET `/internal/accounts/operations/{id}`

**Authentication:** `X-Service-Key`. Returns an internal account operation by UUID.

## 7. Transfer API

### POST `/api/transfers`

**Permission:** `TRANSFER_CREATE`

Optional idempotency header:

```http
Idempotency-Key: <unique-client-key>
```

The authenticated user is taken from the JWT subject; clients must not use the request body to impersonate another user.

### GET `/api/transfers/{id}`

**Permission:** `TRANSFER_READ`

Returns a transfer visible to the authenticated user. Administrators may receive broader visibility based on the `ADMIN`
role.

## 8. Notification API

### GET `/api/notifications`

**Permission:** `NOTIFICATION_READ` or `TRANSFER_READ`

Returns up to the latest 100 notifications belonging to the authenticated user.

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

## 9. Audit API

### GET `/api/audit/transfers/{id}`

**Permission:** `AUDIT_READ`

Returns audit events for the specified transfer UUID.

## 10. Authorization summary

| Endpoint                                 | Auth mechanism                                             | Authorization                                                      |
|------------------------------------------|------------------------------------------------------------|--------------------------------------------------------------------|
| `GET /.well-known/openid-configuration`  | None                                                       | OIDC discovery                                                     |
| `GET /oauth2/authorize`                  | Browser session / user login                               | Registered OAuth2 client + requested scopes                        |
| `POST /oauth2/token`                     | OAuth2 client authentication and grant-specific parameters | `authorization_code` or `refresh_token` for supported client types |
| `POST /oauth2/revoke`                    | OAuth2 client authentication                               | OAuth2 token revocation                                            |
| `POST /api/auth/login`                   | None; legacy                                               | Deprecated                                                         |
| `POST /api/auth/refresh`                 | Legacy refresh token                                       | Deprecated; legacy tokens only                                     |
| `POST /api/auth/logout`                  | Access token + legacy refresh token                        | Deprecated; legacy refresh tokens only                             |
| `GET /api/auth/me`                       | Bearer access token                                        | Identity details                                                   |
| `POST /api/users`                        | Bearer access token                                        | `USER_MANAGE`                                                      |
| `POST /api/customers`                    | Bearer access token                                        | `CUSTOMER_CREATE`                                                  |
| `GET /api/customers/{id}`                | Bearer access token                                        | `CUSTOMER_READ`                                                    |
| `POST /api/customers/{id}/kyc/approve`   | Bearer access token                                        | `KYC_APPROVE`                                                      |
| `POST /api/accounts`                     | Bearer access token                                        | `ACCOUNT_OPEN`                                                     |
| `GET /api/accounts/{id}`                 | Bearer access token                                        | `ACCOUNT_READ`                                                     |
| `GET /api/accounts`                      | Bearer access token                                        | `ACCOUNT_READ`                                                     |
| `POST /api/accounts/{id}/freeze`         | Bearer access token                                        | `ACCOUNT_FREEZE`                                                   |
| `POST /api/accounts/{id}/fund`           | Bearer access token                                        | `ACCOUNT_FUND`                                                     |
| `GET /api/accounts/ledger/{operationId}` | Bearer access token                                        | `ACCOUNT_FUND`                                                     |
| `POST /api/transfers`                    | Bearer access token                                        | `TRANSFER_CREATE`                                                  |
| `GET /api/transfers/{id}`                | Bearer access token                                        | `TRANSFER_READ`                                                    |
| `GET /api/notifications`                 | Bearer access token                                        | `NOTIFICATION_READ` or `TRANSFER_READ`                             |
| `GET /api/audit/transfers/{id}`          | Bearer access token                                        | `AUDIT_READ`                                                       |
| `/internal/**`                           | `X-Service-Key`                                            | Internal service credential                                        |

## 11. Standard request headers

### Protected REST request

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

## 12. Errors

OAuth2 protocol endpoints return OAuth2-compliant errors, commonly including `error` and `error_description`. They do
not use the legacy API error envelope below.

The legacy Auth Service REST endpoints currently expose a custom error envelope:

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

Common application errors include:

|  HTTP | Code                      | Meaning                                                  |
|------:|---------------------------|----------------------------------------------------------|
| `400` | `VALIDATION_ERROR`        | Bean validation failed                                   |
| `400` | `INVALID_JSON`            | Request body is not valid JSON                           |
| `401` | `INVALID_CREDENTIALS`     | Legacy login credentials failed                          |
| `401` | `INVALID_REFRESH_TOKEN`   | Legacy refresh token invalid, revoked, or expired        |
| `401` | `USER_INACTIVE`           | Legacy refresh token owner is inactive                   |
| `403` | `USER_LOCKED_OR_INACTIVE` | Legacy login rejected because account is locked/inactive |
| `409` | `DATA_CONFLICT`           | Database uniqueness/integrity conflict                   |
| `500` | `INTERNAL_ERROR`          | Unexpected server failure                                |

## 13. Client security rules

- Use Authorization Code + PKCE for interactive user authentication.
- Use a confidential client only in a trusted backend/BFF or trusted native client that can securely protect its secret.
- Keep refresh tokens out of browser JavaScript, mobile bundles, URLs, and logs.
- On successful refresh, replace both stored tokens and discard the old refresh token.
- Use `POST /oauth2/revoke` for Authorization Server tokens; do not send OAuth2 refresh tokens to legacy
  `/api/auth/logout`.
- Never send a legacy refresh token to `/oauth2/token`.
- Use HTTPS outside local development.
- Never log `Authorization` headers, access tokens, refresh tokens, or client secrets.
- Do not call `/internal/**` endpoints from browser/mobile clients.
- Do not trust client-supplied user IDs when the authenticated subject is available in the JWT.
- Send a stable `X-Correlation-Id` for traceability where supported.

## 14. Implementation notes

This page describes the current implementation, not a hypothetical API. The `/api/auth/login`, `/api/auth/refresh`, and
`/api/auth/logout` methods are deprecated compatibility endpoints; new clients should use Spring Authorization Server.
Some REST responses are still returned as maps or database projections rather than dedicated response DTOs. A formal
OpenAPI specification can be added as a follow-up contract artifact.
