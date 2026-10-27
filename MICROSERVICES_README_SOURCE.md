# Microservices Platform

A from-scratch, production-oriented backend learning project built as **independent Spring Boot microservices**, each
with its own Git repository and database ownership.

This project is designed for a restricted company environment: no Keycloak, no Auth0, no cloud-managed infrastructure,
and no dependency on external SaaS. PostgreSQL is the primary database. Infrastructure can run locally or on
company-managed servers.

---

# 1. Goals

Build a realistic platform while learning the complete backend lifecycle:

- Authentication and authorization
- OAuth 2.0 / JWT concepts without Keycloak
- User and role management
- Customer and KYC lifecycle
- Account lifecycle
- Double-entry ledger
- Balance management
- Internal transfers
- Idempotency
- Concurrency control
- Transaction boundaries
- Outbox pattern
- Kafka event processing
- Notification
- Audit trail
- Configuration as a service
- API gateway
- Service-to-service security
- Observability
- Resilience
- Performance engineering
- Testing
- CI/CD
- Failure handling

The goal is not to create the largest possible system. The goal is to understand **why each production component exists
and what failure it protects against**.

---

# 2. Non-Negotiable Architecture Rules

## Rule 1: Every runtime service is independent

Each service has:

```text
own Git repository
own Gradle build
own Spring Boot application
own port
own deployment lifecycle
own database ownership
own Flyway migrations
own tests
```

Do **not** make one multi-module monorepo containing all runtime services.

## Rule 2: Database ownership is strict

A service must never query another service's database.

```text
auth-service       -> auth
customer-service   -> customer
account-service    -> account
transfer-service   -> transfer
notification       -> notification
audit-service      -> audit
config-service     -> config
```

Communication between services happens through APIs or events.

## Rule 3: No Keycloak

Authentication and authorization are implemented using Spring Security and Spring Authorization Server
concepts/components where appropriate.

We do not outsource identity to Keycloak, Auth0, Okta, Cognito, or another external identity platform.

## Rule 4: No secrets in Git

Passwords, private keys, signing keys, encryption keys, database credentials, and client secrets are injected through
the environment or an approved internal secret mechanism.

## Rule 5: Gateway is not the only security boundary

The gateway performs edge-level security and routing, but each protected service validates the caller and enforces its
own authorization/business rules.

---

# 3. Final Repository Layout

Create these as separate Git repositories:

```text
config-service
gateway-service
auth-service
customer-service
account-service
transfer-service
notification-service
audit-service
contracts
infrastructure
```

`contracts` is optional and should contain only deliberately shared API/event contracts. It must not become a shared
domain model or shared JPA entity library.

`infrastructure` contains local/company infrastructure definitions such as PostgreSQL/Kafka configuration, scripts,
certificates for local development, and deployment manifests where permitted.

---

# 4. Final System Architecture

```text
                                  Client / UI
                                      |
                                      v
                           +----------------------+
                           |      gateway         |
                           |       :8080          |
                           +----------+-----------+
                                      |
             +------------------------+------------------------+
             |                        |                        |
             v                        v                        v
   +----------------+       +----------------+       +----------------+
   | auth-service   |       | customer       |       | account        |
   |     :8081      |       | service :8082  |       | service :8083  |
   +-------+--------+       +-------+--------+       +-------+--------+
           |                        |                        |
           v                        v                        v
          auth                   customer                  account

                                      |
                                      v
                           +----------------------+
                           | transfer-service     |
                           |       :8084          |
                           +----------+-----------+
                                      |
                                      v
                                   transfer
                                      |
                                      v
                                outbox_events
                                      |
                                      v
                                    Kafka
                                  /       \\
                                 v         v
                       notification       audit
                        :8085             :8086
                           |                 |
                           v                 v
                    notification            audit

                 +-----------------------------+
                 | config-service :8888         |
                 |             config           |
                 +-----------------------------+
```

Later, observability infrastructure can be added independently:

```text
Services
   |
   +--> Micrometer / OpenTelemetry
            |
            +--> Prometheus
            +--> Grafana
            +--> tracing backend
```

---

# 5. Port Allocation

```text
gateway-service        8080
auth-service           8081
customer-service       8082
account-service        8083
transfer-service       8084
notification-service   8085
audit-service          8086
config-service         8888
```

Do not hard-code production ports into application code. The examples are local-development defaults.

---

# 6. Database Allocation

```text
config
└── configurations

auth
├── users
├── user_credentials
├── roles
├── permissions
├── user_roles
├── role_permissions
├── refresh_tokens
└── login_attempts

customer
├── customers
├── customer_addresses
├── customer_kyc
└── customer_status_history

account
├── accounts
├── account_holders
├── account_balances
├── account_transactions
└── ledger_entries

transfer
├── transfers
├── transfer_items
├── transfer_status_history
├── idempotency_keys
└── outbox_events

notification
└── notifications

audit
└── audit_events
```

Each database may contain supporting tables such as Flyway metadata and service-specific technical tables.

---

# 7. Service Responsibilities

## 7.1 config-service

Purpose:

```text
Central application configuration
Configuration versioning
Environment-specific values
Configuration access
Future encryption
Future rollback
Future configuration audit
```

Owns:

```text
config
```

API:

```http
GET /api/config/{service}/{environment}
POST /api/config
```

Example:

```http
GET /api/config/auth-service/local
```

Response:

```json
{
  "service": "auth-service",
  "environment": "local",
  "properties": {
    "server.port": "8081",
    "jwt.access-token.ttl": "900",
    "jwt.refresh-token.ttl": "604800"
  }
}
```

