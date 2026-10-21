#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v openssl >/dev/null || { echo "OpenSSL is required" >&2; exit 1; }
if [[ ! -f .env ]]; then
  umask 077
  {
    echo 'JWT_ISSUER=http://localhost:8081'
    # Existing resource servers expect audience "api"; the auth-service now
    # issues legacy and authorization-server access tokens with this value.
    echo 'JWT_AUDIENCE=api'
    echo "APP_UID=$(id -u)"
    echo "APP_GID=$(id -g)"
    echo "POSTGRES_PASSWORD=$(openssl rand -hex 24)"
    echo "INTERNAL_SERVICE_KEY=$(openssl rand -hex 32)"
    echo "JWT_KEYSTORE_PASSWORD=$(openssl rand -hex 24)"
    echo "OAUTH2_DEMO_CLIENT_SECRET=$(openssl rand -hex 32)"
    echo "DEMO_ADMIN_PASSWORD=$(openssl rand -hex 18)"
    echo "DEMO_USER_PASSWORD=$(openssl rand -hex 18)"
    for svc in AUTH CUSTOMER ACCOUNT TRANSFER NOTIFICATION AUDIT; do
      echo "DB_PASSWORD_${svc}=$(openssl rand -hex 24)"
    done
  } > .env
  echo "Generated .env; runtime secrets remain local."
else
  echo "Using existing .env (not overwritten)."
fi

# Add required local settings without overwriting existing values.
if ! grep -q '^JWT_AUDIENCE=' .env; then printf 'JWT_AUDIENCE=%s\n' 'api' >> .env; fi
if ! grep -q '^JWT_KEYSTORE_PASSWORD=' .env; then printf 'JWT_KEYSTORE_PASSWORD=%s\n' "$(openssl rand -hex 24)" >> .env; fi
if ! grep -q '^OAUTH2_DEMO_CLIENT_SECRET=' .env; then printf 'OAUTH2_DEMO_CLIENT_SECRET=%s\n' "$(openssl rand -hex 32)" >> .env; fi

mkdir -p secrets
KEYSTORE_PASSWORD="$(grep '^JWT_KEYSTORE_PASSWORD=' .env | cut -d= -f2-)"
if [[ -z "$KEYSTORE_PASSWORD" ]]; then echo 'JWT_KEYSTORE_PASSWORD is empty' >&2; exit 1; fi

# Generate a local PKCS12 signing keystore. The private key is never committed and is not used directly by application config.
if [[ ! -f secrets/jwt-signing.p12 || ! -f secrets/jwt-public.pem ]]; then
  temp_key="secrets/.jwt-signing.tmp.key.pem"
  temp_cert="secrets/.jwt-signing.tmp.crt.pem"
  trap 'rm -f "$temp_key" "$temp_cert"' EXIT
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$temp_key" >/dev/null 2>&1
  openssl req -new -x509 -key "$temp_key" -out "$temp_cert" -days 3650 -subj '/CN=microservices-demo-jwt' >/dev/null 2>&1
  openssl pkcs12 -export -out secrets/jwt-signing.p12 -inkey "$temp_key" -in "$temp_cert" -name jwt-signing -passout "pass:$KEYSTORE_PASSWORD" >/dev/null 2>&1
  openssl x509 -in "$temp_cert" -pubkey -noout > secrets/jwt-public.pem
  rm -f "$temp_key" "$temp_cert"
  trap - EXIT
  chmod 600 secrets/jwt-signing.p12
fi
chmod 644 secrets/jwt-public.pem

printf '\nDemo user credentials are stored in infrastructure/.env\n'
printf 'OAuth2 confidential client secret is stored in infrastructure/.env\n'
printf 'JWT signing material: infrastructure/secrets/jwt-signing.p12 (ignored by Git)\n'
printf 'Start: cd infrastructure && docker compose up --build -d\n'
