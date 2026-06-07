"""
Call graph construction and security-relevance scoring.

The goal: given N parsed files, rank them by how likely they are to contain
real, exploitable vulnerabilities — so we can send only the high-priority ones
to Claude and skip the rest.

Scoring model
─────────────
Each file gets a float score. Components:

  sink_score       +3 per distinct security sink the file's functions call
  entry_score      +5 if the file is an entry point (handles external input)
  reachable_score  +2 for every function reachable from an entry point that
                   also calls a sink (cross-file taint path)
  complexity_bonus +0.2 per unit of average cyclomatic complexity
  import_bonus     +1 if the file imports a security-sensitive library

Files with score >= PRIORITY_THRESHOLD are sent to Claude.
Files below threshold still get secret detection; they skip Claude.
"""
from __future__ import annotations

import logging
from collections import defaultdict, deque
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from core.models import FileInfo, FunctionInfo

logger = logging.getLogger(__name__)

# Score threshold above which a file is sent to Claude
PRIORITY_THRESHOLD = 3.0

# ── Security sinks ────────────────────────────────────────────────────────────
# Function / method names that are security-relevant call targets.
# Keyed by category for scoring transparency.

SINKS: dict[str, set[str]] = {
    "sql": {"execute", "executemany", "raw", "query", "callproc", "mogrify"},
    "command": {"system", "popen", "call", "run", "Popen", "check_call",
                "check_output", "spawn", "spawnl", "spawnle"},
    "code_exec": {"eval", "exec", "compile", "execfile"},
    "deserialization": {"loads", "load", "unpickle", "fromstring", "frombytes"},
    "file_ops": {"open", "read", "write", "rename", "remove", "unlink",
                 "chmod", "makedirs"},
    "network": {"get", "post", "put", "patch", "delete", "request",
                "urlopen", "fetch", "send", "sendall", "connect"},
    "template": {"render_template_string", "from_string", "Environment"},
    "crypto": {"md5", "sha1", "new"},   # weak hash usage
}

ALL_SINKS: set[str] = {s for sinks in SINKS.values() for s in sinks}

# ── Entry point heuristics ────────────────────────────────────────────────────

ENTRY_POINT_IMPORTS = {
    "flask", "fastapi", "django", "starlette", "aiohttp",
    "tornado", "falcon", "bottle", "sanic", "quart",
    "click", "typer", "argparse", "celery", "dramatiq",
    "rq", "apscheduler", "aws_lambda", "boto3",
}

ENTRY_POINT_FILENAMES = {
    "views", "routes", "handlers", "api", "endpoints", "controller",
    "main", "app", "server", "wsgi", "asgi", "lambda_handler",
}

ENTRY_POINT_FUNCTION_NAMES = {
    "main", "run", "start", "handle", "handler", "process",
    "dispatch", "execute", "invoke", "lambda_handler", "entrypoint",
}

# Libraries whose presence in imports elevates a file's risk score
SECURITY_SENSITIVE_IMPORTS = {
    "subprocess", "os", "sys", "pickle", "marshal", "shelve",
    "yaml", "xml", "lxml", "requests", "httpx", "urllib",
    "sqlite3", "psycopg2", "pymysql", "sqlalchemy",
    "hashlib", "hmac", "cryptography", "jwt",
    "jinja2", "mako", "chameleon",
    "paramiko", "ftplib", "smtplib",
}


# ── Data model ────────────────────────────────────────────────────────────────

@dataclass
class FileNode:
    filepath: str
    functions: list[FunctionInfo]
    imports: list[str]
    score: float = 0.0
    is_entry_point: bool = False
    sink_calls: list[str] = field(default_factory=list)
    score_breakdown: dict[str, float] = field(default_factory=dict)


