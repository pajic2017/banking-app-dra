import { useState } from "react";
import { date, money, type Page, type Transaction } from "./model";
import { Badge, Field, Pager, Panel, State, useQuery } from "./ui";
export default function History({
  accountId,
  isStaff,
  version,
}: {
  accountId: string;
  isStaff: boolean;
  version: number;
}) {
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const path = accountId
    ? "/transactions/account/" + accountId
    : isStaff
      ? "/transactions"
      : null;
  const result = useQuery<Page<Transaction>>(
    path
      ? `${path}?page=${page}&size=10${status ? "&status=" + status : ""}${type ? "&type=" + type : ""}`
      : null,
    version,
  );
  return (
    <Panel
      title={
        accountId ? `Account #${accountId} activity` : "Transaction history"
      }
    >
      <div className="filters">
        <Field label="Transaction status">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            <option>PENDING</option>
            <option>COMPLETED</option>
            <option>REJECTED</option>
          </select>
        </Field>
        <Field label="Transaction type">
          <select
            value={type}
            onChange={(e) => {
              setType(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All types</option>
            <option>DEPOSIT</option>
            <option>WITHDRAWAL</option>
            <option>TRANSFER</option>
          </select>
        </Field>
      </div>
      {!path && (
        <p className="empty">
          Select an account from your portfolio to view activity.
        </p>
      )}
      <State {...result} empty={result.data?.items.length === 0} />
      {!!result.data?.items.length && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Transaction</th>
                <th>Accounts</th>
                <th>Amount</th>
                <th>Status</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {result.data.items.map((t) => (
                <tr key={t.id}>
                  <td>
                    <strong>{t.type}</strong>
                    <small>{t.transactionReference}</small>
                    <small>{t.description}</small>
                  </td>
                  <td>
                    {t.fromAccountId ? "#" + t.fromAccountId : "Deposit"} →{" "}
                    {t.toAccountId ? "#" + t.toAccountId : "Withdrawal"}
                  </td>
                  <td className="amount">{money(t.amount, t.currency)}</td>
                  <td>
                    <Badge value={t.status} />
                    {t.failureCode && <small>{t.failureCode}</small>}
                  </td>
                  <td>{date(t.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <Pager data={result.data} page={page} setPage={setPage} />
    </Panel>
  );
}
