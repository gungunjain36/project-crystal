import ast
import logging
from pathlib import Path
from typing import Optional

from core.base_parser import BaseParser
from core.models import FunctionInfo, FileInfo

logger = logging.getLogger(__name__)

MAX_FILE_BYTES = 500_000  # skip files larger than 500 KB — likely generated


class PythonParser(BaseParser):

    def parse_file(self, filepath) -> Optional[FileInfo]:
        path = Path(filepath)

        if path.stat().st_size > MAX_FILE_BYTES:
            logger.warning("Skipping %s — exceeds %d bytes", filepath, MAX_FILE_BYTES)
            return None

        # Try UTF-8 first, fall back to latin-1 which never fails
        file_content = None
        for encoding in ("utf-8", "latin-1"):
            try:
                file_content = path.read_text(encoding=encoding)
                break
            except (UnicodeDecodeError, OSError):
                continue

        if file_content is None:
            logger.warning("Could not read %s — skipping", filepath)
            return None

        try:
            tree = ast.parse(file_content, filename=str(filepath))
        except SyntaxError as exc:
            logger.warning("Syntax error in %s (%s) — skipping", filepath, exc)
            return None

        imports: list[str] = []
        functions: list[FunctionInfo] = []
        classes: list[str] = []

        for node in ast.walk(tree):
            # Both sync and async function definitions
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                params = [arg.arg for arg in node.args.args]

                complexity = sum(
                    1 for n in ast.walk(node)
                    if isinstance(n, (
                        ast.If, ast.For, ast.While, ast.Try,
                        ast.AsyncFor, ast.AsyncWith, ast.With,
                        ast.ExceptHandler, ast.Match,
                    ))
                )

                calls: list[str] = []
                for n in ast.walk(node):
                    if isinstance(n, ast.Call):
                        if isinstance(n.func, ast.Name):
                            # Direct call: func()
                            calls.append(n.func.id)
                        elif isinstance(n.func, ast.Attribute):
                            # Method call: obj.method() — most injection sinks are here
                            calls.append(n.func.attr)

                functions.append(FunctionInfo(
                    name=node.name,
                    params=params,
                    return_type=ast.unparse(node.returns) if node.returns else None,
                    complexity=complexity,
                    calls=list(dict.fromkeys(calls)),  # deduplicate, preserve order
                    docstring=ast.get_docstring(node),
                ))

            elif isinstance(node, ast.Import):
                for alias in node.names:
                    imports.append(alias.name)

            elif isinstance(node, ast.ImportFrom):
                # node.module is None for relative imports like `from . import x`
                if node.module:
                    imports.append(node.module)

            elif isinstance(node, ast.ClassDef):
                classes.append(node.name)

        return FileInfo(
            filepath=str(filepath),
            imports=imports,
            functions=functions,
            classes=classes,
            docstring=ast.get_docstring(tree),
            total_lines=file_content.count("\n") + 1,
            raw_code=file_content,
        )
