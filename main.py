from db.database import init_db, save_file, save_issue
from core.parser import CodeParser
from agents.reviewer import review_file

init_db()
parser = CodeParser()
file_infos = parser.parse_dir(".")

for file_info in file_infos:
    file_id = save_file(file_info)
    issues = review_file(file_info, file_id=file_id)
    for issue in issues:
        save_issue(issue)
        print(issue)

