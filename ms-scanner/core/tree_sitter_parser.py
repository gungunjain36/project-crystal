"""
Language-agnostic parser backed by tree-sitter.
Supports JavaScript, TypeScript, Java, Go, Ruby — and any language
that has a tree-sitter grammar package.

The Python parser stays as the dedicated AST-based implementation
(deeper extraction); this covers every other language.
"""
import logging
from pathlib import Path
from typing import Optional

from tree_sitter import Language, Parser, Node
from core.base_parser import BaseParser
from core.models import FileInfo, FunctionInfo

logger = logging.getLogger(__name__)

MAX_FILE_BYTES = 500_000


# ── Language registry ─────────────────────────────────────────────────────────

def _load_language(name: str) -> Optional[Language]:
    try:
        if name == "javascript":
            import tree_sitter_javascript as m
        elif name == "typescript":
            import tree_sitter_typescript as m
            return Language(m.language_typescript())
        elif name == "tsx":
            import tree_sitter_typescript as m
            return Language(m.language_tsx())
        elif name == "java":
            import tree_sitter_java as m
        elif name == "go":
            import tree_sitter_go as m
        elif name == "ruby":
            import tree_sitter_ruby as m
        else:
            return None
        return Language(m.language())
    except (ImportError, AttributeError) as exc:
        logger.warning("tree-sitter grammar not available for %s: %s", name, exc)
        return None


# ── Per-language extraction queries ──────────────────────────────────────────
# Each query captures: function names, call targets, import sources, class names.
# We use the tree-sitter query language (S-expression patterns).

_QUERIES: dict[str, dict[str, str]] = {
    "javascript": {
        "functions": """
            (function_declaration name: (identifier) @name) @func
            (function_expression name: (identifier) @name) @func
            (method_definition name: (property_identifier) @name) @func
            (arrow_function) @func
        """,
        "calls": """
            (call_expression function: (identifier) @call)
            (call_expression function: (member_expression property: (property_identifier) @call))
        """,
        "imports": """
            (import_statement source: (string (string_fragment) @src))
            (call_expression
              function: (identifier) @require (#eq? @require "require")
              arguments: (arguments (string (string_fragment) @src)))
        """,
        "classes": "(class_declaration name: (identifier) @name)",
    },
    "java": {
        "functions": """
            (method_declaration name: (identifier) @name) @func
            (constructor_declaration name: (identifier) @name) @func
        """,
        "calls": """
            (method_invocation name: (identifier) @call)
            (object_creation_expression type: (type_identifier) @call)
        """,
        "imports": "(import_declaration (scoped_identifier) @src)",
        "classes": "(class_declaration name: (identifier) @name)",
    },
    "go": {
        "functions": """
            (function_declaration name: (identifier) @name) @func
            (method_declaration name: (field_identifier) @name) @func
        """,
        "calls": """
            (call_expression function: (identifier) @call)
            (call_expression function: (selector_expression field: (field_identifier) @call))
        """,
        "imports": '(import_spec path: (interpreted_string_literal) @src)',
        "classes": "(type_spec name: (type_identifier) @name)",
    },
    "ruby": {
        "functions": """
            (method name: (identifier) @name) @func
            (singleton_method name: (identifier) @name) @func
        """,
        "calls": """
            (call method: (identifier) @call)
            (method_call method: (identifier) @call)
        """,
        "imports": "(require (argument_list (string (string_content) @src)))",
        "classes": "(class name: (constant) @name)",
    },
}

# TypeScript shares JavaScript queries
_QUERIES["typescript"] = _QUERIES["javascript"]
_QUERIES["tsx"] = _QUERIES["javascript"]


