# ms-results

Spring Boot microservice for Project Crystal that consumes scan results from Kafka and exposes a REST API to query findings persisted in PostgreSQL.

## Purpose

`ms-results` sits downstream of the scanning pipeline. It:
- Listens on the Kafka topic `scan-results` for completed scan payloads
- Persists findings (`ScanResult` + `ScanIssue` records) to PostgreSQL
- Provides a REST API for querying results, issues, and severity statistics
- Protects all endpoints with an API key header (`X-API-Key`)

## Prerequisites

- Java 17+
- Maven 3.8+ (or use the included `./mvnw` wrapper)
- PostgreSQL (for production use)
- Kafka broker

## How to Run

### With Maven

```bash
cd ms-results

# Using defaults (expects Postgres on localhost:5432, Kafka on localhost:9092)
./mvnw spring-boot:run

# With overrides
API_KEY=mysecret DB_URL=jdbc:postgresql://myhost:5432/crystal_results ./mvnw spring-boot:run
```

### Build JAR

```bash
./mvnw package -DskipTests
java -jar target/ms-results-0.0.1-SNAPSHOT.jar
```

### Run Tests

```bash
./mvnw test
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8082` | Port the service listens on |
| `DB_URL` | `jdbc:postgresql://localhost:5432/crystal_results` | JDBC URL for PostgreSQL |
| `DB_USERNAME` | `crystal` | Database username |
| `DB_PASSWORD` | `crystal` | Database password |
| `JPA_DDL_AUTO` | `update` | Hibernate DDL mode (`update`, `create-drop`, `none`) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address |
| `KAFKA_CONSUMER_GROUP` | `ms-results-group` | Kafka consumer group ID |
| `KAFKA_TOPIC_SCAN_RESULTS` | `scan-results` | Topic to consume from |
| `API_KEY` | `dev-secret-key` | API key required in `X-API-Key` header |

## REST API Reference

All endpoints require the `X-API-Key` header. Actuator and Swagger UI endpoints are public.

### List all scan results (paginated)

```
GET /api/v1/results?page=0&size=20
```

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "jobId": "abc-123",
      "completedAt": "2024-01-15T10:30:00Z",
      "status": "success",
      "createdAt": "2024-01-15T10:30:05Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

### Get full scan result by job ID

```
GET /api/v1/results/{jobId}
```

**Response 200:**
```json
{
  "id": "uuid",
  "jobId": "abc-123",
  "completedAt": "2024-01-15T10:30:00Z",
  "status": "success",
  "createdAt": "2024-01-15T10:30:05Z",
  "issues": [
    {
      "id": 1,
      "severity": "high",
      "type": "SQL_INJECTION",
      "location": "src/UserDao.java:42",
      "description": "SQL injection vulnerability detected"
    }
  ]
}
```

### Get issues for a job

```
GET /api/v1/results/{jobId}/issues
```

**Response 200:**
```json
[
  {
    "id": 1,
    "severity": "critical",
    "type": "HARDCODED_SECRET",
    "location": "config/secrets.java:10",
    "description": "Hardcoded API key found"
  }
]
```

### Get severity statistics

```
GET /api/v1/results/stats/severity
```

**Response 200:**
```json
{
  "critical": 3,
  "high": 12,
  "medium": 27,
  "low": 45
}
```

## Example curl Commands

```bash
# List results
curl -H "X-API-Key: dev-secret-key" http://localhost:8082/api/v1/results

# Get specific result
curl -H "X-API-Key: dev-secret-key" http://localhost:8082/api/v1/results/my-job-uuid

# Get issues for a job
curl -H "X-API-Key: dev-secret-key" http://localhost:8082/api/v1/results/my-job-uuid/issues

# Get severity breakdown
curl -H "X-API-Key: dev-secret-key" http://localhost:8082/api/v1/results/stats/severity

# Health check (no API key needed)
curl http://localhost:8082/actuator/health
```

## Swagger UI

Available at: `http://localhost:8082/swagger-ui.html`

## Kafka Message Schema

The service consumes messages from the `scan-results` topic in the following format:

```json
{
  "jobId": "uuid-string",
  "completedAt": "2024-01-15T10:30:00Z",
  "status": "success",
  "issues": [
    {
      "severity": "high",
      "type": "SQL_INJECTION",
      "location": "src/UserDao.java:42",
      "description": "SQL injection vulnerability detected"
    }
  ]
}
```

Duplicate messages (same `jobId`) are handled idempotently — the second message is silently skipped.

## Database Schema

Two tables are created automatically via Hibernate DDL:

- `scan_results` — one row per scan job
- `scan_issues` — one row per finding, FK to `scan_results`
