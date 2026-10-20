#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export SERVER_PORT="${SERVER_PORT:-8888}"

echo "Starting Config Server without HTTP Basic credentials on port $SERVER_PORT"
cd "$ROOT_DIR/config-service"

if command -v gradle >/dev/null 2>&1; then
  exec gradle --no-daemon bootRun
fi

if [[ -x "$ROOT_DIR/gradlew" && -f "$ROOT_DIR/gradle/wrapper/gradle-wrapper.jar" ]]; then
  exec "$ROOT_DIR/gradlew" --no-daemon -p "$ROOT_DIR/config-service" bootRun
fi

echo "Gradle is not installed and a working Gradle Wrapper was not found." >&2
echo "Install an approved Gradle version or restore the wrapper files." >&2
exit 1
