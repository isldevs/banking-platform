#!/usr/bin/env python3
"""Exercise the isolated demo through its public API; never print credentials or tokens."""

import argparse
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
import json
import os
from pathlib import Path
import secrets
import sys
import time
import urllib.error
import urllib.request
import uuid


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def settings(path):
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key] = value.strip().strip("\"'")
    # Explicit process variables can select credentials for a separately started demo.
    values.update({key: os.environ[key] for key in values if key in os.environ})
    for key in ("DEMO_ADMIN_USERNAME", "DEMO_ADMIN_PASSWORD", "INTERNAL_SERVICE_KEY"):
        require(bool(values.get(key)), "Missing required smoke-test setting: " + key)
    return values


class BankingSmoke:
    def __init__(self, base_url, values, timeout):
        self.base_url = base_url.rstrip("/")
        self.values = values
        self.timeout = timeout
        self.run_id = uuid.uuid4().hex[:16]

    def request(self, method, path, token=None, body=None, headers=None, expected=(200, 201)):
        request_headers = {"Accept": "application/json", **(headers or {})}
        if token:
            request_headers["Authorization"] = "Bearer " + token
        data = None
        if body is not None:
            request_headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode("utf-8")
        request = urllib.request.Request(
            self.base_url + path, data=data, headers=request_headers, method=method
        )
        try:
            response = urllib.request.urlopen(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            status = response.status
            raw = response.read()
        require(status in expected, f"{method} {path} returned {status}, expected {expected}")
        return json.loads(raw, parse_float=Decimal) if raw else None

    def login(self, username, password):
        return self.request(
            "POST", "/api/auth/login", body={"username": username, "password": password}
        )["access_token"]

    def onboard(self, admin, label):
        username = f"smoke-{label}-{self.run_id}"
        password = secrets.token_urlsafe(32)
        self.request(
            "POST", "/api/users", admin,
            {"username": username, "password": password, "email": username + "@example.test"},
        )
        token = self.login(username, password)
        customer = self.request(
            "POST", "/api/customers", token,
            {"first_name": "Smoke", "last_name": label, "date_of_birth": "1990-01-01"},
        )
        require(customer["status"] == "PENDING_KYC", "Customer must begin pending KYC")
        approved = self.request("POST", f"/api/customers/{customer['id']}/kyc/approve", admin)
        require(approved["status"] == "ACTIVE", "KYC approval must activate the customer")
        return token, customer["id"]

    def open_account(self, token, customer, label, deposit=0):
        body = {
            "customer_id": customer, "product_code": "BASIC-USD", "currency": "USD",
            "initial_deposit": deposit, "funding_channel": "BANK_TRANSFER" if deposit else None,
        }
        headers = {"Idempotency-Key": f"smoke-open-{self.run_id}-{label}"}
        account = self.request("POST", "/api/accounts", token, body, headers)
        repeated = self.request("POST", "/api/accounts", token, body, headers)
        require(account["id"] == repeated["id"], "Account-opening retry created another account")
        return account

    def balance(self, token, account, expected):
        current = self.request("GET", f"/api/accounts/{account}", token)
        amount = Decimal(expected)
        require(Decimal(str(current["ledger_balance"])) == amount, "Unexpected ledger balance")
        require(Decimal(str(current["available_balance"])) == amount, "Unexpected available balance")
        return current

    def wait_for(self, callback, label):
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            result = callback()
            if result:
                return result
            time.sleep(1)
        raise RuntimeError("Timed out waiting for " + label)

    def run(self):
        admin = self.login(self.values["DEMO_ADMIN_USERNAME"], self.values["DEMO_ADMIN_PASSWORD"])
        alice, customer_a = self.onboard(admin, "sender")
        bob, customer_b = self.onboard(admin, "recipient")
        source = self.open_account(alice, customer_a, "source", deposit=100)
        destination = self.open_account(bob, customer_b, "destination")
        require(source["status"] == "PENDING_FUNDING", "Opening deposit must await settlement")
        self.balance(alice, source["id"], "0.00")
        print("PASS: onboarding, KYC, account-opening retries and pending funding")

        reference = "SMOKE-SETTLEMENT-" + self.run_id
        settlement_headers = {
            "X-Service-Key": self.values["INTERNAL_SERVICE_KEY"],
            "X-External-Reference": reference,
        }
        funding_path = f"/api/accounts/funding/{source['funding']['instruction_id']}/post"
        self.request("POST", funding_path, admin, headers=settlement_headers)
        self.request("POST", funding_path, admin, headers=settlement_headers)
        self.balance(alice, source["id"], "100.00")

        duplicate = self.open_account(alice, customer_a, "duplicate-settlement", deposit=100)
        self.request(
            "POST", f"/api/accounts/funding/{duplicate['funding']['instruction_id']}/post", admin,
            headers=settlement_headers, expected=(409,),
        )
        require(self.balance(alice, duplicate["id"], "0.00")["status"] == "PENDING_FUNDING",
                "Rejected settlement must not activate another account")
        print("PASS: settlement retry and cross-instruction duplicate rejection")

        transfer_body = {
            "source_account_id": source["id"], "destination_account_id": destination["id"],
            "amount": "12.50", "currency": "USD", "reference": "SMOKE-" + self.run_id,
        }
        transfer_headers = {"Idempotency-Key": "smoke-transfer-" + self.run_id}
        with ThreadPoolExecutor(max_workers=4) as callers:
            transfers = list(callers.map(
                lambda _: self.request("POST", "/api/transfers", alice, transfer_body, transfer_headers),
                range(4),
            ))
        require(len({item["id"] for item in transfers}) == 1, "Concurrent retries created transfers")
        transfer_id = transfers[0]["id"]
        self.wait_for(
            lambda: self.request("GET", "/api/transfers/" + transfer_id, alice)["status"] == "COMPLETED",
            "transfer completion",
        )
        self.request("POST", "/api/transfers", alice, transfer_body, transfer_headers)
        self.request(
            "POST", "/api/transfers", alice, {**transfer_body, "amount": "13.00"},
            transfer_headers, expected=(409,),
        )
        self.balance(alice, source["id"], "87.50")
        self.balance(bob, destination["id"], "12.50")
        self.request("GET", "/api/accounts/" + destination["id"], alice, expected=(403,))
        ledger = self.request("GET", "/api/accounts/ledger/" + transfer_id, alice)
        require(ledger["balanced"] and len(ledger["entries"]) == 2, "Transfer journal must balance")
        require(Decimal(str(ledger["total_debit"])) == Decimal("12.50"), "Wrong debit total")
        require(Decimal(str(ledger["total_credit"])) == Decimal("12.50"), "Wrong credit total")
        print("PASS: concurrent transfer retries, balances, ownership and balanced ledger")

        audit = self.wait_for(
            lambda: self.request("GET", "/api/audit/transfers/" + transfer_id, admin), "audit delivery"
        )
        require(len(audit) == 1 and audit[0]["action"] == "transfer.completed.v1",
                "Expected one completed audit event")
        notifications = self.wait_for(
            lambda: [item for item in self.request("GET", "/api/notifications", alice)
                     if transfer_id in item["message"]], "notification delivery"
        )
        require(len(notifications) == 1, "Expected one notification for the transfer")
        print("PASS: durable outbox delivery to audit and notifications")
        print("Banking API smoke test passed. Only throwaway demo records were created.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path, default=Path(__file__).resolve().parents[1] / ".env")
    parser.add_argument("--gateway", default="http://localhost:8080")
    parser.add_argument("--timeout", type=int, default=120, help="Maximum wait per asynchronous stage")
    args = parser.parse_args()
    try:
        BankingSmoke(args.gateway, settings(args.env_file), args.timeout).run()
    except (RuntimeError, OSError, KeyError, ValueError) as error:
        # Do not include request bodies, credentials or token responses in diagnostics.
        print("Banking API smoke test failed:", str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
