from db.database import init_db, save_file, save_issue
from core.parser import CodeParser
from agents.reviewer import review_file

init_db()
parser = CodeParser()
file_info = parser.parse_file("core/parser.py")
save_file(file_info)

issues = review_file(file_info, file_id=1)
for issue in issues:
    save_issue(issue)
    print(issue)