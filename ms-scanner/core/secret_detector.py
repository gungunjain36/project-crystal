"""
Deterministic regex-based secret and hardcoded credential detector.
Runs before Claude — fast, zero API cost, catches patterns Claude might normalise over.
"""
import re
import logging
from pathlib import Path
from core.models import IssueInfo

logger = logging.getLogger(__name__)

# Each entry: (pattern, title, cwe_id, description_template)
_RULES: list[tuple[re.Pattern, str, str, str]] = [
    (
        re.compile(r'AKIA[0-9A-Z]{16}', re.MULTILINE),
        "Hardcoded AWS Access Key ID",
        "CWE-798",
        "An AWS Access Key ID is hardcoded in source code. Attackers who access this repository can use it to authenticate to AWS.",
    ),
    (
        re.compile(r'(?i)(aws_secret_access_key|aws_secret)\s*[=:]\s*["\']([A-Za-z0-9/+=]{40})["\']', re.MULTILINE),
        "Hardcoded AWS Secret Access Key",
        "CWE-798",
        "An AWS Secret Access Key is hardcoded. This provides full programmatic access to AWS resources.",
    ),
    (
        re.compile(r'ghp_[A-Za-z0-9]{36}', re.MULTILINE),
        "Hardcoded GitHub Personal Access Token",
        "CWE-798",
        "A GitHub Personal Access Token is hardcoded. This grants access to GitHub repositories and APIs.",
    ),
    (
        re.compile(r'github_pat_[A-Za-z0-9_]{82}', re.MULTILINE),
        "Hardcoded GitHub Fine-Grained PAT",
        "CWE-798",
        "A GitHub fine-grained Personal Access Token is hardcoded in source code.",
    ),
    (
        re.compile(r'sk-ant-[A-Za-z0-9\-_]{80,}', re.MULTILINE),
        "Hardcoded Anthropic API Key",
        "CWE-798",
        "An Anthropic API key is hardcoded. This could be used to make billable API calls.",
    ),
    (
        re.compile(r'sk-[A-Za-z0-9]{48}', re.MULTILINE),
        "Hardcoded OpenAI API Key",
        "CWE-798",
        "An OpenAI API key is hardcoded. This could be used to make billable API calls.",
    ),
    (
        re.compile(r'-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----', re.MULTILINE),
        "Private Key in Source Code",
        "CWE-321",
        "A private key is embedded in source code. Anyone with read access to this repository can impersonate the key owner.",
    ),
    (
        re.compile(r'(?i)(password|passwd|pwd)\s*=\s*["\'](?!.*\{)[^"\']{6,}["\']', re.MULTILINE),
        "Hardcoded Password",
        "CWE-259",
        "A password appears to be hardcoded as a string literal. Hardcoded passwords cannot be rotated without a code change.",
    ),
    (
        re.compile(r'(?i)(secret_key|jwt_secret|signing_secret|app_secret)\s*=\s*["\'][^"\']{8,}["\']', re.MULTILINE),
        "Hardcoded Secret Key",
        "CWE-321",
        "A secret key used for signing or encryption is hardcoded. This undermines the security of any system relying on it.",
    ),
    (
        re.compile(r'(?i)(api_key|apikey|api_token)\s*=\s*["\'][A-Za-z0-9\-_]{16,}["\']', re.MULTILINE),
        "Hardcoded API Key",
        "CWE-798",
        "An API key is hardcoded in source code. It should be loaded from environment variables or a secrets manager.",
    ),
    (
        re.compile(r'postgres(?:ql)?://[^:]+:[^@]{3,}@', re.MULTILINE),
        "Database Credentials in Connection String",
        "CWE-259",
        "A database connection string with embedded credentials is hardcoded. Credentials are exposed to anyone reading the source.",
    ),
    (
        re.compile(r'mysql://[^:]+:[^@]{3,}@', re.MULTILINE),
        "MySQL Credentials in Connection String",
        "CWE-259",
        "A MySQL connection string with embedded credentials is hardcoded.",
    ),
    (
        re.compile(r'(?i)stripe[_-]?secret[_-]?key\s*=\s*["\']sk_(?:live|test)_[A-Za-z0-9]{24,}["\']', re.MULTILINE),
        "Hardcoded Stripe Secret Key",
        "CWE-798",
        "A Stripe secret key is hardcoded. This can be used to make charges, issue refunds, or access customer data.",
    ),
    (
        re.compile(r'twilio.*?["\']SK[0-9a-f]{32}["\']', re.MULTILINE | re.DOTALL),
        "Hardcoded Twilio API Key",
        "CWE-798",
        "A Twilio API key is hardcoded in source code.",
    ),
]


def _line_number_of_match(content: str, match_start: int) -> int:
    return content[:match_start].count("\n") + 1


def scan_for_secrets(filepath: str, file_id: int) -> list[IssueInfo]:
    """
    Run all secret detection rules against a file's raw content.
    Returns IssueInfo objects ready for persistence.
    """
    path = Path(filepath)
    try:
        content = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return []

    issues: list[IssueInfo] = []

    for pattern, title, cwe_id, description in _RULES:
        for match in pattern.finditer(content):
            line_no = _line_number_of_match(content, match.start())
            snippet = match.group(0)
            # Redact the actual value in the snippet for safety
            redacted = _redact(snippet)

            logger.warning("Secret detected: %s in %s line %d", title, filepath, line_no)

            issues.append(IssueInfo(
                file_id=file_id,
                file_name=filepath,
                function_name="module-level",
                line_number=line_no,
                title=title,
                description=description,
                severity="critical",
                confidence="high",
                cwe_id=cwe_id,
                owasp_category="A02:2021 – Cryptographic Failures",
                affected_snippet=redacted,
                suggestions=(
                    "Remove the hardcoded value immediately. "
                    "Load it from an environment variable (os.getenv) or a secrets manager (AWS Secrets Manager, HashiCorp Vault). "
                    "Rotate the credential — treat it as compromised from the moment it was committed."
                ),
                remediation_example='import os\nvalue = os.getenv("SECRET_NAME")',
                status="open",
                source="secret_detector",
            ))

    return issues


def _redact(value: str) -> str:
    """Show only the first 6 and last 4 characters of a detected secret."""
    if len(value) <= 12:
        return "*" * len(value)
    return value[:6] + "*" * (len(value) - 10) + value[-4:]
