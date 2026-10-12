#!/usr/bin/env python3
"""Add missing random local Phase 6 credentials, preserving existing .env values."""
import os
import secrets
from pathlib import Path
path=Path(__file__).resolve().parents[1]/'.env'
if not path.exists():
    raise SystemExit('Generate .env first with scripts/create-local-env.py')
existing={line.partition('=')[0].strip() for line in path.read_text().splitlines() if '=' in line}
names=['CUSTOMER_SERVICE_CLIENT_SECRET','ACCOUNT_SERVICE_CLIENT_SECRET','TRANSACTION_SERVICE_CLIENT_SECRET',
       'BANK_ADMIN_PASSWORD','BANK_EMPLOYEE_PASSWORD','BANK_ALICE_PASSWORD','BANK_BOB_PASSWORD','BANK_ROLELESS_PASSWORD']
missing=[name for name in names if name not in existing]
os.chmod(path,0o600)
if missing:
    with path.open('a') as stream:
        stream.write('\n')
        for name in missing: stream.write(f'{name}={secrets.token_urlsafe(32)}\n')
print(f'Local security credentials prepared: {len(missing)} missing entries added; no existing values changed.')
