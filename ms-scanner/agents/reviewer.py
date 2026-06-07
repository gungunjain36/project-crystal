from anthropic import Anthropic
from core.models import FileInfo, IssueInfo, IssueList
import json
from core.prompt import SYSTEM_PROMPT
from dotenv import load_dotenv
load_dotenv()
from tools.schema_tool import tools

def build_user_prompt(file_info: FileInfo) -> str:
    return f"""
        File: {file_info.filepath}
        Imports: {file_info.imports}
        Classes: {file_info.classes}
        Total lines: {file_info.total_lines}
        Functions: {[f.name for f in file_info.functions]}

        Code:
        {file_info.raw_code}
    """

client = Anthropic()

def review_file(file_info: FileInfo, file_id: int) -> list[IssueInfo]:
    user_message = user_message = build_user_prompt(file_info)
    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=4096,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": user_message}],
        tools=tools,
        tool_choice={"type": "tool", "name": "report_issues"}
    )
    raw_json = response.content[0].input
    issue_list = IssueList.model_validate(raw_json)
    
    
    issues = []
    for issue in issue_list.issues:
        issue_info = IssueInfo(
            file_id=file_id,
            file_name=file_info.filepath,
            function_name=issue.function_name,
            line_number=issue.line_number,
            description=issue.description,
            severity=issue.severity,
            suggestions=issue.suggestions,
            status="open"
        )
        issues.append(issue_info)
    
    return issues

