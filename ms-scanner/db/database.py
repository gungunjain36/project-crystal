import os
import json
import logging
import psycopg2
from psycopg2.extras import execute_values
from core.models import FileInfo, IssueInfo

logger = logging.getLogger(__name__)

_DB_URL = os.getenv(
    "DB_URL",
    "postgresql://crystal:crystal@localhost:5432/crystal_results"
)


def _connect():
    return psycopg2.connect(_DB_URL)


def init_db():
    conn = _connect()
    try:
        with conn:
            with conn.cursor() as cur:
                cur.execute("""
                    CREATE TABLE IF NOT EXISTS scanner_files (
                        id         SERIAL PRIMARY KEY,
                        filepath   TEXT NOT NULL,
                        imports    JSONB,
                        classes    JSONB,
                        lines      INTEGER,
                        docstring  TEXT,
                        raw_code   TEXT
                    )
                """)
                cur.execute("""
                    CREATE TABLE IF NOT EXISTS scanner_functions (
                        id          SERIAL PRIMARY KEY,
                        file_id     INTEGER REFERENCES scanner_files(id) ON DELETE CASCADE,
                        name        TEXT,
                        params      JSONB,
                        return_type TEXT,
                        complexity  INTEGER,
                        docstring   TEXT,
                        calls       JSONB
                    )
                """)
                cur.execute("""
                    CREATE TABLE IF NOT EXISTS scanner_issues (
                        id            SERIAL PRIMARY KEY,
                        file_id       INTEGER REFERENCES scanner_files(id) ON DELETE CASCADE,
                        file_name     TEXT,
                        function_name TEXT,
                        line_number   INTEGER,
                        description   TEXT,
                        severity      TEXT,
                        suggestions   TEXT,
                        status        TEXT DEFAULT 'open',
                        UNIQUE (file_name, function_name, line_number, severity)
                    )
                """)
        logger.info("scanner DB tables initialised")
    finally:
        conn.close()


def save_file(file_info: FileInfo) -> int:
    conn = _connect()
    try:
        with conn:
            with conn.cursor() as cur:
                cur.execute(
                    """INSERT INTO scanner_files
                         (filepath, imports, classes, lines, docstring, raw_code)
                       VALUES (%s, %s, %s, %s, %s, %s)
                       RETURNING id""",
                    (
                        file_info.filepath,
                        json.dumps(file_info.imports),
                        json.dumps(file_info.classes),
                        file_info.total_lines,
                        file_info.docstring,
                        file_info.raw_code,
                    ),
                )
                file_id = cur.fetchone()[0]

                if file_info.functions:
                    execute_values(
                        cur,
                        """INSERT INTO scanner_functions
                             (file_id, name, params, return_type, complexity, docstring, calls)
                           VALUES %s""",
                        [
                            (
                                file_id,
                                f.name,
                                json.dumps(f.params),
                                f.return_type,
                                f.complexity,
                                f.docstring,
                                json.dumps(f.calls),
                            )
                            for f in file_info.functions
                        ],
                    )
        return file_id
    except psycopg2.Error as e:
        logger.error("save_file error: %s", e)
        raise
    finally:
        conn.close()


def save_issue(issue_info: IssueInfo) -> None:
    conn = _connect()
    try:
        with conn:
            with conn.cursor() as cur:
                # ON CONFLICT DO NOTHING enforces the UNIQUE constraint as an idempotent upsert
                cur.execute(
                    """INSERT INTO scanner_issues
                         (file_id, file_name, function_name, line_number,
                          description, severity, suggestions, status)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                       ON CONFLICT (file_name, function_name, line_number, severity)
                       DO NOTHING""",
                    (
                        issue_info.file_id,
                        issue_info.file_name,
                        issue_info.function_name,
                        issue_info.line_number,
                        issue_info.description,
                        issue_info.severity,
                        issue_info.suggestions,
                        issue_info.status,
                    ),
                )
    except psycopg2.Error as e:
        logger.error("save_issue error: %s", e)
        raise
    finally:
        conn.close()
