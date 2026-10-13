# Phase 8 — React banking frontend

This phase implements the user's requested React application on top of Phases 1–7. It does not implement the deployment phase from the original roadmap. The cumulative feature branch includes the earlier backend phases.

## Run locally

Requirements: Node.js 22.12+ (24 recommended), npm, and the existing Java/Docker banking stack. Start/reconcile Keycloak before signing in; realm imports do not update retained databases automatically.

```bash
# From the repository root; preserve an existing .env.
# On Codex cloud only: source scripts/cloud-environment.sh
npm ci --prefix frontend
# For a first backend setup follow README prerequisites/build instructions.
docker compose up -d --wait --wait-timeout 240 keycloak
python3 scripts/setup-keycloak.py
docker compose up -d --wait --wait-timeout 360 api-gateway auth-service transaction-service notification-service
bash scripts/start-frontend.sh
```

The frontend uses port 5173 and the exact browser origin `http://localhost:5173`. Use `localhost`, not `127.0.0.1`, for sign-in: Keycloak registers an exact callback at `/callback`, web origin and post-logout redirect. Vite binds loopback and proxies `/api` to the gateway at port 8080; backend services still validate every bearer token and ownership. `BANK_API_URL` changes the proxy target, not the canonical Keycloak issuer. This local configuration is not a remote/production identity-provider setup.

Sign in through Keycloak with a configured banking identity. The tagged development users and generated passwords are described in Phase 6; credentials stay in the ignored private `.env`. A CUSTOMER identity needs an administrator-managed `customer_id` binding. The application does not infer ownership from an email address or allow self-linking.

## Screens and roles

| Screen | CUSTOMER | BANK_EMPLOYEE | ADMIN |
|---|---|---|---|
| Overview/accounts | Own paginated accounts, balances, activity | Select customer; create/block/unblock/close accounts | Same as employee |
| Customers/profile | Read/update own profile | Paginated directory; create/update/deactivate | Same as employee |
| Move money | Withdraw/transfer from owned active accounts | Deposit/withdraw/transfer for selected customer | Same as employee |
| Transactions | Selected owned-account history | Global or account history | Same as employee |
| Event operations | Hidden; API denies access | Hidden; API denies access | Event filters, retained dead letters and reviewed replay |

Accounts and histories are paginated, with status/type filters where the API provides them. Account balance cards retain their original currency; the overview does not add different currencies together. A transfer accepts a destination account ID without requiring permission to read the destination's profile. Empty, loading, forbidden, expired-session and unavailable-service states use actual responses. Deactivation, account status and dead-letter replay require a separate user confirmation in the screen. Replay acceptance is explicitly a request, with event records used for consumer confirmation.

## Authentication and payments

`keycloak-js` runs Authorization Code with S256 PKCE, validates the returned state through its adapter and refreshes a short-lived token before API calls. No client secret or password grant is used. Banking tokens stay in adapter memory, not localStorage/sessionStorage. Sign-out redirects to Keycloak and ends the refresh session; already-issued JWT expiry follows the backend's documented security behavior. OIDC discovery is obtained from `/api/auth/oidc`, and the bank identity/roles from `/api/auth/me`.

Payments send decimal **strings**; input checks use ISO currency fraction digits without floating-point arithmetic. The server remains authoritative for currency, limits, funds, ownership and balances. Display formatting uses `Intl.NumberFormat`; it is not used to calculate money.

A payment intent gets one UUID idempotency key. Submission is locked against double clicks. An uncertain network or timeout response keeps the exact path/body/key frozen for retry; the payment component stays mounted across navigation. Same-tab reload recovers that intent from subject-scoped sessionStorage, which contains the request, not tokens. A receipt shows the original intent and request key. Storage may contain a payment description: it is cleared after a confirmed terminal response or a definite validation/permission/not-found rejection on the initial attempt. A denied retry after an uncertain response preserves the original key, since that denial cannot establish the original outcome. Before-unload warns while an outcome is uncertain. If browser storage is unavailable, keep the tab open; closing the browser or submitting the same business intent in a new tab does not preserve client-side intent. Never retry an uncertain payment with a new key.

202/PENDING starts a two-second transaction status poll. Only COMPLETED is reported as successful; REJECTED shows the failure code. Poll failures preserve the pending receipt and continue checking. Terminal status refreshes portfolio/history from the API; the frontend does not compensate, subtract balances optimistically, or orchestrate separate debit/credit requests. Reconciliation and durable posting remain backend responsibilities.

## Build and verification

```bash
npm test --prefix frontend
npm run build --prefix frontend
npm audit --prefix frontend
# Real browser test: disposable local stack ONLY, never production.
# Install a Playwright Chromium browser or set CHROMIUM_BIN to a system Chromium.
cd frontend
npx playwright install chromium
BANK_E2E_LOCAL=1 npm run test:e2e
# Cloud with its existing browser:
CHROMIUM_BIN=/usr/bin/chromium BANK_E2E_LOCAL=1 npm run test:e2e
```

The browser test uses real Keycloak sign-in and gateway APIs. It creates synthetic records, loses an accepted deposit response intentionally, reloads, preserves the key even if a retry is denied, retries the same key and verifies a single posting, transfers, checks admin event screens, signs in as a bound customer on a mobile viewport, withdraws, checks history and logs out. Cleanup restores the prior fixture identity binding, drains and closes created accounts and deactivates the customer while retaining transaction/audit records. Credentials are consumed privately; Playwright traces, screenshots and videos are disabled to avoid recording sign-in secrets. Do not enable authentication tracing or print helper output.

CI installs the lockfile, runs the input/role tests and performs the TypeScript production build. The real browser test is an explicit local integration check, not an automatically provisioned CI backend. Build assets are emitted in ignored `frontend/dist`. `npm run preview --prefix frontend` serves a local production-build check on the same origin, with the same API proxy. Production hosting needs an HTTPS reverse proxy for `/api`, SPA fallback for `/callback`, and exact production Keycloak redirect/origin settings; deployment is outside this phase. Keep tokens, fixture secrets and private `.env` files out of builds and Git.
