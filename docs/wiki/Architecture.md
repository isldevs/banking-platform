# Architecture

## Design goals

The project demonstrates independently built Java 21 / Spring Boot services organized around business capabilities, with DDD and Hexagonal Architecture principles. It is a learning/demo architecture, not a claim that every bank uses the same topology.

## System view

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

Each business service owns its database. Cross-service access is through explicit APIs or events; no service queries another service's tables.

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

### Transfer

Transfer Service owns the workflow and idempotency record. Account Service owns the actual financial operation, balance mutation, and ledger. Kafka outbox events are emitted from the Transfer Service for asynchronous consumers.

## Security boundaries

The Gateway validates JWTs at the edge, but it is not the only security boundary. Protected downstream services validate JWTs again and enforce permissions/ownership. `X-Service-Key` is reserved for trusted service-to-service calls.

The settlement-posting route is under `/api/accounts/...` so it can use the existing Gateway proxy, but it is not a public user API because it requires `X-Service-Key`.

## Gateway rate limiting

Redis-backed fixed-window rate limiting runs at the Gateway before requests reach business services. Authenticated requests use JWT subject as identity; anonymous requests use client IP. Limits are configurable by environment.

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
 domain/         business models and rules
infrastructure/  persistence, clients, messaging, security
```

Some services are still being refactored toward stricter port/adapter separation; documentation describes the intended boundary without claiming every package is already identical.

Related pages: [[Services]], [[Configuration]], [[Security-and-Operations]], [[API-Reference]].
