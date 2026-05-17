import sqlite3
import json
from core.models import FunctionInfo, FileInfo, IssueInfo


def init_db():

    conn = sqlite3.connect("my_database.db")
    cursor = conn.cursor()

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS FILES(
                   ID INTEGER PRIMARY KEY AUTOINCREMENT,
                   FILEPATH TEXT,
                   IMPORTS TEXT,
                   CLASSES TEXT,
                   LINES INTEGER,
                   DOCSTRING TEXT,
                   RAW_CODE TEXT
        )
        """)


    cursor.execute("""
            CREATE TABLE IF NOT EXISTS FUNCTIONS(
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    FILE_ID INTEGER REFERENCES FILES(ID),
                    NAME TEXT,
                    PARAMS TEXT,
                    RETURN_TYPE TEXT,
                    COMPLEXITY INTEGER,
                    DOCSTRING TEXT,
                    CALLS TEXT
            )
            """)

    cursor.execute("""
            CREATE TABLE IF NOT EXISTS ISSUES(
                    ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    FILE_ID INTEGER REFERENCES FILES(ID),
                    FILE_NAME TEXT,
                    FUNCTION_NAME TEXT,
                    LINE_NUMBER INTEGER,
                    DESCRIPTION TEXT,
                    SEVERITY TEXT,
                    SUGGESTIONS TEXT,
                    STATUS TEXT DEFAULT 'open'
            )
            """)
    

    conn.commit()
    conn.close()


def save_file(file_info):
    conn = sqlite3.connect("my_database.db")
    cursor = conn.cursor()
    try:
        cursor.execute(
            """INSERT INTO FILES (FILEPATH, IMPORTS, CLASSES, LINES, DOCSTRING, RAW_CODE)
            VALUES (?, ?, ?, ?, ?, ?)""",
            (
                file_info.filepath,
                json.dumps(file_info.imports),
                json.dumps(file_info.classes),
                file_info.total_lines,
                file_info.docstring,
                file_info.raw_code
            )
        )
        file_id = cursor.lastrowid

        for func in file_info.functions:
            cursor.execute(
                """INSERT INTO FUNCTIONS (FILE_ID, NAME, PARAMS, RETURN_TYPE, COMPLEXITY, DOCSTRING, CALLS)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (file_id, func.name, json.dumps(func.params), func.return_type, func.complexity, func.docstring, json.dumps(func.calls))
            )
        conn.commit()
        return file_id
    except sqlite3.Error as e:
        conn.rollback()
        print(f"Database error: {e}")
    finally:
        conn.close()



def save_issue(issue_info):
    conn = sqlite3.connect("my_database.db")
    cursor = conn.cursor()
    try:
        cursor.execute(
            """SELECT ID FROM ISSUES 
            WHERE FILE_NAME = ? AND FUNCTION_NAME = ? AND LINE_NUMBER = ? AND SEVERITY = ?""",
            (issue_info.file_name, issue_info.function_name, issue_info.line_number, issue_info.severity)
        )
        existing = cursor.fetchone()

        if existing:
            return  
        
        
        cursor.execute(
            """INSERT INTO ISSUES (FILE_ID,FILE_NAME,FUNCTION_NAME,LINE_NUMBER,DESCRIPTION,SEVERITY,SUGGESTIONS,STATUS)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                issue_info.file_id,
                issue_info.file_name,
                issue_info.function_name,
                issue_info.line_number,
                issue_info.description,
                issue_info.severity,
                issue_info.suggestions,
                issue_info.status
            )
        )
        conn.commit()
    except sqlite3.Error as e:
        conn.rollback()
        print(f"Database error: {e}")
    finally:
        conn.close()