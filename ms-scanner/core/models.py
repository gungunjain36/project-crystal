from dataclasses import dataclass, field
from pydantic import BaseModel
from enum import Enum
from typing import Any, Optional


class Severity(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"
    critical = "critical"


class Confidence(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"


class Issue(BaseModel):
    function_name: str
    line_number: int
    severity: Severity
    confidence: Confidence = Confidence.medium
    cwe_id: Optional[str] = None
    owasp_category: Optional[str] = None
    title: Optional[str] = None
    description: str
    affected_snippet: Optional[str] = None
    suggestions: str
    remediation_example: Optional[str] = None


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
    title: str
    description: str
    severity: str
    confidence: str
    cwe_id: str
    owasp_category: str
    affected_snippet: str
    suggestions: str
    remediation_example: str
    status: str
    source: str = "claude"  # "claude" | "secret_detector" | "dependency_scanner"
