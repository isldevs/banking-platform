# Architecture

## Design goals

The repository demonstrates independently built Spring Boot services organized around business capabilities, using Domain-Driven Design (DDD) and Hexagonal Architecture (Ports and Adapters). It is a practical architecture example, not a claim that all banks use one universal package standard.

## Logical system view

```text
                          Web / Mobile / API clients
                                    |
                                    v
                          Gateway Service :8080
                           JWT resource-server
                                    |
              +---------------------+---------------------+
              |                     |                     |
              v                     v                     v
       Customer Service       Account Service       Transfer Service
            :8082                  :8083                  :8084
              |                     |                     |
              v                     v                     v
       customer database      account database      transfer database
                                    ^
                                    |
                             internal transfer API

                    Auth / Authorization Server :8081
                     OAuth2 / OIDC + signed JWTs
                              |          |
                              |          +---- JWKS / discovery
                              |
                         auth database

       Transfer and business events ------> Kafka
                                             |
                                +------------+------------+
                                |                         |
                                v                         v
                        Notification Service        Audit Service
                              :8085                      :8086
                                |                         |
                                v                         v
                       notification database        audit database

                  Config Service :8888
             centralized environment configuration
```

The diagram is a logical view of service roles and known dependencies; it is not a deployment topology guarantee for every environment.

## Service boundaries

| Bounded context | Owns |
|---|---|
| Identity and access | User credentials, client registrations, authorization records, token issuance |
| Customer | Customer profile, status, and KYC lifecycle |
| Account | Account state, balances, ledger entries, account operations |
| Transfer | Transfer workflow, transfer idempotency, and recovery state |
| Notification | Notification records and processed delivery state |
| Audit | Transfer audit history and audit-event consumption |
| Gateway | Edge security and request entry concerns |
| Configuration | Application configuration and Config Server access control |

Services should not read or write another service's database tables. Cross-context interactions should use APIs or events with explicit contracts.

## Request and event patterns

- **Synchronous API calls** are used for user-facing queries and commands, and for internal operations that require a response.
- **JWT bearer authentication** carries the authenticated user's identity and permissions across service boundaries.
- **Kafka events** support asynchronous downstream work such as notifications and audit history.
- **Idempotency** is important for financial commands because clients and infrastructure may retry requests.
- **Account ledger changes** belong to Account Service; Transfer Service coordinates the transfer lifecycle without becoming the ledger's data owner.

Internal HTTP endpoints such as `/internal/accounts/transfer` and `/internal/customers/{id}/status` require service-to-service credentials and must not be exposed as public browser/mobile APIs.

## DDD and Hexagonal package convention

A typical service package uses these layers:

```text
com.demo.<service>/
├── api/
│   ├── controller/
│   ├── dto/request/
│   ├── dto/response/
│   ├── mapper/
│   └── error/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── service/
├── domain/
│   ├── model/
│   ├── repository/
│   └── service/
├── infrastructure/
│   ├── persistence/
│   ├── client/
│   ├── messaging/
│   ├── security/
│   └── observability/
└── common/
```

- **API adapters** translate HTTP requests and responses.
- **Application services** orchestrate use cases.
- **Inbound ports** define use cases called by controllers or messaging adapters.
- **Outbound ports** define capabilities the application needs from storage, remote services, or messaging.
- **Domain objects and rules** should not depend on HTTP, SQL, or Kafka.
- **Infrastructure adapters** implement persistence, external clients, messaging, and technical security.

Some current services still combine SQL and orchestration. Refactor incrementally behind ports with characterization tests rather than moving code without verifying behavior.

## Data and consistency

Each service has a separate logical PostgreSQL database. Transfer-to-account interactions must be idempotent and recoverable; an HTTP success response alone is not sufficient evidence that a financial workflow has been reconciled. The project uses an outbox-oriented transfer design and Kafka for asynchronous event delivery.

## Related pages

- [[Services]]
- [[Configuration]]
- [[Security-and-Operations]]
- [[API-Reference]]
