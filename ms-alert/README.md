# ms-alert

Part of **Project Crystal** — a static security analysis platform. `ms-alert` is a Spring Boot microservice that consumes scan results from Kafka and sends Slack notifications when high or critical severity vulnerabilities are found.

## Purpose

After a scan job completes and publishes its results to the `scan-results` Kafka topic, `ms-alert`:

1. Deserializes the `ScanResultMessage` from the topic.
2. Filters issues with severity `high` or `critical`.
3. If any such issues are found, sends a rich Slack webhook notification with details per issue (severity, type, location, and description).

## How to Run

### Prerequisites

- Java 17+
- Maven 3.8+
- A running Kafka broker (default: `localhost:9092`)
- A Slack Incoming Webhook URL (optional but required for actual notifications)

### Build

```bash
cd ms-alert
mvn clean package -DskipTests
```

### Run

```bash
java -jar target/ms-alert-0.0.1-SNAPSHOT.jar
```

Or with Maven directly:

```bash
mvn spring-boot:run
```

### Run with Docker-style environment variables

```bash
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/WEBHOOK/URL \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
java -jar target/ms-alert-0.0.1-SNAPSHOT.jar
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8083` | HTTP port the service listens on |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address(es) |
| `KAFKA_CONSUMER_GROUP` | `ms-alert-group` | Kafka consumer group ID |
| `KAFKA_TOPIC_SCAN_RESULTS` | `scan-results` | Kafka topic to consume scan results from |
| `SLACK_WEBHOOK_URL` | *(empty)* | **Required for notifications.** The Slack Incoming Webhook URL |
| `SLACK_ALERTS_ENABLED` | `true` | Set to `false` to disable all Slack notifications without removing the URL |
| `ALERT_SEVERITIES` | `high,critical` | Comma-separated list of severities that trigger an alert |

## How to Obtain a Slack Webhook URL

1. Go to [https://api.slack.com/apps](https://api.slack.com/apps) and create a new Slack App (or use an existing one).
2. Under **Features**, click **Incoming Webhooks** and enable it.
3. Click **Add New Webhook to Workspace**, choose a channel, and authorize.
4. Copy the generated webhook URL (format: `https://hooks.slack.com/services/T.../B.../...`).
5. Set it as the `SLACK_WEBHOOK_URL` environment variable.

## What Triggers an Alert

An alert is sent when a scan result message consumed from the `scan-results` Kafka topic contains **at least one issue** with a severity matching the configured `ALERT_SEVERITIES` list (default: `high` or `critical`).

The Slack message includes:
- A header summarizing the job ID and count of critical/high issues.
- One attachment per critical/high issue, color-coded:
  - **Red (`danger`)** for `critical`
  - **Yellow (`warning`)** for `high`
- Each attachment shows the severity, type, location, and description of the issue.

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/alerts/health` | Service health status |
| `GET` | `/api/v1/alerts/config` | Current service configuration |
| `GET` | `/actuator/health` | Spring Boot actuator health |
| `GET` | `/swagger-ui.html` | OpenAPI / Swagger UI |

## Kafka Message Schema

The service expects messages on the `scan-results` topic in the following JSON format:

```json
{
  "jobId": "uuid",
  "completedAt": "ISO 8601 timestamp",
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
