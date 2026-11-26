import json
import re
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
out = {'policy_decision_branches': source.count('return new PolicyResult('), 'method_physical_lines': end - start + 1}
# Extract fixed design constants from the pinned original source, not experimental observations.
for field, method in [('restart_error_rate_threshold', 'errorRate'), ('latency_threshold_ms', 'latencyMs'), ('queue_depth_threshold', 'queueDepth')]:
    values = {float(v) for v in re.findall(r'i\.' + method + r'\(\)\s*(?:>|<=)\s*(\d*\.?\d+)', source)}
    assert len(values) == 1, (field, values)
    value = values.pop()
    out[field] = int(value) if value.is_integer() else value
print(json.dumps(out, indent=2))
