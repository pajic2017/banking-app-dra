import { useCallback, useEffect, useState } from "react";
import {
  ArrowLeftRight,
  Building2,
  CreditCard,
  LayoutDashboard,
  LogOut,
  RefreshCw,
  ShieldCheck,
  Users,
  Bell,
  ArrowUpRight,
} from "lucide-react";
import { displayName, hasSession, login, logout, startSession } from "./auth";
import {
  admin,
  staff,
  positiveId,
  type Account,
  type Identity,
  type Page,
} from "./model";
import { ErrorMessage, Field, Pager, Panel, State, useQuery } from "./ui";
import Accounts from "./Accounts";
import Customers from "./Customers";
import Payments from "./Payments";
import History from "./History";
import Notifications from "./Notifications";
type View =
  | "overview"
  | "accounts"
  | "payments"
  | "history"
  | "customers"
  | "notifications";
const nav = [
  { id: "overview", name: "Overview", icon: LayoutDashboard },
  { id: "accounts", name: "Accounts", icon: CreditCard },
  { id: "payments", name: "Move money", icon: ArrowLeftRight },
  { id: "history", name: "Transactions", icon: ArrowUpRight },
  { id: "customers", name: "Customers", icon: Users },
  { id: "notifications", name: "Event operations", icon: Bell },
] as const;
function Workspace({ me }: { me: Identity }) {
  const isStaff = staff(me);
  const isAdmin = admin(me);
  const [view, setView] = useState<View>("overview");
  const [customerId, setCustomerId] = useState(
    isStaff ? "" : me.customerId || "",
  );
  const [input, setInput] = useState(customerId);
  const [inputError, setInputError] = useState("");
  const [accountId, setAccountId] = useState("");
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const changed = useCallback(() => setVersion((v) => v + 1), []);
  const accounts = useQuery<Page<Account>>(
    customerId ? `/accounts/customer/${customerId}?page=${page}&size=20` : null,
    version,
  );
  const choose = (id: string) => {
    setCustomerId(id);
    setInput(id);
    setPage(0);
    setAccountId("");
    setView("accounts");
  };
  const history = (id: string) => {
    setAccountId(id);
    setView("history");
  };
  const visible = nav
    .filter((n) => n.id !== "notifications" || isAdmin)
    .map((n) =>
      n.id === "customers" && !isStaff ? { ...n, name: "Your profile" } : n,
    );
  return (
    <div className="shell">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="DRA Bank home">
          <span className="brand-mark">
            <Building2 size={22} />
          </span>
          DRA<span className="brand-light">bank</span>
        </a>
        <p className="sidebar-label">YOUR WORKSPACE</p>
        <nav aria-label="Main navigation">
          {visible.map((n) => (
            <button
              className={view === n.id ? "selected" : ""}
              key={n.id}
              onClick={() => setView(n.id)}
              aria-current={view === n.id ? "page" : undefined}
            >
              <n.icon size={19} />
              {n.name}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <ShieldCheck size={19} />
          <p>
            Secure banking<small>Protected by Keycloak</small>
          </p>
        </div>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <span>
            Online banking <span className="separator">/</span>{" "}
            {visible.find((n) => n.id === view)?.name}
          </span>
          <div className="user">
            <span className="avatar">
              {displayName().slice(0, 1).toUpperCase()}
            </span>
            <div>
              <strong>{displayName()}</strong>
              <small>
                {isAdmin
                  ? "Administrator"
                  : isStaff
                    ? "Bank employee"
                    : "Customer"}
              </small>
            </div>
            <button
              aria-label="Sign out"
              title="Sign out"
              onClick={() => void logout()}
            >
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main>
          <div className="page-heading">
            <div>
              <p className="eyebrow">DRA BANKING</p>
              <h1>
                {view === "overview"
                  ? "Banking, at a glance."
                  : visible.find((n) => n.id === view)?.name}
              </h1>
              <p className="muted">
                {isStaff
                  ? "Manage customers, accounts and financial operations."
                  : "Your accounts and everyday payments, in one place."}
              </p>
            </div>
            <button className="refresh" onClick={changed}>
              <RefreshCw size={16} />
              Refresh
            </button>
          </div>
          {isStaff && ["overview", "accounts", "payments"].includes(view) && (
            <form
              className="context"
              onSubmit={(e) => {
                e.preventDefault();
                try {
                  choose(String(positiveId(input)));
                  setView(view);
                  setInputError("");
                } catch (e) {
                  setInputError((e as Error).message);
                }
              }}
            >
              <Field label="Customer workspace">
                <input
                  inputMode="numeric"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  placeholder="Enter customer ID"
                />
              </Field>
              <button className="primary">Load accounts</button>
              <span className="muted">
                Choose a customer from the directory or enter an ID.
              </span>
              <ErrorMessage message={inputError} />
            </form>
          )}
          {!isStaff && !customerId && (
            <Panel title="Customer profile not linked">
              <p>
                An administrator must link your identity to a customer record
                before you can access accounts. Your identity has no customer
                ID.
              </p>
            </Panel>
          )}
          {["overview", "accounts", "payments"].includes(view) && (
            <>
              <State {...accounts} empty={accounts.data?.items.length === 0} />
              {!customerId && isStaff && (
                <p className="empty">
                  Select a customer to load their account portfolio.
                </p>
              )}
            </>
          )}
          {view === "overview" && (
            <>
              <div className="hero">
                <div>
                  <span className="hero-tag">YOUR FINANCIAL WORKSPACE</span>
                  <h2>
                    A clearer view.
                    <br />A confident next step.
                  </h2>
                  <p>
                    Review your portfolio or move money with a confirmed
                    transaction trail.
                  </p>
                  <button onClick={() => setView("payments")}>
                    Make a payment <ArrowUpRight size={17} />
                  </button>
                </div>
                <div className="hero-symbol" aria-hidden="true">
                  <Building2 size={100} strokeWidth={1} />
                </div>
              </div>
              <div className="stats">
                <div>
                  <span>Accounts in this view</span>
                  <strong>{accounts.data?.items.length ?? "—"}</strong>
                </div>
                <div>
                  <span>Active in this view</span>
                  <strong>
                    {accounts.data?.items.filter((a) => a.status === "ACTIVE")
                      .length ?? "—"}
                  </strong>
                </div>
                <div>
                  <span>Customer reference</span>
                  <strong>{customerId ? "#" + customerId : "—"}</strong>
                </div>
              </div>
            </>
          )}
          {["overview", "accounts"].includes(view) && accounts.data && (
            <>
              <Accounts
                accounts={accounts.data.items}
                customerId={customerId}
                isStaff={isStaff}
                changed={changed}
                history={history}
              />
              <Pager data={accounts.data} page={page} setPage={setPage} />
            </>
          )}
          {/* Keep payment state mounted while navigating so retries retain the same intent. */}
          <div hidden={view !== "payments"}>
            <Payments
              accounts={accounts.data?.items || []}
              isStaff={isStaff}
              changed={changed}
              subject={me.subject}
            />
            {view === "payments" && (
              <Pager data={accounts.data} page={page} setPage={setPage} />
            )}
          </div>
          {view === "history" && (
            <>
              <div className="filters">
                <Field label="Account history scope">
                  <select
                    value={accountId}
                    onChange={(e) => setAccountId(e.target.value)}
                  >
                    <option value="">
                      {isStaff ? "All transactions" : "Select your account"}
                    </option>
                    {accounts.data?.items.map((a) => (
                      <option key={a.id} value={a.id}>
                        Account #{a.id} · {a.currency}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>
              <History
                key={accountId}
                accountId={accountId}
                isStaff={isStaff}
                version={version}
              />
            </>
          )}
          {view === "customers" && (
            <Customers
              isStaff={isStaff}
              customerId={me.customerId}
              choose={choose}
              version={version}
              changed={changed}
            />
          )}
          {view === "notifications" && isAdmin && (
            <Notifications version={version} changed={changed} />
          )}
          <footer>
            DRA Bank · Banking application{" "}
            <span>Amounts are confirmed by the banking services.</span>
          </footer>
        </main>
      </div>
    </div>
  );
}
export default function App() {
  const [me, setMe] = useState<Identity | null>();
  const [error, setError] = useState("");
  useEffect(() => {
    startSession()
      .then(setMe)
      .catch((e) => setError(e.message));
  }, []);
  if (me) return <Workspace me={me} />;
  return (
    <main className="welcome">
      <div className="welcome-card">
        <div className="brand">
          <span className="brand-mark">
            <Building2 />
          </span>
          DRA<span className="brand-light">bank</span>
        </div>
        <p className="eyebrow">WELCOME TO YOUR BANKING WORKSPACE</p>
        <h1>
          Everyday banking.
          <br />
          Clearly connected.
        </h1>
        <p className="muted">
          Access your accounts, make payments and follow every transaction
          through to completion.
        </p>
        <ErrorMessage message={error} />
        {me === undefined && !error ? (
          <p role="status">Connecting to sign-in…</p>
        ) : (
          <button
            className="primary"
            onClick={() => (error ? window.location.reload() : void login())}
          >
            {error ? "Retry connection" : "Sign in securely"}
            <ArrowUpRight size={18} />
          </button>
        )}
        {error && hasSession() && (
          <button onClick={() => void logout()}>Use another identity</button>
        )}
        <p className="small muted">
          <ShieldCheck size={16} /> Secure sign-in with your banking identity
        </p>
      </div>
      <div className="welcome-art" aria-hidden="true">
        <Building2 size={140} strokeWidth={0.8} />
        <p>
          One place.
          <br />
          Every banking connection.
        </p>
      </div>
    </main>
  );
}
