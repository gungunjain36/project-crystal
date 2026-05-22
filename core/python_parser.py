
import ast
from typing import Any
from core.models import FunctionInfo, FileInfo
from pathlib import Path
from core.base_parser import BaseParser


class PythonParser(BaseParser):
    
    def parse_dir(self,dirpath):
        path_list = Path(dirpath).rglob("*.py")
        file_info_list = []
        for path in path_list:
            file_info_list.append(self.parse_file(path))
        return file_info_list


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

                params = []
                param = node.args.args
                for i in param:
                    params.append(i.arg)

                complexity = sum(1 for n in ast.walk(node) if isinstance(n, (ast.If, ast.For, ast.While, ast.Try)))
                calls = [n.func.id for n in ast.walk(node) if isinstance(n, ast.Call) and isinstance(n.func, ast.Name)]

                functions.append(
                    FunctionInfo(
                        name=node.name,
                        params=params,
                        return_type = ast.unparse(node.returns) if node.returns else None,
                        complexity= complexity,
                        calls=calls,
                        docstring= ast.get_docstring(node)
                    )
                )
            elif isinstance(node, ast.Import):
                for i in node.names:
                    imports.append(i.name)
            elif isinstance(node, ast.ClassDef):
                classes.append(node.name)
            elif isinstance(node, ast.ImportFrom):
                imports.append(node.module)

        docstring = ast.get_docstring(tree)
        
        return FileInfo(
            filepath=str(filepath),
            imports=imports,
            functions=functions,
            classes=classes,
            docstring=docstring,
            total_lines=lines,
            raw_code=file_content
        )