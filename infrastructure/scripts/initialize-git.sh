#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
for dir in config-service gateway-service auth-service customer-service account-service transfer-service notification-service audit-service infrastructure contracts; do
  (cd "$dir" && git init -q && echo "Initialized Git repository: $dir")
done
# No git commits performed: user can choose their own remote, branches and CI policy.
