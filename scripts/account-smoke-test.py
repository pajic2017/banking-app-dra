#!/usr/bin/env python3
"""Exercise the live gateway/customer/account flow; retains closed synthetic accounts."""
import json
import time
import uuid
import urllib.request
import urllib.error
import urllib.parse
from decimal import Decimal

base = 'http://localhost:8080/api'

def request(method, path, payload=None, status=200):
    data = json.dumps(payload).encode() if payload is not None else None
    try:
        response = urllib.request.urlopen(urllib.request.Request(base + path, data=data, method=method,
            headers={'Content-Type': 'application/json', 'Accept': 'application/json'}), timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status == status, f'{method} {path}: {response.status}, expected {status}'
        raw = response.read()
        return json.loads(raw, parse_float=Decimal) if raw else None, response.headers

# Use only reads while waiting for discovery; never retry account creation blindly.
deadline = time.monotonic() + 180
while True:
    try:
        request('GET', '/customers?size=1')
        request('GET', '/accounts/customer/1?size=1')
        break
    except (OSError, AssertionError):
        if time.monotonic() >= deadline:
            raise SystemExit('Customer/Account gateway routes did not become ready')
        time.sleep(3)

customer, _ = request('POST', '/customers', {'firstName': 'AccountSmoke', 'lastName': 'Test',
    'email': f'accounts-{uuid.uuid4().hex}@example.com', 'phone': '+38761123456'}, 201)
cid = customer['id']
accounts = []
for kind, currency in [('CURRENT', 'BAM'), ('SAVINGS', 'JPY')]:
    account, headers = request('POST', '/accounts', {'customerId': cid, 'accountType': kind, 'currency': currency}, 201)
    aid = account['id']
    assert urllib.parse.urljoin(base+'/accounts', headers['Location']) == base+f'/accounts/{aid}'
    assert account['customerId'] == cid and account['status'] == 'ACTIVE'
    assert Decimal(account['balance']) == 0
    assert request('GET', f'/accounts/{aid}')[0] == account
    assert Decimal(request('GET', f'/accounts/{aid}/balance')[0]['balance']) == 0
    accounts.append(aid)
assert len(set(accounts)) == 2
page, _ = request('GET', f'/accounts/customer/{cid}?size=20')
assert {a['id'] for a in page['items']} == set(accounts)
overview, _ = request('GET', f'/customers/{cid}/accounts')
assert overview == page
for aid in accounts:
    for target in ['BLOCKED', 'ACTIVE', 'CLOSED', 'CLOSED']:
        changed, _ = request('PATCH', f'/accounts/{aid}/status', {'status': target})
        assert changed['status'] == target and Decimal(changed['balance']) == 0
    request('PATCH', f'/accounts/{aid}/status', {'status': 'ACTIVE'}, 409)
request('DELETE', f'/customers/{cid}', status=204)
request('POST', '/accounts', {'customerId': cid, 'accountType': 'CURRENT', 'currency': 'BAM'}, 409)
assert request('GET', f'/customers/{cid}/accounts')[0]['totalElements'] == 2
request('POST', '/accounts', {'customerId': cid, 'accountType': 'CURRENT', 'currency': 'ZZZ'}, 400)
print(f'PASS: two real services, customer verification, account lifecycle, zero balances and customer overview (customer={cid}, accounts={accounts})')
