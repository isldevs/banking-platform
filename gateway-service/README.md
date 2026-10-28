# gateway-service

Java 17 / Spring Boot stateless API Gateway on port `8080`.

## Responsibilities

- Public API entry point and routing.
- JWT resource-server validation at the edge.
- Correlation ID propagation.
- Redis-backed API rate limiting.
- HTTP client timeouts and connection limits.

The Gateway contains edge concerns only. Business authorization and business rules remain in downstream services.

## Rate limiting

Rate limiting applies to `/api/**` requests and uses Redis fixed windows with an atomic Lua script.

| Rule | Limit |
|---|---:|
| `POST /api/auth/login` | 5/minute |
| `POST /api/auth/refresh` | 10/minute |
| `POST /api/accounts...` | 10/minute |
| `POST /api/transfers...` | 10/minute |
| Other `/api/**` | 60/minute |

Authenticated requests are keyed by JWT subject; anonymous requests are keyed by client IP. Responses expose `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `X-RateLimit-Reset`; rejected requests return HTTP `429` with `Retry-After`.

Development defaults to fail-open if Redis is unavailable; non-development environments should set the fail-open policy explicitly according to their operational requirements.

## Routing

The Gateway proxies the existing `/api/**` surface. The initial-funding settlement endpoint intentionally remains under `/api/accounts/funding/{instructionId}/post` and is protected by `X-Service-Key`, allowing the normal API proxy to forward the request without adding a special Gateway route.

## Configuration

Configuration is bundled in `config-service/src/main/resources/config/{dev,qa,stage,prod}/gateway-service.yml`. Redis, JWT issuer/audience, service URLs, and internal credentials are runtime values.

## Run

```bash
cp infrastructure/.env.example infrastructure/.env
# edit infrastructure/.env
cd infrastructure
docker compose up --build -d
```

Redis is exposed locally on `127.0.0.1:6379` by Compose.

See [Architecture](../docs/wiki/Architecture.md), [Configuration](../docs/wiki/Configuration.md), and [API Reference](../docs/wiki/API-Reference.md).
