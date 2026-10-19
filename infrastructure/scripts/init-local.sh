#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v openssl >/dev/null || { echo "OpenSSL is required" >&2; exit 1; }
if [[ ! -f .env ]]; then
  umask 077
  {
    echo 'JWT_ISSUER=http://localhost:8081'
    echo "APP_UID=$(id -u)"
    echo "APP_GID=$(id -g)"
    echo "POSTGRES_PASSWORD=password"
    echo "INTERNAL_SERVICE_KEY=$(openssl rand -hex 32)"
    echo "DEMO_ADMIN_PASSWORD=$(openssl rand -hex 18)"
    echo "DEMO_USER_PASSWORD=$(openssl rand -hex 18)"
    for svc in CONFIG AUTH CUSTOMER ACCOUNT TRANSFER NOTIFICATION AUDIT; do
      echo "DB_PASSWORD_${svc}=$(openssl rand -hex 24)"
    done
  } > .env
  echo "Generated .env; credentials remain local."
else
  echo "Using existing .env (not overwritten)."
fi
mkdir -p secrets
if [[ ! -f secrets/jwt-private.pem || ! -f secrets/jwt-public.pem ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out secrets/jwt-private.pem >/dev/null 2>&1
  openssl pkey -in secrets/jwt-private.pem -pubout -out secrets/jwt-public.pem >/dev/null 2>&1
  chmod 600 secrets/jwt-private.pem
fi
# Public key contains no private material; non-root service containers must read it.
chmod 644 secrets/jwt-public.pem
printf '
Demo user credentials are stored in infrastructure/.env
'
printf 'Start: cd infrastructure && docker compose up --build -d
'
