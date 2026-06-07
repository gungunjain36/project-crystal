import json
import os
import logging
from kafka import KafkaConsumer

logger = logging.getLogger(__name__)


class ScanJobConsumer:
    def __init__(self):
        bootstrap_servers = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        group_id = os.getenv("KAFKA_CONSUMER_GROUP", "ms-scanner-group")
        self._topic = os.getenv("KAFKA_TOPIC_SCAN_JOBS", "scan-jobs")
        self._consumer = KafkaConsumer(
            self._topic,
            bootstrap_servers=bootstrap_servers,
            group_id=group_id,
            auto_offset_reset="earliest",
            enable_auto_commit=True,
            value_deserializer=lambda b: json.loads(b.decode("utf-8")),
            consumer_timeout_ms=int(os.getenv("KAFKA_CONSUMER_TIMEOUT_MS", "-1")),
        )
        logger.info("ScanJobConsumer listening on topic=%s group=%s", self._topic, group_id)

    def __iter__(self):
        for message in self._consumer:
            yield message.value

    def close(self) -> None:
        self._consumer.close()
