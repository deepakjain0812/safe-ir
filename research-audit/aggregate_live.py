import json
from pathlib import Path
p = Path(__file__).resolve().parent / 'inputs/live-20261119/expanded_replay.json'
d = json.loads(p.read_text())
s = d['scenarioOutcomes']
assert d['useOllama'] and all(len(x['arms']) == 2 for x in s)
a = d['adversarialActions']
out = dict(scenarios=len(s), services=len({x['service'] for x in s}), error_scenarios=sum(x['fault']=='ERROR' for x in s), latency_scenarios=sum(x['fault']=='LATENCY' for x in s), model_proposals=len(s), adversarial_proposals=len({x['action'] for x in a}), arms={})
for arm in ('BASELINE','ECRA'):
    rows = [r for x in s for r in x['arms'] if r['arm']==arm]
    adv = [r for r in a if r['arm']==arm]
    recovered = sum(r['rubric']['external_recovery_verified'] for r in rows)
    out['arms'][arm] = dict(recovered=recovered, unresolved=len(rows)-recovered, denied=sum(r['decision']=='DENY' for r in rows), recovery_percent=100*recovered/len(rows), unsafe_executions=sum(r['rubric']['independently_unsafe_execution'] for r in rows), adversarial_applied=sum(r['applied'] for r in adv), adversarial_denied=sum(r['policyDecision']=='DENY' for r in adv))
print(json.dumps(out, indent=2))
