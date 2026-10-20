# Security Policy

This repository is an educational demo, not a production banking platform. Do not use its demo credentials, shared
service key, or generated signing keys in production.

## Supported Versions

Only the current `main` branch is maintained for this demo. There are no production support or security SLAs.

## Secrets and signing keys

- Never commit `.env` files, private keys, keystores, access tokens, or passwords.
- JWT signing private keys must stay outside Git, preferably in an approved secret manager or key-management service.
  For local demonstrations, the initialization script generates keys under `infrastructure/secrets/`, which is ignored
  by Git. Only the public key may be distributed to services that validate JWTs.
- The private-key *path* must be supplied at runtime via `SECURITY_JWT_PRIVATEKEYPATH`; do not put it in the
  Git-backed `config-repo/`.
- If a private key is ever committed or exposed, treat it as compromised. Remove it from the current branch, generate
  a replacement keypair, update every service that signs or validates tokens, invalidate/revoke affected tokens where
  possible, and purge the exposed key from Git history. A deletion commit alone does not remove earlier copies from
  repository history.

## Reporting a vulnerability

Please use the repository's **Security** tab to report vulnerabilities privately where GitHub private vulnerability
reporting is enabled. Include affected paths and steps to reproduce, but do not include live credentials or private keys
in an issue.
