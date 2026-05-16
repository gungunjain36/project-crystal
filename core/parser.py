
from dataclasses import dataclass
import ast
from typing import Any


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

class CodeParser:

    def parse_file(self, filepath) -> FileInfo:
        with open(filepath, 'r', encoding="utf-8") as file:
            file_content = file.read()
        
            lines = file_content.count("\n") + 1

        tree = ast.parse(file_content)
        tree_walk = ast.walk(tree)

        imports = []
        functions = []
        classes = []

        for node in tree_walk:
            if isinstance(node, ast.FunctionDef):
                functions.append(node.name)
            elif isinstance(node, ast.Import):
                for i in node.names:
                    imports.append(i.name)
            elif isinstance(node, ast.ClassDef):
                classes.append(node.name)
            elif isinstance(node, ast.ImportFrom):
                imports.append(node.module)

        docstring = ast.get_docstring(tree)
        
        return FileInfo(
            filepath=filepath,
            imports=imports,
            functions=functions,
            classes=classes,
            docstring=docstring,
            total_lines=lines,
            raw_code=file_content
        )

def main():
    obj = CodeParser()      
    result = obj.parse_file("core/parser.py")  
    print(result)

main()