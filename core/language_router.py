from core.python_parser import PythonParser
from pathlib import Path

PARSERS = {
    ".py" : PythonParser(),
}

class LanguageRouter:

    def parse_dir(self,dirpath):
        path_list = Path(dirpath).rglob("*")
        file_info_list = []
        for path in path_list:
            result = self.parse_file(path)
            if result:
                file_info_list.append(result)
        return file_info_list

    def parse_file(self, filepath):
        ext = Path(filepath).suffix
        parser = PARSERS.get(ext)
        if parser:
            return parser.parse_file(filepath)
        return None