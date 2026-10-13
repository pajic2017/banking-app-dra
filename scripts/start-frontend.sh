#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../frontend"
if [[ ! -d node_modules ]]; then npm ci --no-fund; fi
exec npm run dev
