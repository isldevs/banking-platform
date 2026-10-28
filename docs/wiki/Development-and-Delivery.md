# Development and Delivery

## Repository structure

The workspace contains eight independent Spring Boot builds coordinated by a root Gradle composite build:

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

## Build and test

From the repository root:

```bash
gradle showServices
gradle buildAll
gradle testAll
gradle checkAll
gradle bootJarAll
gradle cleanAll
```

Formatting:

```bash
gradle spotlessApplyAll
gradle spotlessCheckAll
```

Target one service through the composite build, for example:

```bash
gradle :account-service:test
gradle :gateway-service:bootJar
```

Or work directly inside a service directory with the approved Gradle installation.

## Runtime-contract validation

`buildAll` and `checkAll` include `validateRuntimeContract`, which checks the expected service builds, Config Service environment files, Micrometer/OpenTelemetry dependencies, correlation configuration, and required bootstrap settings.

## CI

GitHub Actions provides build/test and CodeQL workflows. Do not report a queued or unavailable workflow as passed; inspect the actual workflow conclusion and logs before merging.

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

## Documentation rule

`docs/wiki/` is the canonical project documentation set. Avoid creating duplicate API references, architecture descriptions, or configuration guides elsewhere. Root and service READMEs should summarize and link to the canonical pages.

Related pages: [[Architecture]], [[Services]], [[API-Reference]].
