from rich.console import Console
from rich.table import Table

console = Console()

table = Table(title="Test Table")
table.add_column("Name")
table.add_column("Value")
table.add_row("hello", "world")

console.print(table)