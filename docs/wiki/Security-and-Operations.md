# Security and Operations

## Security baseline

This project is a demo and should not be deployed as a real banking system without substantial additional controls.

### Access tokens

- Use Spring Authorization Server's Authorization Code + PKCE flow for interactive login.
- Access tokens are RSA-signed JWTs with a 15-minute lifetime.
- Resource servers validate the issuer, audience, signature, and expiration.
- Keep bearer tokens out of application logs and error reports.
- Use HTTPS in shared or deployed environments.
- Do not trust client-supplied user IDs when the authenticated JWT subject is available.

### Refresh tokens and client credentials

- The confidential demo client receives refresh tokens with a seven-day lifetime and token rotation.
- Keep refresh tokens in a trusted server-side store or approved secure credential store.
- Do not ship a confidential client secret with a browser or mobile application.
- Replace the stored refresh token after every successful refresh; do not replay the consumed token.
- OAuth2 and legacy custom refresh-token stores are separate.
- Revoke OAuth2 tokens at `POST /oauth2/revoke`; use the deprecated logout endpoint only for legacy tokens.

### Signing keys and secrets

- Never commit private signing keys, keystore passwords, client secrets, database passwords, or internal service keys.
- Locally generated signing material is stored under ignored `infrastructure/secrets/`.
- `infrastructure/.env` is a local secrets file and must remain ignored.
- Any signing key or credential that was committed to Git at any time should be treated as compromised and rotated. Removing it from the current tree does not erase it from repository history.
- Production deployments should use the organization's approved secret manager and a managed key-management boundary where available.

### Internal endpoints

Endpoints under `/internal/**` are service-to-service APIs. They must not be exposed directly to browser/mobile clients. Requests must carry `X-Service-Key`; in deployed production, use stronger service identity controls such as mTLS or an approved workload identity mechanism.

## Financial consistency

- Account Service owns the account ledger and the account database.
- Transfer Service owns transfer workflow and idempotency state.
- Financial commands must be idempotent or protected with an explicit idempotency strategy.
- Use durable event handling and reconcile account/transfer state; a successful HTTP response alone is not the only audit record.
- Audit data should remain append-oriented and traceable.
- Do not allow downstream services to access each other's tables directly.

## Observability

Application configuration exposes health/info and selected metrics endpoints. Confirm the exact actuator endpoint exposure and authorization behavior in the active environment before exposing monitoring publicly.

Use `X-Correlation-Id` where supported to link logs and service requests. Avoid logging authorization headers, passwords, token payloads, refresh tokens, and client secrets.

## Operational checklist

Before using a shared environment:

- [ ] Confirm correct environment profile (`dev`, `qa`, `stage`, or `prod`).
- [ ] Confirm Config Server is reachable only from the intended trusted network; this demo does not require Config Server credentials.
- [ ] Confirm all database URLs and credentials are supplied securely.
- [ ] Confirm JWT issuer, audience, signing key, and public key match.
- [ ] Confirm internal service credentials are unique and supplied securely.
- [ ] Confirm Postgres and Kafka health and persistence.
- [ ] Confirm transfer retries, idempotency, and reconciliation behavior.
- [ ] Confirm backups, retention/cleanup, alerts, and incident procedures.
- [ ] Run automated tests and security checks; do not assume local build success proves production readiness.

Related pages: [[Authentication-and-OAuth2]], [[Configuration]], [[Development-and-Delivery]].
