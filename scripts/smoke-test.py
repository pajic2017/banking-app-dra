#!/usr/bin/env python3
"""Live discovery, centralized configuration, health and gateway routing check."""
import json
import time
import urllib.request
import urllib.error

def get(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'Accept': 'application/json'}), timeout=5) as response:
        return json.load(response)

def check():
    for port in (8761, 8888, 8080, 8081, 8082, 8083, 8084, 8085):
        assert get(f'http://localhost:{port}/actuator/health')['status'] == 'UP'
    config = get('http://localhost:8888/customer-service/default')
    assert any(p['source'].get('bank.foundation.phase') == 'foundation' for p in config['propertySources'])
    apps = get('http://localhost:8761/eureka/apps')['applications']['application']
    names = {a['name'] for a in apps}
    assert {'CONFIG-SERVER', 'API-GATEWAY', 'AUTH-SERVICE', 'CUSTOMER-SERVICE',
            'ACCOUNT-SERVICE', 'TRANSACTION-SERVICE', 'NOTIFICATION-SERVICE'} <= names
    for path, service in [('auth', 'auth-service'), ('customers', 'customer-service'),
                          ('accounts', 'account-service'), ('transactions', 'transaction-service'),
                          ('notifications', 'notification-service')]:
        assert get(f'http://localhost:8080/api/{path}/foundation') == {'service': service, 'phase': 'foundation'}

if __name__ == '__main__':
    deadline = time.monotonic() + 180
    while True:
        try:
            check()
            print('PASS: eight health endpoints, centralized config, seven registrations, five gateway routes')
            break
        except (AssertionError, OSError, KeyError, json.JSONDecodeError) as error:
            if time.monotonic() >= deadline:
                raise SystemExit(f'Foundation smoke test failed: {error}')
            time.sleep(3)
