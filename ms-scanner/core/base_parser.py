from abc import ABC, abstractmethod
from core.models import FileInfo

class BaseParser(ABC):
    
    @abstractmethod
    def parse_file(self, filepath: str) -> FileInfo:
        pass