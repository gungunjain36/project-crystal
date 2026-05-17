import sqlite3
from rich.console import Console
from rich.table import Table
from rich import box
from rich.markup import escape

console = Console()

SEVERITY_COLORS = {
    "critical": "bold red",
    "high": "orange1",
    "medium": "yellow",
    "low": "green"
}

STATUS_COLORS = {
    "open": "red",
    "resolved": "green"
}

def show_dashboard():
    conn = sqlite3.connect("my_database.db")
    cursor = conn.cursor()
    cursor.execute("""
        SELECT FILE_NAME, FUNCTION_NAME, LINE_NUMBER, DESCRIPTION, SEVERITY, SUGGESTIONS, STATUS
        FROM ISSUES
        ORDER BY 
            CASE SEVERITY 
                WHEN 'critical' THEN 1
                WHEN 'high' THEN 2
                WHEN 'medium' THEN 3
                WHEN 'low' THEN 4
            END
    """)
    rows = cursor.fetchall()
    conn.close()

    table = Table(
        title="Crystal — Security Issues",
        box=box.ROUNDED,
        show_lines=True
    )

    table.add_column("File", style="cyan", max_width=20)
    table.add_column("Function", style="blue", max_width=15)
    table.add_column("Line", justify="center", max_width=6)
    table.add_column("Description", max_width=40)
    table.add_column("Severity", justify="center", max_width=10)
    table.add_column("Suggestions", max_width=40)
    table.add_column("Status", justify="center", max_width=10)

    for row in rows:
        file_name, function_name, line_number, description, severity, suggestions, status = row
        severity_color = SEVERITY_COLORS.get(severity, "white")
        status_color = STATUS_COLORS.get(status, "white")


        table.add_row(
            str(file_name),
            str(function_name),
            str(line_number),
            escape(str(description)),
            f"[{severity_color}]{severity}[/{severity_color}]",
            escape(str(suggestions)),
            f"[{status_color}]{status}[/{status_color}]"
        )

    console.print(table)
    console.print(f"\n[bold]Total issues: {len(rows)}[/bold]")