Important bootstrap rule:

```text
config-service cannot load its own DB password from config-service
before it can connect to its own DB.
```

Therefore its bootstrap connection is supplied externally:

```text
CONFIG_DB_URL
CONFIG_DB_USERNAME
CONFIG_DB_PASSWORD
SERVER_PORT
```

---

# 8. auth-service

Purpose:

```text
User identity
Authentication
Credentials
Password hashing
Access token issuance
Refresh token lifecycle
Logout/revocation
Roles
Permissions
Authentication audit
```

Owns:

```text
auth
```

## Tables

### users

```text
id UUID PK
username
email
status
failed_login_count
locked_until
created_at
updated_at
```

### user_credentials

```text
id UUID PK
user_id UUID FK
password_hash
password_changed_at
created_at
updated_at
```

Never store plaintext passwords.

Use a password hashing algorithm supported by Spring Security, such as BCrypt/Argon2 according to the chosen security
baseline.

### roles

```text
id UUID PK
name
created_at
```

### permissions

```text
id UUID PK
code
description
created_at
```

Examples:

```text
USER_READ
USER_MANAGE
CUSTOMER_READ
ACCOUNT_READ
ACCOUNT_FREEZE
TRANSFER_CREATE
TRANSFER_READ
TRANSFER_REVERSE
AUDIT_READ
CONFIG_READ
CONFIG_MANAGE
```

### user_roles

```text
user_id
role_id
```

### role_permissions

```text
role_id
permission_id
```

### refresh_tokens

Store a hash or otherwise protected representation of refresh tokens rather than plaintext reusable credentials where
the chosen design permits it.

```text
id
user_id
token_hash
expires_at
revoked_at
replaced_by
created_at
```

### login_attempts

```text
id
user_id / username
success
ip_address
user_agent
created_at
```

## Authentication flow

```text
Client
  |
  | username + password
  v
Auth Service
  |
  +--> find user
  +--> check status
  +--> check lockout
  +--> verify password hash
  +--> load roles/permissions
  +--> issue access token
  +--> issue refresh token
  v
Client
```

Access tokens should be short-lived. Refresh tokens should be longer-lived, revocable, and rotated.

## Endpoints

