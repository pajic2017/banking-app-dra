#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .run
wait_health() {
  for ((attempt=0; attempt<90; attempt++)); do
    if curl -fsS "http://localhost:$1/actuator/health/readiness" >/dev/null 2>&1; then return; fi
    sleep 2
  done
  echo "Service on port $1 failed readiness; inspect .run logs" >&2
  return 1
}
start() {
  local service="$1" port="$2"
  if [[ -s ".run/$service.pid" ]] &&
      [[ -r "/proc/$(cat ".run/$service.pid")/cmdline" ]] &&
      tr '\0' ' ' <"/proc/$(cat ".run/$service.pid")/cmdline" | grep -Fq "$service/build/libs/app.jar"; then
    echo "$service is already running"
  else
    if curl -fsS "http://localhost:$port/actuator/health" >/dev/null 2>&1; then
      echo "Port $port is occupied; refusing to start $service" >&2; return 1
    fi
    nohup "${JAVA_HOME:+$JAVA_HOME/bin/}java" -Xms64m -Xmx256m -jar "$service/build/libs/app.jar" >".run/$service.log" 2>&1 &
    local process_pid=$!
    if ! echo "$process_pid" >".run/$service.pid"; then
      kill "$process_pid"
      return 1
    fi
  fi
  wait_health "$port"
}
start discovery-server 8761
start config-server 8888
start api-gateway 8080
start auth-service 8081
start customer-service 8082
start account-service 8083
start transaction-service 8084
start notification-service 8085
python3 scripts/smoke-test.py
