# Versioned API/Event Contracts

`contracts/` contains deliberately shared wire contracts only. It must never become a shared domain model, JPA entity library, or service implementation dependency.

## Current event contract

- `transfer-event-v1.json` documents the versioned transfer event payload.
- Transfer Service is the producer of the transfer completion/failure event contract.
- Notification Service and Audit Service consume the published events independently.

Changes to an existing event contract should be backward compatible or introduced as a new version. Update the producers, consumers, and this contract documentation together.

For service ownership and event flow, see [Architecture](../docs/wiki/Architecture.md) and [Services](../docs/wiki/Services.md).
