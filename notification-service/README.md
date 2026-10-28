# notification-service

Java 17 / Spring Boot Kafka consumer on port `8085`. Notification Service owns the `notification` database and processes asynchronous events into user-facing in-app notifications.

## Responsibilities

- Consume Kafka events from the platform event stream.
- Persist notification records and processed-event state.
- Expose authenticated notification reads.
- Tolerate at-least-once delivery through event-processing idempotency.

## API

- `GET /api/notifications` — requires `NOTIFICATION_READ` or `TRANSFER_READ` and returns recent notifications for the authenticated user.

## Configuration

Configuration is bundled in `config-service/src/main/resources/config/{dev,qa,stage,prod}/notification-service.yml`. Kafka settings, database credentials, JWT public-key path, and internal credentials are runtime values.

## Run

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env
cd infrastructure
docker compose up --build -d
```

See [Architecture](../docs/wiki/Architecture.md) and [Services](../docs/wiki/Services.md) for the canonical event and service-boundary documentation.
