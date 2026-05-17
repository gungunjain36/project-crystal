from db.database import init_db, save_file, save_issue
from core.parser import CodeParser
from agents.reviewer import review_file
from langfuse import get_client
from opentelemetry.instrumentation.anthropic import AnthropicInstrumentor
from dotenv import load_dotenv
from core.dashboard import show_dashboard
from rich.progress import Progress, SpinnerColumn, TextColumn
from rich.console import Console
from utils.github_integration import create_github_issue

load_dotenv()

AnthropicInstrumentor().instrument()
langfuse = get_client()
console = Console()

init_db()
parser = CodeParser()

with langfuse.start_as_current_observation(as_type="span", name="codesheriff-run"):
    file_infos = parser.parse_dir(".")
    console.print(f"[bold cyan]Found {len(file_infos)} files to review...[/bold cyan]")

    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        transient=True
    ) as progress:
        for file_info in file_infos:
            with langfuse.start_as_current_observation(as_type="span", name=f"review-{file_info.filepath}"):
                task = progress.add_task(f"Reviewing {file_info.filepath}...", total=None)
                file_id = save_file(file_info)
                issues = review_file(file_info, file_id=file_id)
                for issue in issues:
                    save_issue(issue)
                progress.remove_task(task)

langfuse.flush()
console.print("[bold green]Review complete![/bold green]")
# show_dashboard()

for issue in issues:
    save_issue(issue)
    create_github_issue(issue)