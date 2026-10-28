# transfer-service

Java 21 / Spring Boot service on port `8084`. Transfer Service owns the `transfer` database and the transfer workflow boundary.

## Responsibilities

- Transfer creation and lookup.
- Client idempotency.
- Transfer lifecycle and recovery state.
- Account-service financial-operation orchestration.
- Kafka outbox events for downstream processing.

## APIs

- `POST /api/transfers` — requires `TRANSFER_CREATE`; accepts optional `Idempotency-Key`.
- `GET /api/transfers/{id}` — requires `TRANSFER_READ`.

The authenticated user comes from the JWT subject. Clients must not use request-body identity fields to impersonate another user.

## Data ownership

Transfer Service owns transfer workflow state, idempotency records, status history, and outbox events. Account Service owns customer account balances and ledger entries. Transfer Service must not query the account database.

## Configuration

Configuration is bundled in `config-service/src/main/resources/config/{dev,qa,stage,prod}/transfer-service.yml`. Database credentials, Kafka bootstrap settings, JWT public-key path, and internal credentials are runtime values.

## Run

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env
cd infrastructure
docker compose up --build -d
```

See [Architecture](../docs/wiki/Architecture.md), [Services](../docs/wiki/Services.md), and [API Reference](../docs/wiki/API-Reference.md).
