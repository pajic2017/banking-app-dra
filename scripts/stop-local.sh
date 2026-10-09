#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
for service in notification-service transaction-service account-service customer-service auth-service api-gateway config-server discovery-server; do
  file=".run/$service.pid"
  if [[ -f "$file" ]]; then
    pid=$(cat "$file")
    # Check the recorded command before sending a signal; do not kill reused PIDs.
    if [[ -r "/proc/$pid/cmdline" ]] && tr '\0' ' ' <"/proc/$pid/cmdline" | grep -Fq "$service/build/libs/app.jar"; then
      kill "$pid"
      for ((i=0; i<30; i++)); do
        [[ -r "/proc/$pid/cmdline" ]] &&
          tr '\0' ' ' <"/proc/$pid/cmdline" | grep -Fq "$service/build/libs/app.jar" || break
        sleep 1
      done
    fi
    rm "$file"
  fi
done
