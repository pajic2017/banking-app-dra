# Phase 6 executed verification

Validation date: 2026-10-12 (Europe/Sarajevo). Branch `feat/phase-6-security`, based on Phase 5 commit `b5f8bcb49152c4962596d95dac9552c7688409a4`.

## Build and security tests

`./gradlew --no-daemon build` passed **117 tests, zero failures/errors/skips**: Account 34, Customer 34, Transaction 17, Notification 9, Common Contracts 5, Gateway 3, Auth 5, Security Support 8, and Config/Discovery one each. Changed service/security suites executed; unchanged Common/Config/Discovery results were reused. A subsequent missing-audience guard passed the affected Security Support/Gateway suites (**11 tests**) and all six application jars were rebuilt. Security remains enabled during testing. Authorization fixtures use a dedicated test-fixtures artifact; actual JWT verification uses signed RSA tokens/JWKs and deployed Keycloak.

The JWT protocol suite exercises missing/malformed/forged JWTs, wrong issuer/audience, expired/future tokens, missing subject, key rotation, own/foreign customer claims, missing/malformed ownership claim, and authorized-party/service-role combinations. Service-token tests check the client-credentials form, token caching and sanitized failure responses. Gateway tests reject missing and malformed bearer tokens before routing.

Real MySQL/PostgreSQL API authorization tests verify own-resource access, denied foreign reads/updates, denied customer list/create/status operations, narrow service reads, denied human/wrong-service postings with no receipt mutation, caller-scoped idempotency and legacy-key conflicts without another durable command. Earlier monetary, outbox rollback/retry and Kafka/Mongo duplicate/replay tests remain active.

## Local identity provider

Local Keycloak was started with its retained volume. Provisioning passed bank realm settings, mandatory S256 public code flow, disabled public password grant, bank audience/customer claim mapping, admin-only user-profile identity attributes, three separate client-credentials roles and five tagged fixture identities. Actual admin browser login/code exchange returned access and refresh tokens without printing them.

Initial harness issues were corrected: Keycloak 26 represents disabled unmanaged attributes by an absent policy; localhost Secure-cookie handling needed an exact-origin development adapter in urllib; colon-containing bearer fixtures were replaced with RFC 6750-compatible strings. An earlier build also needed restarting after the shared actor helper changed during compilation. These do not disable validation or assertions.

This verifies local application behavior, not a production security audit or deployment. HTTPS, production identity storage, managed secret rotation and infrastructure hardening remain deployment requirements described in the design guide.

## Deployed security and regression

All six changed images were rebuilt with the final audience guard. The fourteen-container secured target became healthy. The security smoke passed **95 authenticated/denied HTTP checks** using actual Keycloak admin, employee, Alice/Bob and three service identities. It checked unlinked customers, own/foreign resources, spoofed headers, roleless/anonymous/invalid JWTs, independent direct backend enforcement, narrowly scoped internal reads, denied human/wrong-service posting, accepted worker posting, owned transfer/withdrawal, forbidden funding/foreign source, participant reads, caller-scoped idempotency, refresh and logout behavior. Logged-out refresh was denied; the already issued access token remained valid until expiry as documented.

The authenticated event smoke passed eight unique correlated Kafka/Mongo notifications. The authenticated monetary regression passed deposits/withdrawals/transfers/rejections, twenty concurrent transfers, ten duplicate requests, history and ledger reconciliation. Synthetic funds were drained, accounts closed and customers deactivated; audit data remains.

The token-aware Newman runner passed the events collection with **32 requests/54 assertions**, and the authorization collection with **20 requests/20 assertions**, zero failures. Private bearer environments and full token-bearing runner exports were deleted; retained summaries contain only aggregate statistics. Protected OpenAPI documents were retrieved on ports 8082/8083/8084/8085. All five backend jars were inspected: each includes production security support and excludes the fixture-decoder artifact.

A first deployed write hit discovery-cache convergence; a read-only Account/Transaction readiness check was added before creating records. The final security and regression runs passed without weakening authentication or retrying ambiguous money writes.

## Recovery and host startup

The authenticated development recovery script passed all six checks: Kafka outage/nonblocking business commit and stable event recovery; duplicate delivery; consumer process restart; malformed quarantine/corrected replay; real MongoDB outage through Kafka DLT and replay after restart; restored services and retained inactive synthetic records. Volumes were preserved.

The updated host startup was exercised with local Keycloak provisioning and per-service client-secret injection. It passed eight health endpoints, centralized configuration, seven Eureka registrations and five gateway foundation routes. Host security smoke passed 95 checks, and host event smoke passed eight unique correlated notifications. Owned host applications were stopped before restoring the fourteen-container secured Docker target.
