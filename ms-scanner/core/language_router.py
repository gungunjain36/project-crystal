import logging
from pathlib import Path

from core.python_parser import PythonParser
from core.file_filter import should_scan
from core.models import FileInfo

logger = logging.getLogger(__name__)

_PARSERS = {
    ".py": PythonParser(),
}


class LanguageRouter:

    def parse_dir(self, dirpath: str) -> list[FileInfo]:
        results: list[FileInfo] = []
        skipped = 0

        for path in Path(dirpath).rglob("*"):
            if not should_scan(path):
                skipped += 1
                continue

            file_info = self.parse_file(path)
            if file_info is not None:
                results.append(file_info)

        logger.info(
            "parse_dir(%s): %d files queued for review, %d skipped",
            dirpath, len(results), skipped,
        )
        return results

    def parse_file(self, filepath) -> FileInfo | None:
        ext = Path(filepath).suffix.lower()
        parser = _PARSERS.get(ext)
        if parser is None:
            return None
        try:
            return parser.parse_file(filepath)
        except Exception as exc:
            logger.warning("Parser error on %s: %s — skipping", filepath, exc)
            return None
