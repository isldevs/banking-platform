# audit-service

Java 17 / Spring Boot Kafka consumer on port `8086`. Audit Service owns the `audit` database and records transfer-related audit events for traceability.

## Responsibilities

- Consume relevant Kafka events.
- Persist append-oriented audit records.
- Track processed events for at-least-once delivery handling.
- Provide transfer audit history to authorized callers.

## API

- `GET /api/audit/transfers/{id}` — requires `AUDIT_READ`.

Audit data is evidence for traceability; it is not a replacement for financial reconciliation or the Account Service ledger.

## Configuration

Configuration is bundled in `config-service/src/main/resources/config/{dev,qa,stage,prod}/audit-service.yml`. Kafka settings, database credentials, JWT public-key path, and internal credentials are runtime values.

## Run

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env
cd infrastructure
docker compose up --build -d
```

See [Architecture](../docs/wiki/Architecture.md), [Services](../docs/wiki/Services.md), and [Security and Operations](../docs/wiki/Security-and-Operations.md).
