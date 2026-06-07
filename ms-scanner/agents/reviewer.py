import logging
import time
from anthropic import Anthropic, APIError, APITimeoutError, RateLimitError
from core.models import FileInfo, IssueInfo, IssueList
from core.prompt import SYSTEM_PROMPT
from tools.schema_tool import tools

logger = logging.getLogger(__name__)

# Files larger than this are truncated before sending to avoid burning context
MAX_CODE_CHARS = 80_000  # ~20K tokens

_RETRY_EXCEPTIONS = (APIError, APITimeoutError, RateLimitError)
_MAX_RETRIES = 3
_RETRY_BACKOFF = [2, 5, 15]  # seconds between retries


def _build_user_prompt(file_info: FileInfo) -> str:
    function_details = []
    for f in file_info.functions:
        detail = (
            f"  • {f.name}("
            f"params={f.params}, "
            f"returns={f.return_type}, "
            f"cyclomatic_complexity={f.complexity}, "
            f"calls={f.calls}"
            f")"
        )
        if f.docstring:
            detail += f"\n    docstring: {f.docstring[:120]}"
        function_details.append(detail)

    code = file_info.raw_code
    truncated = False
    if len(code) > MAX_CODE_CHARS:
        code = code[:MAX_CODE_CHARS]
        truncated = True

    return f"""Perform a thorough security analysis of this file.

=== FILE METADATA ===
Path:         {file_info.filepath}
Total lines:  {file_info.total_lines}{'  [TRUNCATED to first ~80K chars]' if truncated else ''}
Imports:      {file_info.imports}
Classes:      {file_info.classes}

=== FUNCTION SIGNATURES ===
{chr(10).join(function_details) if function_details else '  (no functions)'}

=== SOURCE CODE ===
{code}
"""


def review_file(file_info: FileInfo, file_id: int) -> list[IssueInfo]:
    client = Anthropic()
    user_message = _build_user_prompt(file_info)

    response = None
    for attempt in range(_MAX_RETRIES):
        try:
            response = client.messages.create(
                model="claude-sonnet-4-6",
                max_tokens=8192,
                system=SYSTEM_PROMPT,
                messages=[{"role": "user", "content": user_message}],
                tools=tools,
                tool_choice={"type": "tool", "name": "report_issues"},
            )
            break
        except _RETRY_EXCEPTIONS as exc:
            if attempt == _MAX_RETRIES - 1:
                logger.error("Claude API failed after %d retries for %s: %s", _MAX_RETRIES, file_info.filepath, exc)
                return []
            wait = _RETRY_BACKOFF[attempt]
            logger.warning("Claude API error (%s), retrying in %ds [attempt %d/%d]", exc, wait, attempt + 1, _MAX_RETRIES)
            time.sleep(wait)

    if response is None:
        return []

    # Find the tool_use block — don't assume it's content[0]
    tool_block = next(
        (block for block in response.content if block.type == "tool_use"),
        None,
    )
    if tool_block is None:
        logger.warning("No tool_use block in response for %s", file_info.filepath)
        return []

    try:
        issue_list = IssueList.model_validate(tool_block.input)
    except Exception as exc:
        logger.error("Failed to parse Claude response for %s: %s", file_info.filepath, exc)
        return []

    issues: list[IssueInfo] = []
    for issue in issue_list.issues:
        issues.append(IssueInfo(
            file_id=file_id,
            file_name=file_info.filepath,
            function_name=issue.function_name,
            line_number=issue.line_number,
            title=issue.title or issue.description[:80],
            description=issue.description,
            severity=issue.severity.value,
            confidence=issue.confidence.value,
            cwe_id=issue.cwe_id or "",
            owasp_category=issue.owasp_category or "",
            affected_snippet=issue.affected_snippet or "",
            suggestions=issue.suggestions,
            remediation_example=issue.remediation_example or "",
            status="open",
            source="claude",
        ))

    logger.info("Claude found %d issue(s) in %s", len(issues), file_info.filepath)
    return issues
