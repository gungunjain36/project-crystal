# Crystal

Crystal is a cloud-native static security analysis platform. It scans source code repositories for vulnerabilities using AST parsing and Claude AI, and exposes the full pipeline as event-driven microservices. This document covers the full system design, architectural decisions, and deployment guide.

---

## Table of Contents

- [How We Built This](#how-we-built-this)
- [System Architecture](#system-architecture)
- [Services](#services)
- [Data Flow](#data-flow)
- [Kafka Message Schemas](#kafka-message-schemas)
- [API Gateway & Auth](#api-gateway--auth)
- [System Design Decisions](#system-design-decisions)
- [Known Gaps & Next Steps](#known-gaps--next-steps)
- [Running Locally](#running-locally)
- [Deploying to AWS](#deploying-to-aws)

---

## How We Built This

Crystal started as a single Python script: walk a directory, parse Python files with AST, send each file to Claude for security review, write findings to SQLite. That was MS-1 — simple, useful, but not scalable and not usable by external clients.

**The problem with the single-service model:**
- Scanning is slow (LLM calls per file). A synchronous REST API would time out.
- Results storage, querying, and alerting were all tangled together in one script.
- No way to scale the scanner independently of the API layer.

**The solution: event-driven microservices over Kafka.**

We decomposed the pipeline into four services with clear contracts, connected by two Kafka topics. Each service can be scaled, deployed, and debugged independently.

**Technology choices:**
- **Kafka** over a message queue (SQS, RabbitMQ): partitioned, replayable, multiple independent consumers on the same topic. Both ms-results and ms-alert consume from `scan-results` without knowing about each other.
- **Spring Boot** for the Java services: battle-tested ecosystem for Kafka, JPA, REST, and Swagger out of the box.
- **Python for the scanner**: the existing scanner logic (AST parsing, Claude integration, Langfuse tracing) was already Python. Rewriting it in Java would have been pure churn.
- **Spring Cloud Gateway** as the API gateway: reactive, integrates natively with the Spring ecosystem, handles routing, rate limiting, and auth in one place.
- **PostgreSQL** for results persistence (ms-results): structured queries, supports pagination and severity stats natively.
- **SQLite** for the scanner's internal state: the scanner's own file/function metadata is append-only and local to each scan run. Not shared state — no coordination required.

---

## System Architecture

```
                         ┌─────────────────────────────┐
                         │        ms-gateway :8080      │
                         │    Spring Cloud Gateway      │
                         │  - API key validation        │
                         │  - Rate limiting (IP-based)  │
                         │  - CORS                      │
                         │  - Route to downstream       │
                         └────────────┬────────────────┘
                                      │
               ┌──────────────────────┼──────────────────────┐
               │                      │                       │
               ▼                      ▼                       ▼
      /api/v1/scans/**     /api/v1/results/**      /api/v1/alerts/**
               │
               ▼
    ┌─────────────────────┐
    │   ms-intake :8081   │
    │   Spring Boot REST  │
    │  Validates request  │
    │  Generates job UUID │
    └────────┬────────────┘
             │  Kafka: scan-jobs
             ▼
    ┌─────────────────────┐
    │  ms-scanner         │
    │  Python 3.11        │
    │  AST parsing        │
    │  Claude AI review   │
    │  GitHub issues      │
    │  Langfuse traces    │
    └────────┬────────────┘
             │  Kafka: scan-results
             ├──────────────────────────────┐
             ▼                              ▼
    ┌─────────────────────┐       ┌──────────────────────┐
    │  ms-results :8082   │       │   ms-alert :8083     │
    │  Spring Boot        │       │   Spring Boot        │
    │  Persists to        │       │   Slack webhook for  │
    │  PostgreSQL         │       │   high/critical only │
    │  REST query API     │       └──────────────────────┘
    └─────────────────────┘

Infrastructure:
  Kafka + Zookeeper    — async message transport
  PostgreSQL           — persistent results store (ms-results)
  SQLite               — scanner-local file/function metadata (ms-scanner)
```

---

## Services

| Service | Stack | Port | Responsibility |
|---|---|---|---|
| [ms-gateway](./ms-gateway/README.md) | Spring Cloud Gateway | 8080 | Single public entry point. Auth, rate limiting, routing. |
| [ms-intake](./ms-intake/README.md) | Spring Boot 3.5 | 8081 | Accepts scan requests, validates, produces to `scan-jobs`. |
| [ms-scanner](./ms-scanner/README.md) | Python 3.11 | — | Consumes `scan-jobs`, runs Claude analysis, produces to `scan-results`. |
| [ms-results](./ms-results/README.md) | Spring Boot 3.5 | 8082 | Consumes `scan-results`, persists to PostgreSQL, REST query API. |
| [ms-alert](./ms-alert/README.md) | Spring Boot 3.5 | 8083 | Consumes `scan-results`, sends Slack webhook for high/critical findings. |

---

## Data Flow

```
1. Client → POST /api/v1/scans (X-API-Key: <key>)
   Body: { targetType, target, requestedBy }

2. ms-gateway validates API key, rate checks, routes to ms-intake

3. ms-intake:
   - Validates request body
   - Generates jobId (UUID)
   - Publishes to Kafka topic: scan-jobs
   - Returns 202 Accepted { jobId, status: "accepted" }

4. ms-scanner (async, may take seconds to minutes):
   - Consumes scan-jobs message
   - If targetType=github_url: clones repo to tmp dir
   - Walks source files, extracts AST (imports, functions, complexity)
   - For each file: calls Claude claude-sonnet-4-6 with security prompt
   - Claude returns structured issues via report_issues tool
   - Saves findings to SQLite (internal audit log)
   - Creates GitHub issues for high/critical findings
   - Publishes to Kafka topic: scan-results

5a. ms-results (async):
    - Consumes scan-results message
    - Persists ScanResult + ScanIssue entities to PostgreSQL
    - Client can poll GET /api/v1/results/{jobId} for findings

5b. ms-alert (async, parallel to 5a):
    - Consumes same scan-results message (separate consumer group)
    - Filters issues with severity = high or critical
    - POSTs Slack webhook with attachment per finding
```

---

## Kafka Message Schemas

**Topic: `scan-jobs`** (produced by ms-intake, consumed by ms-scanner)
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "requestedAt": "2024-01-15T10:30:00Z",
  "targetType": "file | github_url",
  "target": "/path/to/code OR https://github.com/owner/repo",
  "requestedBy": "username"
}
```

**Topic: `scan-results`** (produced by ms-scanner, consumed by ms-results AND ms-alert)
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "completedAt": "2024-01-15T10:31:45Z",
  "status": "success | failure",
  "issues": [
    {
      "severity": "low | medium | high | critical",
      "type": "function_name_or_pattern",
      "location": "path/to/file.py:42",
      "description": "SQL injection via unsanitised input in execute() call"
    }
  ]
}
```

---

## API Gateway & Auth

**Why a gateway:** Without it, every client must know each service's host and port. In production (ECS, EKS) services live behind private networking — only the gateway is public-facing. It also means auth, rate limiting, and CORS live in one place rather than being duplicated across four services.

**Current auth: API key (`X-API-Key` header)**
The gateway validates the key before forwarding any request. All downstream services also validate it as a second line of defence (defence in depth). The key is shared via `API_KEY` environment variable.

**Why not a dedicated auth microservice yet:**
A separate auth service (issuing JWTs, managing users, OAuth flows) adds significant operational complexity: its own database, token refresh logic, key rotation, availability requirements. The right time to add it is when you have multiple client types (web app, CLI, CI integration), or when you need user accounts with different permission levels. For API-to-API communication with a known set of clients, a shared secret or JWT validated at the gateway is sufficient.

**Natural upgrade path:**
```
Current:  X-API-Key → gateway validates static secret
Next:     X-API-Key → AWS API Gateway + Cognito authoriser (managed)
Later:    JWT (RS256) → gateway validates with JWKS, no auth service needed
Full:     Dedicated auth service (Keycloak / custom) → user management, OAuth, RBAC
```

---

## System Design Decisions

### Why Kafka over direct HTTP between scanner and results?

Scanning is slow — each file requires a Claude API call. A synchronous chain (intake → scanner → results) would hold the HTTP connection open for minutes. Kafka decouples request acceptance (< 50ms) from processing (seconds to minutes). The client gets an immediate `jobId` and polls for results.

### Why two consumer groups on scan-results?

ms-results and ms-alert both consume from `scan-results` but do completely different things. Independent consumer groups mean:
- Each service maintains its own offset — they can't block each other.
- If ms-alert is down, ms-results keeps consuming unaffected.
- You can add more consumers (e.g. ms-metrics, ms-webhook) without touching existing services.

### Why PostgreSQL for ms-results but SQLite for ms-scanner?

ms-results needs to serve concurrent read queries with pagination, filtering, and aggregation — PostgreSQL handles this natively. The scanner's SQLite is an internal audit log (file paths, function graphs, raw code) used only by that one process. It never needs to be queried by anything else. Using a shared database for this would couple services unnecessarily.

### Why Spring Cloud Gateway over AWS API Gateway?

For local development and self-hosted deployments, a code-managed gateway is simpler. On AWS, you'd put AWS API Gateway in front of the ALB and optionally keep the Spring Cloud Gateway for internal routing. The two are complementary, not competing.

---

## Known Gaps & Next Steps

These are real production gaps to address before a serious deployment:

| Gap | Impact | Fix |
|---|---|---|
| No dead-letter topic | Failed scan messages are lost on scanner crash | Add `scan-jobs.DLT` topic + retry logic in ms-scanner |
| ms-scanner SQLite not persistent in containers | Audit log lost on container restart | Mount EFS volume (AWS) or switch to shared PostgreSQL |
| Static API key auth | No per-client rate limiting or revocation | Upgrade to JWT validated at gateway |
| No circuit breaker | Gateway retries to a crashed service → cascading failure | Add Resilience4j circuit breaker to gateway routes |
| ms-scanner not horizontally scalable | Single scanner = bottleneck for high job volume | Kafka consumer group already configured — just run multiple replicas |
| No job status endpoint | Client can't distinguish "scanning" from "failed" | Add job status table to ms-results, ms-scanner publishes `status: scanning` on pickup |
| Slack-only alerting | | Add ms-alert webhook fanout (PagerDuty, email, Teams) |

---

## Running Locally

### Prerequisites
- Docker and Docker Compose
- An Anthropic API key

### Setup

1. Create `.env` at the repo root:

```env
# Required
ANTHROPIC_API_KEY=sk-ant-...

# Shared API key for all REST services
API_KEY=dev-secret-key

# Optional — Slack alerts
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...

# Optional — GitHub issue creation from scanner
GITHUB_TOKEN=ghp_...
GITHUB_REPO=owner/repo

# Optional — Langfuse observability
LANGFUSE_PUBLIC_KEY=pk-lf-...
LANGFUSE_SECRET_KEY=sk-lf-...
LANGFUSE_HOST=https://cloud.langfuse.com

# PostgreSQL (defaults work with docker-compose)
DB_NAME=crystal_results
DB_USERNAME=crystal
DB_PASSWORD=crystal
```

2. Start the full stack:

```bash
docker-compose up --build
```

### Triggering a scan

```bash
# Scan a GitHub repository
curl -X POST http://localhost:8080/api/v1/scans \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-secret-key" \
  -d '{
    "targetType": "github_url",
    "target": "https://github.com/owner/repo",
    "requestedBy": "your-name"
  }'
# Response: { "jobId": "...", "status": "accepted" }

# Poll for results
curl http://localhost:8080/api/v1/results/{jobId} \
  -H "X-API-Key: dev-secret-key"

# Severity breakdown
curl http://localhost:8080/api/v1/results/stats/severity \
  -H "X-API-Key: dev-secret-key"
```

All traffic goes through the gateway on port **8080**. Direct service ports (8081–8083) are for internal/debugging only.

### Swagger UI

| Service | URL |
|---|---|
| ms-intake | http://localhost:8081/swagger-ui.html |
| ms-results | http://localhost:8082/swagger-ui.html |
| ms-alert | http://localhost:8083/swagger-ui.html |

---

## Deploying to AWS

### Architecture on AWS

```
Internet
   │
   ▼
Route 53 (DNS)
   │
   ▼
ACM Certificate (SSL termination)
   │
   ▼
Application Load Balancer (ALB)
   │  Listener: HTTPS :443 → Target Group: ms-gateway ECS tasks
   ▼
ECS Fargate Cluster
   ├── ms-gateway     (1–3 tasks, public subnet, port 8080)
   ├── ms-intake      (1–N tasks, private subnet, port 8081)
   ├── ms-results     (1–N tasks, private subnet, port 8082)
   ├── ms-alert       (1–N tasks, private subnet, port 8083)
   └── ms-scanner     (1–N tasks, private subnet)
         │
         └── EFS mount for SQLite persistence
Infrastructure:
   Amazon MSK        — managed Kafka (replaces local Kafka + Zookeeper)
   Amazon RDS        — managed PostgreSQL (replaces local Postgres container)
   Amazon ECR        — container registry
   AWS Secrets Manager — all env vars and secrets
   Amazon CloudWatch — logs, metrics, alarms
   Amazon EFS        — persistent volume for ms-scanner SQLite
```

### Step-by-step deployment

#### 1. Push images to ECR

```bash
# Authenticate
aws ecr get-login-password --region ap-south-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.ap-south-1.amazonaws.com

# Create repos (once)
for svc in ms-gateway ms-intake ms-results ms-alert ms-scanner; do
  aws ecr create-repository --repository-name crystal/$svc --region ap-south-1
done

# Build and push each service
for svc in ms-gateway ms-intake ms-results ms-alert; do
  docker build -t crystal/$svc ./$svc
  docker tag crystal/$svc <account-id>.dkr.ecr.ap-south-1.amazonaws.com/crystal/$svc:latest
  docker push <account-id>.dkr.ecr.ap-south-1.amazonaws.com/crystal/$svc:latest
done

# Scanner (Python)
docker build -t crystal/ms-scanner ./ms-scanner
docker tag crystal/ms-scanner <account-id>.dkr.ecr.ap-south-1.amazonaws.com/crystal/ms-scanner:latest
docker push <account-id>.dkr.ecr.ap-south-1.amazonaws.com/crystal/ms-scanner:latest
```

#### 2. Provision infrastructure

**MSK (Kafka):**
```bash
# Create MSK cluster (2 brokers, kafka.t3.small for dev)
aws kafka create-cluster \
  --cluster-name crystal-kafka \
  --kafka-version 3.5.1 \
  --number-of-broker-nodes 2 \
  --broker-node-group-info \
    InstanceType=kafka.t3.small,ClientSubnets=[subnet-xxx,subnet-yyy],SecurityGroups=[sg-xxx]

# After creation, get bootstrap servers
aws kafka get-bootstrap-brokers --cluster-arn <arn>
# Use the PLAINTEXT endpoint value as KAFKA_BOOTSTRAP_SERVERS
```

**RDS PostgreSQL:**
```bash
aws rds create-db-instance \
  --db-instance-identifier crystal-postgres \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 16 \
  --master-username crystal \
  --master-user-password <password> \
  --db-name crystal_results \
  --vpc-security-group-ids sg-xxx \
  --db-subnet-group-name <subnet-group>
```

#### 3. Store secrets in AWS Secrets Manager

```bash
aws secretsmanager create-secret --name crystal/production \
  --secret-string '{
    "API_KEY": "...",
    "ANTHROPIC_API_KEY": "sk-ant-...",
    "DB_PASSWORD": "...",
    "SLACK_WEBHOOK_URL": "https://hooks.slack.com/...",
    "GITHUB_TOKEN": "ghp_...",
    "LANGFUSE_SECRET_KEY": "sk-lf-..."
  }'
```

Reference these in your ECS Task Definition as `valueFrom` secrets — never hardcode them as `environment` variables.

#### 4. ECS Task Definitions

Create a task definition per service. Key fields:

```json
{
  "family": "crystal-ms-gateway",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "containerDefinitions": [{
    "name": "ms-gateway",
    "image": "<account-id>.dkr.ecr.ap-south-1.amazonaws.com/crystal/ms-gateway:latest",
    "portMappings": [{ "containerPort": 8080 }],
    "environment": [
      { "name": "MS_INTAKE_URL", "value": "http://ms-intake.crystal.local:8081" },
      { "name": "MS_RESULTS_URL", "value": "http://ms-results.crystal.local:8082" },
      { "name": "MS_ALERT_URL", "value": "http://ms-alert.crystal.local:8083" }
    ],
    "secrets": [
      { "name": "API_KEY", "valueFrom": "arn:aws:secretsmanager:...:crystal/production:API_KEY::" }
    ],
    "logConfiguration": {
      "logDriver": "awslogs",
      "options": {
        "awslogs-group": "/crystal/ms-gateway",
        "awslogs-region": "ap-south-1",
        "awslogs-stream-prefix": "ecs"
      }
    }
  }]
}
```

Use **AWS Cloud Map** (Service Discovery) for internal DNS — this gives services hostnames like `ms-intake.crystal.local` that resolve within the VPC.

#### 5. ECS Services with ALB

```bash
# Create ECS cluster
aws ecs create-cluster --cluster-name crystal

# Create service (ms-gateway example — this one gets the ALB target group)
aws ecs create-service \
  --cluster crystal \
  --service-name ms-gateway \
  --task-definition crystal-ms-gateway:1 \
  --desired-count 2 \
  --launch-type FARGATE \
  --network-configuration "awsvpcConfiguration={subnets=[subnet-xxx],securityGroups=[sg-xxx]}" \
  --load-balancers "targetGroupArn=<alb-tg-arn>,containerName=ms-gateway,containerPort=8080"
```

Only **ms-gateway** gets an ALB target group. All other services are internal, registered only with Cloud Map.

#### 6. Auto-scaling

```bash
# Register ms-scanner as scalable (scales on Kafka consumer lag)
aws application-autoscaling register-scalable-target \
  --service-namespace ecs \
  --resource-id service/crystal/ms-scanner \
  --scalable-dimension ecs:service:DesiredCount \
  --min-capacity 1 \
  --max-capacity 10
```

For the scanner, scale based on the `scan-jobs` consumer lag metric from MSK. For Java services, scale on ALB RequestCount or CPU.

#### 7. Kafka topics on MSK

```bash
# Connect to MSK (via a bastion or Lambda in the same VPC)
kafka-topics.sh --bootstrap-server <msk-bootstrap> \
  --create --topic scan-jobs --partitions 6 --replication-factor 2

kafka-topics.sh --bootstrap-server <msk-bootstrap> \
  --create --topic scan-results --partitions 6 --replication-factor 2
```

Use **6 partitions** in production — this allows horizontal scaling up to 6 scanner replicas consuming in parallel from the same consumer group.

### Environment variables per service on AWS

| Service | Key variables |
|---|---|
| ms-gateway | `API_KEY`, `MS_INTAKE_URL`, `MS_RESULTS_URL`, `MS_ALERT_URL` |
| ms-intake | `API_KEY`, `KAFKA_BOOTSTRAP_SERVERS` |
| ms-scanner | `ANTHROPIC_API_KEY`, `KAFKA_BOOTSTRAP_SERVERS`, `GITHUB_TOKEN`, `GITHUB_REPO`, `LANGFUSE_*` |
| ms-results | `API_KEY`, `KAFKA_BOOTSTRAP_SERVERS`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| ms-alert | `API_KEY`, `KAFKA_BOOTSTRAP_SERVERS`, `SLACK_WEBHOOK_URL` |

### Cost estimate (dev/staging, ap-south-1)

| Resource | Spec | ~Monthly cost |
|---|---|---|
| ECS Fargate | 5 services × 0.25 vCPU / 0.5 GB | ~$25 |
| MSK | 2 × kafka.t3.small | ~$90 |
| RDS PostgreSQL | db.t3.micro, 20 GB | ~$15 |
| ALB | 1 ALB | ~$20 |
| ECR | 5 repos, ~500 MB images | ~$2 |
| CloudWatch Logs | ~5 GB/month | ~$3 |
| **Total** | | **~$155/month** |

MSK is the biggest cost driver. For a dev environment, replace MSK with a self-managed Kafka container on a small EC2 instance (t3.small, ~$15/month) to cut costs significantly.

---

## Repo Structure

```
project-crystal/
├── docker-compose.yml       # Full local stack
├── README.md                # This document
│
├── ms-gateway/              # Spring Cloud Gateway — public entry point
├── ms-intake/               # Spring Boot — scan request intake
├── ms-scanner/              # Python 3.11 — Claude AI scanner
├── ms-results/              # Spring Boot — results storage + query API
└── ms-alert/                # Spring Boot — Slack alerting
```
