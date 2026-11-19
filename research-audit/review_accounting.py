import csv,json,re
from pathlib import Path
from collections import defaultdict
root=Path(__file__).resolve().parent
groups=defaultdict(list)
with (root/'inputs/original/results-offline/runs.csv').open(newline='') as f:
    for row in csv.DictReader(f):
        groups[row['scenario'],row['mode']].append(row)
out=[]
for (scenario,mode),rows in sorted(groups.items()):
    fields=('proposal','decision','executed','unsafeExecuted','recovered')
    values={tuple(r[k] for k in fields) for r in rows}
    assert len(values)==1
    out.append(dict(scenario=scenario,mode=mode,repeats=len(rows),**dict(zip(fields,values.pop()))))
source=(root/'inputs/original-policy.java').read_text()
lines=source.splitlines()
start=next(i for i,l in enumerate(lines) if 'PolicyResult evaluate(' in l)
depth=0
for end in range(start,len(lines)):
    depth+=lines[end].count('{')-lines[end].count('}')
    if depth==0: break
print(json.dumps({'method_physical_lines':end-start+1,'scenario_mode_outcomes':out},indent=2))
