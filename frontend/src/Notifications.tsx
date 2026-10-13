import { useState } from "react";
import { api } from "./api";
import { date, type DeadLetter, type Notice, type Page } from "./model";
import { ErrorMessage, Field, Pager, Panel, State, useQuery } from "./ui";
export default function Notifications({
  version,
  changed,
}: {
  version: number;
  changed: () => void;
}) {
  const [tab, setTab] = useState("events");
  const [page, setPage] = useState(0);
  const [eventType, setEventType] = useState("");
  const [aggregateId, setAggregateId] = useState("");
  const [record, setRecord] = useState<DeadLetter>();
  const [payload, setPayload] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const events = useQuery<Page<Notice>>(
    tab === "events"
      ? `/notifications?page=${page}&size=10${eventType ? "&eventType=" + eventType : ""}${aggregateId ? "&aggregateId=" + encodeURIComponent(aggregateId) : ""}`
      : null,
    version,
  );
  const dead = useQuery<Page<DeadLetter>>(
    tab === "dead" ? `/notifications/dead-letters?page=${page}&size=10` : null,
    version,
  );
  async function replay() {
    if (!record || busy) return;
    setError("");
    setBusy(true);
    try {
      JSON.parse(payload);
      const response = await api<{ eventId: string }>(
        "/notifications/dead-letters/" + record.id + "/replay",
        "POST",
        { correctedPayload: payload },
      );
      setMessage(
        "Replay requested for event " +
          response.eventId +
          ". Check event records for consumer confirmation.",
      );
      setRecord(undefined);
      changed();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Panel title="Event operations">
        <div className="tabs">
          <button
            aria-pressed={tab === "events"}
            onClick={() => {
              setTab("events");
              setPage(0);
            }}
          >
            Events
          </button>
          <button
            aria-pressed={tab === "dead"}
            onClick={() => {
              setTab("dead");
              setPage(0);
            }}
          >
            Dead letters
          </button>
        </div>
        {message && (
          <p role="status" className="receipt">
            {message}
          </p>
        )}
        {tab === "events" ? (
          <>
            <div className="filters">
              <Field label="Event type">
                <select
                  value={eventType}
                  onChange={(e) => {
                    setEventType(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">All types</option>
                  {[
                    "CUSTOMER_CREATED",
                    "ACCOUNT_CREATED",
                    "TRANSACTION_COMPLETED",
                    "TRANSACTION_FAILED",
                    "SUSPICIOUS_TRANSACTION_DETECTED",
                  ].map((t) => (
                    <option key={t}>{t}</option>
                  ))}
                </select>
              </Field>
              <Field label="Aggregate ID">
                <input
                  value={aggregateId}
                  maxLength={64}
                  onChange={(e) => {
                    setAggregateId(e.target.value);
                    setPage(0);
                  }}
                />
              </Field>
            </div>
            <State {...events} empty={events.data?.items.length === 0} />
            {!!events.data?.items.length && (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Event</th>
                      <th>Aggregate</th>
                      <th>Received</th>
                    </tr>
                  </thead>
                  <tbody>
                    {events.data.items.map((n) => (
                      <tr key={n.id}>
                        <td>
                          <strong>{n.event.eventType}</strong>
                          <small>{n.id}</small>
                        </td>
                        <td>{n.event.aggregateId}</td>
                        <td>{date(n.receivedAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <Pager data={events.data} page={page} setPage={setPage} />
          </>
        ) : (
          <>
            <State {...dead} empty={dead.data?.items.length === 0} />
            {dead.data?.items.map((d) => (
              <div className="dead-row" key={d.id}>
                <div>
                  <strong>{d.originalTopic}</strong>
                  <small>
                    {date(d.receivedAt)} · {d.replayRequests || 0} replay
                    requests
                  </small>
                </div>
                <button
                  onClick={() => {
                    setRecord(d);
                    setPayload(d.payload);
                    setError("");
                  }}
                >
                  Review replay
                </button>
              </div>
            ))}
            <Pager data={dead.data} page={page} setPage={setPage} />
          </>
        )}
      </Panel>
      {record && (
        <Panel title="Review quarantined event">
          <p>
            Confirm the event ID and corrected payload before replay. Preserve
            the event ID when retrying an unconfirmed replay.
          </p>
          <Field label="Event JSON">
            <textarea
              rows={12}
              value={payload}
              onChange={(e) => setPayload(e.target.value)}
              spellCheck={false}
            />
          </Field>
          <ErrorMessage message={error} />
          <button className="primary" disabled={busy} onClick={replay}>
            {busy ? "Requesting…" : "Confirm replay"}
          </button>{" "}
          <button disabled={busy} onClick={() => setRecord(undefined)}>
            Cancel
          </button>
        </Panel>
      )}
    </>
  );
}
