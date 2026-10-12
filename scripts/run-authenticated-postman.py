#!/usr/bin/env python3
"""Run a development collection with a private, ephemeral short-lived bearer environment."""
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path
from local_oidc import ROOT,USERS,admin_token,login,service_token,management,management_token
import urllib.request
os.umask(0o077)
collection=sys.argv[1] if len(sys.argv)>1 else 'postman/events.postman_collection.json'
path=(ROOT/collection).resolve()
if not path.is_relative_to(ROOT/'postman') or not path.is_file():raise SystemExit('Select a collection under postman/.')
(ROOT/'.run').mkdir(exist_ok=True)
values=[{'key':'accessToken','value':admin_token(),'enabled':True}]
if path.name=='security.postman_collection.json':
    for key,role in [('employeeAccessToken','employee'),('customerAccessToken','alice')]:values.append({'key':key,'value':login(role)['access_token'],'enabled':True})
    values.append({'key':'serviceAccessToken','value':service_token('account-service'),'enabled':True})
    token=management_token()
    for role in ('alice','bob'):
        user=management('GET','/users?username='+USERS[role]+'&exact=true',token=token)[0]
        customer=user.get('attributes',{}).get('customer_id',[None])[0]
        if customer is None:raise SystemExit('Run security-smoke-test.py to bind local fixture identities first.')
        request=urllib.request.Request(f'http://localhost:8080/api/accounts/customer/{customer}?size=1',headers={'Authorization':'Bearer '+admin_token()})
        with urllib.request.urlopen(request,timeout=10) as response:account=json.load(response)['items'][0]['id']
        values.extend([{'key':role+'CustomerId','value':customer,'enabled':True},{'key':role+'AccountId','value':account,'enabled':True}])
fd,name=tempfile.mkstemp(prefix='postman-security-',suffix='.json',dir=ROOT/'.run')
try:
    with os.fdopen(fd,'w') as stream:
        json.dump({'name':'Private local authorization','values':values},stream)
    result=subprocess.run(['npx','--yes','newman@6.2.1','run',str(path),'--environment',name,'--delay-request','200',
        '--reporters','cli,json','--reporter-json-export',str(ROOT/'.run'/'postman-result.json')],cwd=ROOT)
    raise SystemExit(result.returncode)
finally:
    Path(name).unlink(missing_ok=True)
    # Newman JSON embeds request Authorization headers; keep only aggregate counts, never tokens.
    result_path=ROOT/'.run'/'postman-result.json'
    if result_path.exists():
        report=json.loads(result_path.read_text());result_path.unlink()
        (ROOT/'.run'/'postman-summary.json').write_text(json.dumps({'stats':report['run']['stats'],'failures':len(report['run']['failures'])},indent=2)+'\n')
