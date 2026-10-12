#!/usr/bin/env python3
"""Verify real monetary APIs; uses only synthetic development data and retains closed accounts."""
import concurrent.futures
import json
from local_oidc import admin_token
import time
import uuid
import urllib.error
import urllib.parse
import urllib.request
from decimal import Decimal

base = 'http://localhost:8080/api'

def request(method, path, payload=None, expected=(200,), key=None):
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {'Content-Type': 'application/json', 'Accept': 'application/json'}
    headers['Authorization'] = 'Bearer '+admin_token()
    if key:
        headers['Idempotency-Key'] = key
    try:
        response = urllib.request.urlopen(urllib.request.Request(base + path, data=data, method=method, headers=headers), timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status in expected, f'{method} {path}: {response.status}, expected {expected}'
        raw = response.read()
        return json.loads(raw, parse_float=Decimal) if raw else None, response.headers

def settled(row, status='COMPLETED', code=None):
    deadline = time.monotonic() + 90
    while row['status'] == 'PENDING' and time.monotonic() < deadline:
        time.sleep(.3)
        row = request('GET', '/transactions/' + row['id'])[0]
    assert row['status'] == status, row
    if code:
        assert row['failureCode'] == code, row
    return row

def post(path, payload, key=None, status='COMPLETED', code=None):
    row, headers = request('POST', path, payload, (200, 202), key or uuid.uuid4().hex)
    assert urllib.parse.urljoin(base + path, headers['Location']) == base + '/transactions/' + row['id']
    return settled(row, status, code)

def balance(aid):
    return Decimal(request('GET', f'/accounts/{aid}/balance')[0]['balance'])

# Discovery convergence is a read-only readiness check, before submitting money commands.
deadline = time.monotonic() + 180
while True:
    try:
        request('GET', '/customers?size=1')
        request('GET', '/accounts/customer/1?size=1')
        request('GET', '/transactions?size=1')
        break
    except (OSError, AssertionError):
        if time.monotonic() >= deadline:
            raise SystemExit('Customer/Account/Transaction gateway routes did not become ready')
        time.sleep(3)

customer = request('POST', '/customers', {'firstName': 'TransactionSmoke', 'lastName': 'Test',
    'email': f'money-{uuid.uuid4().hex}@example.com', 'phone': '+38761123456'}, (201,))[0]
cid = customer['id']
accounts = [request('POST', '/accounts', {'customerId': cid, 'accountType': kind, 'currency': 'BAM'}, (201,))[0]['id']
            for kind in ['CURRENT', 'SAVINGS']]
a, b = accounts
key = uuid.uuid4().hex
funding = post('/transactions/deposit', {'accountId': a, 'amount': '100.00', 'currency': 'BAM'}, key)
replay = post('/transactions/deposit', {'accountId': a, 'amount': '100', 'currency': 'bam'}, key)
assert replay['id'] == funding['id'] and balance(a) == 100
request('POST', '/transactions/deposit', {'accountId': a, 'amount': '101', 'currency': 'BAM'}, (409,), key)
post('/transfers', {'fromAccountId': a, 'toAccountId': b, 'amount': '35.00', 'currency': 'BAM'})
post('/transactions/withdraw', {'accountId': b, 'amount': '5.00', 'currency': 'BAM'})
assert (balance(a), balance(b)) == (65, 30)
post('/transactions/withdraw', {'accountId': b, 'amount': '31', 'currency': 'BAM'}, status='REJECTED', code='INSUFFICIENT_FUNDS')
request('PATCH', f'/accounts/{b}/status', {'status': 'BLOCKED'})
post('/transfers', {'fromAccountId': a, 'toAccountId': b, 'amount': '1', 'currency': 'BAM'}, status='REJECTED', code='ACCOUNT_NOT_ACTIVE')
request('PATCH', f'/accounts/{b}/status', {'status': 'ACTIVE'})
request('POST', '/transactions/deposit', {'accountId': a, 'amount': '1.001', 'currency': 'BAM'}, (400,), uuid.uuid4().hex)

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    rows = list(pool.map(lambda _: post('/transfers', {'fromAccountId': a, 'toAccountId': b, 'amount': '1', 'currency': 'BAM'}), range(20)))
assert len({row['id'] for row in rows}) == 20
assert (balance(a), balance(b)) == (45, 50)
key = uuid.uuid4().hex
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    rows = list(pool.map(lambda _: post('/transactions/deposit', {'accountId': a, 'amount': '1', 'currency': 'BAM'}, key), range(10)))
assert len({row['id'] for row in rows}) == 1
assert (balance(a), balance(b)) == (46, 50)
history = request('GET', f'/transactions/account/{a}?size=100')[0]
assert history['totalElements'] == 24 and len(history['items']) == 24
assert request('GET', f'/customers/{cid}/accounts')[0]['totalElements'] == 2

with urllib.request.urlopen(urllib.request.Request('http://localhost:8083/internal/v1/ledger/reconciliation?currency=BAM',headers={'Authorization':'Bearer '+admin_token()})) as response:
    report = json.load(response, parse_float=Decimal)
assert report['balanced'] and report['accountMismatches'] == 0 and report['invalidPostings'] == 0, report
for aid, amount in [(a, '46'), (b, '50')]:
    post('/transactions/withdraw', {'accountId': aid, 'amount': amount, 'currency': 'BAM'})
    assert balance(aid) == 0
    request('PATCH', f'/accounts/{aid}/status', {'status': 'CLOSED'})
request('DELETE', f'/customers/{cid}', expected=(204,))
print(f'PASS: deposits, withdrawals, balanced transfers, rejection, 20 concurrent transfers, 10 duplicate requests, history and reconciliation (customer={cid}, accounts={accounts}, funding={funding["id"]})')
