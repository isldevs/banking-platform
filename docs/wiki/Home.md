# iSLDevs Banking Platform — Project Wiki

> **Status:** active learning and development. Not production ready, and not for real customer funds or regulated data.

This is the **canonical, version-controlled documentation** for [isldevs/banking-platform](https://github.com/isldevs/banking-platform). It separates implemented banking functionality from the future enterprise banking architecture. Keep detailed API, architecture, configuration, security, and delivery guidance here rather than duplicating it in the root README.

## Navigation

- [Architecture](Architecture.md) — service boundaries, synchronous/asynchronous communication, DDD and Hexagonal Architecture
- [Services](Services.md) — service catalogue, ports, ownership, and API entry points
- [Getting Started](Getting-Started.md) — local prerequisites and startup
- [Authentication and OAuth2](Authentication-and-OAuth2.md) — Authorization Code + PKCE, tokens, and legacy compatibility
- [API Reference](API-Reference.md) — OAuth2 and application API reference
- [Configuration](Configuration.md) — Config Server and runtime configuration contract
- [Security and Operations](Security-and-Operations.md) — security boundaries, rate limiting, secrets, and operational controls
- [Development and Delivery](Development-and-Delivery.md) — Gradle composite build, tests, formatting, and contribution workflow

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

Local Compose infrastructure also includes PostgreSQL (one local instance with separate logical databases), Kafka, and Redis. The current Java toolchain is **17**. A Gradle **8.13** wrapper is committed, while GitHub Actions separately installs Gradle 8.10.2; those build versions should be aligned.

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

## Current versus target banking platform

| Implemented now | Planned, not implemented yet |
| --- | --- |
| OAuth2/OIDC, permissions, Gateway routing | Workload identity, production-grade access governance |
| Customer lifecycle and basic KYC approval | Enhanced CDD, sanctions/PEP screening and AML |
| Account opening, simulated settlement funding, USD/KHR transfers | Payment hub, external clearing, reconciliation and FX |
| Account-owned debit/credit ledger entries | Posting engine, immutable journals, general ledger and reversals |
| Kafka outbox, notifications, transfer audit | SWIFT/ISO 20022, correspondent banking, lending, regulatory reporting |

### Recommended implementation roadmap

1. Harden financial correctness: identity/ownership, idempotency, journal invariants, concurrency and recovery.
2. Extend core accounting: general ledger, holds, reversals, interest/fees and business dates.
3. Add payment and risk controls: approvals, monitoring, domestic clearing and external settlement.
4. Add global banking domains: FX, international payments, legal entities/branches, lending and reporting.

This is an architectural roadmap, not a claim that the target services already exist or that all banks use the same implementation.

## Recommended reading order

1. [Architecture](Architecture.md)
2. [Services](Services.md)
3. [Getting Started](Getting-Started.md)
4. [Authentication and OAuth2](Authentication-and-OAuth2.md)
5. [API Reference](API-Reference.md)
6. [Configuration](Configuration.md)
7. [Security and Operations](Security-and-Operations.md)
8. [Development and Delivery](Development-and-Delivery.md)

The database ownership and Gradle-specific reference documents remain under `docs/` because they cover implementation details rather than duplicating the wiki pages.

## Documentation rule

When an API or architectural behavior changes, update the canonical wiki page and the affected service README only. Do not create another API or architecture document containing the same information.

The separate native GitHub Wiki, if used, must be synchronized deliberately from these versioned files; `docs/wiki/` is the source stored in this repository.
