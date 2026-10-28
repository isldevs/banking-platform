# Security and Operations

> **Development/demo only.** Not approved for production banking, real funds, regulated customer data or production settlement. Roadmap controls are not implemented security measures.

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

Redis-backed fixed-window rate limiting exists at the Gateway, but it is **disabled by default** (`GATEWAY_RATE_LIMIT_ENABLED=false`) in checked-in environment configs. The following values apply **only when enabled**:

| Rule | Default |
|---|---:|
| `POST /api/auth/login` | 5/minute |
| `POST /api/auth/refresh` | 10/minute |
| `POST /api/accounts...` | 10/minute |
| `POST /api/transfers...` | 10/minute |
| Other `/api/**` | 60/minute |

Authenticated requests use JWT subject as the rate-limit identity. Anonymous requests use client IP. Responses expose `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset`; blocked requests return `429` and `Retry-After`.

Development defaults to fail-open on a Redis outage; QA, stage and prod default to fail-closed **if the limiter is enabled**. Document and test the actual desired behavior in each deployment.

## Security and correctness gaps identified in code review

These are static-code findings, **not** outcomes of a completed security audit or test run:

1. Account-opening retries enforce owner/admin authorization and keys are uniquely scoped by the authenticated JWT subject. Migration V4 backfills prior requests from account ownership; legacy admin-initiated retries may need new keys. Same-principal concurrent first attempts still need integration testing to ensure predictable retry responses.
2. Authorized demo funding can transfer from seeded system clearing accounts with `FUNDING_ENABLED=true`. A real bank must require verified settlement evidence and controlled posting authority.
3. Internal `X-Service-Key` is a shared demo mechanism, not strong per-workload identity. Move toward scoped, short-lived credentials and mTLS.
4. `/api/accounts/funding/{instructionId}/post` is technically gateway-routed and protected with a service key. Separate this from end-user channels and authenticate trusted settlement callers more strongly.
5. The implemented paired ledger entries do not yet constitute an enterprise GL, immutable journal subsystem or reversal workflow.
6. Transfer recovery retries ambiguous operations using a stable ID; the future workflow should explicitly verify committed financial operation status and distinguish transient from permanent failures.

## Financial consistency

- Account Service is the financial accounting boundary.
- Ledger postings must balance debit and credit amounts.
- Account mutations use explicit transaction boundaries and concurrency controls.
- Opening with initial funding creates a funding instruction; money is credited only through settlement posting.
- Settlement posting is idempotent by funding instruction/external reference.
- Transfer commands require a retry/idempotency strategy.
- Never delete a posted financial record to correct history. A controlled reversal/compensating operation is **planned**; it is not an implemented customer-facing API yet.
- Audit records are traceability evidence and do not replace reconciliation.

## Observability

Services expose health/info/metrics endpoints according to environment configuration. Use `X-Correlation-Id` to connect logs and cross-service requests where supported.

Never log Authorization headers, passwords, refresh tokens, client secrets, private keys, or service credentials.

## Target enterprise controls (not implemented)

Enhanced KYC/CDD, sanctions and PEP screening, AML/transaction monitoring, fraud decisions, maker-checker, HSM-backed keys, workload identity/mTLS, legal-entity isolation, immutable GL journal, EOD reconciliation, regulated reporting, and tested business continuity/disaster recovery are all **future design work**.

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
- [ ] Run financial concurrency, retry, rollback, recovery and security tests before merging.
- [ ] Add a full banking business-flow CI test with financial and ledger assertions; the current Docker job is a startup smoke test only.

Related pages: [Authentication and OAuth2](Authentication-and-OAuth2.md), [Configuration](Configuration.md), [Development and Delivery](Development-and-Delivery.md).
