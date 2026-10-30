# Development and Delivery

## Repository structure

The `isldevs/banking-platform` workspace contains eight independent Spring Boot builds coordinated by a root Gradle composite build:

```text
config-service/
auth-service/
customer-service/
account-service/
transfer-service/
notification-service/
audit-service/
gateway-service/
contracts/
infrastructure/
docs/
```

Each service has its own `settings.gradle` and `build.gradle`. The root `settings.gradle` uses `includeBuild(...)`; this is not a single multi-project Gradle build.

## Toolchain

- Current Java toolchains and CI: **JDK 17**, Spring Boot 3.3.x.
- Committed Gradle wrapper: **8.13** (`gradlew`, `gradlew.bat`, `gradle/wrapper/`).
- GitHub Actions executes the committed Gradle **8.13** wrapper.
- The project is a Gradle composite build, not one cross-service JPA module.

## Build and test

From the repository root:

```bash
./gradlew showServices
./gradlew buildAll
./gradlew testAll
./gradlew checkAll
./gradlew bootJarAll
./gradlew cleanAll
```

Formatting:

```bash
./gradlew spotlessApplyAll
./gradlew spotlessCheckAll
```

Target one service through the composite build, for example:

```bash
./gradlew :account-service:test
./gradlew :gateway-service:bootJar
```

On Windows use `.\gradlew.bat`. For service-only work, run the composite task from the root or use an approved standalone Gradle setup inside the service directory.

## Runtime-contract validation

`buildAll` and `checkAll` include `validateRuntimeContract`, which checks the expected service builds, Config Service environment files, Micrometer/OpenTelemetry dependencies, correlation configuration, and required bootstrap settings. Config Service tests also resolve the supported profile properties using the documented environment names and verify the Compose environment contract.

## CI

GitHub Actions defines build/test and CodeQL workflows. The Docker job generates ephemeral credentials and test keys with `infrastructure/scripts/setup-postman.sh`, verifies service health, runs `python3 infrastructure/scripts/smoke-banking.py`, and executes the committed Postman collection with Newman. The smoke script exercises settlement deduplication and concurrent retry behavior; the collection covers every configured application, OAuth/OIDC, Config Server and management endpoint using collection variables only. Both create throwaway demo data and avoid printing credentials or tokens. Inspect actual workflow conclusions/logs rather than assuming a defined workflow has passed.

The Windows local setup helper is `scripts/setup-local.ps1`. It provisions local environment and JWT material but does not actually start Docker. Internal microservice host ports are not all published by Compose.

## Change workflow

1. Confirm the owning bounded context.
2. Read the current controller, application service/port, domain model and infrastructure adapter.
3. Add/update tests before significant behavior changes.
4. Preserve the shared snake_case REST contract unless an exception is deliberate.
5. Keep financial operations idempotent and transaction boundaries explicit.
6. Keep secrets out of commits and logs.
7. Update the canonical wiki API/architecture page when behavior changes.
8. Keep the affected service README concise; link to the canonical documentation instead of duplicating it.
9. Run targeted tests and the root verification tasks.
10. Open a focused PR with behavior, test evidence and migration/operational notes.

## Banking-specific engineering standards

- Preserve a stable financial operation ID across retries and ambiguous outcomes.
- Keep posting operations and balanced debit/credit journal entries in one atomic boundary.
- Validate money precision per currency, account ownership, idempotency and concurrency.
- Add PostgreSQL integration tests, failure-injection tests and reconciliation checks before expanding international payment support.
- Distinguish current implementation from proposed general ledger, AML/FX, payment-rail and lending capabilities in all documentation.
- Review producer outbox and Kafka consumer behavior for at-least-once delivery and duplicates.

## Documentation rule

`docs/wiki/` is the canonical project documentation set. Avoid creating duplicate API references, architecture descriptions, or configuration guides elsewhere. Root and service READMEs should summarize and link to the canonical pages.

Related pages: [Architecture](Architecture.md), [Services](Services.md), [API Reference](API-Reference.md).
