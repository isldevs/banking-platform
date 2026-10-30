# Complete API collection

`banking-platform.postman_collection.json` is an executable Postman v2.1 collection for every configured endpoint in this repository. It covers 121 endpoint contracts with 152 defined requests. A normal live run can execute more requests while bounded audit and notification polling waits for asynchronous delivery.

All configuration and captured state use collection variables. The repository contains no Postman environment file, and the collection scripts reject environment, global and iteration-data scopes. The checked-in secret variables are empty.

## Coverage

The collection exercises:

- all 22 application controller contracts, including onboarding, KYC, account opening and funding settlement, idempotent transfer posting, balanced ledgers, account administration, audit and notification delivery;
- 16 configured OAuth 2.0, OpenID Connect and browser-session contracts, including Authorization Code with PKCE, UserInfo, refresh rotation, introspection, revocation and RP-initiated logout;
- all 19 Config Server read and encryption contracts;
- eight management routes on each of the eight services, for 64 management contract instances.

The exact counts and intentionally unavailable framework capabilities are recorded in `endpoint-coverage.json`. Device authorization, dynamic client registration, password/client-credentials grants, unmapped gateway methods, unexposed Actuator endpoints and absent Swagger routes are excluded because the application does not configure them.

## Automated run

From the repository root:

```bash
bash infrastructure/scripts/setup-postman.sh
cd infrastructure
docker compose -f docker-compose.yml -f docker-compose.postman.yml up --build -d
cd ..
npm ci --prefix docs/postman
python3 infrastructure/scripts/check-postman.py
npm test --prefix docs/postman
```

The setup helper creates local demo server credentials when `infrastructure/.env` is absent and prepares a separate Config Server encryption key. It refuses to overwrite existing application credentials or signing keys. `docker-compose.postman.yml` exposes the internal services on loopback, enables demo funding and mounts the test-only encryption key.

The Newman runner reads five server settings from `infrastructure/.env` and copies them into the in-memory collection variables: administrator username/password, internal service key, OAuth client secret and expected issuer. The file is application server configuration, not a Postman environment. The runner does not create or pass Newman environment, global or iteration-data objects, does not write populated collection values, and stores a sanitized summary in `.local/postman-summary.json` with mode `600`.

Stop the test stack with:

```bash
cd infrastructure
docker compose -f docker-compose.yml -f docker-compose.postman.yml down --volumes --remove-orphans
```

## Postman Desktop

Import `banking-platform.postman_collection.json`, start the stack with the Postman Compose override, and set the empty secret collection variables in your local Current Value column. Keep those values local and never export or commit them. No Postman environment should be selected.

The workflow generates unique users, request IDs and idempotency keys on each run. Business and management endpoints must return HTTP 200. OAuth authorization and logout operations deliberately assert their protocol redirects as HTTP 302; the remaining OAuth operations return HTTP 200.

Validate the checked-in collection after endpoint changes:

```bash
python3 infrastructure/scripts/check-postman.py
```
