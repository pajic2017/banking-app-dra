import { useState, type FormEvent } from "react";
import { api } from "./api";
import { type Customer, type Page } from "./model";
import {
  Badge,
  ErrorMessage,
  Field,
  Pager,
  Panel,
  State,
  useQuery,
} from "./ui";
export default function Customers({
  isStaff,
  customerId,
  choose,
  version,
  changed,
}: {
  isStaff: boolean;
  customerId?: string;
  choose: (id: string) => void;
  version: number;
  changed: () => void;
}) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState<Partial<Customer>>();
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmId, setConfirmId] = useState<number>();
  const list = useQuery<Page<Customer>>(
    isStaff
      ? `/customers?page=${page}&size=10${status ? "&status=" + status : ""}`
      : null,
    version,
  );
  const own = useQuery<Customer>(
    !isStaff && customerId ? "/customers/" + customerId : null,
    version,
  );
  const items = isStaff ? list.data?.items : own.data ? [own.data] : [];
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setBusy(true);
    setError("");
    try {
      const { firstName, lastName, email, phone } = editing;
      await api(
        "/customers" + (editing.id ? "/" + editing.id : ""),
        editing.id ? "PUT" : "POST",
        { firstName, lastName, email, phone },
      );
      setEditing(undefined);
      changed();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function deactivate() {
    setBusy(true);
    setError("");
    try {
      await api("/customers/" + confirmId, "DELETE");
      setConfirmId(undefined);
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
        title={isStaff ? "Customer directory" : "Your profile"}
        action={
          isStaff && (
            <button
              className="primary"
              onClick={() => {
                setEditing({});
                setError("");
              }}
            >
              Add customer
            </button>
          )
        }
      >
        {isStaff && (
          <Field label="Customer status">
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value);
                setPage(0);
              }}
            >
              <option value="">All statuses</option>
              <option>ACTIVE</option>
              <option>INACTIVE</option>
            </select>
          </Field>
        )}
        <State
          loading={isStaff ? list.loading : own.loading}
          error={isStaff ? list.error : own.error}
          empty={items?.length === 0}
        />
        {!!items?.length && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Customer</th>
                  <th>Contact</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <strong>
                        {c.firstName} {c.lastName}
                      </strong>
                      <small>Customer #{c.id}</small>
                    </td>
                    <td>
                      {c.email}
                      <small>{c.phone}</small>
                    </td>
                    <td>
                      <Badge value={c.status} />
                    </td>
                    <td className="actions">
                      <button onClick={() => choose(String(c.id))}>
                        Accounts
                      </button>
                      <button
                        onClick={() => {
                          setEditing(c);
                          setError("");
                        }}
                      >
                        Edit
                      </button>
                      {isStaff && c.status === "ACTIVE" && (
                        <button
                          onClick={() => {
                            setConfirmId(c.id);
                            setError("");
                          }}
                        >
                          Deactivate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {isStaff && <Pager data={list.data} page={page} setPage={setPage} />}
      </Panel>
      {confirmId && (
        <Panel title="Deactivate customer">
          <p>
            Deactivate customer #{confirmId}? Existing financial records are
            retained.
          </p>
          <ErrorMessage message={error} />
          <button className="danger" disabled={busy} onClick={deactivate}>
            Confirm deactivation
          </button>{" "}
          <button disabled={busy} onClick={() => setConfirmId(undefined)}>
            Cancel
          </button>
        </Panel>
      )}
      {editing && (
        <Panel title={editing.id ? "Edit customer" : "New customer"}>
          <form onSubmit={save} className="form-grid">
            {(["firstName", "lastName", "email", "phone"] as const).map(
              (key, i) => (
                <Field
                  key={key}
                  label={
                    ["First name", "Last name", "Email", "Phone (E.164)"][i]
                  }
                >
                  <input
                    required
                    type={
                      key === "email"
                        ? "email"
                        : key === "phone"
                          ? "tel"
                          : "text"
                    }
                    maxLength={
                      key === "email" ? 254 : key === "phone" ? 16 : 100
                    }
                    value={editing[key] || ""}
                    onChange={(e) =>
                      setEditing({ ...editing, [key]: e.target.value })
                    }
                    placeholder={key === "phone" ? "+38761123456" : ""}
                  />
                </Field>
              ),
            )}
            <ErrorMessage message={error} />
            <div className="span-all">
              <button className="primary" disabled={busy}>
                {busy ? "Saving…" : "Save customer"}
              </button>{" "}
              <button
                type="button"
                disabled={busy}
                onClick={() => setEditing(undefined)}
              >
                Cancel
              </button>
            </div>
          </form>
        </Panel>
      )}
    </>
  );
}
