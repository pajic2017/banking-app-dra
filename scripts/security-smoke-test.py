#!/usr/bin/env python3
"""Exercise real Keycloak PKCE, gateway/backend JWTs, ownership and service identities.
Local development only; retains closed synthetic accounts and audit records.
"""
import base64
import json
import time
import uuid
import urllib.error
import urllib.request
from local_oidc import ISSUER,login,form,bind_customer,service_token,management,management_token

base='http://localhost:8080/api'
tokens={}
checks=0
def request(method,path,body=None,identity='admin',expected=(200,),key=None,extra=None):
    global checks
    headers={'Content-Type':'application/json'}
    if identity is not None: headers['Authorization']='Bearer '+tokens[identity]
    if key: headers['Idempotency-Key']=key
    if extra: headers.update(extra)
    url=path if path.startswith('http') else base+path
    try:
        response=urllib.request.urlopen(urllib.request.Request(url,data=json.dumps(body).encode() if body is not None else None,
            headers=headers,method=method),timeout=10)
    except urllib.error.HTTPError as error: response=error
    with response:
        assert response.status in expected,f'{method} {path}: {response.status}, expected {expected}'
        raw=response.read();checks+=1
        if response.status in (401,403):
            assert response.headers.get('Content-Type','').startswith('application/problem+json')
            assert b'access_token' not in raw
        return json.loads(raw) if raw else None

def wait_ready():
    deadline=time.monotonic()+180
    while True:
        try: request('GET','/customers?size=1');request('GET','/notifications?size=1');request('GET','/auth/me');request('GET','/accounts/customer/1?size=1');request('GET','/transactions?size=1');return
        except (AssertionError,OSError):
            if time.monotonic()>deadline: raise
            time.sleep(2)

def money(path,body,identity='admin',key=None):
    row=request('POST',path,body,identity,(200,202),key or uuid.uuid4().hex)
    deadline=time.monotonic()+90
    while row['status']=='PENDING':
        assert time.monotonic()<deadline,'Unconfirmed monetary result';time.sleep(.3)
        row=request('GET','/transactions/'+row['id'],identity=identity)
    assert row['status']=='COMPLETED',row['status'];return row

def decode(token):
    payload=token.split('.')[1];return json.loads(base64.urlsafe_b64decode(payload+'='*(-len(payload)%4)))

sessions={role:login(role) for role in ('admin','employee','alice','bob','roleless')}
tokens.update({role:s['access_token'] for role,s in sessions.items()})
for token in tokens.values():
    claims=decode(token);assert claims['iss']==ISSUER and 'banking-api' in claims['aud']
wait_ready()
for url in ['/customers','/accounts/1','/transactions','/notifications','/auth/me',
            'http://localhost:8082/customers','http://localhost:8083/accounts/1','http://localhost:8084/transactions',
            'http://localhost:8085/notifications','http://localhost:8081/me']:
    request('GET',url,identity=None,expected=(401,))
tokens['invalid']='not-a-jwt'
request('GET','/customers',identity='invalid',expected=(401,))
request('GET','http://localhost:8083/accounts/1',identity='invalid',expected=(401,))
request('GET','/customers',identity='roleless',expected=(403,))
for role in ('alice','bob','employee'):
    request('GET','/notifications',identity=role,expected=(403,))
    request('GET','http://localhost:8085/notifications',identity=role,expected=(403,))
request('GET','/notifications/dead-letters')
request('GET','/auth/oidc',identity=None)
realm=management('GET','',token=management_token())
assert realm['accessTokenLifespan']==300
try:
    form(ISSUER+'/protocol/openid-connect/token',{'grant_type':'password','client_id':'banking-local','username':'unused','password':'unused'})
    raise AssertionError('Public password grant must be disabled')
except RuntimeError: pass
print('PASS: real PKCE login, issuer/audience, anonymous and invalid JWT 401, roleless/admin-role boundaries, password grant disabled')

