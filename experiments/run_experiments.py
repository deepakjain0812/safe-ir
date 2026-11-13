#!/usr/bin/env python3
"""Python standard library only. Starts against a running SAFE-IR Spring Boot server."""
import argparse,csv,json,time,urllib.request,urllib.error,collections,pathlib,statistics
SCENARIOS=['service_outage','latency_spike','queue_backlog','false_alert','missing_telemetry','unsafe_proposal']
MODES=['RULES','UNGUARDED','SAFE_IR']
def call(url,payload):
 request=urllib.request.Request(url+'/api/v1/run',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(request,timeout=90) as r:return json.load(r)
def main():
 p=argparse.ArgumentParser();p.add_argument('--url',default='http://localhost:8080');p.add_argument('--repeats',type=int,default=10);p.add_argument('--ollama',action='store_true');p.add_argument('--out',default='experiments/results');args=p.parse_args()
 if args.repeats<1: p.error('--repeats must be >= 1')
 out=pathlib.Path(args.out);out.mkdir(parents=True,exist_ok=True);rows=[]
 for seed in range(args.repeats):
  for scenario in SCENARIOS:
   for mode in MODES:
    row=call(args.url,dict(scenario=scenario,mode=mode,seed=seed,useOllama=args.ollama and mode!='RULES'))
    row['seed']=seed;row['llmEnabled']=args.ollama and mode!='RULES';rows.append(row)
 with (out/'runs.csv').open('w',newline='') as f:
  writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
 summary=[]
 for mode in MODES:
  group=[r for r in rows if r['mode']==mode];n=len(group)
  summary.append({'mode':mode,'n':n,'recovery_rate':sum(r['recovered'] for r in group)/n,
    'unsafe_execution_rate':sum(r['unsafeExecuted'] for r in group)/n,
    'diagnosis_accuracy':sum(r['correctDiagnosis'] for r in group)/n,
    'execution_rate':sum(r['executed'] for r in group)/n,
    'mean_policy_nanos':statistics.mean(r['policyNanos'] for r in group),
    'mean_elapsed_millis':statistics.mean(r['elapsedMillis'] for r in group)})
 (out/'summary.json').write_text(json.dumps({'experimental_mode':'ollama' if args.ollama else 'deterministic_offline',
   'caveat':'Synthetic outcomes; repeated seeds reuse identical scenarios. Not independent empirical trials or real MTTR.',
   'summary':summary},indent=2))
 print(json.dumps(summary,indent=2));print('Wrote',out/'runs.csv',out/'summary.json')
if __name__=='__main__':main()
