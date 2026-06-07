# Crystal

Crystal is a static security analysis platform. It scans source code for vulnerabilities using AST parsing and Claude AI, and exposes the full pipeline as a set of event-driven microservices.

---

## Architecture

```
[Client]
   │  POST /api/v1/scans   (X-API-Key)
   ▼
ms-intake          Spring Boot — REST entry point, validates requests
   │  → Kafka: scan-jobs
   ▼
ms-scanner         Python — AST parsing + Claude AI vulnerability detection
   │  → Kafka: scan-results
   ▼              ▼
ms-results      ms-alert
Spring Boot     Spring Boot
PostgreSQL      Slack webhook
REST query API  (high / critical only)
```

**Two Kafka topics:**
- `scan-jobs` — carries scan requests from intake to scanner
- `scan-results` — carries findings from scanner to results and alert

---

## Services

| Service | Stack | Port | Responsibility |
|---|---|---|---|
| [ms-intake](./ms-intake/README.md) | Spring Boot 3.5 | 8081 | REST entry point, produces to `scan-jobs` |
| [ms-scanner](./ms-scanner/README.md) | Python 3.11 | — | Consumes `scan-jobs`, runs Claude analysis, produces to `scan-results` |
| [ms-results](./ms-results/README.md) | Spring Boot 3.5 | 8082 | Consumes `scan-results`, persists to PostgreSQL, REST query API |
| [ms-alert](./ms-alert/README.md) | Spring Boot 3.5 | 8083 | Consumes `scan-results`, sends Slack webhook for high/critical issues |

---

## Running locally

All services + Kafka + PostgreSQL start with one command:

```bash
docker-compose up --build
```

Create a `.env` file at the repo root before starting:

```env
# Required
ANTHROPIC_API_KEY=sk-ant-...

# Optional — Slack alerts
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...

# Optional — GitHub issue creation
GITHUB_TOKEN=ghp_...
GITHUB_REPO=owner/repo

# Optional — Langfuse observability
LANGFUSE_PUBLIC_KEY=pk-lf-...
LANGFUSE_SECRET_KEY=sk-lf-...
LANGFUSE_HOST=https://cloud.langfuse.com

# Shared API key for REST services (change in production)
API_KEY=dev-secret-key

# PostgreSQL (defaults work with docker-compose)
DB_NAME=crystal_results
DB_USERNAME=crystal
DB_PASSWORD=crystal
```

---

## Triggering a scan

Once the stack is up, submit a scan job via ms-intake:

```bash
# Scan a local directory
curl -X POST http://localhost:8081/api/v1/scans \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-secret-key" \
  -d '{
    "targetType": "file",
    "target": "/path/to/your/code",
    "requestedBy": "your-name"
  }'

# Scan a GitHub repository
curl -X POST http://localhost:8081/api/v1/scans \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-secret-key" \
  -d '{
    "targetType": "github_url",
    "target": "https://github.com/owner/repo",
    "requestedBy": "your-name"
  }'
```

Both return a `202 Accepted` with a `jobId`. Results land in ms-results as the scanner finishes.

---

## Querying results

```bash
# List all scan results (paginated)
curl http://localhost:8082/api/v1/results \
  -H "X-API-Key: dev-secret-key"

# Get a specific job's findings
curl http://localhost:8082/api/v1/results/{jobId} \
  -H "X-API-Key: dev-secret-key"

# Issue counts by severity
curl http://localhost:8082/api/v1/results/stats/severity \
  -H "X-API-Key: dev-secret-key"
```

---

## API Documentation

Each Spring Boot service exposes Swagger UI when running:

| Service | Swagger UI |
|---|---|
| ms-intake | http://localhost:8081/swagger-ui.html |
| ms-results | http://localhost:8082/swagger-ui.html |
| ms-alert | http://localhost:8083/swagger-ui.html |

---

## Repo structure

```
project-crystal/
├── docker-compose.yml       # Full stack — one command to run everything
│
├── ms-intake/               # Spring Boot — REST entry point
├── ms-scanner/              # Python — Claude AI scanner
├── ms-results/              # Spring Boot — results persistence + query API
└── ms-alert/                # Spring Boot — Slack alerting
```

Each service folder contains its own `README.md`, `Dockerfile`, and all source code.

---

## Kafka message schemas

**`scan-jobs`**
```json
{
  "jobId": "uuid",
  "requestedAt": "ISO timestamp",
  "targetType": "file | github_url",
  "target": "string",
  "requestedBy": "string"
}
```

**`scan-results`**
```json
{
  "jobId": "uuid",
  "completedAt": "ISO timestamp",
  "status": "success | failure",
  "issues": [
    {
      "severity": "low | medium | high | critical",
      "type": "string",
      "location": "string",
      "description": "string"
    }
  ]
}
```

---

## Auth

All REST endpoints are protected by a simple API key header: `X-API-Key`. Set the same value in `API_KEY` env var across all Spring Boot services. OAuth/JWT is deferred.
