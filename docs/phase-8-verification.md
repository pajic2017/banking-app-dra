# Phase 8 verification — 2026-10-13

Verified in the Codex cloud checkout on `feat/phase-8-react-frontend`, based on the pushed Phase 7 branch. This is current-instance evidence, not a production deployment or fresh snapshot restoration test.

| Check | Result |
|---|---|
| `npm ci --prefix frontend --no-fund` | Lockfile installation passed using Node 24.19.0 / npm 11.9.0 |
| `npm test --prefix frontend` | 18 tests passed: exact decimal text, ISO precision, invalid amount/identifier rejection and role navigation |
| `npm run build --prefix frontend` | Strict TypeScript check and Vite production build passed |
| `npm audit --prefix frontend` | Zero reported vulnerabilities, including development dependencies |
| Real Playwright/Chromium integration | One full lifecycle test passed (40.1 seconds; 48.4 seconds including startup) |
| `python3 scripts/security-smoke-test.py` | 91 real authenticated/denied HTTP checks passed |
| `bash scripts/start-frontend.sh` | Server started; SPA root/callback and proxied S256 discovery checked |
| `git diff --check` | Passed |

The browser test signed in as administrator through real Keycloak S256 PKCE, created a synthetic customer and two zero-balance accounts, and submitted a 25.00 BAM deposit. The server accepted the request, while a deliberate browser interception lost the response. Reload restored the session through a top-level SSO check. A simulated 403 on the next retry retained the original intent/key; the subsequent real retry returned the same transaction ID. The account held 25.00 BAM, demonstrating one posting rather than two.

The same workflow completed a 5.00 BAM transfer, read administrator event/dead-letter screens, temporarily linked the tagged CUSTOMER fixture to the synthetic customer, and used a 390 × 844 mobile viewport to withdraw 2.00 BAM and read owned-account history. Staff funding/account creation and administrator navigation were absent for CUSTOMER. The mobile page stayed within the viewport, with horizontally scrollable navigation/tables. Keycloak logout returned to the sign-in screen. No JavaScript page errors were reported.

Cleanup restored the fixture's previous customer binding, drained and closed both synthetic accounts, and deactivated the synthetic customer. Financial history was retained. The security regression separately verified issuer/audience validation, anonymous/invalid token denial, ownership and spoofed-header denial, staff/admin boundaries, scoped internal identities, durable posting, caller-scoped idempotency, refresh, logout and expiry semantics.

The first browser run exposed missing sign-in restoration after reload; this was fixed with Keycloak's top-level `check-sso`. Later failures were strict locator ambiguities in the test and were corrected. Final browser validation includes the retry-denial safeguard. A Playwright browser download was denied by the cloud network policy; the installed system Chromium was used successfully without changing TLS verification or network policy. The development test runner was upgraded to clear the audit findings.

Backend Java source and database migrations were unchanged in this phase, so the full Gradle/Testcontainers suite was not rerun; Phase 7's 120-test evidence remains historical. New realm configuration adds exact frontend callback/origin/logout settings and was reconciled into the retained local Keycloak instance. Production hosting, HTTPS/identity configuration, CI backend provisioning and deployment automation remain outside this frontend phase.
