# ms-gateway

Spring Cloud Gateway microservice — the single public entry point for **Project Crystal** (static security analysis platform).

## Routes

| Path prefix          | Downstream service | Default URI              |
|----------------------|--------------------|--------------------------|
| `/api/v1/scans/**`   | ms-intake          | `http://ms-intake:8081`  |
| `/api/v1/results/**` | ms-results         | `http://ms-results:8082` |
| `/api/v1/alerts/**`  | ms-alert           | `http://ms-alert:8083`   |

Public (no auth required):

| Path                  | Description                          |
|-----------------------|--------------------------------------|
| `GET /actuator/health`| Gateway self health (Spring Actuator)|
| `GET /health/services`| Aggregated downstream services health|

## Authentication

All `/api/**` routes require the `X-API-Key` header with the shared secret configured via the `API_KEY` environment variable (default: `dev-secret-key` for local development).

Missing or incorrect key → **401 Unauthorized** with JSON body:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing required header: X-API-Key",
  "path": "/api/v1/scans/...",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

## Rate Limiting

In-memory per-IP rate limiting is implemented directly in `ApiKeyAuthFilter` using [Guava RateLimiter](https://guava.dev/releases/snapshot/api/docs/com/google/common/util/concurrent/RateLimiter.html):

- **20 requests/second** per client IP (based on `X-Forwarded-For` or remote address)
- Exceeding the limit → **429 Too Many Requests**

> For production deployments requiring distributed / persistent rate limiting, replace the Guava RateLimiter with Spring Cloud Gateway's built-in `RequestRateLimiter` filter backed by Redis (`spring-boot-starter-data-redis-reactive`).

## Environment Variables

| Variable               | Default                      | Description                              |
|------------------------|------------------------------|------------------------------------------|
| `SERVER_PORT`          | `8080`                       | Port the gateway listens on              |
| `API_KEY`              | `dev-secret-key`             | Shared secret for X-API-Key validation   |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000`      | Comma-separated list of allowed origins  |
| `MS_INTAKE_URL`        | `http://ms-intake:8081`      | ms-intake base URL                       |
| `MS_RESULTS_URL`       | `http://ms-results:8082`     | ms-results base URL                      |
| `MS_ALERT_URL`         | `http://ms-alert:8083`       | ms-alert base URL                        |

## Running Locally

```bash
# Build
./mvnw clean package -DskipTests

# Run
API_KEY=my-secret java -jar target/ms-gateway-0.0.1-SNAPSHOT.jar

# Test
curl -H "X-API-Key: my-secret" http://localhost:8080/api/v1/scans/
curl http://localhost:8080/actuator/health
curl http://localhost:8080/health/services
```

## Running with Docker

```bash
docker build -t ms-gateway .
docker run -p 8080:8080 \
  -e API_KEY=my-secret \
  -e MS_INTAKE_URL=http://host.docker.internal:8081 \
  -e MS_RESULTS_URL=http://host.docker.internal:8082 \
  -e MS_ALERT_URL=http://host.docker.internal:8083 \
  ms-gateway
```

## Running Tests

```bash
./mvnw test
```

## Architecture

```
Client
  │
  ▼  :8080
ms-gateway  ──── ApiKeyAuthFilter (order -1): validates X-API-Key, rate-limits per IP
                 RequestLoggingFilter (order 0): logs method/path/status/latency
                 GlobalErrorHandler: converts exceptions to JSON responses
  │
  ├─ /api/v1/scans/**    ──► ms-intake  :8081
  ├─ /api/v1/results/**  ──► ms-results :8082
  └─ /api/v1/alerts/**   ──► ms-alert   :8083
```
