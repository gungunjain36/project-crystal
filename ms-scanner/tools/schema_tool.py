tools = [
    {
        "name": "report_issues",
        "description": (
            "Report confirmed security vulnerabilities found in the code. "
            "Only call this once per file with ALL findings. "
            "Only include issues with confidence MEDIUM or HIGH. "
            "Do not report style issues, missing docs, or purely theoretical concerns."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "issues": {
                    "type": "array",
                    "description": "List of confirmed security issues. Empty array if none found.",
                    "items": {
                        "type": "object",
                        "properties": {
                            "function_name": {
                                "type": "string",
                                "description": "Name of the function containing the vulnerability. Use 'module-level' for top-level code."
                            },
                            "line_number": {
                                "type": "integer",
                                "description": "Exact line number of the vulnerable code."
                            },
                            "severity": {
                                "type": "string",
                                "enum": ["low", "medium", "high", "critical"],
                                "description": "Severity of the vulnerability."
                            },
                            "confidence": {
                                "type": "string",
                                "enum": ["low", "medium", "high"],
                                "description": "Confidence that this is a real, exploitable issue."
                            },
                            "cwe_id": {
                                "type": "string",
                                "description": "CWE identifier, e.g. 'CWE-89' for SQL injection."
                            },
                            "owasp_category": {
                                "type": "string",
                                "description": "OWASP Top 10 2021 category, e.g. 'A03:2021 – Injection'."
                            },
                            "title": {
                                "type": "string",
                                "description": "Short vulnerability title, e.g. 'SQL Injection via f-string in execute()'."
                            },
                            "description": {
                                "type": "string",
                                "description": "Clear explanation of the vulnerability, why it is dangerous, and what an attacker can do with it."
                            },
                            "affected_snippet": {
                                "type": "string",
                                "description": "The exact vulnerable line(s) of code, as they appear in the file."
                            },
                            "suggestions": {
                                "type": "string",
                                "description": "Concrete, actionable remediation steps."
                            },
                            "remediation_example": {
                                "type": "string",
                                "description": "A corrected version of the vulnerable code snippet showing how to fix it."
                            }
                        },
                        "required": [
                            "function_name",
                            "line_number",
                            "severity",
                            "confidence",
                            "cwe_id",
                            "owasp_category",
                            "title",
                            "description",
                            "affected_snippet",
                            "suggestions"
                        ]
                    }
                }
            },
            "required": ["issues"]
        }
    }
]
