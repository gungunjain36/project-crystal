# Crystal

Crystal is a static security analysis tool for Python codebases. It parses source files using the Abstract Syntax Tree (AST), sends the extracted code structure and raw content to Claude AI for vulnerability detection, and persists all findings to a local SQLite database. Execution is traced end-to-end through Langfuse for observability and debugging.

---

## Overview

Crystal automates the process of security code review. Given a directory of Python files, it:

1. Walks the directory recursively and parses every `.py` file with the Python AST module, extracting functions, classes, imports, docstrings, complexity metrics, and raw source code.
2. Sends each parsed file to a Claude-powered review agent that acts as a cybersecurity expert. Claude is forced to respond through a structured tool (`report_issues`) so output is always machine-readable.
3. Stores all results — files, functions, and issues — in a local SQLite database (`my_database.db`).
4. Wraps every operation in Langfuse spans so you can inspect latency, token usage, and model behavior after each run.

Crystal does not execute code. All analysis is static and performed purely on AST output and raw source text.

---

## Architecture

```
Directory of .py files
        |
        v
  [core/parser.py]          AST-based extraction
  Produces FileInfo objects  (imports, functions, classes, raw code, line counts)
        |
        v
  [db/database.py]          Persists file + function metadata to SQLite
        |
        v
  [agents/reviewer.py]      Calls Claude Sonnet with security system prompt
  Uses report_issues tool    Forces structured JSON output
        |
        v
  [db/database.py]          Persists issues to SQLite
        |
        v
  [Langfuse]                Traces entire run for observability
```

### Component breakdown

| Component | File | Responsibility |
|---|---|---|
| Entry point | `main.py` | Orchestrates the full pipeline |
| Parser | `core/parser.py` | AST extraction from Python source files |
| Data models | `core/models.py` | Pydantic schemas for all domain objects |
| System prompt | `core/prompt.py` | Chain-of-thought security analysis instructions for Claude |
| Review agent | `agents/reviewer.py` | Manages Claude API calls and parses tool output |
| Tool schema | `tools/schema_tool.py` | Defines the `report_issues` structured output tool |
| Database | `db/database.py` | SQLite CRUD operations |

---

## Data Models

### FileInfo
Produced by the parser for each source file.

| Field | Type | Description |
|---|---|---|
| `filepath` | str | Absolute path to the file |
| `imports` | list[str] | All imported module names |
| `functions` | list[FunctionInfo] | Parsed function definitions |
| `classes` | list[str] | Class names defined in the file |
| `total_lines` | int | Total line count |
| `raw_code` | str | Full source text |

### FunctionInfo
Extracted per function within a file.

| Field | Type | Description |
|---|---|---|
| `name` | str | Function name |
| `params` | list[str] | Parameter names |
| `return_type` | str or None | Annotated return type |
| `complexity` | int | Count of branching statements (if/for/while/try) |
| `calls` | list[str] | Functions called within the body |
| `docstring` | str or None | Docstring content |

### Issue
A single security finding reported by Claude.

| Field | Type | Description |
|---|---|---|
| `function_name` | str | Function where the issue was found |
| `line_number` | int | Approximate line number |
| `description` | str | Plain-language explanation of the vulnerability |
| `severity` | Severity | One of: `low`, `medium`, `high`, `critical` |
| `suggestions` | list[str] | Actionable remediation steps |

### IssueInfo
Issue extended with file context for database storage — combines `Issue` fields with `FileInfo.filepath`.

---

## Database Schema

Crystal writes to a local SQLite database at `my_database.db`. The schema consists of three tables.

### FILES
Stores one row per analyzed source file.

```
FILEPATH    TEXT (primary key)
IMPORTS     TEXT (comma-separated)
CLASSES     TEXT (comma-separated)
LINES       INTEGER
DOCSTRING   TEXT
RAW_CODE    TEXT
```

### FUNCTIONS
Stores one row per function, linked to its parent file.

```
FILE_ID         TEXT (foreign key -> FILES.FILEPATH)
NAME            TEXT
PARAMS          TEXT (comma-separated)
RETURN_TYPE     TEXT
COMPLEXITY      INTEGER
DOCSTRING       TEXT
CALLS           TEXT (comma-separated)
```

