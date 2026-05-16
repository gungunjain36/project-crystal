# import ast

# code = """
# import os
# from typing import Any
# """

# tree = ast.parse(code)
# for node in ast.walk(tree):
#     if isinstance(node, ast.Import):
#         print(node.names)

import ast

code = """
import os
from typing import Any
def my_func(a, b):
    pass
class MyClass:
    pass
"""

tree = ast.parse(code)
for node in ast.walk(tree):
    print(type(node), node.__dict__)