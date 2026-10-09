#!/usr/bin/env python3
"""Exercise the durable customer lifecycle through the gateway; retains an inactive test record."""
import json
import os
import urllib.error
import urllib.request
import urllib.parse
import uuid
import time

base = os.environ.get('CUSTOMER_API_BASE', 'http://localhost:8080/api/customers')

def request(method, url, payload=None, expected=200):
    data = json.dumps(payload).encode() if payload is not None else None
    headers = {'Accept': 'application/json', 'Content-Type': 'application/json'}
    try:
        response = urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method), timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        assert response.status == expected, f'{method} {url}: expected {expected}, got {response.status}'
        body = response.read()
        return (json.loads(body) if body else None), response.headers

# Discovery/client caches may lag readiness. Wait with read-only requests before writing.
deadline = time.monotonic() + 180
while True:
    try:
        request('GET', f'{base}?size=1')
        break
    except (OSError, AssertionError) as error:
        if time.monotonic() >= deadline:
            raise SystemExit(f'Customer API did not become ready: {error}')
        time.sleep(3)

email = f'smoke-{uuid.uuid4().hex}@example.com'
customer, headers = request('POST', base, {'firstName': 'Smoke', 'lastName': 'Test', 'email': email,
                                          'phone': '+38761123456'}, 201)
url = f'{base}/{customer["id"]}'
assert urllib.parse.urljoin(base, headers['Location']) == url, 'Location must preserve the gateway host and prefix'
assert customer['status'] == 'ACTIVE'
assert request('GET', url)[0] == customer
request('POST', base, {'firstName': 'Duplicate', 'lastName': 'Test', 'email': email.upper(),
                      'phone': '+38761123456'}, 409)
updated, _ = request('PUT', url, {'firstName': 'Updated', 'lastName': 'Test', 'email': email,
                                'phone': '+38762123456'})
assert updated['firstName'] == 'Updated' and updated['createdAt'] == customer['createdAt']
page, _ = request('GET', f'{base}?size=100&status=ACTIVE')
assert page['totalElements'] >= 1 and page['size'] == 100
request('DELETE', url, expected=204)
request('DELETE', url, expected=204)
inactive, _ = request('GET', url)
assert inactive['status'] == 'INACTIVE' and inactive['createdAt'] == customer['createdAt']
problem, _ = request('POST', base, {'firstName': '', 'lastName': '', 'email': 'invalid', 'phone': '123'}, 400)
assert set(problem['errors']) == {'firstName', 'lastName', 'email', 'phone'}
request('GET', f'{base}?size=101', expected=400)
print(f'PASS: gateway customer lifecycle, unique email, validation, pagination and durable deactivation (id={customer["id"]})')
