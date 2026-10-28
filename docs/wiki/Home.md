# iSLDevs Microservices Platform — Project Wiki

> **Status:** learning/demo project, not a production-ready banking platform.

This is the canonical versioned project guide. Keep detailed API, architecture, configuration, security, and development guidance here instead of duplicating it in multiple top-level documents.

## Navigation

- [[Architecture]] — service boundaries, synchronous/asynchronous communication, DDD and Hexagonal Architecture
- [[Services]] — service catalogue, ports, ownership, and API entry points
- [[Getting-Started]] — local prerequisites and startup
- [[Authentication-and-OAuth2]] — Authorization Code + PKCE, tokens, and legacy compatibility
- [[API-Reference]] — OAuth2 and application API reference
- [[Configuration]] — Config Server and runtime configuration contract
- [[Security-and-Operations]] — security boundaries, rate limiting, secrets, and operational controls
- [[Development-and-Delivery]] — Gradle composite build, tests, formatting, and contribution workflow

## Platform at a glance

| Service | Port | Primary responsibility |
|---|---:|---|
| Gateway | 8080 | API entry point, JWT validation, Redis rate limiting and routing |
| Auth | 8081 | OAuth2/OIDC Authorization Server and identity |
| Customer | 8082 | Customer and KYC lifecycle |
| Account | 8083 | Accounts, funding, balances, operations and ledger |
| Transfer | 8084 | Transfer workflow, idempotency, recovery and Kafka outbox |
| Notification | 8085 | Kafka-driven user notifications |
| Audit | 8086 | Kafka-driven audit history |
| Config | 8888 | Native Spring Cloud Config Server |

Local Compose infrastructure also includes PostgreSQL, Kafka, and Redis.

## Core banking flow

```text
Client
  |
  v
Gateway :8080
  |
  +--> Auth :8081 ----> auth DB
  +--> Customer :8082 -> customer DB
  +--> Account :8083 --> account DB / ledger
  +--> Transfer :8084 -> transfer DB / outbox
                           |
                           v
                         Kafka
                       /       \
                      v         v
               Notification    Audit
                 :8085          :8086

Config Service :8888 supplies non-secret application configuration.
Redis supports Gateway API rate limiting.
```

Account Service is the financial accounting boundary. Transfer Service owns transfer workflow state, while Notification and Audit consume asynchronous events.

## Recommended reading order

1. [[Architecture]]
2. [[Services]]
3. [[Getting-Started]]
4. [[Authentication-and-OAuth2]]
5. [[API-Reference]]
6. [[Configuration]]
7. [[Security-and-Operations]]
8. [[Development-and-Delivery]]

The database ownership and Gradle-specific reference documents remain under `docs/` because they cover implementation details rather than duplicating the wiki pages.

## Documentation rule

When an API or architectural behavior changes, update the canonical wiki page and the affected service README only. Do not create another API or architecture document containing the same information.

The separate native GitHub Wiki, if used, must be synchronized deliberately from these versioned files; `docs/wiki/` is the source stored in this repository.