```http
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

## JWT

Recommended claims:

```json
{
  "iss": "http://localhost:8081",
  "sub": "user-id",
  "aud": "api",
  "scope": "account.read transfer.create",
  "roles": ["USER"],
  "permissions": ["ACCOUNT_READ", "TRANSFER_CREATE"],
  "iat": 1790000000,
  "exp": 1790000900
}
```

Do not put sensitive customer/account data into JWTs.

JWTs are signed/encoded, not confidential encrypted storage.

Spring Security supports JWT resource-server validation and custom JWT validation. citeturn0search5turn0search8

---

# 9. gateway-service

Purpose:

```text
Single client entry point
Routing
CORS policy
Authentication at edge
Correlation ID
Rate limiting
Request filtering
Observability
```

Example routes:

```text
/api/auth/**      -> auth-service
/api/customers/** -> customer-service
/api/accounts/**  -> account-service
/api/transfers/** -> transfer-service
```

The gateway must not contain business logic.

It must not become a second monolith.

Security model:

```text
Gateway validates token
        |
        v
Downstream service validates token again
        |
        v
Downstream service performs business authorization
```

---

# 10. customer-service

Purpose:

```text
Customer profile
Customer lifecycle
KYC state
Customer status
Customer addresses
```

Owns:

```text
customer
```

## customers

```text
id UUID PK
customer_number
user_id
first_name
last_name
date_of_birth
phone
email
status
created_at
updated_at
```

Possible statuses:

```text
PENDING_KYC
ACTIVE
SUSPENDED
BLOCKED
CLOSED
```

## customer_kyc

```text
id
customer_id
kyc_level
verification_status
verified_at
verified_by
created_at
updated_at
```

KYC rules must be explicit. Do not allow account/transfer behavior to depend on hidden UI logic.

---

# 11. account-service

Purpose:

```text
Account opening
Account status
Account ownership
Balance
Account transactions
Ledger
Debit/credit operations
Account freeze/unfreeze
```

Owns:

```text
account
```

## accounts

```text
id UUID PK
account_number
currency
status
customer_id
version
created_at
updated_at
```

## account_balances

```text
account_id PK
ledger_balance
available_balance
updated_at
```

Use `BigDecimal` for money. Never use `double` or `float` for monetary values.

## account_transactions

```text
id UUID PK
account_id
transaction_type
amount
currency
reference_id
status
created_at
```

## ledger_entries

```text
id UUID PK
transaction_id
account_id
entry_type
amount
currency
created_at
```

Entry types:

```text
DEBIT
CREDIT
```

---

# 12. Double-Entry Ledger Rule

A transfer of 100 USD:

```text
Account A
DEBIT  100 USD

Account B
CREDIT 100 USD
```

The invariant is:

```text
Total Debit == Total Credit
```

Never implement a transfer as only:

```text
A.balance -= 100
B.balance += 100
```

without a corresponding ledger trail.

The ledger is the accounting record. The balance is an operational representation that must remain consistent with the
accounting model.

---

# 13. Account Concurrency

Two requests can arrive simultaneously:

```text
Request A: debit 100
Request B: debit 100
```

If the account has only 150 available:

```text
Only one request should succeed.
```

Possible techniques:

```text
optimistic locking
pessimistic row locking
serializable transaction
```

Start with an explicit `version` column and optimistic locking where appropriate. Use database locking where the
invariant requires serialization.

Never assume Java synchronization can protect a distributed service.

---

# 14. transfer-service

Purpose:

```text
Transfer creation
Validation
Idempotency
Limits
Transfer state
Ledger orchestration
Outbox event creation
Reversal
```

Owns:

```text
transfer
```

## transfers

```text
id UUID PK
transfer_reference
source_account_id
destination_account_id
amount
currency
status
requested_by
failure_code
failure_reason
created_at
updated_at
```

Statuses:

```text
PENDING
PROCESSING
COMPLETED
FAILED
REVERSED
```

## transfer_status_history

```text
id
transfer_id
old_status
new_status
reason
changed_by
created_at
```

## idempotency_keys

```text
id
client_key
request_hash
transfer_id
response_status
expires_at
created_at
```

Unique constraint:

```text
(client/application, idempotency_key)
```

---

# 15. Transfer API

```http
POST /api/transfers
Idempotency-Key: 5f5d...
Authorization: Bearer <access-token>
Content-Type: application/json
```

Request:

```json
{
  "sourceAccountId": "...",
  "destinationAccountId": "...",
  "amount": "100.00",
  "currency": "USD",
  "reference": "Invoice 123"
}
```

Validation:

```text
Authenticate user
        ↓
Authorize TRANSFER_CREATE
        ↓
Verify source ownership
        ↓
Verify source account status
        ↓
Verify destination status
        ↓
Verify currency
        ↓
Verify amount > 0
        ↓
Verify transfer limits
        ↓
Verify available balance
        ↓
Perform atomic financial operation
        ↓
Create ledger entries
        ↓
Create outbox event
        ↓
Commit
```

---

# 16. Idempotency

A client may send:

```text
POST /transfers
Idempotency-Key: ABC123
```

Then timeout before receiving the response.

The client retries:

```text
POST /transfers
Idempotency-Key: ABC123
```

The service must not create another transfer.

Correct behavior:

```text
same idempotency key
        |
        v
existing result
        |
        v
return original result
```

This protects against duplicate financial transactions caused by client retries or network ambiguity.

---

# 17. Transfer Transaction Boundary

The core transfer operation should be atomic inside the transfer/accounting boundary chosen by the architecture.

Conceptually:

```text
BEGIN

validate
lock/version-check source account
lock/version-check destination account
create transfer
create debit ledger entry
create credit ledger entry
update balances
create outbox event

COMMIT
```

If any step fails:

```text
ROLLBACK
```

No successful transfer should exist without its corresponding accounting records.

---

# 18. Service-to-Service Communication

Initial implementation:

```text
REST / HTTP
```

Example:

```text
transfer-service
       |
       +--> account-service
       |
       +--> customer-service
```

Use explicit timeouts.

Never allow an HTTP call to wait forever.

Later, event-driven communication is introduced for operations that do not need a synchronous response.

---

# 19. notification-service

Purpose:

```text
Consume domain events
Create notifications
Email/SMS adapter later
Notification history
Retry failed notifications
```

Owns:

```text
notification
```

## notifications

```text
id
customer_id
channel
type
subject
message
status
attempt_count
last_error
created_at
sent_at
```

Channels can eventually include:

```text
IN_APP
EMAIL
SMS
```

The service should not send a notification directly inside the transfer DB transaction.

Instead:

```text
Transfer committed
      |
      v
transfer.completed
      |
      v
Kafka
      |
      v
Notification service
```

---

# 20. audit-service

Purpose:

```text
Immutable audit history
Who performed an action
What changed
When
Where/from which request
Why/reference
Correlation/trace information
```

Owns:

```text
audit
```

## audit_events

```text
id UUID PK
actor_id
action
resource_type
resource_id
old_value
new_value
ip_address
user_agent
correlation_id
trace_id
created_at
```

Never log passwords, access tokens, refresh tokens, private keys, or other secrets.

Audit records should be append-oriented. Updates/deletes to audit history require special justification and controls.

---

# 21. Outbox Pattern

Do not do this:

```text
DB COMMIT
   |
   v
Kafka SEND
```

If Kafka fails after DB commit, the event is lost.

Do not do this either:

```text
Kafka SEND
   |
   v
DB COMMIT
```

If DB commit fails, the event may describe a transaction that never committed.

Use the outbox pattern:

```text
BEGIN
  transfer update
  ledger update
  outbox insert
COMMIT
```

Then:

```text
Outbox Publisher
      |
      v
Kafka
```

The outbox row and financial transaction commit together.

---

# 22. outbox_events

Owned by `transfer`:

```text
id UUID PK
aggregate_type
aggregate_id
event_type
payload
status
attempt_count
available_at
published_at
created_at
```

Example event:

```json
{
  "eventId": "...",
  "eventType": "transfer.completed",
  "occurredAt": "2026-09-26T10:00:00Z",
  "transferId": "...",
  "sourceAccountId": "...",
  "destinationAccountId": "...",
  "amount": "100.00",
  "currency": "USD"
}
```

Do not publish sensitive information unnecessarily.

---

# 23. Kafka

Kafka is introduced only after synchronous business flows work.

Topics:

```text
transfer.completed.v1
transfer.failed.v1
transfer.reversed.v1
account.created.v1
account.frozen.v1
customer.created.v1
```

Consumer groups:

```text
notification-service
  -> notification consumer group

audit-service
  -> audit consumer group
```

Each service receives the events it needs independently.

---

# 24. Kafka Failure Model

Assume messages can be duplicated.

Consumer must be idempotent.

Example:

```text
Kafka message
      |
      v
notification-service
      |
      +--> already processed? --> return
      |
      +--> process
      |
      +--> record processed state
```

Later add:

```text
retry topic
DLQ
backoff
poison-message handling
consumer lag monitoring
partition strategy
schema versioning
```

Exactly-once semantics should not be treated as a replacement for application-level idempotency.

---

# 25. Event Contract Versioning

Prefer versioned event names or explicit schema versions:

```text
transfer.completed.v1
```

A future incompatible contract can become:

```text
transfer.completed.v2
```

Consumers should tolerate additive changes where possible.

Do not share JPA entities between services.

---

# 26. Configuration Flow

For a service such as account-service:

```text
account-service starts
        |
        v
bootstrap config
        |
        v
call config-service
        |
        v
GET /api/config/account-service/local
        |
        v
validate configuration
        |
        v
initialize application
```

The service still needs minimal bootstrap information to discover the config service:

```text
CONFIG_SERVICE_URL
SERVICE_NAME
ENVIRONMENT
```

---

# 27. Configuration Security

Do not store production passwords in plaintext.

Future config model:

```text
configurations
├── service_name
├── environment
├── config_key
├── encrypted_value
├── version
├── created_at
└── updated_at
```

Potential encryption architecture:

```text
Config DB
   |
   | encrypted value
   v
Config Service
   |
   | decrypt using protected key
   v
Authorized service
```

The encryption master key itself must not be stored in the same database as the encrypted secrets.

---

# 28. Authentication vs Authorization

Authentication:

```text
Who are you?
```

Authorization:

```text
What are you allowed to do?
```

Business authorization:

```text
Are you allowed to perform this specific operation on this specific resource?
```

Example:

```text
User has TRANSFER_CREATE
        |
        v
Does user own source account?
        |
        v
Is account active?
        |
        v
Is amount within limit?
        |
        v
Allow transfer
```

A permission alone must not bypass business rules.

---

# 29. Resource Server Security

Every protected service should be a resource server for its own API boundary.

Conceptually:

```text
Authorization: Bearer <JWT>
```

Spring Security can validate JWT bearer tokens at the resource server. It supports JWT and opaque bearer-token
validation. citeturn0search5turn0search8

Validate at minimum:

```text
signature
issuer
expiration
not-before where used
audience
```

Then map roles/permissions into Spring authorities.

---

# 30. OAuth 2.0 Direction

For a browser/mobile/native client architecture, OAuth 2.0 / OIDC concepts should be used rather than inventing a
proprietary authentication protocol.

For a JavaFX/native client, use Authorization Code + PKCE where the client is a public client.

The client must not contain a confidential client secret.

Spring Security provides OAuth 2.0 support including authorization-server, resource-server, and client capabilities.
citeturn0search8

The project can initially keep the authentication API simple, then add full OAuth/OIDC flows as the security milestone.

---

# 31. API Error Model

All services should converge on a consistent error format.

Example:

```json
{
  "timestamp": "2026-09-26T10:00:00Z",
  "status": 409,
  "code": "INSUFFICIENT_BALANCE",
  "message": "Available balance is insufficient",
  "path": "/api/transfers",
  "correlation_id": "abc-123"
}
```

Never return stack traces to clients.

Do not expose internal SQL errors.

Use stable machine-readable error codes.

---

# 32. Correlation ID

Every inbound request should receive or propagate a correlation ID:

```text
X-Correlation-Id
```

Flow:

```text
Client
  |
  | X-Correlation-Id
  v
Gateway
  |
  v
Auth / Customer / Account / Transfer
  |
  v
Kafka event metadata
```

The correlation ID allows a transaction to be followed across services.

Distributed tracing adds `trace_id` and `span_id` for deeper diagnostics.

---

# 33. Observability

Every service should expose health and metrics.

Spring Boot Actuator:

```text
/actuator/health
/actuator/info
/actuator/metrics
```

Track:

```text
request count
request latency
HTTP errors
JVM memory
GC
thread pools
DB connection pool
DB query latency
Kafka producer errors
Kafka consumer lag
outbox backlog
transfer success/failure
```

Recommended future stack:

```text
Micrometer
OpenTelemetry
Prometheus
Grafana
```

Use the observability stack locally or on approved internal infrastructure.

---

# 34. Resilience

Every remote service call needs a defined policy.

At minimum:

```text
timeout
retry where safe
circuit breaker where useful
bulkhead where useful
fallback where semantically valid
```

Do not blindly retry financial operations.

For example:

```text
GET customer -> potentially retryable

POST transfer -> retry only with idempotency protection
```

Never use an unlimited retry loop.

---

# 35. Timeout Design

Every remote call should have:

```text
connect timeout
read/response timeout
overall request deadline
```

Example concept:

```text
Gateway
  |
  | 2s
  v
Transfer
  |
  | 1s
  v
Account
```

Do not allow downstream timeout values to exceed the upstream request budget without a reason.

---

# 36. Transaction Failure Scenarios

The platform must explicitly test:

```text
DB unavailable
DB connection timeout
slow DB
Kafka unavailable
Kafka producer timeout
consumer crash
duplicate Kafka event
service timeout
network timeout
account locked
account frozen
insufficient balance
currency mismatch
invalid customer
invalid destination account
concurrent transfers
client retry
process crash after DB commit
process crash before DB commit
outbox publisher crash
```

Each scenario needs an expected state, not just an exception.

---

# 37. Testing Strategy

## Unit tests

Use for:

```text
business rules
validators
mappers
calculators
state transitions
```

## Integration tests

Use for:

```text
PostgreSQL
Flyway
JPA
transactions
repositories
```

Testcontainers can be used where Docker is approved.

## API tests

Verify:

```text
status code
response body
security
validation
error contract
```

## Contract tests

Verify service-to-service API/event contracts.

## Security tests

Test:

```text
missing token
invalid token
expired token
wrong audience
wrong issuer
insufficient permission
wrong resource owner
```

Spring Security provides testing support for JWT-authenticated MockMvc requests. citeturn0search16

---

# 38. Database Migration Rules

Every service owns Flyway migrations.

Example:

```text
src/main/resources/db/migration/

V1__create_users.sql
V2__create_roles.sql
V3__create_permissions.sql
V4__create_indexes.sql
```

Never use:

```yaml
spring.jpa.hibernate.ddl-auto: update
```

as the production database migration strategy.

Recommended production setting:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Schema changes are delivered through reviewed migrations.

---

# 39. Money Rules

Use:

```java
BigDecimal
```

with explicit scale and rounding policy.

Currency must be explicit:

```text
amount = 100.00
currency = USD
```

Do not compare money using binary floating-point types.

Do not silently convert currencies.

Currency conversion must be an explicit business operation with a defined rate source and audit trail.

---

# 40. Account State Machine

Example:

```text
PENDING
   |
   v
ACTIVE
 |   |
 |   +----> FROZEN
 |             |
 |             v
 |           ACTIVE
 |
 v
CLOSED
```

Not every transition should be legal.

Implement explicit transition validation.

For example:

```text
CLOSED -> ACTIVE
```

should normally not be allowed simply because a request contains the right role.

---

# 41. Transfer State Machine

```text
PENDING
   |
   v
PROCESSING
  /   \\
 v     v
COMPLETED FAILED
   |
   v
REVERSED
```

Transitions must be controlled by service logic.

Do not allow arbitrary clients to set:

```json
{
  "status": "COMPLETED"
}
```

Status is server-owned state.

---

# 42. Audit Requirements

For security-sensitive operations record:

```text
login
logout
password change
role change
permission change
customer status change
account creation
account freeze
account closure
transfer creation
transfer reversal
configuration change
```

Audit data should contain enough information to answer:

```text
Who?
What?
When?
Which resource?
What was the old state?
What was the new state?
From where?
Which request/correlation?
```

---

# 43. Maker-Checker / Four-Eyes

For sensitive administrative operations, introduce approval workflows.

Example:

```text
Maker creates change
       |
       v
PENDING_APPROVAL
       |
       v
Checker reviews
       |
   +---+---+
   |       |
 APPROVE  REJECT
   |       |
   v       v
APPLIED   REJECTED
```

Example uses:

```text
GL threshold changes
large transfer limits
account freeze overrides
privileged role assignment
configuration changes
```

The exact approval policy must be defined per operation.

---

# 44. Performance Engineering

Performance is not only benchmark numbers.

Measure:

```text
latency
throughput
CPU
allocation rate
GC
DB latency
connection pool usage
lock contention
Kafka lag
network time
```

Tools:

```text
JFR
JMH
async-profiler
load testing
PostgreSQL EXPLAIN ANALYZE
Micrometer
```

JMH should be used for isolated microbenchmarks. Do not assume a JMH benchmark represents real end-to-end application
performance.

For end-to-end performance, use realistic load tests against the running service stack.

---

# 45. Benchmarking Strategy

Three levels:

```text
Level 1: JMH
micro-level Java operation

Level 2: integration benchmark
service + database

Level 3: load test
multiple services + network + DB + Kafka
```

Example:

```text
JMH
  -> mapping performance
  -> validation performance
  -> serialization

Integration
  -> repository query
  -> transaction

Load test
  -> POST /api/transfers
```

Never optimize before measuring.

---

# 46. PostgreSQL Performance

For important queries inspect:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT ...;
```

Check:

```text
index usage
row estimates
actual rows
sequential scans
nested loops
sorts
buffer hits
buffer reads
```

Indexes should support actual query patterns, not every column automatically.

---

# 47. Connection Pool

Each service has its own connection pool.

Important settings:

```text
maximumPoolSize
connectionTimeout
idleTimeout
maxLifetime
```

Do not copy one company's values blindly into another environment.

Tune against:

```text
PostgreSQL capacity
DB connection limit
service instance count
request concurrency
query latency
```

Total possible connections are approximately:

```text
pool size × service instances
```

This must remain within database capacity.

---

# 48. Logging

Use structured logging where possible.

Every important log should contain:

```text
timestamp
level
service
correlation_id
trace_id
operation
business reference
result
```

Avoid:

```text
passwords
tokens
session IDs where sensitive
full card numbers
secrets
```

Use masking/redaction for sensitive data.

---

# 49. Health Checks

Separate:

```text
liveness
readiness
```

Liveness means:

```text
process is alive
```

Readiness means:

```text
service is ready to receive traffic
```

Do not make liveness fail because an optional downstream service is temporarily unavailable.

Readiness policy should reflect whether the service can safely serve requests.

---

# 50. API Versioning

Prefer explicit versioning for public/business contracts where compatibility requires it:

```text
/api/v1/accounts
/api/v1/transfers
```

Events should also have explicit versions.

Do not break existing consumers silently.

---

# 51. API Pagination

For list endpoints:

```http
GET /api/v1/accounts?page=0&size=50
```

For large datasets, use keyset/cursor pagination where offset pagination becomes expensive or unstable.

Never return unlimited database rows through a REST endpoint.

---

# 52. Database Query Rules

Avoid:

```text
SELECT *
```

for service APIs or large tables.

Select only required columns.

Use projections for read-heavy queries when appropriate.

Check N+1 query behavior.

Do not solve an N+1 problem by blindly switching every relationship to eager loading.

---

# 53. Distributed Transaction Rule

Do not attempt a distributed database transaction across every microservice.

Avoid:

```text
Customer DB transaction
       +
Account DB transaction
       +
Transfer DB transaction
       +
Kafka transaction
```

as one giant transaction.

Instead use:

```text
local transaction
      |
      v
state/event
      |
      v
next service
```

and design explicit consistency/recovery behavior.

---

# 54. Transfer Consistency Boundary

The most important design decision is where the financial transaction is authoritative.

For this project:

```text
transfer-service
      |
      +--> coordinates transfer
      |
      +--> account/ledger operation
      |
      +--> records outbox
```

The implementation must ensure the actual debit/credit and ledger invariants cannot partially succeed.

If account and transfer data must be atomically committed, reconsider the service boundary rather than pretending a
normal REST call provides atomicity.

A practical learning architecture may initially keep the financial ledger in `account-service` and have
`transfer-service` invoke a dedicated atomic transfer operation exposed by account-service. The API must be designed so
that account-service owns the atomic account mutation.

---

# 55. Recommended Transfer Call

```text
POST /internal/accounts/transfer
```

Request:

```json
{
  "transferId": "...",
  "sourceAccountId": "...",
  "destinationAccountId": "...",
  "amount": "100.00",
  "currency": "USD"
}
```

Account service performs:

```text
validate accounts
lock/version check
check balance
debit
credit
create ledger entries
commit
```

Transfer service owns the transfer workflow and idempotency record.

The design must also handle the ambiguous result case where account-service commits but the network response is lost.
The `transferId` must therefore be idempotent at the account operation boundary too.

This is one of the most important distributed-systems exercises in the project.

---

# 56. Service Authentication

Internal calls should not rely on a user JWT alone for service identity.

Distinguish:

```text
end-user identity
service identity
```

Future options:

```text
mTLS
service JWT
signed internal requests
short-lived service credentials
```

The first implementation can use internal JWT/service credentials appropriate for the local environment, then harden
with mTLS if required by the target environment.

---

# 57. Gateway Responsibilities vs Service Responsibilities

Gateway:

```text
routing
edge authentication
rate limiting
CORS
correlation
request filtering
```

Service:

```text
business authorization
resource ownership
business validation
financial rules
DB transactions
state transitions
```

Do not put:

```text
balance validation
transfer limits
account ownership
ledger logic
```

inside the gateway.

---

# 58. Security Checklist

Before calling the system production-ready:

- [ ] HTTPS/TLS
- [ ] Strong database credentials
- [ ] Secrets externalized
- [ ] Password hashing
- [ ] Login throttling
- [ ] Account lockout policy
- [ ] JWT signature validation
- [ ] JWT issuer validation
- [ ] JWT audience validation
- [ ] Short access-token lifetime
- [ ] Refresh-token rotation
- [ ] Refresh-token revocation
- [ ] Role/permission checks
- [ ] Resource ownership checks
- [ ] Service-to-service authentication
- [ ] No secrets in logs
- [ ] No tokens in logs
- [ ] No sensitive data in logs
- [ ] Audit trail
- [ ] Security headers
- [ ] CORS policy
- [ ] Rate limiting
- [ ] Dependency vulnerability scanning
- [ ] Database migration review
- [ ] Backup/recovery strategy
- [ ] Key rotation
- [ ] Incident logging

---

# 59. CI/CD

Every repository should eventually have:

```text
checkout
   |
compile
   |
unit tests
   |
static analysis
   |
security/dependency scan
   |
integration tests
   |
build artifact
   |
container image if approved
   |
publish internally
   |
deploy
   |
health check
```

Jenkins can orchestrate this in the company environment.

Do not deploy directly from a developer workstation to production.

---

# 60. Git Strategy

Each service:

```bash
git init
git add .
git commit -m "Initial service"
```

Branches can follow the company's standard.

At minimum:

```text
main
feature/*
release/* where required
```

Keep commits small enough to review.

---

# 61. Local Startup Order

Start PostgreSQL first.

Then:

```text
1. config-service
2. auth-service
3. customer-service
4. account-service
5. transfer-service
6. notification-service
7. audit-service
8. gateway-service
```

Kafka is introduced when event-driven functionality is enabled.

Infrastructure scripts can automate this later.

---

# 62. Initial Database Creation

Create databases:

```sql
CREATE DATABASE config;
CREATE DATABASE auth;
CREATE DATABASE customer;
CREATE DATABASE account;
CREATE DATABASE transfer;
CREATE DATABASE notification;
CREATE DATABASE audit;
```

In a real environment, create dedicated database users/roles per service rather than letting every service connect as
`postgres`.

Example principle:

```text
config_user       -> config only
auth_user         -> auth only
customer_user     -> customer only
account_user      -> account only
transfer_user     -> transfer only
notification_user -> notification only
audit_user        -> audit only
```

---

# 63. Environment Configuration

Local:

```text
ENVIRONMENT=local
```

Test:

```text
ENVIRONMENT=test
```

Production:

```text
ENVIRONMENT=prod
```

Never use `prod` credentials locally.

---

# 64. Example Service Bootstrap Variables

```text
SERVICE_NAME=account-service
ENVIRONMENT=local
CONFIG_SERVICE_URL=http://localhost:8888
```

Database bootstrap:

```text
ACCOUNT_DB_URL=jdbc:postgresql://localhost:5432/account
ACCOUNT_DB_USERNAME=account_user
ACCOUNT_DB_PASSWORD=...
```

Secrets remain external.

---

# 65. Example End-to-End Login

```text
Client
  |
  | POST /api/auth/login
  v
Gateway
  |
  v
Auth Service
  |
  +--> users
  +--> user_credentials
  +--> roles
  +--> permissions
  |
  v
Access token + refresh token
```

Then:

```text
Client
  |
  | Authorization: Bearer <token>
  v
Gateway
  |
  v
Account Service
  |
  +--> JWT validation
  +--> permission check
  +--> business rule
  +--> database
```

---

# 66. Example End-to-End Transfer

```text
Client
  |
  | POST /api/transfers
  | Idempotency-Key: ABC
  v
Gateway
  |
  v
Transfer Service
  |
  +--> authenticate user
  +--> authorize TRANSFER_CREATE
  +--> validate customer/account ownership
  +--> validate amount/currency/limits
  +--> call atomic account transfer operation
  |
  v
Account Service
  |
  +--> validate source
  +--> validate destination
  +--> check available balance
  +--> debit source
  +--> credit destination
  +--> create ledger entries
  |
  v
Commit
  |
  v
Transfer Service
  |
  +--> mark COMPLETED
  +--> insert outbox event
  |
  v
Kafka
  |
  +------------------+
  |                  |
  v                  v
Notification       Audit
```

---

# 67. Example Transfer Failure

Insufficient balance:

```text
POST transfer
    |
    v
validate
    |
    v
account service
    |
    v
available_balance < amount
    |
    v
INSUFFICIENT_BALANCE
```

No debit.

No credit.

No successful transfer event.

Audit may record the rejected attempt according to the audit policy.

---

# 68. Example Timeout Failure

```text
Client
  |
  v
Transfer Service
  |
  v
Account Service
  |
  X network timeout
```

The transfer service must not automatically assume:

```text
"timeout = operation failed"
```

The account service might have committed successfully before the response was lost.

Use an idempotent operation ID and query/recovery mechanism.

This is a core distributed-systems lesson.

---

# 69. Example Kafka Failure

```text
Transfer DB
    |
    +--> transfer COMPLETED
    +--> outbox event
    |
    v
COMMIT
    |
    v
Kafka unavailable
```

Transfer remains completed.

Outbox remains pending.

Publisher retries later.

The financial transaction does not roll back simply because Kafka is unavailable after the database transaction
committed.

---

# 70. Example Duplicate Event

```text
Kafka
  |
  +--> transfer.completed
  |
  +--> transfer.completed again
```

Notification service must detect that the event was already processed.

Use an event ID and a durable processed-event/idempotency mechanism where required.

---

# 71. Data Retention

Define retention per data type:

```text
audit
transaction history
notifications
login attempts
outbox records
logs
metrics
traces
```

Do not delete financial records simply because an application no longer needs them for a UI screen.

Retention must follow the applicable company/regulatory policy.

---

# 72. Privacy

Minimize sensitive data.

For example, avoid logging:

```text
full account numbers
full payment-card numbers
passwords
tokens
identity documents
```

Use masking:

```text
****1234
```

where business diagnostics require a partial identifier.

---

# 73. Disaster Recovery

Eventually document:

```text
RPO
RTO
backup frequency
backup encryption
restore procedure
database replication
Kafka recovery
configuration recovery
key recovery
```

A backup that has never been restored in a test is not a proven recovery strategy.

---

# 74. Deployment Model

Local development:

```text
Windows 11
PostgreSQL
JDK
Gradle
optional Docker
```

Company environment:

```text
Jenkins
internal artifact repository
internal container registry if approved
company-managed runtime
company-managed PostgreSQL/Kafka
```

The application must not require public cloud services to function.

---

# 75. Recommended Development Order

Do not build everything at once.

## Phase 1 - Config

```text
config-service
```

Done when:

```text
PostgreSQL
Flyway
REST API
health
tests
Git
```

work.

## Phase 2 - Authentication

```text
auth-service
```

Build:

```text
users
password hashing
roles
permissions
login
JWT
refresh token
logout
```

## Phase 3 - Gateway

```text
gateway-service
```

Build:

```text
routing
JWT validation
correlation ID
rate limiting foundation
```

## Phase 4 - Customer

```text
customer-service
```

Build:

```text
customer
KYC
status
```

## Phase 5 - Account

```text
account-service
```

Build:

```text
account
balance
ledger
transactions
concurrency
```

## Phase 6 - Transfer

```text
transfer-service
```

Build:

```text
transfer
idempotency
limits
state machine
account operation
outbox
```

## Phase 7 - Audit

```text
audit-service
```

Build:

```text
audit event consumer
immutable history
```

## Phase 8 - Notification

```text
notification-service
```

Build:

```text
event consumer
notification state
retry
```

## Phase 9 - Kafka

Introduce:

```text
Kafka
outbox publisher
consumer groups
retry
DLQ
idempotency
```

## Phase 10 - Observability

Add:

```text
metrics
tracing
structured logs
health
alerts
```

## Phase 11 - Resilience

Add:

```text
timeouts
retry
circuit breaker
bulkhead
rate limiting
```

## Phase 12 - Performance

Add:

```text
JFR
JMH
load tests
PostgreSQL tuning
connection-pool tuning
Kafka tuning
```

## Phase 13 - Production Hardening

Add:

```text
TLS
secret management
key rotation
backup/recovery
CI/CD
deployment
security scanning
```

---

# 76. Definition of Done for Every Service

A service is not complete merely because it starts.

Every service must eventually have:

- [ ] README
- [ ] independent Git repository
- [ ] Gradle build
- [ ] Spring Boot application
- [ ] database ownership defined
- [ ] Flyway migrations
- [ ] REST/API contract
- [ ] validation
- [ ] error handling
- [ ] security rules
- [ ] unit tests
- [ ] integration tests
- [ ] health endpoint
- [ ] metrics
- [ ] structured logging
- [ ] correlation ID
- [ ] timeout policy
- [ ] failure scenarios
- [ ] API documentation
- [ ] no secrets committed
- [ ] CI build

Financial services additionally require:

- [ ] idempotency where relevant
- [ ] concurrency strategy
- [ ] audit requirements
- [ ] transaction boundaries
- [ ] recovery behavior
- [ ] data-retention rules

---

# 77. What Not to Build

Do not introduce complexity just because a technology exists.

Avoid initially:

```text
service mesh
Kubernetes
distributed transactions
CQRS everywhere
event sourcing everywhere
multiple databases per service
Redis everywhere
GraphQL everywhere
Kafka for every request
```

First understand the business flow.

Then introduce infrastructure when there is a concrete problem it solves.

---

# 78. Learning Method

For each feature follow:

```text
1. Business problem
2. API contract
3. Database model
4. Transaction boundary
5. Happy path
6. Failure paths
7. Security
8. Tests
9. Observability
10. Performance
11. Production hardening
```

Example:

```text
Transfer
```

Do not stop at:

```text
POST /transfers -> 200
```

Also answer:

```text
What if the client retries?
What if DB dies?
What if account-service times out?
What if account-service committed but response was lost?
What if Kafka is down?
What if Kafka duplicates the event?
What if two transfers debit simultaneously?
What if the account is frozen?
What if currency differs?
What if the user owns neither account?
What if the process crashes after commit?
```

That is where the senior-level learning happens.

---

# 79. Final Architecture Checklist

```text
[ ] Independent repositories
[ ] Independent database ownership
[ ] Config service
[ ] Auth service
[ ] Gateway
[ ] Customer service
[ ] Account service
[ ] Transfer service
[ ] Notification service
[ ] Audit service
[ ] JWT/OAuth security
[ ] RBAC
[ ] Business authorization
[ ] Double-entry ledger
[ ] Idempotency
[ ] Concurrency control
[ ] Outbox
[ ] Kafka
[ ] Event idempotency
[ ] Retry/DLQ
[ ] Audit trail
[ ] Configuration versioning
[ ] Secret protection
[ ] Metrics
[ ] Tracing
[ ] Structured logs
[ ] Resilience
[ ] JMH/JFR
[ ] Load testing
[ ] CI/CD
[ ] Backup/recovery
[ ] Security hardening
```

---

# 80. Final Target

The final system should behave like this:

```text
                              CLIENT
                                |
                                v
                         +--------------+
                         |    GATEWAY   |
                         |     :8080    |
                         +------+-------+
                                |
          +---------------------+---------------------+
          |                     |                     |
          v                     v                     v
       AUTH                 CUSTOMER                ACCOUNT
      :8081                  :8082                   :8083
          |                     |                     |
          v                     v                     v
       AUTH DB              CUSTOMER DB            ACCOUNT DB
                                                        |
                                                        |
                                                        v
                                                   TRANSFER
                                                     :8084
                                                        |
                                                   TRANSFER DB
                                                        |
                                                        v
                                                     OUTBOX
                                                        |
                                                        v
                                                      KAFKA
                                                   /          \\
                                                  /            \\
                                                 v              v
                                          NOTIFICATION        AUDIT
                                             :8085            :8086
                                                |                |
                                                v                v
                                          NOTIFICATION DB    AUDIT DB

                         CONFIGURATION
                              |
                              v
                         CONFIG SERVICE
                            :8888
                              |
                              v
                         CONFIG DB
```

The core financial invariant remains:

```text
TOTAL DEBITS == TOTAL CREDITS
```

The core distributed-systems invariant is:

```text
A retry must not create a second financial operation.
```

The core security invariant is:

```text
The backend, not the UI, decides what the caller is allowed to do.
```

The core architecture invariant is:

```text
A service owns its data.
Other services use APIs/events, never another service's tables.
```

---

# 81. First Implementation

Start with:

```text
config-service
```

Then proceed to:

```text
auth-service
```

Do not start Kafka, notification, audit, or performance optimization before the basic synchronous business path works.

The project is intentionally built incrementally so every architectural decision can be tested and understood before the
next distributed-system concern is introduced.

---

# 82. Project Principle

Build it from scratch.

Understand every layer.

Do not hide complexity behind a platform you cannot control.

Use Spring for the framework capabilities, PostgreSQL for durable data, Kafka for asynchronous events, and explicit
application code for business rules.

```text
Business
   ↓
Domain rules
   ↓
Database
   ↓
Transaction boundary
   ↓
API
   ↓
Security
   ↓
Failure handling
   ↓
Observability
   ↓
Performance
   ↓
Production
```

That is the complete roadmap for the microservices platform.
