from anthropic import Anthropic
from opentelemetry.instrumentation.anthropic import AnthropicInstrumentor
from langfuse import get_client
from dotenv import load_dotenv

load_dotenv()

AnthropicInstrumentor().instrument()

langfuse = get_client()
client = Anthropic()

with langfuse.start_as_current_observation(as_type="span", name="hello-codesheriff"):
    response = client.messages.create(
        model="claude-sonnet-4-6",
        max_tokens=256,
        messages=[{"role": "user", "content": "Review this code: print('hello world')"}]
    )
    print(response.content[0].text)

langfuse.flush()