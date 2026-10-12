#!/usr/bin/env python3
"""Prepare private local scrape credentials; preserve existing .env values."""
import os,secrets
from pathlib import Path
from local_oidc import ROOT,local_value
path=ROOT/'.env'
if not any(line.startswith('MONITORING_PASSWORD=') for line in path.read_text().splitlines()):
    with path.open('a') as stream:stream.write('\nMONITORING_PASSWORD='+secrets.token_urlsafe(32)+'\n')
os.chmod(path,0o600)
directory=ROOT/'.run/monitoring';directory.mkdir(parents=True,exist_ok=True)
secret=directory/'prometheus-password';secret.write_text(local_value('MONITORING_PASSWORD'))
# Prometheus runs as nobody; this directory contains only its narrowly scoped credential.
os.chmod(directory,0o755);os.chmod(secret,0o644)
public=ROOT/'infrastructure/monitoring'
public.chmod(0o755)
for entry in public.rglob('*'):entry.chmod(0o755 if entry.is_dir() else 0o644)
print('Monitoring credential prepared privately in ignored local configuration.')
