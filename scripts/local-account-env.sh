#!/usr/bin/env bash
# Source from the repository root. Read one credential without executing .env as shell code.
if [[ -z "${ACCOUNT_DB_PASSWORD:-}" ]]; then
  if [[ ! -f .env ]]; then
    echo 'Set ACCOUNT_DB_PASSWORD securely or run python3 scripts/create-local-env.py first.' >&2
    return 1
  fi
  ACCOUNT_DB_PASSWORD=$(python3 - <<'PY'
from pathlib import Path
for line in Path('.env').read_text().splitlines():
    key, sep, value = line.partition('=')
    if sep and key.strip() == 'ACCOUNT_DB_PASSWORD' and value.strip():
        print(value.strip())
        break
else:
    raise SystemExit('ACCOUNT_DB_PASSWORD is missing from .env')
PY
  ) || return 1
  export ACCOUNT_DB_PASSWORD
fi
