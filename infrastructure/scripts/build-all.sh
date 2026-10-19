#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
TASKS=("${@:-buildAll}")
if command -v gradle >/dev/null 2>&1; then
  exec gradle --no-daemon "${TASKS[@]}"
fi
if command -v docker >/dev/null 2>&1; then
  exec docker run --rm --init \
    -v "$ROOT:/workspace" \
    -v gradle-platform-cache:/home/gradle/.gradle \
    -w /workspace \
    gradle:8.10.2-jdk21 gradle --no-daemon "${TASKS[@]}"
fi
printf 'ERROR: Install Gradle 8.10.2 + JDK 21 or Docker.\n' >&2
exit 1
