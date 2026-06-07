import json
import os
import logging
import traceback
from datetime import datetime, timezone
from kafka import KafkaProducer

logger = logging.getLogger(__name__)


class ScanResultProducer:
    def __init__(self):
        bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        self._producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8"),
            acks="all",
            retries=3,
        )
        self._topic = os.getenv("KAFKA_TOPIC_SCAN_RESULTS", "scan-results")
        self._dlt_topic = os.getenv("KAFKA_TOPIC_SCAN_JOBS_DLT", "scan-jobs.DLT")

    def publish(self, job_id: str, status: str, issues: list[dict]) -> None:
        message = {
            "jobId": job_id,
            "completedAt": datetime.now(timezone.utc).isoformat(),
            "status": status,
            "issues": issues,
        }
        future = self._producer.send(self._topic, key=job_id, value=message)
        self._producer.flush()
        future.get(timeout=10)
        logger.info("Published scan-results for job %s status=%s issues=%d", job_id, status, len(issues))

    def publish_dlt(self, original_message: dict, exc: Exception) -> None:
        """Send a failed job to the dead-letter topic so it is not silently lost."""
        dlt_message = {
            "originalMessage": original_message,
            "failedAt": datetime.now(timezone.utc).isoformat(),
            "errorType": type(exc).__name__,
            "errorMessage": str(exc),
            "stackTrace": traceback.format_exc(),
        }
        job_id = original_message.get("jobId", "unknown")
        try:
            future = self._producer.send(self._dlt_topic, key=job_id, value=dlt_message)
            self._producer.flush()
            future.get(timeout=10)
            logger.warning("Published to DLT for job %s: %s", job_id, type(exc).__name__)
        except Exception as dlt_exc:
            logger.error("Failed to publish to DLT for job %s: %s", job_id, dlt_exc)

    def close(self) -> None:
        self._producer.close()
