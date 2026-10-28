# Architecture

## Design goals

The project demonstrates independently built Java 17 / Spring Boot 3.3.x services organized around business capabilities, with DDD and Hexagonal Architecture principles. It is a learning/demo architecture, not a claim that every bank uses the same topology.

## Current system view

This is the **implemented architecture**. OAuth2 protocol endpoints are served by Auth Service; client banking APIs are routed through Gateway. Internal financial calls use HTTP, and notifications/audit use Kafka.

```text
                         Client / Postman / UI
                                  |
                                  v
                         Gateway :8080
                   JWT + routing + rate limit
                                  |
        +-------------------------+-------------------------+
        |                         |                         |
        v                         v                         v
   Auth :8081               Customer :8082             Account :8083
    auth DB                 customer DB             account DB / ledger
        ^                         ^                         ^
        |                         |                         |
        +---- OAuth2/JWT ---------+-------------------------+
                                  |
                                  v
                           Transfer :8084
                            transfer DB
                                  |
                                  v
                               Kafka
                              /     \
                             v       v
                    Notification   Audit
                       :8085         :8086
                       notif DB      audit DB

                       Config :8888
                  native classpath config

                         Redis :6379
                   Gateway rate-limit state
```

Each stateful business service owns a logical PostgreSQL database and its Flyway migrations. The local Compose stack uses a single physical PostgreSQL instance for these databases. No service joins another service's tables.

## Service boundaries

| Context | Owns |
|---|---|
| Identity | users, credentials, OAuth2/OIDC authorization data and token issuance |
| Customer | customer profile and KYC lifecycle |
| Account | accounts, balances, funding instructions, operations and ledger |
| Transfer | transfer workflow, idempotency and outbox state |
| Notification | notification records and processed-event state |
| Audit | audit records and processed-event state |
| Gateway | edge routing, JWT validation, correlation and rate limiting |
| Configuration | Spring Cloud Config classpath configuration |

## Transaction ownership

- **Customer Service:** customer profiles and basic KYC approval, not automated AML/sanctions screening.
- **Account Service:** financial accounting boundary. It posts operation, debit/credit ledger entries, and balance mutations together in one local transaction.
- **Transfer Service:** transfer intent, status, client idempotency, recovery and transactional outbox. It sends the stable financial operation identity to Account Service.
- **Notification and Audit:** at-least-once Kafka consumers, not the ledger or external settlement source of truth.

No distributed database transaction is used between Transfer and Account services.

## Financial flow boundaries

### Account opening

```text
Client
  -> Gateway
  -> Account Service
       validate customer/product/currency
       validate Idempotency-Key
       create account
       if initial deposit > 0:
           create funding instruction
           account = PENDING_FUNDING
       else:
           account = ACTIVE
```

An initial deposit is a funding instruction, not a direct balance mutation.

### Settlement posting

```text
Settlement caller
  -> Gateway / normal /api proxy
  -> POST /api/accounts/funding/{instructionId}/post
       X-Service-Key
       X-External-Reference
       |
       +--> lock funding instruction/account
       +--> validate settlement source/currency/amount
       +--> create financial operation
       +--> create debit + credit ledger entries
       +--> update balances
       +--> mark funding instruction POSTED
       +--> activate PENDING_FUNDING account
       |
       COMMIT
```

The posting operation is idempotent by funding instruction and external reference. A repeated settlement with the same reference returns the existing result; a conflicting reference is rejected.

### Internal transfer and recovery

```text
Client + Idempotency-Key -> Transfer Service (PENDING + idempotency)
 -> Account Service (/internal/accounts/transfer)
 -> lock accounts, validate owner/balance/currency
 -> operation + debit/credit entries + balances (atomic DB commit)
 -> Transfer Service COMPLETED + outbox
 -> Kafka -> Notification / Audit
```

On ambiguous network outcomes, Transfer Service moves to `PENDING_RECONCILIATION` and retries with the **same operation ID**, so Account Service can detect prior posting. Future recovery should explicitly verify the committed operation state rather than classifying timeouts as failures.

## Target global banking architecture (future, **not implemented**)

| Target domain | Intended responsibilities |
| --- | --- |
| Enhanced KYC / AML / fraud | CDD, sanctions/PEP, transaction monitoring, case management, limits and maker-checker |
| Payment orchestration | Payment status, approval, domestic/international routing, rejects and returns |
| Financial posting and GL | Immutable journals, chart of accounts, holds, reversals, accounting periods and reconciliation |
| Clearing and settlement | Domestic rails, settlement confirmation, reconciliation and external accounts |
| Global and treasury | ISO 20022/SWIFT, correspondent banks, FX, Nostro/Vostro, legal entities, branches, booking centers |
| Additional domains | Lending, interest accrual, regulatory reporting, financial operations and EOD controls |

Separate services only at justified business boundaries; do not distribute a single atomic posting transaction across multiple databases. The future target is jurisdiction-dependent.

## Security boundaries

The Gateway validates JWTs at the edge, but it is not the only security boundary. Protected downstream services validate JWTs again and enforce permissions/ownership. `X-Service-Key` is reserved for trusted service-to-service calls.

The settlement-posting route is under `/api/accounts/...` so it can use the existing Gateway proxy, but it is not a public user API because it requires `X-Service-Key`.

## Gateway rate limiting

A Redis-backed fixed-window limiter is implemented, but `GATEWAY_RATE_LIMIT_ENABLED` defaults to **false** in the committed profiles. When enabled, authenticated requests use JWT subject and anonymous requests use client IP. Rate limits and failure policy are configurable.

## Current limitations

- The current account ledger provides paired debit/credit entries, not a complete immutable enterprise general ledger.
- Simulated funding uses seeded local system clearing accounts, not verified external settlement.
- Account-opening retry authorization/scoping and service-to-service shared-key trust need strengthening.
- The current transfer code supports USD and KHR only; international payments, FX and external clearing are not implemented.
- Formal reversal workflows, accounting periods and complete external settlement reconciliation remain roadmap work.

## Consistency and events

- Financial account mutations use explicit database transactions and row/version concurrency controls.
- Ledger postings must remain balanced: total debit equals total credit.
- Financial commands must be idempotent where retries can duplicate money movement.
- Kafka consumers must tolerate at-least-once delivery.
- Audit history is traceability evidence, not the financial source of truth.

## Package convention

A typical service follows:

```text
api/             HTTP adapters, DTOs, errors
application/     use cases and ports
domain/          business models and rules
infrastructure/  persistence, clients, messaging, security
```

Some services are still being refactored toward stricter port/adapter separation; documentation describes the intended boundary without claiming every package is already identical.

Related pages: [Services](Services.md), [Configuration](Configuration.md), [Security and Operations](Security-and-Operations.md), [API Reference](API-Reference.md).
