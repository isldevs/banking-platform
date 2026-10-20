#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/infrastructure/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Creating local demo credentials in infrastructure/.env..."
  bash "$ROOT_DIR/infrastructure/scripts/init-local.sh"
fi

# Read dotenv values as data; do not source/evaluate the .env file.
read_env() {
  local key="$1"
  awk -v key="$key" '
    /^[[:space:]]*#/ || !index($0, "=") { next }
    {
      eq = index($0, "=")
      name = substr($0, 1, eq - 1)
      if (name == key) {
        value = substr($0, eq + 1)
        sub(/\r$/, "", value)
        print value
        exit
      }
    }
  ' "$ENV_FILE"
}

# Spring Boot imports this file as a properties source as well; use an absolute path
# so the import works regardless of the process working directory.
export INFRASTRUCTURE_ENV_FILE="$ENV_FILE"
export CONFIG_SERVER_USERNAME="$(read_env CONFIG_SERVER_USERNAME)"
export CONFIG_SERVER_PASSWORD="$(read_env CONFIG_SERVER_PASSWORD)"
export SERVER_PORT="$(read_env SERVER_PORT)"

: "${CONFIG_SERVER_USERNAME:?CONFIG_SERVER_USERNAME is missing in infrastructure/.env}"
: "${CONFIG_SERVER_PASSWORD:?CONFIG_SERVER_PASSWORD is missing in infrastructure/.env}"
export SERVER_PORT="${SERVER_PORT:-8888}"

echo "Starting config-service on http://localhost:$SERVER_PORT"
echo "Config Server credentials are loaded from infrastructure/.env (values are not printed)."
cd "$ROOT_DIR/config-service"
exec gradle --no-daemon bootRun
