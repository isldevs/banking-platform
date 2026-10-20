# Banking Microservice Package Standard

## Recommended architecture

This repository uses independent Spring Boot services for separate banking capabilities. The package convention is based on Domain-Driven Design (DDD) and Hexagonal Architecture (Ports and Adapters). This is a practical enterprise pattern, not a claim that every global bank mandates one identical folder standard.

```text
<service>/src/main/java/com/demo/<service>/
├── <Service>Application.java
├── api/
│   ├── controller/       # REST entry points
│   ├── dto/
│   │   ├── request/      # Inbound request DTOs; JSON uses snake_case
│   │   └── response/     # Outbound response DTOs; JSON uses snake_case
│   └── error/            # HTTP error mapping/advice
├── application/
│   ├── port/in/          # Use-case interfaces called by inbound adapters
│   ├── port/out/         # Interfaces for capabilities the application needs
│   └── service/          # Application workflows/use-case orchestration
├── domain/
│   └── model/            # Business concepts and domain state
├── infrastructure/
│   ├── bootstrap/        # Seed/bootstrap wiring
│   ├── messaging/        # Kafka consumers, outbox publishers, messaging adapters
│   ├── observability/    # Correlation IDs and request telemetry
│   └── security/         # Spring Security and authorization-server adapters
└── common/               # Small service-local helpers only
```

## API naming convention

All external REST request and response JSON fields use `snake_case`, while Java field/record component names remain idiomatic `camelCase`. DTOs should use Jackson's `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` so the transport contract is explicit at the API boundary.

Example:

```json
{
  "customer_id": "9f5c7f6e-...",
  "date_of_birth": "1990-01-15",
  "source_account_id": "...",
  "destination_account_id": "..."
}
```

Do not rename Java properties to snake_case merely to satisfy the wire contract. Keep Java code idiomatic and apply the naming strategy at the DTO boundary.

## Service boundaries

- `auth-service`: identity, credentials, OAuth clients, authorization and token issuance.
- `customer-service`: customer profile, KYC status and customer lifecycle.
- `account-service`: account state, balances, ledger and account operations.
- `transfer-service`: transfer lifecycle, idempotency, limits and reconciliation.
- `notification-service`: notification delivery and processed-event tracking.
- `audit-service`: append-oriented audit events and event consumption.
- `gateway-service`: API routing and edge concerns only.
- `config-service`: centralized application configuration and its access controls.

Each service should own its database/schema and business rules. Other services should use versioned APIs or events rather than read/write another service's tables. Avoid sharing domain entities or persistence models between services.

## Banking-specific guardrails

- Keep authorization decisions explicit and testable; do not rely only on gateway routing for access control.
- Preserve idempotency for financial commands, and make duplicate/retry behavior deterministic.
- Keep ledger entries append-oriented and reconcile transfer/account outcomes; do not treat a successful HTTP response as the only record of a financial transaction.
- Use an outbox/inbox or equivalent durable event-handling pattern where database changes and messages must remain consistent.
- Keep signing keys and credentials out of Git. Use a secrets manager or approved key-management service; in production, prefer a managed cryptographic boundary such as a KMS/HSM where available.
- Keep operational controls—correlation IDs, audit events, metrics, health checks and alerting—consistent across services.

## Migration note

This change standardizes package ownership and moves current classes without intentionally changing business behavior or database schemas. Some existing service classes still combine orchestration, SQL, and remote calls. To complete a strict ports-and-adapters design, extract persistence and downstream HTTP/Kafka concerns behind `application/port/out` interfaces and implement those ports under `infrastructure/persistence`, `infrastructure/client`, or `infrastructure/messaging`. Do that incrementally with characterization tests rather than creating empty packages or performing a large behavior rewrite in one step.

## Naming guidance

Retain the existing `com.demo` namespace in this migration rather than inventing a bank-specific Java namespace. For production, replace it with the organization's registered reverse-domain namespace after the official domain and naming policy are confirmed.
