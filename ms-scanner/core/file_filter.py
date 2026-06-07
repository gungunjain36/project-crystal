from pathlib import Path

# Directories that are never worth scanning
_SKIP_DIRS = {
    ".git", ".hg", ".svn",
    "__pycache__", ".mypy_cache", ".pytest_cache", ".ruff_cache",
    "node_modules", ".npm",
    "venv", ".venv", "env", ".env", "virtualenv",
    "dist", "build", ".next", ".nuxt", "out",
    "migrations", "alembic",          # DB migrations — auto-generated DDL
    "fixtures", "testdata", "mocks",  # test data files
    ".terraform", ".serverless",
    "coverage", ".coverage",
    "htmlcov", ".tox",
}

# File name patterns that are low-signal for security analysis
_SKIP_FILENAME_PATTERNS = (
    "test_",
    "_test.",
    ".test.",
    ".spec.",
    "_spec.",
    "conftest",
    "setup.py",
    "setup.cfg",
)

# Extensions we can analyse (extend as parsers are added)
_SCANNABLE_EXTENSIONS = {".py", ".js", ".ts", ".jsx", ".tsx", ".java", ".go"}


def should_scan(path: Path) -> bool:
    """Return True if this path is worth sending to a parser."""
    # Reject any path that passes through a skipped directory
    for part in path.parts:
        if part in _SKIP_DIRS:
            return False

    # Must be a regular file
    if not path.is_file():
        return False

    # Must have a scannable extension
    if path.suffix.lower() not in _SCANNABLE_EXTENSIONS:
        return False

    name = path.name.lower()

    # Skip generated / test files
    for pattern in _SKIP_FILENAME_PATTERNS:
        if pattern in name:
            return False

    return True


def is_test_file(path: Path) -> bool:
    """True for test files — still scanned but flagged separately."""
    name = path.name.lower()
    return any(p in name for p in ("test_", "_test.", ".test.", ".spec.", "_spec.", "conftest"))
