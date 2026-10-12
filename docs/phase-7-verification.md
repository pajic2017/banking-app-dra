# Phase 7 executed verification

Validated on 2026-10-12 (Europe/Sarajevo), branch `feat/phase-7-observability`, based on Phase 6 commit `3b072fc819657f2d46dc2986a8978ea2741cbd19`.

## Automated checks

The build passed 120 tests before the final OpenTelemetry switch; final OpenTelemetry results and deployed outcomes are recorded below. Three new observability cases prove persisted W3C parent continuation after the original scope ends, legacy no-context handling with MDC restoration, and authenticated/unauthenticated/wrong-password Prometheus access. Existing JWT/ownership, real MySQL/PostgreSQL monetary/outbox tests and real Kafka/Mongo duplicate/retry/replay tests remain enabled. Background metric polling is disabled only in disposable business-test contexts, preventing polls after their test containers close; live scraping verifies the active collectors.

Initial scrape/tracing fixture failures were diagnosed: Spring Boot tests disable observability by default, so the focused tests explicitly enable real observation/tracing/metrics. An empty reactive user list was replaced with a disabled placeholder when no scrape credential is configured. No authentication/assertion was bypassed. The master prompt requires OpenTelemetry; the initially tested Brave bridge was replaced before final commit.

## Infrastructure and storage

All seven vendor monitoring components were prepared and their source digests recorded. The official crane v0.20.3 archive SHA-256 was verified. On this VFS-only 32 GB cloud, digest-verified vendor filesystems were imported as single-layer local images with preserved runtime configuration; ordinary Compose retains official references. Only unused project-tagged images and unshared project jar-cache records were removed. Financial/notification volumes and user data were retained.

The standard twenty-one-container profile reached the disk limit because VFS duplicates both image layers and container writable layers. The verified cloud workflow runs the twenty-container core target and switches to Kibana viewing mode separately. Monitoring was paused/removed for large Testcontainers builds, retaining all named volumes. Public provisioning-file permissions were corrected for non-root Prometheus/Grafana; private application credentials remain ignored. Elasticsearch's development disk watermarks were set to absolute free-space thresholds and the flood block cleared after freeing space, without deleting indices. MongoDB was restarted after the disk-pressure exit and retained its volume.

## Live checks

The first live run confirmed eight authenticated scrape targets, three nonnegative outbox gauges, four loaded alerts, Grafana's six-panel dashboard/two data sources, a sampled W3C trace through gateway/durable worker/Account/outbox/Kafka/Notification, and indexed logs from Transaction/Account/Notification sharing that trace. Synthetic balances were drained, accounts closed and customer deactivated. Final OpenTelemetry live verification follows below.

Kibana reached available status and its `bank-logs-*` data view/default view were successfully provisioned in the separate viewing mode. Seven-day ILM policy/template/settings were applied without erasing records. Zipkin's local in-memory traces are deliberately transient across restarts; Elasticsearch and Grafana data remain persistent.

## Final OpenTelemetry results

The final `./gradlew --no-daemon build` passed **120 tests, zero failures/errors/skips**, using the OpenTelemetry bridge and Zipkin exporter. Affected service/library suites executed; unchanged wire-contract results reused prior passing output. The final gateway/worker/consumer jars were inspected for the OpenTelemetry bridge and exclusion of Brave.

All eight final application images were rebuilt. The core monitoring smoke passed eight authenticated scrape targets, three nonnegative outbox gauges, four loaded alerts and the six-panel/two-source Grafana provisioning. Trace `691352bf27f44bf0974e3ebb57390605` linked gateway, Transaction worker, Account posting, outbox, Kafka and Notification. Elasticsearch contained correlated operation logs from Transaction, Account and Notification. Synthetic funds were drained and account/customer audit retained.

The secured regression passed **92 HTTP checks** (readiness polling makes the total run-dependent), including owned/foreign resources, role boundaries, service identities, refresh/logout and idempotency. Events passed eight unique correlated notifications. Money passed deposits/withdrawals/transfers/rejections, twenty concurrent transfers, ten duplicate requests, history and reconciliation. The token-aware events Postman collection passed **31 requests/53 assertions**, with no token-bearing environment/report retained.

The final running cloud layout is twenty core containers. Kibana was validated separately with an available UI/API and successfully provisioned default bank-log data view; it is stopped in the core layout. No twenty-one-container concurrency or production availability/security claim is made. The earlier disk-pressure exit required restarting MongoDB with its retained volume; final notification/security/event workflows all passed after recovery. Financial data and indices were never erased to repair setup.
