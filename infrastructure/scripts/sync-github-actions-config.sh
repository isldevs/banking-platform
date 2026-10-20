#!/usr/bin/env bash
set -euo pipefail

# Upload the demo's local configuration into GitHub Actions repository
# Variables (non-sensitive settings) and Secrets (credentials).
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="$ROOT_DIR/infrastructure/.env"
REPO="${GH_REPO:-isldevs/microservice-platform-demo}"

if ! command -v gh >/dev/null 2>&1; then
  echo "GitHub CLI (gh) is required. Install it from https://cli.github.com/." >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "Authenticate first with: gh auth login" >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "No infrastructure/.env found; generating local demo configuration first."
  bash "$ROOT_DIR/infrastructure/scripts/init-local.sh"
fi

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

read_env_or_default() {
  local value
  value="$(read_env "$1")"
  if [[ -n "$value" ]]; then
    printf '%s' "$value"
  else
    printf '%s' "$2"
  fi
}

set_variable() {
  local name="$1" value="$2"
  [[ -n "$value" ]] || { echo "Skipping unset variable: $name"; return; }
  printf '%s' "$value" | gh variable set "$name" --repo "$REPO" >/dev/null
  echo "Set Actions variable: $name"
}

validate_secret() {
  local name="$1" value
  value="$(read_env "$name")"
  if [[ -z "$value" || "$value" == replace-with-* ]]; then
    echo "Required secret $name is missing or still a placeholder in $ENV_FILE." >&2
    echo "Set a real value in infrastructure/.env and run this script again." >&2
    exit 1
  fi
}

set_secret() {
  local name="$1" value
  value="$(read_env "$name")"
  if [[ -z "$value" || "$value" == replace-with-* ]]; then
    echo "Required secret $name is missing or still a placeholder in $ENV_FILE." >&2
    echo "Set a real value in infrastructure/.env and run this script again." >&2
    exit 1
  fi
  printf '%s' "$value" | gh secret set "$name" --repo "$REPO" >/dev/null
  echo "Set Actions secret: $name"
}

for name in \
  POSTGRES_PASSWORD \
  INTERNAL_SERVICE_KEY \
  CONFIG_SERVER_PASSWORD \
  OAUTH2_DEMO_CLIENT_SECRET \
  DEMO_ADMIN_PASSWORD \
  DEMO_USER_PASSWORD \
  DB_PASSWORD_AUTH \
  DB_PASSWORD_CUSTOMER \
  DB_PASSWORD_ACCOUNT \
  DB_PASSWORD_TRANSFER \
  DB_PASSWORD_NOTIFICATION \
  DB_PASSWORD_AUDIT
do
  validate_secret "$name"
done

set_variable "JWT_ISSUER" "$(read_env_or_default JWT_ISSUER http://localhost:8081)"
set_variable "APP_UID" "$(read_env_or_default APP_UID 1000)"
set_variable "APP_GID" "$(read_env_or_default APP_GID 1000)"
set_variable "CONFIG_SERVER_USERNAME" "$(read_env_or_default CONFIG_SERVER_USERNAME config-client)"
set_variable "DEMO_ADMIN_USERNAME" "$(read_env_or_default DEMO_ADMIN_USERNAME admin)"

for name in \
  POSTGRES_PASSWORD \
  INTERNAL_SERVICE_KEY \
  CONFIG_SERVER_PASSWORD \
  OAUTH2_DEMO_CLIENT_SECRET \
  DEMO_ADMIN_PASSWORD \
  DEMO_USER_PASSWORD \
  DB_PASSWORD_AUTH \
  DB_PASSWORD_CUSTOMER \
  DB_PASSWORD_ACCOUNT \
  DB_PASSWORD_TRANSFER \
  DB_PASSWORD_NOTIFICATION \
  DB_PASSWORD_AUDIT
do
  set_secret "$name"
done

echo
echo "Finished syncing configuration names to $REPO."
echo "Review them at: https://github.com/$REPO/settings/variables/actions"
echo "Secrets can be reviewed at: https://github.com/$REPO/settings/secrets/actions"
