#!/usr/bin/env python3
"""Fail when the checked-in Postman collection drifts from the exposed API."""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COLLECTION = ROOT / "docs/postman/banking-platform.postman_collection.json"
CONTRACT = re.compile(r"^Contract: ([a-z]+) (GET|POST|PUT|PATCH|DELETE) (.+)$", re.MULTILINE)

APPLICATION = {
    ("auth", "POST", "/api/auth/login"),
    ("auth", "POST", "/api/auth/refresh"),
    ("auth", "POST", "/api/auth/logout"),
    ("auth", "GET", "/api/auth/me"),
    ("auth", "POST", "/api/users"),
    ("customer", "POST", "/api/customers"),
    ("customer", "GET", "/api/customers/{id}"),
    ("customer", "POST", "/api/customers/{id}/kyc/approve"),
    ("customer", "GET", "/internal/customers/{id}/status"),
    ("account", "POST", "/api/accounts"),
    ("account", "GET", "/api/accounts/{id}"),
    ("account", "GET", "/api/accounts"),
    ("account", "POST", "/api/accounts/{id}/freeze"),
    ("account", "POST", "/api/accounts/{id}/fund"),
    ("account", "GET", "/api/accounts/ledger/{operationId}"),
    ("account", "POST", "/internal/accounts/transfer"),
    ("account", "POST", "/api/accounts/funding/{instructionId}/post"),
    ("account", "GET", "/internal/accounts/operations/{id}"),
    ("transfer", "POST", "/api/transfers"),
    ("transfer", "GET", "/api/transfers/{id}"),
    ("notification", "GET", "/api/notifications"),
    ("audit", "GET", "/api/audit/transfers/{id}"),
}
OAUTH = {
    ("auth", "GET", "/.well-known/openid-configuration"),
    ("auth", "GET", "/.well-known/oauth-authorization-server"),
    ("auth", "GET", "/oauth2/jwks"),
    ("auth", "GET", "/logout"),
    ("auth", "POST", "/logout"),
    ("auth", "GET", "/oauth2/authorize"),
    ("auth", "POST", "/oauth2/authorize"),
    ("auth", "GET", "/login"),
    ("auth", "POST", "/login"),
    ("auth", "POST", "/oauth2/token"),
    ("auth", "GET", "/userinfo"),
    ("auth", "POST", "/userinfo"),
    ("auth", "POST", "/oauth2/introspect"),
    ("auth", "GET", "/connect/logout"),
    ("auth", "POST", "/connect/logout"),
    ("auth", "POST", "/oauth2/revoke"),
}
CONFIG_READ = {
    ("config", "GET", path)
    for path in (
        "/{name}/{profiles}", "/{name}/{profiles}/{label}",
        "/{name}-{profiles}.properties", "/{label}/{name}-{profiles}.properties",
        "/{name}-{profiles}.json", "/{label}/{name}-{profiles}.json",
        "/{name}-{profiles}.yml", "/{label}/{name}-{profiles}.yml",
        "/{name}-{profiles}.yaml", "/{label}/{name}-{profiles}.yaml",
        "/{name}/{profile}/{label}/{path}", "/{name}/{profile}/{path}?useDefaultLabel",
    )
}
CONFIG_ENCRYPTION = {
    ("config", method, path)
    for method, path in (
        ("GET", "/key"), ("GET", "/key/{name}/{profiles}"),
        ("GET", "/encrypt/status"), ("POST", "/encrypt"),
        ("POST", "/encrypt/{name}/{profiles}"), ("POST", "/decrypt"),
        ("POST", "/decrypt/{name}/{profiles}"),
    )
}
MANAGEMENT_SERVICES = ("config", "auth", "customer", "account", "transfer", "notification", "audit", "gateway")
MANAGEMENT_PATHS = (
    "/actuator", "/actuator/health", "/actuator/health/liveness",
    "/actuator/health/readiness", "/actuator/info", "/actuator/metrics",
    "/actuator/metrics/{metricName}", "/actuator/prometheus",
)
MANAGEMENT = {(service, "GET", path) for service in MANAGEMENT_SERVICES for path in MANAGEMENT_PATHS}


def leaves(items):
    for item in items:
        if "item" in item:
            yield from leaves(item["item"])
        else:
            yield item


def fail(message):
    print("Postman coverage check failed:", message, file=sys.stderr)
    raise SystemExit(1)


data = json.loads(COLLECTION.read_text(encoding="utf-8"))
requests = list(leaves(data.get("item", [])))
declared = {entry["key"] for entry in data.get("variable", [])}
contracts = []
referenced = set()
script_text = []

for item in requests:
    req = item.get("request", {})
    match = CONTRACT.search(req.get("description", ""))
    if not match:
        fail(f"{item.get('name')} has no exact Contract description")
    contracts.append(tuple(match.groups()))
    encoded = json.dumps(item)
    referenced.update(name for name in re.findall(r"\{\{([^{}]+)\}\}", encoded) if not name.startswith("$"))
    events = item.get("event", [])
    tests = [entry for entry in events if entry.get("listen") == "test"]
    if not tests:
        fail(f"{item.get('name')} has no test script")
    combined = "\n".join(line for entry in tests for line in entry.get("script", {}).get("exec", []))
    if "pm.response.code" not in combined and "pm.response.to.have.status" not in combined:
        fail(f"{item.get('name')} has no status assertion")
    script_text.extend(line for entry in events for line in entry.get("script", {}).get("exec", []))

missing_variables = sorted(referenced - declared)
if missing_variables:
    fail("undeclared collection variables: " + ", ".join(missing_variables))

all_scripts = "\n".join(script_text)
for forbidden in ("pm.environment", "pm.globals", "pm.iterationData", "pm.variables.get", "pm.variables.set"):
    if forbidden in all_scripts:
        fail(f"forbidden variable scope used: {forbidden}")

for key in re.findall(r"pm\.collectionVariables\.(?:get|set)\(['\"]([^'\"]+)", all_scripts):
    if key not in declared:
        fail(f"script uses undeclared collection variable: {key}")

coverage = set(contracts)
expected = APPLICATION | OAUTH | CONFIG_READ | CONFIG_ENCRYPTION | MANAGEMENT
missing = sorted(expected - coverage)
if missing:
    fail("missing contracts: " + ", ".join(" ".join(row) for row in missing))

environment_files = list(ROOT.rglob("*.postman_environment.json"))
if environment_files:
    fail("Postman environment files are prohibited: " + ", ".join(str(path.relative_to(ROOT)) for path in environment_files))

counts = Counter()
counts["application"] = len(APPLICATION)
counts["oauth_oidc_session"] = len(OAUTH)
counts["config_read"] = len(CONFIG_READ)
counts["config_encryption"] = len(CONFIG_ENCRYPTION)
counts["management"] = len(MANAGEMENT)
if sum(counts.values()) != 121:
    fail("checker inventory count is not 121")

print(
    f"Postman coverage passed: {len(requests)} executable requests cover "
    f"{sum(counts.values())} endpoint contracts; {len(declared)} collection variables; no environment scope."
)
