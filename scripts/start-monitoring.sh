#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/prepare-monitoring.py
compose_args=(-f docker-compose.yml)
services=(prometheus grafana zipkin elasticsearch logstash filebeat kibana)
if [[ "$(docker info --format '{{.Driver}}')" == vfs ]]; then
  bash scripts/setup-crane.sh
  python3 scripts/prepare-vfs-monitoring.py
  compose_args+=(-f .run/monitoring/vfs.compose.json)
  services=(prometheus grafana zipkin elasticsearch logstash filebeat)
fi
docker compose "${compose_args[@]}" --profile monitoring up -d "${services[@]}"
