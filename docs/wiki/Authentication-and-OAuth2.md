# Authentication and OAuth2/OIDC

This page documents the **current demo authorization server**, not a production identity platform. The public OAuth2 protocol is served directly by Auth Service (port 8081) in the local stack.

## Recommended flow

New interactive clients should use **OAuth 2.0 Authorization Code + PKCE** through Spring Authorization Server. Do not
implement login by posting a username/password JSON object to the token endpoint.

Local host issuer: `http://localhost:8081` for matching host configuration. Docker Compose supports a container-specific issuer via `DOCKER_JWT_ISSUER`; ensure issuer, token validators, client redirect/discovery configuration and service hostnames are consistent.

| Endpoint                                | Purpose                                          |
|-----------------------------------------|--------------------------------------------------|
| `GET /.well-known/openid-configuration` | OIDC discovery                                   |
| `GET /oauth2/authorize`                 | Begins the browser authorization flow            |
| `POST /oauth2/token`                    | Exchanges an authorization code or refresh token |
| `POST /oauth2/revoke`                   | Requests token revocation                        |
| `GET /oauth2/jwks`                      | Public JWK set for token signature verification  |
| `GET /userinfo`                         | OIDC UserInfo endpoint                           |

Use the issuer and endpoint URLs returned by discovery in client configuration. Use HTTPS outside local development.

## Registered demo clients

| Client ID      | Type                                              | Intended use                              | Refresh token      |
|----------------|---------------------------------------------------|-------------------------------------------|--------------------|
| `demo-browser` | Public client; no secret; PKCE required           | Public-client authorization code examples | Not issued         |
| `demo-bff`     | Confidential client; client secret; PKCE required | Trusted backend/BFF or Postman demo       | Issued and rotated |

Spring Authorization Server does not issue refresh tokens to public clients. The confidential client's secret is
supplied as `OAUTH2_DEMO_CLIENT_SECRET` and stored hashed in the registered-client database entry.

Never embed `demo-bff`'s secret or a refresh token in browser JavaScript, a mobile application bundle, a URL, or logs.
For browser/mobile applications needing long-lived access, keep refresh tokens in a backend-for-frontend (BFF).

The `demo-bff` redirect URI is `https://oauth.pstmn.io/v1/callback`; `demo-browser` additionally supports `https://oauth.pstmn.io/v1/browser-callback`. Both clients support `openid`, `profile`, `email` and the customer scopes below. Only the confidential client may delegate `banking.admin` or `audit.read`.

| Scope | User permissions it may delegate |
|---|---|
| `customer.read` | `CUSTOMER_READ` |
| `customer.write` | `CUSTOMER_CREATE` |
| `account.read` | `ACCOUNT_READ` |
| `account.write` | `ACCOUNT_OPEN` |
| `transfer.create` | `TRANSFER_CREATE` |
| `transfer.read` | `TRANSFER_READ` |
| `notification.read` | `NOTIFICATION_READ` |
| `audit.read` | `AUDIT_READ` (confidential client only) |
| `banking.admin` | Administrative user management, KYC approval, freezing, demo funding and audit permissions (confidential client only) |

Granted scopes restrict the user's database permissions; they never grant a permission the user lacks. Request ordinary read/write scopes as well as `banking.admin` when an administrative workflow needs both. An OAuth access token only carries the `ADMIN` ownership-bypass role when `banking.admin` is granted. A read-only grant therefore cannot write or use administrator ownership bypass. Existing demo registrations have their scope policy synchronized without changing their persisted IDs or client secrets.

## Authorization-code exchange

A client library or Postman should generate a high-entropy PKCE `code_verifier`, its S256 `code_challenge`, a unique
`state`, and (for OIDC) a `nonce`.

The browser authorization request includes:

```text
response_type=code
client_id=demo-bff
redirect_uri=https://oauth.pstmn.io/v1/callback
scope=openid profile email customer.read customer.write account.read account.write transfer.create transfer.read notification.read
state=<random-state>
nonce=<random-nonce>
code_challenge=<BASE64URL_SHA256_CODE_CHALLENGE>
code_challenge_method=S256
```

After the user signs in, validate the returned `state` and exchange the authorization code at `POST /oauth2/token`. The
token request is `application/x-www-form-urlencoded`, not JSON. Authenticate the confidential client using HTTP Basic
with the client ID and secret.

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

A successful response commonly includes `access_token`, `token_type`, `expires_in`, `scope`, and (for OIDC requests)
`id_token`. The confidential client can receive a `refresh_token`.

## Refresh tokens

Access-token lifetime is 15 minutes (900 seconds). Confidential-client refresh tokens have a seven-day lifetime and
rotation is enabled.

```bash
curl -X POST http://localhost:8081/oauth2/token \
  -u "demo-bff:${OAUTH2_DEMO_CLIENT_SECRET}" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=refresh_token" \
  --data-urlencode "client_id=demo-bff" \
  --data-urlencode "refresh_token=<current-refresh-token>"
```

On success, replace both the access token and refresh token with the values returned. Treat the submitted refresh token
as consumed. If refresh fails, restart authorization rather than repeatedly replaying the same token.

## Access-token claims and API calls

JWT access tokens include:

- `iss`: configured issuer
- `aud`: configured API audience (`api`)
- `sub`: authenticated user's UUID
- `username`: username
- `roles`: role names
- `permissions`: authorization permission codes
- `iat` / `exp`: issuance and expiry timestamps

Call protected application APIs with:

```http
Authorization: Bearer <access-token>
```

The resource servers validate signature, issuer, audience, and expiry. Application permissions are mapped from the JWT
`permissions` claim after the authorization server intersects user permissions and granted scopes. Refresh-token issuance applies the current policy again. Previously issued self-contained access tokens remain valid until expiry; reauthorize clients after a policy upgrade and account for the existing 15-minute token lifetime.

## Revocation and sign-out

For OAuth2 tokens, send a form-encoded revocation request to `POST /oauth2/revoke`, authenticated as the confidential
client. After sign-out, discard locally stored tokens.

## Resource server and internal-service authorization

- Current JWT access tokens carry the UUID user ID in `sub`, plus `roles` and `permissions`. Controllers and application services must enforce both permission and ownership.
- Gateway validation is not sufficient: protected downstream services independently validate JWTs.
- Current `X-Service-Key` internal calls are a **demo trust mechanism**. The future enterprise design should use scoped workload identity, mTLS and service authorization.
- Never leak tokens, client secrets, PKCE verifiers, signing keys or authorization codes in logs or committed Postman resources.
- The Postman callback and demo credentials are **local development examples**, not production configurations.

## Deprecated custom token endpoints

The following endpoints remain for old demo clients but are deprecated for new clients:

- `POST /api/auth/login` — custom JSON username/password login
- `POST /api/auth/refresh` — rotates tokens in the legacy refresh-token table
- `POST /api/auth/logout` — revokes a legacy refresh token

The legacy endpoints and Spring Authorization Server use separate token stores. Never pass a legacy refresh token to
`/oauth2/token`, or an OAuth2 refresh token to `/api/auth/refresh` or `/api/auth/logout`.

Legacy login has no delegated OAuth scope request and intentionally issues the authenticated user's existing permissions and roles. It is retained for compatibility and isolated demo smoke-test setup, not as a replacement for scoped client authorization. Demo identities are initialized by one dev-only initializer; QA, stage and prod do not require demo passwords and must provision identities separately.

See [API Reference](API-Reference.md) for the current REST contract, [Security and Operations](Security-and-Operations.md) for security gaps, and [Configuration](Configuration.md) for environment-specific issuer settings.
