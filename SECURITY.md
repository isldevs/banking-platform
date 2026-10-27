# Security Policy

This repository is an educational/demo banking platform. It is not approved for production banking use. Do not reuse demo credentials, demo service keys, or generated signing keys in production.

## Supported versions

Only the current `main` branch is maintained for this demo. There are no production support commitments or security SLAs.

## Secrets and signing keys

- Never commit `.env` files, private keys, keystores, access tokens, passwords, OAuth client secrets, or internal service keys.
- Local Docker signing material belongs under ignored `infrastructure/secrets/`.
- Host-based local signing material, when used, must also remain outside Git.
- Auth Service receives its JWT signing keystore path/password/alias at runtime. Resource services receive the public-key path at runtime.
- Config Service stores configuration structure and non-secret defaults; secret values must come from the runtime environment or an approved secret-management system.
- If a private key or credential is ever committed or exposed, treat it as compromised. Rotate it, revoke affected credentials/tokens where applicable, and remove the secret from repository history using an approved remediation process. A deletion commit alone does not erase historical copies.

## Security boundaries

- Gateway performs edge JWT validation and rate limiting.
- Protected downstream services validate JWTs again and enforce their own permissions/ownership.
- `X-Service-Key` is a demo service-to-service credential and must never be exposed to browser/mobile clients.
- Redis-backed Gateway rate limiting is part of the current demo architecture; its failure policy must be reviewed before shared/production deployment.

## Reporting a vulnerability

Use the repository's **Security** tab for private vulnerability reporting when GitHub private reporting is enabled. Include affected paths and reproduction steps, but never include live credentials, tokens, private keys, or other secrets in reports.
