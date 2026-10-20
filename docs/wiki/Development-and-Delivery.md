# Development and Delivery

## Repository structure

The repository contains independent Spring Boot service builds coordinated by a root Gradle composite build:

```text
auth-service/
customer-service/
account-service/
transfer-service/
notification-service/
audit-service/
gateway-service/
config-service/
contracts/
infrastructure/
docs/
```

Each service keeps its own Gradle build rather than being a single Gradle subproject. Java 21 is required.

## Build and test

From the repository root:

```bash
gradle buildAll
```

On Windows PowerShell, use:

```powershell
./infrastructure/scripts/build-all.ps1
```

Run targeted Gradle tests inside the service directory when iterating on one service:

```bash
./gradlew test
```

Use the Gradle wrapper where available so the project-selected Gradle distribution is used. If a dependency is missing, validate the wrapper, Java version, and approved network access rather than changing corporate network controls.

## Continuous integration

The repository uses GitHub Actions for build/test and CodeQL checks. A commit can be queued before a result is available; do not report a queued run as passed. Review the actual workflow conclusion and test logs before merging.

The helper script `infrastructure/scripts/sync-github-actions-config.sh` can sync required local settings to Actions Variables and Secrets through the GitHub CLI. It requires an authenticated `gh` session and a populated `infrastructure/.env`; review the script before syncing. Never print secrets into logs or paste secrets into issues or pull requests.

## Suggested change workflow

1. Confirm the service owns the business capability you are changing.
2. Read the existing controller, application service/port, domain model, and infrastructure adapter.
3. Add or update characterization tests before major refactoring.
4. Keep external JSON fields snake_case with the shared Jackson setting while retaining camelCase Java names.
5. Keep secrets and signing material out of the diff.
6. Run targeted tests and the root build.
7. Review authorization, idempotency, error cases, observability, and migration behavior.
8. Open a pull request with the behavioral change, test evidence, migration/rollback notes, and operational implications.

## Architecture quality checks

- Avoid a service accessing another service's database.
- Keep controllers thin and move orchestration into application services.
- Add outbound ports for persistence and remote dependencies when extracting infrastructure concerns.
- Keep financial operations idempotent and transaction boundaries explicit.
- Preserve stable API behavior and update [[API-Reference]] when endpoints change.
- Review migration compatibility and token/client changes whenever updating authorization behavior.

Related pages: [[Architecture]], [[Services]], [[API-Reference]].
