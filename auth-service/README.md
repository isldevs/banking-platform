# auth-service

Independent Java 21 / Spring Boot service on port `8081` with its own `auth` PostgreSQL database. It also acts as the
platform's Spring Authorization Server and OpenID Connect Provider.

## Centralized configuration

Application configuration is bundled in `config-service/src/main/resources/config/{dev,qa,stage,prod}/`. Activate only
an environment profile (`dev`, `qa`, `stage`, or `prod`); Docker is a deployment mechanism, not a Spring profile.
Database credentials, the JWT signing keystore password, OAuth2 confidential-client secret, issuer overrides, and demo
user passwords are supplied at runtime through environment variables. Generated signing material and
`infrastructure/.env` are ignored by Git.

## OAuth2 / OIDC endpoints

The recommended client flow is **Authorization Code + PKCE**. The user authenticates on the Authorization Server's
browser login page; clients do not exchange a username/password JSON body for OAuth tokens.

- Issuer: `http://localhost:8081`
- Discovery: `http://localhost:8081/.well-known/openid-configuration`
- Authorization endpoint: `http://localhost:8081/oauth2/authorize`
- Token endpoint: `http://localhost:8081/oauth2/token`
- Token revocation endpoint: `http://localhost:8081/oauth2/revoke`
- JWKS endpoint: `http://localhost:8081/oauth2/jwks`
- Public PKCE client ID: `demo-browser` (authorization-code flow; Spring Authorization Server does not issue refresh
  tokens to public clients)
- Confidential client ID: `demo-bff` (trusted backend/BFF or Postman; supports refresh-token rotation)
- Redirect URI registered for the demo clients: `https://oauth.pstmn.io/v1/callback`

The confidential client's secret is read from `OAUTH2_DEMO_CLIENT_SECRET`; it is stored hashed in the Authorization
Server's registered-client database record. The local initialization script generates the secret in ignored
`infrastructure/.env`. Do not embed this secret in browser JavaScript or a mobile application. For a browser/mobile app
that needs long-lived login, keep refresh tokens in a backend-for-frontend (BFF) and use the BFF as the confidential
client.

Configure Postman as OAuth 2.0 **Authorization Code (With PKCE)** with client ID `demo-bff`, its client secret from
`infrastructure/.env`, callback `https://oauth.pstmn.io/v1/callback`, and the endpoints above. Use the requested scopes
`openid profile email customer.read account.read account.write transfer.create`. The token endpoint accepts form-encoded
OAuth parameters, not a JSON login request. It supports `authorization_code` and `refresh_token`; it does not support
the deprecated OAuth password grant.

Access tokens are JWTs with `iss`, `aud`, `sub`, `username`, `roles`, and `permissions` claims. Access tokens expire
after 15 minutes. Confidential-client refresh tokens expire after 7 days and are rotated on use. Store new refresh
tokens after each successful refresh and discard the old ones.

## Legacy compatibility endpoints

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`

These direct endpoints use a separate legacy refresh-token store and are retained temporarily for existing automated
demo clients. New clients should use Spring Authorization Server endpoints. Tokens from the legacy flow and tokens from
Spring Authorization Server must not have their refresh tokens exchanged or revoked through the other flow's endpoints.
`GET /api/auth/me` remains available for checking the identity and permissions in a bearer access token.

## Persistence

Registered OAuth2 clients, authorization codes/tokens, consent records, and legacy refresh-token records are stored in
the `auth` PostgreSQL database using Spring Authorization Server JDBC repositories and Flyway migrations. Expired
authorizations still need a retention/cleanup policy.

## Run locally from IntelliJ (no Docker)

First run `bash infrastructure/scripts/init-local.sh` from Git Bash or WSL at the repository root to generate ignored
local secrets. Start `config-service` first; it serves the bundled non-secret configuration without Config Client
credentials. Then run AuthApplication with at least:

- `SPRING_PROFILES_ACTIVE=dev`
- `CONFIG_SERVICE_URL=http://localhost:8888`
- `SECURITY_JWT_KEYSTOREPATH=<absolute path to infrastructure/secrets/jwt-signing.p12>`
- `SECURITY_JWT_KEYSTOREPASSWORD=<JWT_KEYSTORE_PASSWORD from infrastructure/.env>`
- `SECURITY_JWT_KEYSTOREALIAS=jwt-signing`
- `OAUTH2_DEMO_CLIENT_SECRET=<value from infrastructure/.env>`
- `DATASOURCE_URL=jdbc:postgresql://localhost:5432/auth`
- `DATASOURCE_USERNAME=auth_user`
- `DATASOURCE_PASSWORD=<DB_PASSWORD_AUTH from infrastructure/.env>`

PostgreSQL must already be installed/running locally or be reachable on an approved company database host. Create
database `auth` owned by role `auth_user`; Flyway applies the identity and OAuth2 persistence migrations automatically.
Do not assume the Docker-only host port `5439` is available for a direct local installation.

## Run with Docker

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
```

Compose passes `OAUTH2_DEMO_CLIENT_SECRET` from the ignored local `.env` file, mounts the ignored PKCS12 signing
keystore read-only, and publishes auth-service on `127.0.0.1:8081` for local OAuth2 discovery and browser/Postman
authorization. The gateway remains on `127.0.0.1:8080`.
