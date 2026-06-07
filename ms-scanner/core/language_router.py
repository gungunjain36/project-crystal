import logging
from pathlib import Path

from core.python_parser import PythonParser
from core.file_filter import should_scan
from core.models import FileInfo

logger = logging.getLogger(__name__)


def _build_parsers() -> dict:
    """
    Build the extension → parser map.
    Python uses the dedicated AST parser for maximum extraction quality.
    Everything else uses tree-sitter via TreeSitterParser.
    Parsers for unavailable grammars are silently omitted.
    """
    parsers = {".py": PythonParser()}

    ts_languages = {
        ".js": "javascript",
        ".mjs": "javascript",
        ".cjs": "javascript",
        ".jsx": "javascript",
        ".ts": "typescript",
        ".tsx": "tsx",
        ".java": "java",
        ".go": "go",
        ".rb": "ruby",
    }

    for ext, lang_name in ts_languages.items():
        try:
            from core.tree_sitter_parser import TreeSitterParser
            parsers[ext] = TreeSitterParser(lang_name)
            logger.debug("Registered parser: %s → %s", ext, lang_name)
        except (ImportError, Exception) as exc:
            logger.info("Skipping %s parser (%s): %s", lang_name, ext, exc)

    return parsers


_PARSERS: dict = {}  # lazily initialised on first use


def _get_parsers() -> dict:
    global _PARSERS
    if not _PARSERS:
        _PARSERS = _build_parsers()
        exts = ", ".join(sorted(_PARSERS.keys()))
        logger.info("Language router ready. Supported extensions: %s", exts)
    return _PARSERS


class LanguageRouter:

    def parse_dir(self, dirpath: str) -> list[FileInfo]:
        parsers = _get_parsers()
        results: list[FileInfo] = []
        skipped = 0

        for path in Path(dirpath).rglob("*"):
            if not should_scan(path):
                skipped += 1
                continue

            ext = path.suffix.lower()
            if ext not in parsers:
                skipped += 1
                continue

            file_info = self.parse_file(path)
            if file_info is not None:
                results.append(file_info)

        logger.info(
            "parse_dir(%s): %d files queued for analysis, %d skipped",
            dirpath, len(results), skipped,
        )
        return results

    def parse_file(self, filepath) -> FileInfo | None:
        parsers = _get_parsers()
        ext = Path(filepath).suffix.lower()
        parser = parsers.get(ext)
        if parser is None:
            return None
        try:
            return parser.parse_file(filepath)
        except Exception as exc:
            logger.warning("Parser error on %s: %s — skipping", filepath, exc)
            return None
