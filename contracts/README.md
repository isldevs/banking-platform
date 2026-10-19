# Versioned API/Event Contracts

This repository intentionally has no shared JPA entities. Kafka event type `transfer.completed.v1` and
`transfer.failed.v1` are produced by transfer-service and consumed by notification-service and audit-service. Example in
`transfer-event-v1.json`.
