import { useState, type FormEvent } from "react";
import { api } from "./api";
import { money, positiveId, type Account } from "./model";
import { Badge, ErrorMessage, Field, Panel } from "./ui";
export default function Accounts({
  accounts,
  customerId,
  isStaff,
  changed,
  history,
}: {
  accounts: Account[];
  customerId: string;
  isStaff: boolean;
  changed: () => void;
  history: (id: string) => void;
}) {
  const [creating, setCreating] = useState(false);
  const [type, setType] = useState("CURRENT");
  const [currency, setCurrency] = useState("BAM");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [change, setChange] = useState<{ id: number; status: string }>();
  async function create(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await api("/accounts", "POST", {
        customerId: positiveId(customerId),
        accountType: type,
        currency,
      });
      setCreating(false);
      changed();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function update() {
    if (!change) return;
    setBusy(true);
    setError("");
    try {
      await api("/accounts/" + change.id + "/status", "PATCH", {
        status: change.status,
      });
      setChange(undefined);
      changed();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Panel
        title="Account portfolio"
        action={
          isStaff &&
          customerId && (
            <button
              className="primary"
              onClick={() => {
                setCreating(true);
                setError("");
              }}
            >
              Open account
            </button>
          )
        }
      >
        <div className="account-grid">
          {accounts.map((a) => (
            <article className="account-card" key={a.id}>
              <div className="account-top">
                <span>{a.accountType} ACCOUNT</span>
                <Badge value={a.status} />
              </div>
              <h3>{money(a.balance, a.currency)}</h3>
              <p>{a.accountNumber}</p>
              <small>
                Account #{a.id} · Customer #{a.customerId}
              </small>
              <div className="actions">
                <button onClick={() => history(String(a.id))}>
                  View activity
                </button>
                {isStaff && a.status !== "CLOSED" && (
                  <>
                    <button
                      onClick={() => {
                        setChange({
                          id: a.id,
                          status: a.status === "BLOCKED" ? "ACTIVE" : "BLOCKED",
                        });
                        setError("");
                      }}
                    >
                      {a.status === "BLOCKED" ? "Unblock" : "Block"}
                    </button>
                    <button
                      onClick={() => {
                        setChange({ id: a.id, status: "CLOSED" });
                        setError("");
                      }}
                    >
                      Close
                    </button>
                  </>
                )}
              </div>
            </article>
          ))}
        </div>
      </Panel>
      {creating && (
        <Panel title="Open an account">
          <form className="form-grid" onSubmit={create}>
            <Field label="Account type">
              <select value={type} onChange={(e) => setType(e.target.value)}>
                <option>CURRENT</option>
                <option>SAVINGS</option>
              </select>
            </Field>
            <Field label="Currency">
              <input
                required
                pattern="[A-Z]{3}"
                maxLength={3}
                value={currency}
                onChange={(e) => setCurrency(e.target.value.toUpperCase())}
              />
            </Field>
            <p className="muted span-all">
              New accounts start with a zero balance. Funding is a separate
              deposit.
            </p>
            <ErrorMessage message={error} />
            <div className="span-all">
              <button className="primary" disabled={busy}>
                Create account
              </button>{" "}
              <button
                type="button"
                disabled={busy}
                onClick={() => setCreating(false)}
              >
                Cancel
              </button>
            </div>
          </form>
        </Panel>
      )}
      {change && (
        <Panel title="Confirm account status">
          <p>
            Set account #{change.id} to {change.status}? Closed accounts cannot
            be reopened and must have a zero balance.
          </p>
          <ErrorMessage message={error} />
          <button
            className={change.status === "CLOSED" ? "danger" : "primary"}
            disabled={busy}
            onClick={update}
          >
            Confirm status change
          </button>{" "}
          <button disabled={busy} onClick={() => setChange(undefined)}>
            Cancel
          </button>
        </Panel>
      )}
    </>
  );
}
