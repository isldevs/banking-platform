#!/usr/bin/env bash
# Generate throwaway credentials for an isolated CI Docker Compose smoke test.
# Never use this helper to reset an existing development environment.
set -euo pipefail
umask 077

infra_dir="$(cd "$(dirname "$0")/.." && pwd)"
env_file="$infra_dir/.env"
secrets_dir="$infra_dir/secrets"

if [[ -e "$env_file" || -e "$secrets_dir/jwt-signing.p12" ]]; then
  echo "Refusing to overwrite existing local credentials." >&2
  exit 1
fi

command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }
command -v keytool >/dev/null || { echo "Java 17 keytool is required" >&2; exit 1; }

mkdir -p "$secrets_dir"

python3 - "$infra_dir/.env.example" "$env_file" "$(id -u)" "$(id -g)" <<'PY'
from pathlib import Path
import re
import secrets
import sys

template_path, destination_path, uid, gid = sys.argv[1:]
source = Path(template_path).read_text(encoding="utf-8")

service_names = ("AUTH", "CUSTOMER", "ACCOUNT", "TRANSFER", "NOTIFICATION", "AUDIT")
generated = {
    name: secrets.token_hex(32)
    for name in (
        "SECURITY_JWT_KEYSTORE_PASSWORD",
        "INTERNAL_SERVICE_KEY",
        "OAUTH2_DEMO_CLIENT_SECRET",
        "DEMO_ADMIN_PASSWORD",
        "DEMO_USER_PASSWORD",
        "POSTGRES_PASSWORD",
        *(f"DB_PASSWORD_{service}" for service in service_names),
    )
}
for service in service_names:
    generated[f"DB_{service}_PASSWORD"] = generated[f"DB_PASSWORD_{service}"]
generated["APP_UID"] = uid
generated["APP_GID"] = gid

lines = []
seen = set()
for line in source.splitlines():
    match = re.match(r"^([A-Za-z_][A-Za-z0-9_]*)=", line)
    if match:
        name = match.group(1)
        if name in generated:
            line = f"{name}={generated[name]}"
            seen.add(name)
    lines.append(line)
for name, value in generated.items():
    if name not in seen:
        lines.append(f"{name}={value}")

result = "\n".join(lines) + "\n"
if "replace-with-" in result:
    raise SystemExit("Unresolved placeholder in generated .env")
Path(destination_path).write_text(result, encoding="utf-8")
PY

# Keytool must use the same password that was generated for the PKCS12 keystore.
keystore_password="$(python3 - "$env_file" <<'PY'
from pathlib import Path
import sys
for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    if line.startswith("SECURITY_JWT_KEYSTORE_PASSWORD="):
        print(line.split("=", 1)[1])
        break
else:
    raise SystemExit("Missing signing password")
PY
)"

keytool -genkeypair \
  -alias jwt-signing \
  -keyalg RSA -keysize 3072 -validity 30 \
  -storetype PKCS12 \
  -keystore "$secrets_dir/jwt-signing.p12" \
  -storepass "$keystore_password" -keypass "$keystore_password" \
  -dname "CN=ci-jwt-signing, OU=Test, O=iSLDevs, C=KH" >/dev/null

keytool -exportcert -rfc \
  -alias jwt-signing \
  -keystore "$secrets_dir/jwt-signing.p12" \
  -storepass "$keystore_password" \
  -file "$secrets_dir/jwt-public.pem" >/dev/null

chmod 600 "$env_file" "$secrets_dir/jwt-signing.p12"
chmod 644 "$secrets_dir/jwt-public.pem"
echo "Ephemeral CI Docker Compose credentials prepared (values not printed)."
