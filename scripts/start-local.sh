#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INFRA="$ROOT/infrastructure"
ENV_FILE="$INFRA/.env"
SECRET_DIR="$INFRA/secrets"
KEYSTORE="$SECRET_DIR/jwt-signing.p12"
PUBLIC_KEY="$SECRET_DIR/jwt-public.pem"

command -v docker >/dev/null || { echo 'ERROR: Docker is required.' >&2; exit 1; }
command -v java >/dev/null || { echo 'ERROR: Java 21 is required.' >&2; exit 1; }
java -version 2>&1 | grep -q 'version "21\.' || { echo 'ERROR: Java 21 is required.' >&2; exit 1; }

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$INFRA/.env.example" "$ENV_FILE"
  echo 'Created infrastructure/.env from .env.example.'
  echo 'Configure infrastructure/.env, then run ./scripts/start-local.sh again.'
  exit 0
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

for name in SECURITY_JWT_KEYSTORE_PASSWORD INTERNAL_SERVICE_KEY OAUTH2_DEMO_CLIENT_SECRET DEMO_ADMIN_USERNAME DEMO_ADMIN_PASSWORD DEMO_USER_PASSWORD POSTGRES_PASSWORD DB_PASSWORD_AUTH DB_PASSWORD_CUSTOMER DB_PASSWORD_ACCOUNT DB_PASSWORD_TRANSFER DB_PASSWORD_NOTIFICATION DB_PASSWORD_AUDIT; do
  value="${!name:-}"
  if [[ -z "$value" || "$value" == replace-with-* ]]; then
    echo "ERROR: $name is missing or still contains a placeholder in infrastructure/.env." >&2
    exit 1
  fi
done

command -v keytool >/dev/null || { echo 'ERROR: keytool was not found. Java 21 is required.' >&2; exit 1; }
mkdir -p "$SECRET_DIR"

if [[ ! -f "$KEYSTORE" ]]; then
  keytool -genkeypair -alias jwt-signing -keyalg RSA -keysize 3072 -validity 3650 -storetype PKCS12 -keystore "$KEYSTORE" -storepass "$SECURITY_JWT_KEYSTORE_PASSWORD" -keypass "$SECURITY_JWT_KEYSTORE_PASSWORD" -dname 'CN=demo-jwt-signing, OU=Security, O=Microservice Platform, C=KH'
fi
if [[ ! -f "$PUBLIC_KEY" ]]; then
  keytool -exportcert -rfc -alias jwt-signing -keystore "$KEYSTORE" -storepass "$SECURITY_JWT_KEYSTORE_PASSWORD" -file "$PUBLIC_KEY"
fi

cd "$INFRA"
docker compose --env-file .env up --build -d
echo
echo 'Local platform started.'
echo 'Gateway: http://localhost:8080'
echo 'Auth:    http://localhost:8081'
echo 'Config:  http://localhost:8888'
echo 'Login:   DEMO_ADMIN_USERNAME / DEMO_ADMIN_PASSWORD from infrastructure/.env'
