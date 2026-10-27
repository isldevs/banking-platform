# customer-service

Java 21 / Spring Boot service on port `8082`. Customer Service owns the `customer` database and the customer/KYC lifecycle.

## Responsibilities

- Customer profile creation and retrieval.
- Customer status lifecycle.
- KYC approval.
- Internal customer-status lookup for trusted services.

## APIs

- `POST /api/customers`
- `GET /api/customers/{id}`
- `POST /api/customers/{id}/kyc/approve`
- `GET /internal/customers/{id}/status` — service-to-service only with `X-Service-Key`.

Business authorization is enforced by Customer Service; the Gateway is not the only security boundary.

## Configuration

Configuration is bundled in `config-service/src/main/resources/config/{dev,qa,stage,prod}/customer-service.yml`. Database credentials, JWT public-key path, and the internal service key are runtime values.

## Run

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env
cd infrastructure
docker compose up --build -d
```

See [Services](../docs/wiki/Services.md), [API Reference](../docs/wiki/API-Reference.md), and [Getting Started](../docs/wiki/Getting-Started.md) for canonical documentation.
