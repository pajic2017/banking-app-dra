#!/usr/bin/env bash
# Source only in the Codex cloud machine; local developers use their own JDK.
export JAVA_HOME=/workspace/toolchains/java17
export GRADLE_USER_HOME=/workspace/.gradle-cache
export DOCKER_CONFIG=/workspace/.docker-runtime
export NPM_CONFIG_CACHE=/workspace/.npm-cache
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$GRADLE_USER_HOME" "$DOCKER_CONFIG" "$NPM_CONFIG_CACHE"
python3 - <<'PY'
import os
from pathlib import Path
from urllib.parse import urlsplit
proxy = os.environ.get('HTTPS_PROXY') or os.environ.get('https_proxy')
path = Path(os.environ['GRADLE_USER_HOME']) / 'gradle.properties'
lines = path.read_text().splitlines() if path.exists() else []
keys = ('systemProp.https.proxyHost=', 'systemProp.https.proxyPort=',
        'systemProp.http.proxyHost=', 'systemProp.http.proxyPort=',
        'systemProp.javax.net.ssl.trustStore=')
lines = [line for line in lines if not line.startswith(keys)]
if proxy:
    address = urlsplit(proxy)
    if address.username or address.password:
        raise SystemExit('Use platform-provided proxy authentication; never save proxy credentials.')
    for protocol in ('http', 'https'):
        lines += [f'systemProp.{protocol}.proxyHost={address.hostname}',
                  f'systemProp.{protocol}.proxyPort={address.port or 8080}']
if Path('/etc/ssl/certs/java/cacerts').exists():
    lines.append('systemProp.javax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts')
path.write_text('\n'.join(lines) + '\n')
PY