### ISSUES
Stores one row per security finding.

```
FILE_ID         TEXT (foreign key -> FILES.FILEPATH)
FUNCTION_NAME   TEXT
LINE_NUMBER     INTEGER
DESCRIPTION     TEXT
SEVERITY        TEXT
SUGGESTIONS     TEXT (newline-separated)
STATUS          TEXT
```

---

## Review Agent

The review agent (`agents/reviewer.py`) is the core of Crystal. For each file it:

1. Constructs a user message containing the parsed file structure (imports, functions, complexity) and the full raw source code.
2. Calls `claude-sonnet-4-6` with the cybersecurity system prompt from `core/prompt.py` and the `report_issues` tool from `tools/schema_tool.py`.
3. Claude reasons through the code using chain-of-thought, then calls `report_issues` with a JSON array of findings.
4. The agent deserializes the tool input and returns a list of `IssueInfo` objects.

The system prompt instructs Claude to:
- Understand the file structure and function relationships before reporting.
- Identify vulnerabilities, insecure patterns, and exploitable code paths.
- Report exact locations with severity classifications and concrete remediation suggestions.
- Use the `report_issues` tool exclusively for output so results are always structured.

Severity levels follow a four-tier scale: `low`, `medium`, `high`, `critical`.

---

## Observability

Crystal integrates Langfuse for full-run tracing. Every analysis run is wrapped in a Langfuse trace, and each file review is wrapped in a child span. After the run completes, the client flushes all pending events to Langfuse.

This lets you inspect:
- Total tokens consumed per file and per run.
- Claude latency per file.
- Model inputs and outputs (the exact prompt sent and tool response received).
- Which files produced the most findings.

---

## Setup

### Prerequisites

- Python 3.9 or later
- An Anthropic API key
- A Langfuse account (cloud or self-hosted)

### Installation

```bash
git clone <repository-url>
cd codeview
pip install -r requirements.txt
```

### Environment variables

Create a `.env` file in the project root with the following keys:

```
ANTHROPIC_API_KEY=your_anthropic_api_key
LANGFUSE_SECRET_KEY=your_langfuse_secret_key
LANGFUSE_PUBLIC_KEY=your_langfuse_public_key
LANGFUSE_BASE_URL=https://cloud.langfuse.com
```

Adjust `LANGFUSE_BASE_URL` if you are using a self-hosted or regional Langfuse instance.

---

## Usage

Run Crystal against the current directory:

```bash
python main.py
```

Crystal will:
1. Initialize the database (creates `my_database.db` if it does not exist).
2. Parse all `.py` files found recursively from the current directory.
3. Review each file with Claude and print findings to stdout.
4. Write all results to the database.
5. Flush observability data to Langfuse.

To scan a different directory, modify the `parse_dir` call in `main.py` to point to your target path.

---

## Dependencies

| Package | Purpose |
|---|---|
| `anthropic` | Claude API client |
| `langfuse` | Observability and tracing |
| `opentelemetry-instrumentation-anthropic` | OTel instrumentation for Anthropic calls |
| `pydantic` | Data validation and model definitions |
| `python-dotenv` | Loading `.env` into environment |
| `rich` | Terminal output formatting |

Install all dependencies with:

```bash
pip install -r requirements.txt
```

---

## Project Structure

```
crystal/
├── main.py                  # Entry point
├── requirements.txt         # Python dependencies
├── config.py                # Reserved for future configuration
├── my_database.db           # SQLite output (created on first run)
│
├── core/
│   ├── parser.py            # AST-based Python file parser
│   ├── models.py            # Pydantic data models
│   └── prompt.py            # Claude system prompt
│
├── agents/
│   └── reviewer.py          # Claude review agent
│
├── db/
│   └── database.py          # SQLite initialization and CRUD
│
└── tools/
    └── schema_tool.py       # report_issues tool definition for Claude
```

---

## Limitations

- Crystal currently supports Python source files only. Other languages are not parsed.
- Analysis quality depends on the Claude model's understanding of the codebase context. Large files with many functions may produce less precise line number references.
- The database schema does not enforce foreign key constraints by default in SQLite; 
- Crystal does not deduplicate issues across runs. Re-running against the same directory will insert duplicate rows.
