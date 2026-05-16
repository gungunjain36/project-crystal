from db.database import init_db, save_file
from core.parser import CodeParser

init_db()
parser = CodeParser()
file_info = parser.parse_file("core/parser.py")
save_file(file_info)
print("Saved successfully!")