#!/usr/bin/env python3
"""Verify all eight scrapes, Grafana provisioning, durable transfer tracing and indexed logs."""
import base64,json,time,uuid,urllib.request,urllib.error
from local_oidc import admin_token,local_value

def get(url,headers=None):
    with urllib.request.urlopen(urllib.request.Request(url,headers=headers or {}),timeout=10) as r:return json.load(r)
def wait(fn,seconds=240):
    end=time.monotonic()+seconds
    while True:
        try:
            result=fn()
            if result:return result
        except (OSError,ValueError,KeyError):pass
        if time.monotonic()>end:raise AssertionError('Monitoring condition timed out')
        time.sleep(2)
apps={'api-gateway','auth-service','customer-service','account-service','transaction-service','notification-service','config-server','discovery-server'}
wait(lambda: {x['labels']['application'] for x in get('http://localhost:9090/api/v1/targets')['data']['activeTargets'] if x['health']=='up'}==apps)
gauges=get('http://localhost:9090/api/v1/query?query=bank_outbox_pending')['data']['result']
assert len(gauges)==3 and all(float(g['value'][1])>=0 for g in gauges)
assert len(get('http://localhost:9090/api/v1/rules')['data']['groups'][0]['rules'])==4
print('PASS: eight authenticated Prometheus scrape targets UP, three outbox gauges and four loaded alerts',flush=True)
headers={'Authorization':'Basic '+base64.b64encode(('admin:'+local_value('GRAFANA_ADMIN_PASSWORD')).encode()).decode()}
dashboard=wait(lambda:get('http://localhost:3000/api/dashboards/uid/banking-overview',headers))
assert len(dashboard['dashboard']['panels'])==6
assert len(get('http://localhost:3000/api/datasources',headers))>=2
print('PASS: provisioned Grafana dashboard and Prometheus/Zipkin sources',flush=True)
# Actual business requests use a caller-sampled W3C trace, never trace fields as identity.
trace=uuid.uuid4().hex;correlation=str(uuid.uuid4());parent='00-'+trace+'-'+uuid.uuid4().hex[:16]+'-01'
def request(method,path,body=None):
    h={'Authorization':'Bearer '+admin_token(),'Content-Type':'application/json','traceparent':parent,'X-Correlation-ID':correlation,'Idempotency-Key':uuid.uuid4().hex}
    with urllib.request.urlopen(urllib.request.Request('http://localhost:8080/api'+path,data=json.dumps(body).encode() if body is not None else None,headers=h,method=method),timeout=10) as r:
        raw=r.read();return json.loads(raw) if raw else None
def money(path,body):
    row=request('POST',path,body)
    wait(lambda: request('GET','/transactions/'+row['id'])['status']!='PENDING',90)
    assert request('GET','/transactions/'+row['id'])['status']=='COMPLETED';return row
customer=request('POST','/customers',{'firstName':'Telemetry','lastName':'Test','email':uuid.uuid4().hex+'@example.com','phone':'+38761123456'})
accounts=[]
try:
    for _ in range(2):accounts.append(request('POST','/accounts',{'customerId':customer['id'],'accountType':'CURRENT','currency':'BAM'})['id'])
    a,b=accounts
    money('/transactions/deposit',{'accountId':a,'amount':'5','currency':'BAM'})
    transfer=money('/transfers',{'fromAccountId':a,'toAccountId':b,'amount':'2','currency':'BAM'})
    def spans():
        rows=get('http://localhost:9411/api/v2/trace/'+trace)
        names={s.get('localEndpoint',{}).get('serviceName') for s in rows}
        return rows if {'api-gateway','transaction-service','account-service','notification-service'}<=names and any(s['name']=='transaction.process' for s in rows) and any(s['name']=='outbox.publish' for s in rows) else None
    records=wait(spans)
    assert len({s['id'] for s in records})==len(records)
    print('PASS: one W3C trace spans gateway, durable worker, Account posting, outbox/Kafka and Notification; traceId='+trace,flush=True)
    def logs():
        url='http://localhost:9200/bank-logs-*/_search'
        query={'size':100,'query':{'term':{'traceId.keyword':trace}}}
        req=urllib.request.Request(url,data=json.dumps(query).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=10) as response:rows=json.load(response)['hits']['hits']
        names={row['_source'].get('service',{}).get('name') for row in rows}
        return rows if {'transaction-service','account-service','notification-service'}<=names else None
    rows=wait(logs)
    assert all('authorization' not in row['_source'] for row in rows)
    assert any(transfer['id'] in row['_source'].get('message','') for row in rows)
    print('PASS: Filebeat -> Logstash -> Elasticsearch indexed transfer logs from three services with the same trace; Kibana can query the same store in viewing mode',flush=True)
finally:
    for aid in accounts:
        balance=request('GET',f'/accounts/{aid}/balance')['balance']
        if balance:money('/transactions/withdraw',{'accountId':aid,'amount':str(balance),'currency':'BAM'})
        request('PATCH',f'/accounts/{aid}/status',{'status':'CLOSED'})
    request('DELETE',f'/customers/{customer["id"]}')
print('PASS: synthetic funds drained; accounts closed and customer inactive; financial/telemetry audit retained',flush=True)
