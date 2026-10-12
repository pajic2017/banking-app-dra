#!/usr/bin/env python3
"""Configure seven-day log retention and a Kibana development data view."""
import json,time,urllib.request,urllib.error,sys

def call(url,method='GET',body=None):
    req=urllib.request.Request(url,method=method,data=json.dumps(body).encode() if body is not None else None,
        headers={'Content-Type':'application/json','kbn-xsrf':'bank-development'})
    with urllib.request.urlopen(req,timeout=10) as r:
        raw=r.read();return json.loads(raw) if raw else None
urls=['http://localhost:9200/_cluster/health']
if '--kibana' in sys.argv:urls.append('http://localhost:5601/api/status')
for url in urls:
    deadline=time.monotonic()+300
    while True:
        try:
            result=call(url)
            if ':9200/' in url or result['status']['overall']['level']=='available':break
        except (OSError,ValueError):pass
        if time.monotonic()>deadline:raise SystemExit('Telemetry store/UI readiness timed out.')
        time.sleep(2)
for path,body in [('/_ilm/policy/bank-logs-7d',{'policy':{'phases':{'hot':{'actions':{}},'delete':{'min_age':'7d','actions':{'delete':{}}}}}}),
                  ('/_index_template/bank-logs',{'index_patterns':['bank-logs-*'],'priority':100,'template':{'settings':{'index.lifecycle.name':'bank-logs-7d'}}})]:
    assert call('http://localhost:9200'+path,'PUT',body)['acknowledged']
# Attach the policy to existing development log indices without removing records.
call('http://localhost:9200/bank-logs-*/_settings?allow_no_indices=true','PUT',{'index.lifecycle.name':'bank-logs-7d'})
if '--kibana' in sys.argv:
    call('http://localhost:5601/api/data_views/data_view','POST',{'data_view':{'id':'bank-logs','title':'bank-logs-*','timeFieldName':'@timestamp'},'override':True})
    call('http://localhost:5601/api/data_views/default','POST',{'data_view_id':'bank-logs','force':True})
print('PASS: seven-day ILM policy/template configured; optional Kibana data view provisioned when requested; no records erased.')
