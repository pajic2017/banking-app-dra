#!/usr/bin/env python3
"""Verify three real outboxes and Kafka-to-Mongo notifications using synthetic development data."""
import collections
import json
from local_oidc import admin_token
import time
import uuid
import urllib.error
import urllib.request
from decimal import Decimal

base = 'http://localhost:8080/api'
correlation = str(uuid.uuid4())

def request(method, path, payload=None, key=None, expected=(200,)):
    headers = {'Content-Type': 'application/json', 'X-Correlation-ID': correlation}
    headers['Authorization'] = 'Bearer '+admin_token()
    if key:
        headers['Idempotency-Key'] = key
    data = json.dumps(payload).encode() if payload is not None else None
    try:
        response = urllib.request.urlopen(urllib.request.Request(base + path, method=method, data=data, headers=headers), timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status in expected, f'{method} {path}: {response.status}, expected {expected}'
        raw = response.read()
        return json.loads(raw, parse_float=Decimal) if raw else None

def post(path, payload, key=None, status='COMPLETED'):
    row = request('POST', path, payload, key or uuid.uuid4().hex, (200, 202))
    deadline = time.monotonic() + 90
    while row['status'] == 'PENDING' and time.monotonic() < deadline:
        time.sleep(.2)
        row = request('GET', '/transactions/' + row['id'])
    assert row['status'] == status, row
    assert row['correlationId'] == correlation, row
    return row

# Wait for discovery with reads before creating uniquely named records.
deadline = time.monotonic() + 180
while True:
    try:
        for path in ['/customers?size=1', '/accounts/customer/1?size=1', '/transactions?size=1', '/notifications?size=1']:
            request('GET', path)
        break
    except (OSError, AssertionError):
        if time.monotonic() >= deadline:
            raise SystemExit('Phase 5 gateway routes did not become ready')
        time.sleep(3)

customer = request('POST', '/customers', {'firstName': 'EventSmoke', 'lastName': 'Test',
    'email': f'events-{uuid.uuid4().hex}@example.com', 'phone': '+38761123456'}, expected=(201,))
cid = customer['id']
accounts = [request('POST', '/accounts', {'customerId': cid, 'accountType': kind, 'currency': 'BAM'}, expected=(201,))['id']
            for kind in ['CURRENT', 'SAVINGS']]
a, b = accounts
funding_key = uuid.uuid4().hex
funding = post('/transactions/deposit', {'accountId': a, 'amount': '10.00', 'currency': 'BAM'}, funding_key)
transfer = post('/transfers', {'fromAccountId': a, 'toAccountId': b, 'amount': '3.00', 'currency': 'BAM'})
failed = post('/transfers', {'fromAccountId': a, 'toAccountId': b, 'amount': '8.00', 'currency': 'BAM'}, status='REJECTED')
assert failed['failureCode'] == 'INSUFFICIENT_FUNDS'
assert post('/transactions/deposit', {'accountId': a, 'amount': '10', 'currency': 'bam'}, funding_key)['id'] == funding['id']
assert request('GET', f'/accounts/{a}/balance')['balance'] == 7
assert request('GET', f'/accounts/{b}/balance')['balance'] == 3
for aid, amount in [(a, '7'), (b, '3')]:
    post('/transactions/withdraw', {'accountId': aid, 'amount': amount, 'currency': 'BAM'})
    request('PATCH', f'/accounts/{aid}/status', {'status': 'CLOSED'})
request('DELETE', f'/customers/{cid}', expected=(204,))

deadline = time.monotonic() + 120
while True:
    page = request('GET', f'/notifications?correlationId={correlation}&size=100')
    if page['totalElements'] >= 8:
        break
    if time.monotonic() >= deadline:
        raise AssertionError(f'Committed events did not arrive: {page}')
    time.sleep(.5)
assert page['totalElements'] == 8, page
counts = collections.Counter(n['event']['eventType'] for n in page['items'])
assert counts == {'CUSTOMER_CREATED': 1, 'ACCOUNT_CREATED': 2, 'TRANSACTION_COMPLETED': 4, 'TRANSACTION_FAILED': 1}, counts
assert len({n['id'] for n in page['items']}) == 8
for notification in page['items']:
    event = notification['event']
    assert event['version'] == 1 and event['correlationId'] == correlation
    assert notification['partition'] in [0, 1, 2] and notification['offset'] >= 0
    assert request('GET', '/notifications/' + notification['id'])['event']['eventId'] == notification['id']
assert any(n['event']['aggregateId'] == transfer['id'] and n['event']['eventType'] == 'TRANSACTION_COMPLETED' for n in page['items'])
assert any(n['event']['aggregateId'] == failed['id'] and n['event']['eventType'] == 'TRANSACTION_FAILED' for n in page['items'])
print(f'PASS: three transactional outboxes, eight unique Kafka/Mongo notifications, transfer success/failure and stable correlation; funds drained (customer={cid}, accounts={accounts}, correlation={correlation})')
