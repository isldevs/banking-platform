# Security and Operations

> This is a demo. Do not treat these controls as sufficient for a real banking deployment.

## Authentication and authorization

- New interactive clients use Authorization Code + PKCE through Spring Authorization Server.
- Access tokens are RSA-signed JWTs with a 15-minute lifetime.
- Gateway and protected downstream services validate JWTs independently.
- Downstream services enforce permissions and ownership; never rely only on Gateway authorization.
- Do not trust client-supplied user identity when the authenticated JWT subject is available.

## Refresh tokens and secrets

- Confidential demo-client refresh tokens have a seven-day lifetime and rotate on use.
- Keep refresh tokens and confidential client secrets out of browser/mobile bundles and logs.
- Never commit private signing keys, keystore passwords, database passwords, OAuth client secrets, or `X-Service-Key` values.
- Treat any credential ever committed to Git as compromised and rotate it.
- Use an approved secret manager and managed key boundary for shared/production environments.

## Service-to-service security

`X-Service-Key` is a demo mechanism for trusted internal calls. In a production design, replace or augment it with stronger workload identity, mTLS, or another approved service-authentication mechanism.

Most `/internal/**` endpoints are service-only. The initial-funding settlement route is the exception to the path convention: `/api/accounts/funding/{instructionId}/post` uses the normal Gateway `/api` proxy but still requires `X-Service-Key` and `X-External-Reference` and must not be exposed as a user-facing operation.

## Gateway rate limiting

Redis-backed fixed-window rate limiting protects `/api/**` at the Gateway.

| Rule | Default |
|---|---:|
| `POST /api/auth/login` | 5/minute |
| `POST /api/auth/refresh` | 10/minute |
| `POST /api/accounts...` | 10/minute |
| `POST /api/transfers...` | 10/minute |
| Other `/api/**` | 60/minute |

Authenticated requests use JWT subject as the rate-limit identity. Anonymous requests use client IP. Responses expose `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset`; blocked requests return `429` and `Retry-After`.

Development is configured to fail open on Redis failure. Production-like environments should set this policy explicitly after deciding whether availability or strict abuse protection is the priority.

## Financial consistency

- Account Service is the financial accounting boundary.
- Ledger postings must balance debit and credit amounts.
- Account mutations use explicit transaction boundaries and concurrency controls.
- Opening with initial funding creates a funding instruction; money is credited only through settlement posting.
- Settlement posting is idempotent by funding instruction/external reference.
- Transfer commands require a retry/idempotency strategy.
- Never delete a posted financial record to correct history; use a compensating/reversal workflow when one is implemented.
- Audit records are traceability evidence and do not replace reconciliation.

## Observability

Services expose health/info/metrics endpoints according to environment configuration. Use `X-Correlation-Id` to connect logs and cross-service requests where supported.

Never log Authorization headers, passwords, refresh tokens, client secrets, private keys, or service credentials.

## Operational checklist

Before using a shared environment:

- [ ] Confirm the intended profile: `dev`, `qa`, `stage`, or `prod`.
- [ ] Keep Config Service on a trusted network and out of the public internet.
- [ ] Confirm database, Kafka, and Redis health where applicable.
- [ ] Confirm JWT issuer, audience, signing key and public key alignment.
- [ ] Confirm service credentials are unique and securely injected.
- [ ] Confirm rate-limit behavior and Redis failure policy.
- [ ] Verify financial idempotency, concurrency and reconciliation paths.
- [ ] Verify backups, retention, alerts and incident procedures.
- [ ] Run automated tests and security checks before merge/deployment.

Related pages: [[Authentication-and-OAuth2]], [[Configuration]], [[Development-and-Delivery]].
