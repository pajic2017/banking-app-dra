import { bearer } from "./auth";
export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
  }
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
  key?: string,
): Promise<T> {
  const token = await bearer();
  let response: Response;
  try {
    response = await fetch("/api" + path, {
      method,
      cache: "no-store",
      headers: {
        Authorization: `Bearer ${token}`,
        "X-Correlation-ID": crypto.randomUUID(),
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(key ? { "Idempotency-Key": key } : {}),
      },
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
      signal: AbortSignal.timeout(15000),
    });
  } catch {
    throw new ApiError(
      "The response is unconfirmed. Check your connection and retry the same request.",
      0,
    );
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const fallback =
      response.status === 401
        ? "Your session has expired. Sign in again."
        : response.status === 403
          ? "You do not have permission to perform this action."
          : `Request failed (${response.status}).`;
    const fields = problem.errors ? JSON.stringify(problem.errors) : "";
    throw new ApiError(
      [problem.detail || problem.message || fallback, fields]
        .filter(Boolean)
        .join(" "),
      response.status,
    );
  }
  return response.status === 204 ? (undefined as T) : response.json();
}
