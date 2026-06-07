# ms-intake

Spring Boot microservice that serves as the entry point for the Project Crystal static security analysis pipeline.

## What it does

`ms-intake` exposes a REST API that:
1. Receives scan requests from external clients
2. Validates the request body (Bean Validation)
3. Authenticates the caller via `X-API-Key` header
4. Produces a message to the Kafka topic `scan-jobs` for downstream processing
5. Returns a `202 Accepted` with a job ID that clients can use to track the scan

## How to run

### Prerequisites
- Java 17+
- Maven 3.8+
- A running Kafka broker (or use the defaults to connect to `localhost:9092`)

### Start the service

```bash
cd ms-intake
mvn spring-boot:run
```

Or with custom configuration:

```bash
SERVER_PORT=8081 \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
API_KEY=my-secure-key \
mvn spring-boot:run
```

### Build the JAR

```bash
mvn clean package
java -jar target/ms-intake-0.0.1-SNAPSHOT.jar
```

## Environment variables

| Variable                  | Default             | Description                                    |
|---------------------------|---------------------|------------------------------------------------|
| `SERVER_PORT`             | `8081`              | Port the service listens on                    |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`    | Kafka broker address(es)                       |
| `KAFKA_TOPIC_SCAN_JOBS`   | `scan-jobs`         | Kafka topic to publish scan jobs to            |
| `API_KEY`                 | `dev-secret-key`    | API key for authenticating requests            |

## API endpoints

| Method | Path                          | Description                        | Auth required |
|--------|-------------------------------|------------------------------------|---------------|
| POST   | `/api/v1/scans`               | Initiate a new security scan       | Yes           |
| GET    | `/api/v1/scans/{jobId}/status`| Get the status of a scan job       | Yes           |
| GET    | `/actuator/health`            | Health check                       | No            |
| GET    | `/actuator/info`              | Application info                   | No            |
| GET    | `/actuator/metrics`           | Metrics                            | No            |
| GET    | `/swagger-ui.html`            | Swagger UI (OpenAPI docs)          | No            |
| GET    | `/v3/api-docs`                | OpenAPI spec (JSON)                | No            |

## Example curl requests

### Scan a GitHub repository

```bash
curl -X POST http://localhost:8081/api/v1/scans \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-secret-key" \
  -d '{
    "targetType": "github_url",
    "target": "https://github.com/my-org/my-repo",
    "requestedBy": "user@example.com"
  }'
```

Response (202 Accepted):
```json
{
  "jobId": "550e8400-e29b-41d4-a716-446655440000",
  "requestedAt": "2024-01-15T10:30:00.000Z",
  "status": "accepted",
  "message": "Scan job accepted and queued for processing"
}
```

### Scan a file

```bash
curl -X POST http://localhost:8081/api/v1/scans \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev-secret-key" \
  -d '{
    "targetType": "file",
    "target": "/path/to/source/file.java",
    "requestedBy": "ci-pipeline"
  }'
```

### Get scan status

```bash
curl http://localhost:8081/api/v1/scans/550e8400-e29b-41d4-a716-446655440000/status \
  -H "X-API-Key: dev-secret-key"
```

### No API key (401)

```bash
curl -X POST http://localhost:8081/api/v1/scans \
  -H "Content-Type: application/json" \
  -d '{
    "targetType": "github_url",
    "target": "https://github.com/org/repo",
    "requestedBy": "user@example.com"
  }'
# Returns 401 Unauthorized
```

## Kafka message schema

Messages published to the `scan-jobs` topic follow this schema:

```json
{
  "jobId": "uuid",
  "requestedAt": "ISO 8601 timestamp",
  "targetType": "file | github_url",
  "target": "string",
  "requestedBy": "string"
}
```

## Project structure

```
src/main/java/com/crystal/msintake/
├── MsIntakeApplication.java          # Spring Boot entry point
├── controller/
│   └── ScanController.java           # REST endpoints
├── service/
│   ├── ScanService.java              # Service interface
│   └── ScanServiceImpl.java          # Service implementation
├── model/dto/
│   ├── ScanRequest.java              # Incoming request body
│   ├── ScanResponse.java             # Outgoing response body
│   ├── ScanJobMessage.java           # Kafka message payload
│   └── ErrorResponse.java           # Error response structure
├── config/
│   ├── KafkaProducerConfig.java      # Kafka producer beans
│   ├── OpenApiConfig.java            # OpenAPI/Swagger config
│   └── SecurityConfig.java          # API key filter registration
├── filter/
│   └── ApiKeyAuthFilter.java         # X-API-Key authentication filter
├── kafka/producer/
│   └── ScanJobProducer.java          # Kafka producer
└── exception/
    ├── GlobalExceptionHandler.java   # Global error handling
    └── InvalidRequestException.java  # Custom exception
```
