from db.database import init_db, save_file, save_issue
from core.parser import CodeParser
from agents.reviewer import review_file
from langfuse import get_client
from opentelemetry.instrumentation.anthropic import AnthropicInstrumentor
from dotenv import load_dotenv

load_dotenv()

AnthropicInstrumentor().instrument()
langfuse = get_client()

init_db()
parser = CodeParser()

with langfuse.start_as_current_observation(as_type="span", name="codesheriff-run"):
    file_infos = parser.parse_dir(".")
    
    for file_info in file_infos:
        with langfuse.start_as_current_observation(as_type="span", name=f"review-{file_info.filepath}"):
            file_id = save_file(file_info)
            issues = review_file(file_info, file_id=file_id)
            for issue in issues:
                save_issue(issue)
                print(issue)

langfuse.flush()