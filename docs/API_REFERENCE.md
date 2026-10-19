# Demo API Reference (base `http://localhost:8080`)

All protected routes accept `Authorization: Bearer <accessToken>`. The gateway checks JWT signature, issuer, expiration
and audience; each downstream service checks again and applies permission/ownership policies. `X-Correlation-Id` can be
supplied, otherwise generated. Use UUID values from previous responses.

| Method | Route                                 | Who                             | Description                                                                  |
|--------|---------------------------------------|---------------------------------|------------------------------------------------------------------------------|
| POST   | `/api/auth/login`                     | Public                          | `{"username":"demo","password":"..."}`                                       |
| POST   | `/api/auth/refresh`                   | Public                          | `{"refreshToken":"..."}`; rotates token                                      |
| POST   | `/api/auth/logout`                    | Authenticated                   | `{"refreshToken":"..."}`; revokes token                                      |
| GET    | `/api/auth/me`                        | Authenticated                   | Identity/roles/permissions                                                   |
| POST   | `/api/users`                          | USER_MANAGE                     | Create user with username/password/email                                     |
| POST   | `/api/customers`                      | CUSTOMER_CREATE                 | New profile (pending KYC)                                                    |
| GET    | `/api/customers/{id}`                 | CUSTOMER_READ + owner/admin     | Customer info                                                                |
| POST   | `/api/customers/{id}/kyc/approve`     | KYC_APPROVE                     | Approve and activate customer                                                |
| POST   | `/api/accounts`                       | ACCOUNT_OPEN                    | `{"customerId":"uuid","currency":"USD"}`                                     |
| GET    | `/api/accounts`                       | ACCOUNT_READ                    | Own accounts                                                                 |
| GET    | `/api/accounts/{id}`                  | ACCOUNT_READ + owner/admin      | Account & balance                                                            |
| POST   | `/api/accounts/{id}/fund`             | ACCOUNT_FUND                    | `{"amount":"500.00"}`; local demo only                                       |
| POST   | `/api/accounts/{id}/freeze`           | ACCOUNT_FREEZE                  | Freeze active account                                                        |
| GET    | `/api/accounts/ledger/{operationId}`  | ACCOUNT_FUND                    | Audit paired DEBIT/CREDIT entries and balanced invariant                     |
| POST   | `/api/transfers`                      | TRANSFER_CREATE                 | Transfer request plus `Idempotency-Key` header                               |
| GET    | `/api/transfers/{id}`                 | TRANSFER_READ + requester/admin | Status                                                                       |
| GET    | `/api/notifications`                  | TRANSFER_READ/NOTIFICATION_READ | Own in-app notifications                                                     |
| GET    | `/api/audit/transfers/{id}`           | AUDIT_READ                      | Transfer event audit trail                                                   |
| GET    | `/api/config/{service}/{environment}` | CONFIG_READ                     | Latest nonsecret config versions                                             |
| POST   | `/api/config`                         | CONFIG_MANAGE                   | `{"service":"transfer-service","environment":"local","key":"x","value":"y"}` |

## Example transfer

```http
POST /api/transfers
Authorization: Bearer <user-token>
Idempotency-Key: client-payment-123456
Content-Type: application/json

{
  "sourceAccountId": "<account-A-uuid>",
  "destinationAccountId": "<account-B-uuid>",
  "amount": "125.00",
  "currency": "USD",
  "reference": "Invoice 123"
}
```

Possible statuses include `PENDING`, `PENDING_RECONCILIATION`, `COMPLETED`, and `FAILED`. Idempotent request retries
return the original transfer ID. The service-to-service financial operation uses a stable `transferId` and a separately
protected `/internal/accounts/transfer` endpoint (not accessible through the gateway).
