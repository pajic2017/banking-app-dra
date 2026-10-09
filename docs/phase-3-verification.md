# Phase 3 verification

Verified on 2026-10-09 in `/workspace/banking-app-dra`, using Java 17, the pinned Gradle 8.14.3 wrapper, Docker and real MySQL 8.4. These are development checks, not a production readiness claim.

## Build and automated tests

`source scripts/cloud-environment.sh && ./gradlew --no-daemon build` completed successfully. JUnit XML results contain **57 tests, zero failures, errors or skips**:

| Module | Tests |
|---|---:|
| Account Service | 22 |
| Customer Service | 29 |
| Six other foundation modules | 6 |

Account/Customer changed suites executed in this build; unchanged foundation tasks reused their passing results. Integration tests use disposable MySQL containers, Flyway migrations and Hibernate schema validation. A real bounded HTTP stub exercises customer success, missing/inactive customer, dependency 503, malformed JSON, unknown status and timeout, with no account inserted after failed verification. Other checks cover ISO monetary currency validation, zero balances, retrieval/pagination, terminal/idempotent statuses, nonzero closure rejection and stale optimistic updates. Customer overview tests cover pagination, absent customer, dependency failure and mismatched customer references.

Initial schema validation exposed a CHAR/VARCHAR mapping mismatch. The entity now explicitly uses `@JdbcTypeCode(Types.CHAR)`; Flyway DDL and checksums were preserved and the affected suite reran successfully.

## Live services

`bash scripts/start-local.sh` started both MySQL databases and all eight host Java applications. Its foundation smoke passed eight health endpoints, centralized configuration, seven Eureka registrations and five gateway routes. `python3 scripts/account-smoke-test.py` then passed through real Gateway, Customer and Account services: two account types/currencies, unique internal numbers, details, zero balances, paginated account overview, status transitions, terminal closure, inactive-customer rejection and unsupported currency rejection.

`npx --yes newman@6.2.1 run postman/accounts.postman_collection.json` passed **16 requests and 25 assertions**, with zero failures, after gateway discovery was ready. An initial invocation before discovery completed received transient 503 responses; the successful run followed the smoke readiness check. The collection writes uniquely named synthetic development customers and retains closed accounts rather than deleting them.

Both services' generated OpenAPI JSON was retrieved. Account paths and Customer's `/customers/{id}/accounts` path were present.

After stopping owned host Java processes, `docker compose build customer-service account-service` and `docker compose up -d --wait --wait-timeout 300 api-gateway account-service` completed. All seven selected containers became healthy. The same account smoke passed in Docker. Previously created host-workflow records were also readable from the containerized service using the same persistent stores.

## Persistence and dependency recovery

Stopped Account Service and verified that Customer's account overview returned HTTP 503 while its customer lookup remained HTTP 200. Restarted Account MySQL without removing its volume, then restarted Account Service and waited for readiness. Both retained closed accounts had exactly the same IDs, account numbers, statuses, balances and timestamps as their pre-restart response. Customer overview recovered with the same page. Flyway history still contained successful V1 (version 1, success 1).

## Limits

Only the seven-container Phase 3 target was rebuilt and checked here; optional Kafka, Keycloak and monitoring were not exercised. No deposit, withdrawal, transfer, ledger, Kafka account event, JWT or customer ownership enforcement is implemented in this phase. Customer verification is point-in-time, not a distributed transaction; creation has no idempotency contract. See [Phase 3 design](phase-3.md) for these boundaries and Phase 4 requirements.
