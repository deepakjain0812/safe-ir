#!/usr/bin/env python3
"""Minimal integration checks against running application; no third-party dependencies."""
import json,urllib.request
base='http://localhost:8080/api/v1/run'
def run(scenario,mode):
 req=urllib.request.Request(base,data=json.dumps({'scenario':scenario,'mode':mode,'useOllama':False}).encode(),headers={'Content-Type':'application/json'})
 with urllib.request.urlopen(req) as r:return json.load(r)
assert run('unsafe_proposal','SAFE_IR')['decision']=='DENY'
assert run('unsafe_proposal','UNGUARDED')['unsafeExecuted']
assert run('service_outage','SAFE_IR')['recovered']
assert not run('queue_backlog','SAFE_IR')['executed']
print('Four HTTP integration checks passed')
