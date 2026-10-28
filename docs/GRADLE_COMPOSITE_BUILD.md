# Gradle Composite Build

The repository contains eight independent Spring Boot Gradle builds. The root build orchestrates them with Gradle composite-build `includeBuild(...)` entries; it is not a single multi-project Gradle module.

## Requirements

- Java 21 JDK.
- Gradle 8.10.x (use the version approved for the project environment).
- Network access to approved dependency repositories or the organization's Maven mirror.
- Docker Compose only when running the full containerized demo.

The repository does **not** ship `gradlew` or a Gradle wrapper JAR.

## Included builds

```text
config-service
auth-service
customer-service
account-service
transfer-service
notification-service
audit-service
gateway-service
```

## Root commands

Run from the repository root:

```bash
gradle showServices
gradle buildAll
gradle testAll
gradle checkAll
gradle bootJarAll
gradle cleanAll
gradle spotlessApplyAll
gradle spotlessCheckAll
```

`buildAll` and `checkAll` also run `validateRuntimeContract`, which verifies the expected service/configuration/observability contract.

## Target one service

```bash
gradle :account-service:test
gradle :gateway-service:bootJar
```

Or work directly in a service directory:

```bash
cd account-service
gradle clean build
```

## Service outputs

Executable JARs are produced under each service's `build/libs/` directory.

## Docker startup

The root Gradle composite build is independent of Docker deployment. Compose builds each service from its own Dockerfile:

```bash
cp infrastructure/.env.example infrastructure/.env
cd infrastructure
docker compose up --build -d
```

Docker is not a Spring profile; Compose runs the application services with the `dev` profile.

## Why composite builds?

The design keeps service builds independently buildable and releasable while still allowing the workspace to validate and package all services together. Shared domain/JPA models are deliberately not introduced as a cross-service library.
