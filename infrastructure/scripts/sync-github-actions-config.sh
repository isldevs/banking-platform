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

# Read dotenv values without sourcing/executing the file.
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

set_variable() {
  local name="$1" value="$2"
  [[ -n "$value" ]] || { echo "Skipping unset variable: $name"; return; }
  # Pipe values on stdin so they are not included in gh's command-line arguments.
  printf '%s' "$value" | gh variable set "$name" --repo "$REPO" >/dev/null
  echo "Set Actions variable: $name"
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

# Repository variables: configuration that is safe to display in workflow logs.
set_variable "JWT_ISSUER" "$(read_env JWT_ISSUER)"
set_variable "APP_UID" "$(read_env APP_UID)"
set_variable "APP_GID" "$(read_env APP_GID)"
set_variable "CONFIG_SERVER_USERNAME" "$(read_env CONFIG_SERVER_USERNAME)"
set_variable "CONFIG_REPO_LABEL" "$(read_env CONFIG_REPO_LABEL)"
set_variable "DEMO_ADMIN_USERNAME" "$(read_env DEMO_ADMIN_USERNAME)"
set_variable "CONFIG_REPO_URI" "$(read_env CONFIG_REPO_URI || true)"
set_variable "CONFIG_REPO_SEARCH_PATHS" "$(read_env CONFIG_REPO_SEARCH_PATHS || true)"
set_variable "CONFIG_REPO_USERNAME" "$(read_env CONFIG_REPO_USERNAME || true)"

# Repository secrets: credentials must never be stored as Actions variables.
for name in \
  POSTGRES_PASSWORD \
  INTERNAL_SERVICE_KEY \
  CONFIG_SERVER_PASSWORD \
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

# These are only needed when Config Server clones a private configuration repo.
config_repo_password="$(read_env CONFIG_REPO_PASSWORD || true)"
if [[ -n "$config_repo_password" ]]; then
  if [[ "$config_repo_password" == replace-with-* ]]; then
    echo "CONFIG_REPO_PASSWORD is still a placeholder; refusing to upload it." >&2
    exit 1
  fi
  printf '%s' "$config_repo_password" | gh secret set CONFIG_REPO_PASSWORD --repo "$REPO" >/dev/null
  echo "Set optional Actions secret: CONFIG_REPO_PASSWORD"
fi

echo
echo "Finished syncing configuration names to $REPO."
echo "Review them at: https://github.com/$REPO/settings/variables/actions"
echo "Secrets can be reviewed at: https://github.com/$REPO/settings/secrets/actions"
