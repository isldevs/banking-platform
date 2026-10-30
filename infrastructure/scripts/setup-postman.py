#!/usr/bin/env python3
"""Prepare local demo settings and a separate Config Server test key."""

from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import tempfile


INFRA = Path(__file__).resolve().parent.parent
ENV_FILE = INFRA / ".env"
SECRETS = INFRA / "secrets"
ENCRYPTION_KEY = SECRETS / "config-encryption.jks"
ALIAS = "config-encryption"
os.umask(0o077)


def setting(name: str) -> str:
    for raw in ENV_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key.strip() != name:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        return value
    raise SystemExit(f"Missing {name} in the application .env")


def keytool(*arguments: str) -> None:
    environment = os.environ.copy()
    environment["POSTMAN_KEYSTORE_PASSWORD"] = password
    subprocess.run(
        ["keytool", *arguments],
        env=environment,
        check=True,
        stdout=subprocess.DEVNULL,
    )


if shutil.which("keytool") is None:
    raise SystemExit("Java 17 or newer keytool is required")

if not ENV_FILE.exists():
    subprocess.run(["bash", str(INFRA / "scripts/init-local.sh")], check=True)

password = setting("SECURITY_JWT_KEYSTORE_PASSWORD")
if len(password) < 6 or "replace-with-" in password:
    raise SystemExit("Configure SECURITY_JWT_KEYSTORE_PASSWORD before Postman setup")

SECRETS.mkdir(parents=True, exist_ok=True)
if ENCRYPTION_KEY.exists():
    keytool(
        "-list", "-alias", ALIAS,
        "-keystore", str(ENCRYPTION_KEY), "-storetype", "JKS",
        "-storepass:env", "POSTMAN_KEYSTORE_PASSWORD",
    )
    print("Existing Postman Config Server encryption key verified; left unchanged.")
    raise SystemExit(0)

with tempfile.TemporaryDirectory(prefix=".postman-key.", dir=SECRETS) as directory:
    generated = Path(directory) / ENCRYPTION_KEY.name
    keytool(
        "-genkeypair", "-alias", ALIAS,
        "-keyalg", "RSA", "-keysize", "3072", "-validity", "30",
        "-storetype", "JKS", "-keystore", str(generated),
        "-storepass:env", "POSTMAN_KEYSTORE_PASSWORD",
        "-keypass:env", "POSTMAN_KEYSTORE_PASSWORD",
        "-dname", "CN=postman-config-encryption, OU=Test, O=iSLDevs, C=KH",
    )
    generated.chmod(0o600)
    os.link(generated, ENCRYPTION_KEY)

print("Separate Postman Config Server encryption key prepared (values not printed).")