@dataclass
class CallGraph:
    """
    Holds the parsed call graph for a set of files and exposes
    priority scoring for cost-effective Claude analysis.
    """
    # filepath → FileNode
    nodes: dict[str, FileNode] = field(default_factory=dict)
    # function_name → list of files that define it (for cross-file resolution)
    _func_index: dict[str, list[str]] = field(default_factory=lambda: defaultdict(list))

    @classmethod
    def build(cls, file_infos: list[FileInfo]) -> "CallGraph":
        graph = cls()

        # First pass: populate nodes and build function index
        for fi in file_infos:
            node = FileNode(
                filepath=fi.filepath,
                functions=fi.functions,
                imports=fi.imports,
            )
            graph.nodes[fi.filepath] = node
            for func in fi.functions:
                graph._func_index[func.name].append(fi.filepath)

        # Second pass: score every file
        for node in graph.nodes.values():
            graph._score_file(node)

        logger.info(
            "CallGraph built: %d files, %d above threshold (%.1f)",
            len(graph.nodes),
            sum(1 for n in graph.nodes.values() if n.score >= PRIORITY_THRESHOLD),
            PRIORITY_THRESHOLD,
        )
        return graph

    def high_priority_files(self) -> list[str]:
        """Files that should be sent to Claude, sorted by score descending."""
        return sorted(
            [fp for fp, n in self.nodes.items() if n.score >= PRIORITY_THRESHOLD],
            key=lambda fp: self.nodes[fp].score,
            reverse=True,
        )

    def skipped_files(self) -> list[str]:
        return [fp for fp, n in self.nodes.items() if n.score < PRIORITY_THRESHOLD]

    def context_for_file(self, filepath: str) -> str:
        """
        Returns a concise paragraph Claude can use as context about where
        this file sits in the call graph — improves finding quality.
        """
        node = self.nodes.get(filepath)
        if not node:
            return ""

        lines = []
        if node.is_entry_point:
            lines.append("⚠ This file is an ENTRY POINT — it directly handles external input.")
        if node.sink_calls:
            lines.append(f"Security sinks called: {', '.join(sorted(set(node.sink_calls))[:10])}")

        # Who calls functions in this file?
        callers = self._find_callers(filepath)
        if callers:
            lines.append(f"Called from: {', '.join(callers[:5])}")

        # What entry points can reach this file?
        entry_paths = self._entry_points_reaching(filepath)
        if entry_paths:
            lines.append(f"Reachable from entry points: {', '.join(entry_paths[:3])}")

        breakdown = node.score_breakdown
        if breakdown:
            parts = [f"{k}={v:.1f}" for k, v in breakdown.items() if v > 0]
            lines.append(f"Security score: {node.score:.1f} ({', '.join(parts)})")

        return "\n".join(lines)

    # ── Internal scoring ──────────────────────────────────────────────────────

    def _score_file(self, node: FileNode) -> None:
        score = 0.0
        breakdown: dict[str, float] = {}

        # Entry point detection
        stem = Path(node.filepath).stem.lower()
        if stem in ENTRY_POINT_FILENAMES:
            node.is_entry_point = True
            score += 5.0
            breakdown["entry_filename"] = 5.0

        imports_lower = {imp.lower().split(".")[0] for imp in node.imports if imp}
        if imports_lower & ENTRY_POINT_IMPORTS:
            matched = imports_lower & ENTRY_POINT_IMPORTS
            node.is_entry_point = True
            score += 4.0
            breakdown[f"entry_import({','.join(list(matched)[:2])})"] = 4.0

        for func in node.functions:
            if func.name.lower() in ENTRY_POINT_FUNCTION_NAMES:
                node.is_entry_point = True
                score += 2.0
                breakdown[f"entry_func({func.name})"] = 2.0
                break

        # Sink detection
        sink_score = 0.0
        for func in node.functions:
            for call in func.calls:
                if call in ALL_SINKS:
                    node.sink_calls.append(call)
                    sink_score += 3.0
        if sink_score:
            # Cap at 15 so a file calling 20 sinks doesn't dwarf everything
            capped = min(sink_score, 15.0)
            score += capped
            breakdown["sinks"] = capped

        # Security-sensitive imports
        sec_imports = imports_lower & SECURITY_SENSITIVE_IMPORTS
        if sec_imports:
            imp_score = min(len(sec_imports) * 1.0, 5.0)
            score += imp_score
            breakdown["sec_imports"] = imp_score

        # Complexity bonus
        if node.functions:
            avg_complexity = sum(f.complexity for f in node.functions) / len(node.functions)
            comp_score = min(avg_complexity * 0.2, 2.0)
            score += comp_score
            breakdown["complexity"] = round(comp_score, 2)

        node.score = round(score, 2)
        node.score_breakdown = {k: round(v, 2) for k, v in breakdown.items() if v > 0}

    def _find_callers(self, filepath: str) -> list[str]:
        """Files that call any function defined in `filepath`."""
        defined_funcs = {f.name for f in self.nodes[filepath].functions}
        callers: list[str] = []
        for fp, node in self.nodes.items():
            if fp == filepath:
                continue
            for func in node.functions:
                if set(func.calls) & defined_funcs:
                    callers.append(Path(fp).name)
                    break
        return callers

    def _entry_points_reaching(self, filepath: str) -> list[str]:
        """Entry-point file stems that have a call path to `filepath`."""
        entry_files = [fp for fp, n in self.nodes.items() if n.is_entry_point and fp != filepath]
        reaching: list[str] = []

        for ep in entry_files:
            if self._can_reach(ep, filepath):
                reaching.append(Path(ep).name)

        return reaching

    def _can_reach(self, source: str, target: str, max_depth: int = 6) -> bool:
        """BFS: can we reach `target` file from `source` file through the call graph?"""
        target_funcs = {f.name for f in self.nodes[target].functions}
        visited: set[str] = set()
        queue: deque[tuple[str, int]] = deque([(source, 0)])

        while queue:
            current, depth = queue.popleft()
            if depth > max_depth or current in visited:
                continue
            visited.add(current)

            node = self.nodes.get(current)
            if not node:
                continue

            for func in node.functions:
                for call in func.calls:
                    if call in target_funcs:
                        return True
                    # Resolve to file
                    for candidate_file in self._func_index.get(call, []):
                        if candidate_file not in visited:
                            queue.append((candidate_file, depth + 1))

        return False
