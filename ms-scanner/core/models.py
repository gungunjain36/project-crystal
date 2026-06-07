
from dataclasses import dataclass
from pydantic import BaseModel
from enum import Enum
from typing import Any

class Severity(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"
    critical = "critical"

class Issue(BaseModel):
    function_name: str
    line_number: int
    description: str
    severity: Severity
    suggestions: str

class IssueList(BaseModel):
    issues: list[Issue]

@dataclass
class FunctionInfo:
    name: str
    params: list[str]
    return_type: Any
    complexity: int
    calls: list[str]
    docstring: str


@dataclass
class FileInfo:
    filepath: str
    imports: list[str]
    functions: list[FunctionInfo]
    classes: list[str]
    total_lines: int
    docstring: str
    raw_code: str

@dataclass
class IssueInfo:
    file_id: int
    file_name: str
    function_name: str
    line_number: int
    description: str
    severity: str
    suggestions: str
    status: str


