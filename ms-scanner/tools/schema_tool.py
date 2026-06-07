tools = [
    {
        "name": "report_issues",
        "description": "Report security issues found in the code",
        "input_schema": {
            "type": "object",
            "properties": {
                "issues": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "function_name": {"type": "string"},
                            "line_number": {"type": "integer"},
                            "description": {"type": "string"},
                            "severity": {
                                "type": "string",
                                "enum": ["low", "medium", "high", "critical"]
                            },
                            "suggestions": {"type": "string"}
                        },
                        "required": ["function_name", "line_number", "description", "severity", "suggestions"]
                    }
                }
            },
            "required": ["issues"]
        }
    }
]