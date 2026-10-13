import { useEffect, useState, type ReactNode } from "react";
import { api } from "./api";
import type { Page } from "./model";
export function useQuery<T>(path: string | null, version = 0) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  useEffect(() => {
    let active = true;
    setData(undefined);
    setError("");
    setLoading(Boolean(path));
    if (path)
      api<T>(path)
        .then((d) => {
          if (active) setData(d);
        })
        .catch((e) => {
          if (active) setError(e.message);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    return () => {
      active = false;
    };
  }, [path, version]);
  return { data, error, loading };
}
export function State({
  loading,
  error,
  empty,
}: {
  loading: boolean;
  error: string;
  empty?: boolean;
}) {
  return loading ? (
    <p className="empty" role="status">
      Loading banking records…
    </p>
  ) : error ? (
    <p className="error" role="alert">
      {error}
    </p>
  ) : empty ? (
    <p className="empty">No records to show.</p>
  ) : null;
}
export function Badge({ value }: { value: string }) {
  return (
    <span className={`badge ${value.toLowerCase()}`}>
      {value.replaceAll("_", " ")}
    </span>
  );
}
export function Pager({
  data,
  page,
  setPage,
}: {
  data?: Page<unknown>;
  page: number;
  setPage: (page: number) => void;
}) {
  if (!data) return null;
  return (
    <div className="pager">
      <span>
        {data.totalElements} records · Page {page + 1} of{" "}
        {Math.max(1, data.totalPages)}
      </span>
      <button disabled={page === 0} onClick={() => setPage(page - 1)}>
        Previous
      </button>
      <button
        disabled={page + 1 >= data.totalPages}
        onClick={() => setPage(page + 1)}
      >
        Next
      </button>
    </div>
  );
}
export function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  );
}
export function Panel({
  title,
  children,
  action,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <section className="panel">
      <div className="panel-heading">
        <h2>{title}</h2>
        {action}
      </div>
      {children}
    </section>
  );
}
export function ErrorMessage({ message }: { message: string }) {
  return message ? (
    <p className="error" role="alert">
      {message}
    </p>
  ) : null;
}
