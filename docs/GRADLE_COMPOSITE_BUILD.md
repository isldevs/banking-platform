# Gradle Composite Build (multi-build orchestration)

The source architecture explicitly requires each microservice to own its Gradle build,
Git repository, database and deployment. Therefore the root uses Gradle **composite builds**
(`includeBuild`) instead of `include(':service')` multi-project modules.

## Requirements

- Java 21 JDK (when running Gradle locally)
- Gradle 8.10.2 (recommended, same as the per-service Dockerfiles)
- Network access to the required dependency repositories or your internal Maven mirror
- Docker is optional for builds, but required for the provided `docker compose` demo

## Root commands

Run in `microservices-platform-gradle-composite/`:

```bash
gradle showServices
gradle buildAll           # compile + tests + packaging, all 8 services
gradle testAll            # only tests
gradle bootJarAll         # produce 8 executable boot JARs
gradle cleanAll           # clean all builds
gradle clean build        # normal Gradle lifecycle also delegates to all builds
```

Build or test just one service (no need to cd into its folder):

```bash
gradle :auth-service:build
gradle :account-service:bootJar
gradle :transfer-service:test
```

Or work directly in an independent service repository:

```bash
cd account-service
gradle clean build
```

A service produces its JAR at `<service>/build/libs/`.

## Without Gradle installed locally

A Docker build container can run the composite build, without installing Gradle:

```bash
# Linux / macOS / WSL / Git Bash
bash infrastructure/scripts/build-all.sh

# Windows PowerShell
./infrastructure/scripts/build-all.ps1
```

Both scripts use local `gradle` when available, and otherwise use the
`gradle:8.10.2-jdk21` container image with this project mounted.
Pass `testAll`, `bootJarAll`, or `:auth-service:build` as optional script arguments.

## Docker application startup

The `infrastructure/docker-compose.yml` file still builds each service independently
using its service-level Dockerfile. That is intentional: compiling every service
should not become a deployment dependency for a single service.

```bash
bash infrastructure/scripts/init-local.sh
cd infrastructure
docker compose up --build -d
python3 scripts/demo_flow.py
```

## Source repository strategy

At present the release ZIP is a convenient workspace containing all independent
service directories. To push them to separate Git remotes, run:

```bash
bash infrastructure/scripts/initialize-git.sh
```

Composite build orchestration works with these sibling directories regardless of
whether they are separate Git repositories or a checked-out workspace.

## Not included

A Gradle wrapper (`gradlew`, `gradle-wrapper.jar`) is **not** shipped. Use a
local Gradle 8.10.2 installation or the provided Docker build scripts. A wrapper
can be generated later in your approved environment using `gradle wrapper
--gradle-version 8.10.2` and then committed with its verified wrapper JAR.
