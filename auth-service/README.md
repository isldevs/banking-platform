# auth-service

Independent Java 21 / Spring Boot service on port `8081` with its own `auth` database.

## Centralized configuration

The service uses a mandatory Spring Cloud Config import and loads safe defaults from `config-repo/auth-service.yml` plus common settings from `config-repo/application.yml`. In Docker, the `docker` profile selects `config-repo/auth-service-docker.yml` and `config-repo/application-docker.yml`. Database passwords, JWT key paths, the issuer override, and demo login passwords are injected through environment variables and are never stored in Git.

## OAuth2 / OIDC demo

Spring Authorization Server exposes standard Authorization Code + PKCE endpoints while the existing `/api/auth/login` and `/api/auth/refresh` endpoints remain available for the repository's automated demo flow.

- Issuer: `http://localhost:8081`
- Discovery: `http://localhost:8081/.well-known/openid-configuration`
- Authorization endpoint: `http://localhost:8081/oauth2/authorize`
- Token endpoint: `http://localhost:8081/oauth2/token`
- JWKS endpoint: `http://localhost:8081/oauth2/jwks`
- Public demo client ID: `demo-browser`
- Grant: Authorization Code + Refresh Token, with PKCE required
- Registered redirect URI for Postman: `https://oauth.pstmn.io/v1/callback`

Use the seeded `demo` or `admin` username and read its password from `infrastructure/.env`. In Postman, configure OAuth 2.0 Authorization Code (With PKCE), leave the client secret empty, use the endpoint URLs above, and choose the registered Postman callback.

For compatibility, access tokens from both the new OAuth2 flow and the legacy demo login contain `sub`, `username`, `roles`, `permissions`, `iss`, and `aud` claims understood by the current gateway/resource-server configuration.

**Demo limitation:** registered client configuration and OAuth2 authorization state use in-memory stores. Restarting auth-service invalidates active OAuth2 authorization codes and refresh tokens. Use durable authorization storage, external client registration, key rotation, HTTPS, and a supported dependency line before production.

## Run

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
```

The Compose deployment publishes auth-service on `127.0.0.1:8081` for local OAuth2 discovery and browser/Postman authorization. The gateway remains on `127.0.0.1:8080`.
