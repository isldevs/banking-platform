# Microservices Platform — Project Wiki

> **Status:** learning/demo project, not a production-ready banking platform.

This wiki is the project guide for the Java 21 / Spring Boot microservices demo. It covers the architecture, service
responsibilities, local development, OAuth2/OIDC authentication, API endpoints, configuration, security, and delivery
workflow.

## Wiki navigation

- [[Architecture]] — system context, service communication, DDD and Hexagonal Architecture
- [[Services]] — service catalogue, ports, databases, and responsibilities
- [[Getting-Started]] — prerequisites and local startup
- [[Authentication-and-OAuth2]] — Spring Authorization Server, PKCE, access tokens, and refresh tokens
- [[API-Reference]] — complete REST and OAuth2 API reference
- [[Configuration]] — Config Server, environment profiles, and configuration layout
- [[Security-and-Operations]] — secrets, token security, health checks, and operational risks
- [[Development-and-Delivery]] — Gradle build, tests, and contribution workflow

## Project at a glance

| Service              |                 Local port | Primary responsibility                                        |
|----------------------|---------------------------:|---------------------------------------------------------------|
| Gateway              |                       8080 | Public entry point and JWT resource-server validation         |
| Auth Service         |                       8081 | OAuth2/OIDC Authorization Server, identity and token issuance |
| Customer Service     |                       8082 | Customer profiles and KYC lifecycle                           |
| Account Service      |                       8083 | Accounts, funding, balances, and ledger operations            |
| Transfer Service     |                       8084 | Transfer workflow, idempotency, and recovery                  |
| Notification Service |                       8085 | User notifications from asynchronous events                   |
| Audit Service        |                       8086 | Transfer audit history                                        |
| Config Service       |                       8888 | Central configuration for application services                |
| PostgreSQL           |  host port 5439 in Compose | Separate logical databases for each application service       |
| Kafka                | host port 29092 in Compose | Event transport for asynchronous workflows                    |

## Architecture summary

- Each business microservice owns its data and domain rules.
- Auth Service issues signed JWT access tokens; resource servers validate issuer, audience, signature, and expiry.
- New interactive clients should use **Authorization Code + PKCE** with Spring Authorization Server.
- The custom `/api/auth/login`, `/api/auth/refresh`, and `/api/auth/logout` endpoints are retained as deprecated
  compatibility endpoints.
- Transfer workflow and account ledger responsibilities are kept in separate bounded contexts.
- Kafka carries asynchronous events used by downstream notification and audit processing.
- Configuration files are bundled in Config Service under `config/{dev,qa,stage,prod}/`.

## Start here

1. Read [[Architecture]] and [[Services]] to understand service boundaries.
2. Follow [[Getting-Started]] to run the demo on an approved local environment.
3. Follow [[Authentication-and-OAuth2]] before connecting a client.
4. Use [[API-Reference]] for endpoints, permissions, headers, and request examples.

Source repository: https://github.com/isldevs/microservice-platform-demo

## Important limitation

This is a demonstration codebase. A production banking platform additionally needs organization-approved identity
controls, managed key storage, TLS and service identity, formal threat modeling, operational support, compliance
controls, resilience validation, and end-to-end integration testing.
