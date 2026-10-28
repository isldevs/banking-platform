# auth-service

Java 17 / Spring Boot service on port `8081`. Auth Service owns the `auth` PostgreSQL database and acts as the platform's Spring Authorization Server / OpenID Connect Provider.

## Responsibilities

- User identity and credentials.
- OAuth2/OIDC authorization and token issuance.
- Authorization-code + PKCE client flow.
- JWT signing and JWKS publication.
- Refresh-token lifecycle and rotation for confidential clients.
- Legacy authentication compatibility endpoints.

## Recommended client flow

Use Authorization Code + PKCE. Local issuer: `http://localhost:8081`.

- `GET /.well-known/openid-configuration`
- `GET /oauth2/authorize`
- `POST /oauth2/token`
- `POST /oauth2/revoke`
- `GET /oauth2/jwks`
- `GET /userinfo`

Demo clients:

- `demo-browser` — public PKCE client; no refresh token is issued.
- `demo-bff` — confidential PKCE client for trusted BFF/Postman usage; refresh tokens are rotated and expire after 7 days.

The confidential secret is supplied through `OAUTH2_DEMO_CLIENT_SECRET`. Never place it in browser/mobile code or source control.

## Legacy compatibility

`POST /api/auth/login`, `POST /api/auth/refresh`, and `POST /api/auth/logout` remain only for existing demo clients. They use a separate legacy token store and are deprecated for new integrations. `GET /api/auth/me` remains available for identity/permission inspection.

## Configuration

Configuration is bundled in `config-service/src/main/resources/config/{dev,qa,stage,prod}/auth-service.yml`. Database credentials, JWT signing keystore settings, demo credentials, and OAuth2 client secrets are runtime values.

## Run

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env
cd infrastructure
docker compose up --build -d
```

For service-only development, start Config Service and PostgreSQL first, then run this independent Gradle build with Java 17.

See [Authentication and OAuth2](../docs/wiki/Authentication-and-OAuth2.md) and [API Reference](../docs/wiki/API-Reference.md) for the canonical client and endpoint documentation.
