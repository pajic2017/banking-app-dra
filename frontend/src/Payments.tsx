import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, ApiError } from "./api";
import {
  decimalAmount,
  money,
  positiveId,
  type Account,
  type Transaction,
} from "./model";
import { Badge, ErrorMessage, Field, Panel } from "./ui";
type Attempt = { key: string; path: string; body: Record<string, unknown> };
export default function Payments({
  accounts,
  isStaff,
  changed,
  subject,
}: {
  accounts: Account[];
  isStaff: boolean;
  changed: () => void;
  subject: string;
}) {
  const storageKey = "dra-payment:" + subject;
  const recovered = useRef<Attempt | undefined>(undefined);
  if (!recovered.current) {
    try {
      recovered.current =
        JSON.parse(sessionStorage.getItem(storageKey) || "null") || undefined;
    } catch {
      /* unavailable or malformed session storage */
    }
  }
  const [kind, setKind] = useState("transfer");
  const [source, setSource] = useState("");
  const [target, setTarget] = useState("");
  const [amount, setAmount] = useState("");
  const [description, setDescription] = useState("");
  const [attempt, setAttempt] = useState<Attempt | undefined>(
    recovered.current,
  );
  const [result, setResult] = useState<Transaction>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [pollError, setPollError] = useState("");
  const lock = useRef(false);
  const active = accounts.find((a) => String(a.id) === source);
  const pending = result?.status === "PENDING";
  // An unconfirmed request is frozen, with its original key, until retried.
  // Same-tab reload can recover the intent; no bearer token is stored.
  useEffect(() => {
    try {
      if (attempt && (!result || result.status === "PENDING"))
        sessionStorage.setItem(storageKey, JSON.stringify(attempt));
      else sessionStorage.removeItem(storageKey);
    } catch {
      /* unload warning still guards volatile state */
    }
  }, [attempt, result, storageKey]);
  const frozen = Boolean(attempt && (!result || pending));
  const inFlight = useRef(false);
  useEffect(() => {
    if (!frozen) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [frozen]);
  useEffect(() => {
    if (!pending || !result) return;
    let cancelled = false;
    const poll = async () => {
      if (inFlight.current) return;
      inFlight.current = true;
      try {
        const next = await api<Transaction>("/transactions/" + result.id);
        if (!cancelled) {
          setResult(next);
          setPollError("");
          if (next.status !== "PENDING") changed();
        }
      } catch (e) {
        if (!cancelled) setPollError((e as Error).message);
      } finally {
        inFlight.current = false;
      }
    };
    const interval = window.setInterval(poll, 2000);
    void poll();
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [result?.id, pending, changed]);
  async function submit(event?: FormEvent) {
    event?.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError("");
    try {
      let current = attempt;
      if (!current) {
        if (!active || active.status !== "ACTIVE")
          throw new Error("Choose an active source account.");
        const accountId = positiveId(source);
        const value = decimalAmount(amount.trim(), active.currency);
        const body: Record<string, unknown> = {
          amount: value,
          currency: active.currency,
          description,
        };
        if (kind === "transfer") {
          body.fromAccountId = accountId;
          body.toAccountId = positiveId(target);
          if (body.toAccountId === accountId)
            throw new Error("Choose a different destination account.");
        } else body.accountId = accountId;
        current = {
          key: crypto.randomUUID(),
          path: kind === "transfer" ? "/transfers" : "/transactions/" + kind,
          body,
        };
        setAttempt(current);
      }
      const next = await api<Transaction>(
        current.path,
        "POST",
        current.body,
        current.key,
      );
      setResult(next);
      if (next.status !== "PENDING") changed();
    } catch (e) {
      setError((e as Error).message);
      if (
        !attempt &&
        e instanceof ApiError &&
        [400, 403, 404, 422].includes(e.status)
      )
        setAttempt(undefined);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  function reset() {
    setAttempt(undefined);
    setResult(undefined);
    setError("");
    setAmount("");
    setDescription("");
  }
  return (
    <Panel title="Move money">
      <p className="muted">
        Payments are processed asynchronously. Completion is confirmed by the
        transaction service.
      </p>
      <form onSubmit={submit} className="form-grid">
        <fieldset disabled={busy || frozen} className="form-grid span-all">
          <Field label="Operation">
            <select value={kind} onChange={(e) => setKind(e.target.value)}>
              <option value="transfer">Transfer</option>
              <option value="withdraw">Withdrawal</option>
              {isStaff && <option value="deposit">Deposit</option>}
            </select>
          </Field>
          <Field
            label={kind === "deposit" ? "Account to fund" : "Source account"}
          >
            <select
              required
              value={source}
              onChange={(e) => setSource(e.target.value)}
            >
              <option value="">Select an account</option>
              {accounts
                .filter((a) => a.status === "ACTIVE")
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    #{a.id} · {a.accountNumber} · {a.currency}
                  </option>
                ))}
            </select>
          </Field>
          {kind === "transfer" && (
            <Field label="Destination account ID">
              <input
                required
                inputMode="numeric"
                value={target}
                onChange={(e) => setTarget(e.target.value)}
                placeholder="e.g. 42"
              />
            </Field>
          )}
          <Field label={`Amount${active ? " (" + active.currency + ")" : ""}`}>
            <input
              required
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
            />
          </Field>
          <Field label="Description">
            <input
              maxLength={240}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Optional reference"
            />
          </Field>
        </fieldset>
        <ErrorMessage message={error} />
        <div className="span-all">
          <button
            className="primary"
            disabled={busy || pending || Boolean(result)}
          >
            {busy
              ? "Submitting…"
              : attempt
                ? "Retry same request"
                : "Submit payment"}
          </button>
        </div>
      </form>
      {attempt && (
        <div className="receipt">
          <strong>
            Submitted intent: {String(attempt.body.amount)}{" "}
            {String(attempt.body.currency)}
          </strong>
          <p>
            {attempt.path === "/transfers"
              ? `Transfer from #${attempt.body.fromAccountId} to #${attempt.body.toAccountId}`
              : `${attempt.path.endsWith("deposit") ? "Deposit to" : "Withdrawal from"} #${attempt.body.accountId}`}
          </p>
        </div>
      )}
      {attempt && (
        <p className="muted small">
          Request key: <code>{attempt.key}</code>. Retries reuse this key,
          including after a reload in this browser tab. Do not submit the same
          payment in another tab.
        </p>
      )}
      {result && (
        <div className="receipt" role="status">
          <Badge value={result.status} />
          <h3>{money(result.amount, result.currency)}</h3>
          <p>Reference: {result.transactionReference}</p>
          <p>
            {result.status === "PENDING"
              ? "Awaiting final confirmation. Checking every 2 seconds…"
              : result.status === "COMPLETED"
                ? "Payment completed. Balances and history have been refreshed."
                : `Payment rejected: ${result.failureCode || "See transaction history"}`}
          </p>
          <ErrorMessage message={pollError} />
          {!pending && <button onClick={reset}>New payment</button>}
        </div>
      )}
    </Panel>
  );
}