customers=[];accounts=[]
try:
    for role in ('alice','bob'):
        c=request('POST','/customers',{'firstName':'Security','lastName':role,'email':f'security-{uuid.uuid4().hex}@example.com','phone':'+38761123456'},expected=(201,))
        customers.append(c['id'])
        # Before binding, a CUSTOMER role alone confers no ownership.
        request('GET','/customers/'+str(c['id']),identity=role,expected=(403,))
        bind_customer(role,c['id']);tokens[role]=login(role)['access_token']
        assert decode(tokens[role])['customer_id']==str(c['id'])
        a=request('POST','/accounts',{'customerId':c['id'],'accountType':'CURRENT','currency':'BAM'},expected=(201,));accounts.append(a['id'])
    alice,bob=customers;a,b=accounts
    for path in [f'/customers/{alice}',f'/customers/{alice}/accounts',f'/accounts/{a}',f'/accounts/{a}/balance',f'/accounts/customer/{alice}',f'/transactions/account/{a}']:
        request('GET',path,identity='alice')
    for path in [f'/customers/{bob}',f'/customers/{bob}/accounts',f'/accounts/{b}',f'/accounts/{b}/balance',f'/accounts/customer/{bob}',f'/transactions/account/{b}', '/customers','/transactions']:
        request('GET',path,identity='alice',expected=(403,),extra={'X-Customer-ID':str(bob)})
    changed={'firstName':'Owned','lastName':'Update','email':f'owned-{uuid.uuid4().hex}@example.com','phone':'+38761123456'}
    request('PUT',f'/customers/{bob}',changed,identity='alice',expected=(403,))
    request('PUT',f'/customers/{alice}',changed,identity='alice')
    request('DELETE',f'/customers/{bob}',identity='alice',expected=(403,))
    request('POST','/accounts',{'customerId':alice,'accountType':'SAVINGS','currency':'BAM'},identity='alice',expected=(403,))
    request('PATCH',f'/accounts/{a}/status',{'status':'CLOSED'},identity='alice',expected=(403,))
    for identity in ('admin','employee','alice'):
        request('POST','http://localhost:8083/internal/v1/postings',{'operationId':str(uuid.uuid4()),'type':'DEPOSIT','toAccountId':a,'amount':'1','currency':'BAM'},identity,expected=(403,))
    tokens['account-service']=service_token('account-service');tokens['customer-service']=service_token('customer-service');tokens['transaction-service']=service_token('transaction-service')
    request('GET',f'http://localhost:8082/internal/v1/customers/{alice}/status',identity='account-service')
    request('GET',f'http://localhost:8082/customers/{alice}',identity='account-service',expected=(403,))
    request('GET',f'http://localhost:8083/internal/v1/accounts/customer/{alice}',identity='customer-service')
    for identity in ('account-service','customer-service'):
        request('POST','http://localhost:8083/internal/v1/postings',{'operationId':str(uuid.uuid4()),'type':'DEPOSIT','toAccountId':a,'amount':'1','currency':'BAM'},identity,expected=(403,))
    request('GET',f'/accounts/{a}',identity='transaction-service',expected=(403,))
    request('GET','http://localhost:8083/internal/v1/ledger/reconciliation?currency=BAM',identity='employee',expected=(403,))
    request('GET','http://localhost:8083/internal/v1/ledger/reconciliation?currency=BAM')
    print('PASS: own-resource reads/update, foreign-resource denial, spoofed headers ignored, staff/admin and three narrow service identities')

    fund=money('/transactions/deposit',{'accountId':a,'amount':'10','currency':'BAM'})
    for path,body in [('/transactions/deposit',{'accountId':a,'amount':'1','currency':'BAM'}),
            ('/transactions/withdraw',{'accountId':b,'amount':'1','currency':'BAM'}),
            ('/transfers',{'fromAccountId':b,'toAccountId':a,'amount':'1','currency':'BAM'})]:
        request('POST',path,body,identity='alice',expected=(403,),key=uuid.uuid4().hex)
    key=uuid.uuid4().hex;body={'fromAccountId':a,'toAccountId':b,'amount':'3','currency':'BAM'}
    transfer=money('/transfers',body,'alice',key)
    assert money('/transfers',body,'alice',key)['id']==transfer['id']
    # Same raw key has independent authenticated subject scope.
    other=money('/transfers',{'fromAccountId':b,'toAccountId':a,'amount':'1','currency':'BAM'},'bob',key)
    assert other['id']!=transfer['id']
    request('GET','/transactions/'+fund['id'],identity='bob',expected=(403,))
    request('GET','/transactions/'+transfer['id'],identity='bob')
    withdrawn=money('/transactions/withdraw',{'accountId':a,'amount':'2','currency':'BAM'},'alice')
    assert request('GET',f'/accounts/{a}/balance',identity='alice')['balance']==6
    assert request('GET',f'/accounts/{b}/balance',identity='bob')['balance']==2
    # Refresh preserves subject/key namespace. A token has a bounded life; local logout is explicit.
    refreshed=form(ISSUER+'/protocol/openid-connect/token',{'grant_type':'refresh_token','client_id':'banking-local','refresh_token':sessions['employee']['refresh_token']})
    assert decode(refreshed['access_token'])['sub']==decode(tokens['employee'])['sub'];tokens['employee']=refreshed['access_token']
    request('GET','/auth/me',identity='employee')
    form(ISSUER+'/protocol/openid-connect/logout',{'client_id':'banking-local','refresh_token':refreshed['refresh_token']})
    try:
        form(ISSUER+'/protocol/openid-connect/token',{'grant_type':'refresh_token','client_id':'banking-local','refresh_token':refreshed['refresh_token']})
        raise AssertionError('Logged-out session must reject refresh')
    except RuntimeError: pass
    request('GET','/auth/me',identity='employee')  # Stateless access token remains valid until its bounded expiry.
    print('PASS: authenticated worker posting, owned transfer/withdrawal, forbidden funding/foreign source, participant reads, scoped idempotency, refresh and logout/expiry semantics')
finally:
    for aid in accounts:
        balance=request('GET',f'/accounts/{aid}/balance')['balance']
        if balance: money('/transactions/withdraw',{'accountId':aid,'amount':str(balance),'currency':'BAM'})
        request('PATCH',f'/accounts/{aid}/status',{'status':'CLOSED'})
    for cid in customers: request('DELETE',f'/customers/{cid}',expected=(204,))
print(f'PASS: {checks} authenticated/denied HTTP checks; synthetic balances drained, accounts closed, customers inactive, audit retained')
