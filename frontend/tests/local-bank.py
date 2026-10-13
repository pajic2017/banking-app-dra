"""Private bridge for disposable, loopback-only browser tests. Never returns tokens."""
import json,sys,urllib.request
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
from local_oidc import login,local_value,management,USERS,bind_customer
args=json.loads(sys.stdin.read())
if args['action']=='password':
    # Consumed privately by the test process. Never log subprocess stdout.
    sys.stdout.write(local_value('BANK_'+args['role'].upper()+'_PASSWORD'))
elif args['action']=='bind':
    rows=management('GET','/users?username='+USERS[args['role']]+'&exact=true')
    user=rows[0]
    assert user.get('attributes',{}).get('banking_fixture')==['phase6']
    old=user.get('attributes',{}).get('customer_id')
    if args.get('customerId') is not None: bind_customer(args['role'],args['customerId'])
    else:
        attributes=user.get('attributes',{});attributes.pop('customer_id',None)
        management('PUT','/users/'+user['id'],{'attributes':attributes})
    print(json.dumps(old))
else:
    assert args['path'].startswith('/')
    token=login('admin')['access_token']
    data=json.dumps(args['body']).encode() if 'body' in args else None
    headers={'Authorization':'Bearer '+token,'Content-Type':'application/json'}
    if args.get('key'):headers['Idempotency-Key']=args['key']
    request=urllib.request.Request('http://localhost:8080/api'+args['path'],data=data,method=args.get('method','GET'),headers=headers)
    with urllib.request.urlopen(request,timeout=20) as response:
        raw=response.read();sys.stdout.write(raw.decode() if raw else 'null')
