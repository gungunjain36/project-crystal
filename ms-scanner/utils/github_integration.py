import os
from github import Github
from core.models import IssueInfo
from dotenv import load_dotenv

load_dotenv()

def create_github_issue(issue_info: IssueInfo):
    token = os.getenv("GITHUB_TOKEN")
    repo_name = os.getenv("GITHUB_REPO")
    
    g = Github(token)
    repo = g.get_repo(repo_name)
    
    title = f"[{issue_info.severity.upper()}] {issue_info.function_name} at line {issue_info.line_number} in {issue_info.file_name}"

    body = f"""
    ## Security Issue Found by Crystal

    **File:** {issue_info.file_name}
    **Function:** {issue_info.function_name}
    **Line:** {issue_info.line_number}

    ## Description
    {issue_info.description}

    ## Suggestion
    {issue_info.suggestions}
    """

    label = issue_info.severity
    repo.create_issue(
        title=title,
        body=body,
        labels=[label]
    )