# account-service

Java 17 / Spring Boot service on port `8083`. Account Service owns the `account` database and is the financial accounting boundary for customer accounts, balances, funding, operations, and ledger entries.

## Responsibilities

- Product-driven account opening with `Idempotency-Key` unique per authenticated principal (database migration V4).
- Account lifecycle and ownership.
- Available and ledger balances.
- Initial funding instructions and settlement posting.
- Debit/credit operations and double-entry ledger entries.
- Account freeze and internal service operations.
- Concurrency control around financial mutations.

## Important flow

An account opened with an initial deposit is created as `PENDING_FUNDING`. The request creates a durable funding instruction; it does not directly create money or mutate the customer balance. A trusted settlement caller posts the instruction with `X-Service-Key` and `X-External-Reference`. Posting is idempotent and activates the account after the financial operation and ledger entries commit successfully.

An external settlement reference can be consumed by only one funding instruction per channel and currency. Retrying that same instruction/reference returns the previous result; using it for another instruction returns `409 EXTERNAL_REFERENCE_ALREADY_USED`. A transaction-scoped lock coordinates concurrent requests and migration V5 enforces uniqueness in PostgreSQL. This demo has no provider identifier; introduce an explicit provider/settlement-source identity before supporting multiple providers on one channel. V5 stops with reconciliation guidance if historical duplicate references exist; it does not delete or reverse financial history automatically.

## Public APIs

- `POST /api/accounts`
- `GET /api/accounts`
- `GET /api/accounts/{id}`
- `POST /api/accounts/{id}/freeze`
- `POST /api/accounts/{id}/fund`
- `GET /api/accounts/ledger/{operationId}`

The settlement-posting route is `/api/accounts/funding/{instructionId}/post`; it is protected by `X-Service-Key` even though its path is under `/api` so the existing Gateway API proxy can route it. It is not a browser/mobile endpoint.

Internal service endpoints also include `/internal/accounts/transfer` and `/internal/accounts/operations/{id}`.

## Configuration

Configuration is supplied by Config Service from `config-service/src/main/resources/config/{dev,qa,stage,prod}/account-service.yml`. Runtime values such as database credentials, JWT public-key paths, and the internal service key come from the environment.

## Run

Full local stack:

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env
cd infrastructure
docker compose up --build -d
```

Or build/test this service directly from its directory with the committed Gradle wrapper:

```bash
cd account-service
../gradlew test
../gradlew build
```

See [Getting Started](../docs/wiki/Getting-Started.md), [Account/API reference](../docs/wiki/API-Reference.md), and [Database ownership](../docs/DATABASES.md) for the canonical documentation.
