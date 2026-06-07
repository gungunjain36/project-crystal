import uuid
import shutil
import logging
import tempfile
from dotenv import load_dotenv

load_dotenv()

from db.database import init_db, save_file, save_issue
from core.language_router import LanguageRouter
from core.call_graph import CallGraph, PRIORITY_THRESHOLD
from core.secret_detector import scan_for_secrets
from core.dependency_scanner import scan_dependencies
from agents.reviewer import review_file
from kafka.consumer import ScanJobConsumer
from kafka.producer import ScanResultProducer
from langfuse import get_client
from opentelemetry.instrumentation.anthropic import AnthropicInstrumentor
from rich.console import Console
from utils.github_integration import create_github_issue

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger(__name__)

AnthropicInstrumentor().instrument()
langfuse = get_client()
console = Console()

init_db()
router = LanguageRouter()
producer = ScanResultProducer()


def _clone_github_repo(url: str, dest: str) -> None:
    from git import Repo
    Repo.clone_from(url, dest)


def _scan_target(job_id: str, target_type: str, target: str) -> list[dict]:
    """
    Four-pass scan pipeline:
      Pass 1 — Dependency scan (fast, no API cost)
      Pass 2 — Parse all files + build call graph
      Pass 3 — Secret detection on every file (fast, no API cost)
      Pass 4 — Claude AI review on HIGH-PRIORITY files only (call graph gated)

    Files below the priority threshold still get secret detection but skip Claude,
    cutting API cost by 50–70% on large repos with no meaningful loss in findings.
    """
    scan_dir = None
    cleanup = False
    issues_for_kafka: list[dict] = []

    try:
        if target_type == "github_url":
            scan_dir = tempfile.mkdtemp(prefix=f"crystal_{job_id}_")
            cleanup = True
            logger.info("Cloning %s → %s", target, scan_dir)
            _clone_github_repo(target, scan_dir)
        else:
            scan_dir = target

        with langfuse.start_as_current_observation(as_type="span", name=f"scan-job-{job_id}"):

            # ── Pass 1: dependency scan ────────────────────────────────────
            with langfuse.start_as_current_observation(as_type="span", name="pass1-dependencies"):
                dep_issues = scan_dependencies(scan_dir)
                for issue in dep_issues:
                    save_issue(issue)
                    issues_for_kafka.append(_to_kafka(issue))
                if dep_issues:
                    console.print(f"[yellow]Job {job_id}: {len(dep_issues)} dependency finding(s)[/yellow]")

            # ── Pass 2: parse + call graph ─────────────────────────────────
            with langfuse.start_as_current_observation(as_type="span", name="pass2-parse"):
                file_infos = router.parse_dir(scan_dir)
                console.print(
                    f"[bold cyan]Job {job_id}: parsed {len(file_infos)} files[/bold cyan]"
                )

            with langfuse.start_as_current_observation(as_type="span", name="pass2-call-graph"):
                call_graph = CallGraph.build(file_infos)
                high_priority = set(call_graph.high_priority_files())
                skipped = call_graph.skipped_files()
                console.print(
                    f"[cyan]Job {job_id}: call graph — "
                    f"{len(high_priority)} files to Claude, "
                    f"{len(skipped)} below threshold (score < {PRIORITY_THRESHOLD})[/cyan]"
                )

            # ── Pass 3 + 4: per-file secret detection + selective Claude review ──
            for file_info in file_infos:
                fp = file_info.filepath

                with langfuse.start_as_current_observation(
                    as_type="span", name=f"review-{fp}"
                ):
                    file_id = save_file(file_info)

                    # Secret detection runs on EVERY file regardless of priority
                    secret_issues = scan_for_secrets(fp, file_id)
                    for issue in secret_issues:
                        save_issue(issue)
                        issues_for_kafka.append(_to_kafka(issue))

                    # Claude review only for high-priority files
                    if fp in high_priority:
                        ctx = call_graph.context_for_file(fp)
                        claude_issues = review_file(file_info, file_id=file_id, call_graph_context=ctx)
                        for issue in claude_issues:
                            save_issue(issue)
                            if issue.severity in ("high", "critical"):
                                create_github_issue(issue)
                            issues_for_kafka.append(_to_kafka(issue))
                    else:
                        logger.debug("Skipping Claude for low-priority file: %s", fp)

        langfuse.flush()

        total = len(issues_for_kafka)
        critical = sum(1 for i in issues_for_kafka if i["severity"] == "critical")
        high = sum(1 for i in issues_for_kafka if i["severity"] == "high")
        console.print(
            f"[bold green]Job {job_id}: complete — "
            f"{total} issue(s) [{critical} critical, {high} high] | "
            f"Claude called on {len(high_priority)}/{len(file_infos)} files[/bold green]"
        )
        return issues_for_kafka

    finally:
        if cleanup and scan_dir:
            shutil.rmtree(scan_dir, ignore_errors=True)


def _to_kafka(issue) -> dict:
    return {
        "severity": issue.severity,
        "type": issue.title or issue.function_name,
        "location": f"{issue.file_name}:{issue.line_number}",
        "description": issue.description,
        "cweId": issue.cwe_id,
        "owaspCategory": issue.owasp_category,
        "confidence": issue.confidence,
        "source": issue.source,
    }


def run_service() -> None:
    consumer = ScanJobConsumer()
    console.print("[bold cyan]Crystal scanner service started — waiting for jobs[/bold cyan]")

    try:
        for job in consumer:
            job_id = job.get("jobId", str(uuid.uuid4()))
            target_type = job.get("targetType", "file")
            target = job.get("target", ".")
            logger.info("Received job id=%s targetType=%s target=%s", job_id, target_type, target)

            try:
                issues = _scan_target(job_id, target_type, target)
                producer.publish(job_id, "success", issues)
            except Exception as exc:
                logger.exception("Job %s failed: %s", job_id, exc)
                producer.publish(job_id, "failure", [])
                producer.publish_dlt(job, exc)

    finally:
        consumer.close()
        producer.close()


if __name__ == "__main__":
    run_service()
