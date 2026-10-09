# Phase 5 executed verification

Validated on 2026-10-09 using Java 17, Gradle 8.14.3 and Docker. Branch: `feat/phase-5-kafka-integration`, based on Phase 4 commit `a4bf4d14f3f71b6b99c9390d5345f26def0f78d5`. These are development checks, not production availability or security certification.

## Automated build

`./gradlew --no-daemon build` passed: **95 tests, zero failures/errors/skips**. Account 32, Customer 32, Transaction 13, Notification 9, Common Contracts 5, and Gateway/Auth/Config/Discovery one each. Changed suites executed; unchanged foundation suites reused passing results. Real disposable MySQL 8.4, PostgreSQL 17, Kafka 3.9.1 and MongoDB 8.0 were used. No H2 substitution or skipped database tests.

Producer checks cover creation/outbox rollback, terminal outcome/outbox rollback after remote monetary posting, stable correlation, broker failure/backoff, and broker acknowledgement followed by a failed local publication mark with identical replay. Earlier monetary concurrency, receipts, balanced-journal and recovery tests also pass.

Notification checks cover all five topics, malformed/unsupported/mismatched events, transient retry, retry exhaustion, retained dead letters, validated correction and repeated replay, conflicting event ID payloads, a storage acknowledgement followed by pre-offset failure, consumer stop/resume with committed offsets, and an independent group reading the same record. Mongo deduplication preserves one notification record per event ID.

## Deployed workflow

The twelve-container target is Discovery, Config, Gateway, Customer, Account, Transaction, Notification, two MySQL stores, PostgreSQL, MongoDB and Kafka. Four changed application images were rebuilt; the gateway was restarted to load its Notification route.

`python3 scripts/events-smoke-test.py` passed all three producer outboxes and eight unique correlated notifications: one customer, two accounts, four successful outcomes and one rejected transfer. An idempotent monetary replay produced no additional outcome. Stored event IDs, v1 envelopes, correlation IDs and Kafka coordinates were checked. Synthetic funds were drained, accounts closed and customer deactivated; audit records remain.

`python3 scripts/transaction-smoke-test.py` passed deposits, withdrawals, successful/rejected transfers, **20 concurrent transfers and 10 duplicate submissions**, history and reconciliation. It drained and closed its synthetic accounts.

Notification OpenAPI JSON and Swagger HTML were retrieved successfully. Five business topics and five corresponding dead-letter topics have three partitions each, replication factor one, with seven-day business and thirty-day dead-letter retention configured. External host Kafka and container listeners use distinct advertised endpoints.

## Recovery and tooling

`python3 scripts/events-recovery-test.py` passed broker outage/nonblocking business commit, retained outbox recovery with the original event ID, duplicate real Kafka deliveries, consumer process restart, malformed-event quarantine/corrected replay, and real MongoDB outage through Kafka DLT followed by Mongo restart and unchanged replay. Earlier stored notifications survived both restarts. Synthetic customers were deactivated and audit records retained.

The first recovery harness run timed out joining a temporary CLI group; direct partition assignment corrected the reader. The final run passed all six checks. The Phase 5 Postman collection passed **32 requests and 54 assertions**, including polling, nine correlated events, exact event-type counts, unique IDs, single-record reads and retained quarantine inspection. Newman used a writable cloud npm cache; no project dependency was added.

The recovery script deliberately stops development containers and restores them without deleting volumes; it never erases financial or notification history.

The updated host-Java startup also passed eight health endpoints, centralized configuration, seven Eureka registrations and five gateway foundation routes. Its event smoke passed eight unique correlated notifications using the external Kafka listener and host MongoDB endpoint. Owned host applications were stopped before restoring the twelve-container Docker target.

Authentication and resource ownership are Phase 6. External email/SMS and advanced fraud detection are not connected. At-least-once delivery with notification deduplication does not imply exactly-once transport or external message delivery.
