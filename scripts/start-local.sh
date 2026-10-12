#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then python3 scripts/prepare-security-env.py; fi
source scripts/local-customer-env.sh
source scripts/local-account-env.sh
source scripts/local-transaction-env.sh
if [[ -z "${CUSTOMER_DB_URL:-}" ]]; then
  docker compose up -d --wait --wait-timeout 180 customer-db
fi
if [[ -z "${ACCOUNT_DB_URL:-}" ]]; then
  docker compose up -d --wait --wait-timeout 180 account-db
fi
if [[ -z "${TRANSACTION_DB_URL:-}" ]]; then
  docker compose up -d --wait --wait-timeout 180 transaction-db
fi
if [[ -z "${NOTIFICATION_MONGO_URI:-}" ]]; then
  docker compose up -d --wait --wait-timeout 180 notification-db
fi
if [[ -z "${KAFKA_BOOTSTRAP_SERVERS:-}" ]]; then
  docker compose up -d --wait --wait-timeout 180 kafka
fi
if [[ -z "${KEYCLOAK_ISSUER:-}" ]]; then
  docker compose up -d --wait --wait-timeout 240 keycloak
  python3 scripts/setup-keycloak.py
fi
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
    local client_secret="${SERVICE_CLIENT_SECRET:-}"
    case "$service" in
      customer-service|account-service|transaction-service)
        client_secret=$(SECURITY_CLIENT_NAME="$service" PYTHONPATH=scripts python3 - <<'PYCODE'
import os
from local_oidc import local_value
print(local_value(os.environ['SECURITY_CLIENT_NAME'].split('-')[0].upper()+'_SERVICE_CLIENT_SECRET'))
PYCODE
        ) || return 1
        ;;
    esac
    SERVICE_CLIENT_SECRET="$client_secret" nohup "${JAVA_HOME:+$JAVA_HOME/bin/}java" -Xms64m -Xmx256m -jar "$service/build/libs/app.jar" >".run/$service.log" 2>&1 &
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
