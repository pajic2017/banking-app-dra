# Phase 4 verification

Executed on 2026-10-09 using Java 17, the pinned Gradle 8.14.3 wrapper, Docker, MySQL 8.4 and PostgreSQL 17. These results verify the development workflow and tested monetary invariants.

## Automated build and tests

`source scripts/cloud-environment.sh && ./gradlew --no-daemon build` completed successfully in 2m 39s. JUnit XML contains **79 tests, zero failures, errors or skips**:

| Module | Tests |
|---|---:|
| Account Service | 31 |
| Transaction Service | 11 |
| Customer Service | 29 |
| Common versioned contracts | 3 |
| Five remaining foundation modules | 5 |

Changed Account and Transaction suites executed using real disposable MySQL/PostgreSQL databases. Common contract tests executed; unchanged Customer/foundation tasks reused their passing results.

Verified financial cases:

* Deposit, withdrawal and transfer produce balanced journal entries and matching balance projections, including clearing counterentries.
* Twelve concurrent identical posting commands apply once and return the same receipt; a different instruction using the same ID returns 409.
* Twenty concurrent transfers against only enough funds for ten yield ten completions and ten rejections, no negative balance and conserved customer totals.
* Twenty simultaneous opposite-direction transfers complete without changing the total or either final balance, using ascending account lock order.
* Missing/blocked accounts, insufficient funds, currency mismatch and balance-capacity overflow reject without partial changes. A rejection remains rejected after later funding.
* A real SQL check failure on the second transfer journal line rolls back both balance changes, journal entries and receipt; retrying the same operation succeeds after the fault is removed.
* Reconciliation detects a deliberately corrupted balance projection.
* Twelve simultaneous idempotency-key submissions create one PostgreSQL command; changed payload reuse is rejected.
* A real HTTP stub applies a posting and closes the response. The command remains PENDING, then replay returns the same receipt with one remote application.
* An injected PostgreSQL trigger failure prevents the terminal status update after remote application. The local transaction rolls back to PENDING and replay recovers without a second application.
* Dependency 503, malformed JSON and wrong receipt IDs remain pending with persisted retries. Business rejection is terminal. A locked queue row is skipped so another worker can progress.
* Currency fraction digits, zero/negative/overflow amounts, invalid keys, pagination, missing transactions, Flyway and generated OpenAPI are checked.

The first monetary test run identified a reserved MySQL alias in the reconciliation query and a privilege requirement in the trigger-based fault fixture. The query alias was corrected; the MySQL fault test now uses a narrow check constraint in its disposable database, preserving a real second-line SQL failure without increasing application privileges. The complete affected suites then passed.

## Live Docker workflow

Built Account and Transaction images, started the nine-container Phase 4 target with Compose and restarted Gateway to reload its route configuration. Discovery, Config, Gateway, Customer, Account, Transaction, two MySQL databases and Transaction PostgreSQL all became healthy.

`python3 scripts/transaction-smoke-test.py` passed through the real Gateway and three business services. It verified funding, canonical duplicate replay, key conflict, transfers, withdrawals, insufficient funds and blocked-account rejection, invalid precision, **20 concurrent transfers**, **10 duplicate submissions**, paginated history, customer overview and ledger reconciliation. It drained its synthetic funds and retained CLOSED accounts and an INACTIVE customer.

`npx --yes newman@6.2.1 run postman/transactions.postman_collection.json --delay-request 200` passed **27 requests and 47 assertions**, zero failures. The collection has 25 distinct requests; bounded status polling added two requests in this run. It drained funds and closed its development accounts. Transaction OpenAPI was retrieved with transfer, deposit and account history paths.

## Outage and restart recovery

Created two synthetic accounts and deposited 10 BAM. Stopped Account Service and submitted a transfer of 3 BAM. Transaction Service accepted and durably retained it PENDING with a retry attempt. Stopped Transaction Service, restarted its PostgreSQL database without deleting volumes, then restarted Transaction Service while Account remained unavailable. The original command ID, creation time and retry state were preserved.

After Account Service returned, the original command completed. Balances were exactly **7 BAM and 3 BAM**. MySQL contained exactly **one receipt and two journal entries** for that operation; the journal reconciliation report was balanced. Resubmitting the same key returned the original completed transaction and moved no money. Recovery fixture funds were then withdrawn, accounts closed and customer deactivated while all history remained retained.

## Host startup workflow

Stopped the six Compose applications while retaining all three databases, then ran the updated `bash scripts/start-local.sh`. It securely loaded the three individual development credentials, verified the MySQL/PostgreSQL stores and started all eight host Java applications. Foundation smoke passed eight health endpoints, centralized configuration, seven Eureka registrations and five gateway routes. The complete monetary smoke also passed under host Java, including its concurrency, idempotency, history and reconciliation checks. Transaction Swagger UI HTML was retrieved successfully.

## Scope

The tested target excludes optional Kafka, Keycloak and monitoring. Phase 5 messaging/outbox and Phase 6 authentication/ownership are not implemented. Clearing is an educational external-settlement counterparty with no real banking network integration. Transfers rely on both accounts being in the same Account database; cross-currency and multi-bank/multi-database settlement require further design. See [Phase 4 architecture and commands](phase-4.md).