class TreeSitterParser(BaseParser):
    """
    Universal parser for any tree-sitter supported language.
    Produces the same FileInfo structure as PythonParser.
    """

    def __init__(self, language_name: str):
        self.language_name = language_name
        lang = _load_language(language_name)
        if lang is None:
            raise ImportError(f"tree-sitter grammar not available for {language_name}")
        self._parser = Parser(lang)
        self._lang = lang
        self._queries = _QUERIES.get(language_name, _QUERIES.get("javascript", {}))

    def parse_file(self, filepath) -> Optional[FileInfo]:
        path = Path(filepath)

        if path.stat().st_size > MAX_FILE_BYTES:
            logger.warning("Skipping %s — exceeds %d bytes", filepath, MAX_FILE_BYTES)
            return None

        for encoding in ("utf-8", "latin-1"):
            try:
                content = path.read_text(encoding=encoding)
                break
            except (UnicodeDecodeError, OSError):
                continue
        else:
            logger.warning("Cannot read %s", filepath)
            return None

        try:
            tree = self._parser.parse(bytes(content, "utf-8"))
        except Exception as exc:
            logger.warning("tree-sitter parse error on %s: %s", filepath, exc)
            return None

        functions = self._extract_functions(tree.root_node, content)
        imports = self._extract_captures(tree.root_node, "imports", "src", content)
        classes = self._extract_captures(tree.root_node, "classes", "name", content)

        return FileInfo(
            filepath=str(filepath),
            imports=imports,
            functions=functions,
            classes=classes,
            docstring=None,
            total_lines=content.count("\n") + 1,
            raw_code=content,
        )

    # ── Internal helpers ──────────────────────────────────────────────────────

    def _query(self, query_str: str) -> object:
        return self._lang.query(query_str)

    def _extract_functions(self, root: Node, content: str) -> list[FunctionInfo]:
        func_query_str = self._queries.get("functions", "")
        call_query_str = self._queries.get("calls", "")
        if not func_query_str:
            return []

        funcs: list[FunctionInfo] = []
        try:
            func_query = self._query(func_query_str)
            call_query = self._query(call_query_str) if call_query_str else None

            for pattern_index, captured_nodes in func_query.matches(root):
                func_node = captured_nodes.get("func")
                name_node = captured_nodes.get("name")
                if func_node is None:
                    continue

                name = _node_text(name_node, content) if name_node else "<anonymous>"
                line_no = func_node.start_point[0] + 1

                # Extract calls within this function node
                calls: list[str] = []
                if call_query:
                    for _, call_caps in call_query.matches(func_node):
                        call_name = call_caps.get("call")
                        if call_name:
                            txt = _node_text(call_name, content)
                            if txt:
                                calls.append(txt)

                complexity = _count_branches(func_node)

                funcs.append(FunctionInfo(
                    name=name,
                    params=[],         # param extraction is language-specific; skip for now
                    return_type=None,
                    complexity=complexity,
                    calls=list(dict.fromkeys(calls)),
                    docstring=None,
                ))
        except Exception as exc:
            logger.debug("Function query failed for %s: %s", self.language_name, exc)

        return funcs

    def _extract_captures(self, root: Node, query_key: str, capture: str, content: str) -> list[str]:
        query_str = self._queries.get(query_key, "")
        if not query_str:
            return []
        results: list[str] = []
        try:
            q = self._query(query_str)
            for _, caps in q.matches(root):
                node = caps.get(capture)
                if node:
                    txt = _node_text(node, content)
                    if txt:
                        results.append(txt)
        except Exception as exc:
            logger.debug("%s query failed: %s", query_key, exc)
        return results


def _node_text(node: Node, content: str) -> str:
    try:
        return content[node.start_byte:node.end_byte].strip()
    except Exception:
        return ""


def _count_branches(node: Node) -> int:
    """Approximate cyclomatic complexity by counting branching node types."""
    branch_types = {
        "if_statement", "if_expression",
        "for_statement", "for_in_statement", "enhanced_for_statement",
        "while_statement", "do_statement",
        "switch_statement", "case_clause",
        "try_statement", "catch_clause",
        "conditional_expression",      # ternary
        "binary_expression",           # catches && and || chains in some grammars
    }
    count = 0
    stack = [node]
    while stack:
        n = stack.pop()
        if n.type in branch_types:
            count += 1
        stack.extend(n.children)
    return count
