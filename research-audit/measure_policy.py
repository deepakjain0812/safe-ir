import json
from pathlib import Path
source = (Path(__file__).resolve().parent / 'inputs/original-policy.java').read_text()
lines = source.splitlines()
start = next(i for i, line in enumerate(lines) if 'PolicyResult evaluate(' in line)
depth = 0
for end in range(start, len(lines)):
    depth += lines[end].count('{') - lines[end].count('}')
    if depth == 0:
        break
else:
    raise ValueError('Unclosed method')
print(json.dumps({'policy_decision_branches': source.count('return new PolicyResult('), 'method_physical_lines': end - start + 1}, indent=2))
