#!/usr/bin/env python3
"""Generate local infrastructure credentials; refuses to overwrite existing files."""
import os
import secrets
from pathlib import Path
path = Path(__file__).resolve().parents[1] / '.env'
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as stream:
    for name in ('MYSQL_ROOT_PASSWORD', 'CUSTOMER_DB_PASSWORD', 'ACCOUNT_DB_PASSWORD',
                 'TRANSACTION_DB_PASSWORD', 'AUTH_DB_PASSWORD', 'KEYCLOAK_ADMIN_PASSWORD', 'GRAFANA_ADMIN_PASSWORD', 'CUSTOMER_SERVICE_CLIENT_SECRET', 'ACCOUNT_SERVICE_CLIENT_SECRET',
                 'TRANSACTION_SERVICE_CLIENT_SECRET', 'BANK_ADMIN_PASSWORD', 'BANK_EMPLOYEE_PASSWORD',
                 'BANK_ALICE_PASSWORD', 'BANK_BOB_PASSWORD', 'BANK_ROLELESS_PASSWORD'):
        stream.write(f'{name}={secrets.token_urlsafe(32)}\n')
print('Created .env with random local credentials; do not commit this file.')
