#!/usr/bin/env bash
# Keep shell entry points consistent with the other local setup helpers.
set -euo pipefail
exec python3 "$(cd "$(dirname "$0")" && pwd)/setup-postman.py"
