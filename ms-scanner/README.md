# ms-scanner

Python-based static security analysis service. Consumes scan jobs from Kafka, runs AST parsing + Claude AI vulnerability detection on the target codebase, and publishes results back to Kafka. Part of the Crystal monorepo.

## What it does

1. Listens on the `scan-jobs` Kafka topic for work
2. For each job: parses source files via AST, sends each file to Claude for security review
3. Saves findings to a local SQLite database and creates GitHub issues for high/critical findings
4. Publishes a structured result message to the `scan-results` Kafka topic

Supports two target types:
- `file` — scans a local directory path
- `github_url` — clones the repo to a temp directory, scans it, then cleans up

## Running locally

```bash
cd ms-scanner
pip install -r requirements.txt
python main.py
```

The service will block and wait for Kafka messages.

## Environment variables

| Variable | Default | Required | Description |
|---|---|---|---|
| `ANTHROPIC_API_KEY` | — | Yes | Claude API key |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | No | Kafka broker address |
| `KAFKA_TOPIC_SCAN_JOBS` | `scan-jobs` | No | Topic to consume jobs from |
| `KAFKA_TOPIC_SCAN_RESULTS` | `scan-results` | No | Topic to publish results to |
| `KAFKA_CONSUMER_GROUP` | `ms-scanner-group` | No | Kafka consumer group ID |
| `LANGFUSE_PUBLIC_KEY` | — | No | Langfuse observability public key |
| `LANGFUSE_SECRET_KEY` | — | No | Langfuse observability secret key |
| `LANGFUSE_HOST` | `https://cloud.langfuse.com` | No | Langfuse host |
| `GITHUB_TOKEN` | — | No | GitHub PAT for creating issues |
| `GITHUB_REPO` | — | No | GitHub repo (`owner/repo`) for issue creation |

## Package structure

```
ms-scanner/
├── main.py                  # Service entry point — Kafka consumer loop
├── requirements.txt
├── Dockerfile
├── config.py
├── agents/
│   └── reviewer.py          # Claude AI review agent
├── core/
│   ├── models.py            # Pydantic domain models
│   ├── python_parser.py     # AST-based Python file parser
│   ├── language_router.py   # Routes files to the correct parser by extension
│   ├── base_parser.py       # Base class for language parsers
│   └── prompt.py            # Claude system prompt
├── db/
│   └── database.py          # SQLite initialization and CRUD
├── kafka/
│   ├── consumer.py          # ScanJobConsumer
│   └── producer.py          # ScanResultProducer
├── tools/
│   └── schema_tool.py       # report_issues tool schema for Claude
└── utils/
    └── github_integration.py
```

## Kafka message schemas

**Consumes** from `scan-jobs`:
```json
{
  "jobId": "uuid",
  "requestedAt": "ISO timestamp",
  "targetType": "file | github_url",
  "target": "string",
  "requestedBy": "string"
}
```

**Publishes** to `scan-results`:
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
