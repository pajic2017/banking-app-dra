export type Identity = {
  subject: string;
  roles: string[];
  customerId?: string;
};
export type Customer = {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  status: string;
};
export type Account = {
  id: number;
  customerId: number;
  accountNumber: string;
  accountType: string;
  currency: string;
  balance: string | number;
  status: string;
};
export type Transaction = {
  id: string;
  transactionReference: string;
  type: string;
  fromAccountId: number | null;
  toAccountId: number | null;
  amount: string | number;
  currency: string;
  description?: string;
  status: "PENDING" | "COMPLETED" | "REJECTED";
  failureCode?: string;
  createdAt: string;
};
export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
export type Notice = {
  id: string;
  event: { eventType: string; aggregateId: string; occurredAt: string };
  receivedAt: string;
};
export type DeadLetter = {
  id: string;
  originalTopic: string;
  payload: string;
  receivedAt: string;
  replayRequests?: number;
};
export const staff = (me: Identity) =>
  me.roles.some((r) => ["ROLE_ADMIN", "ROLE_BANK_EMPLOYEE"].includes(r));
export const admin = (me: Identity) => me.roles.includes("ROLE_ADMIN");
export function positiveId(value: string): number {
  if (!/^[1-9]\d*$/.test(value) || !Number.isSafeInteger(Number(value)))
    throw new Error("Enter a valid positive account or customer ID.");
  return Number(value);
}
export function decimalAmount(value: string, currency: string): string {
  const digits =
    new Intl.NumberFormat("en", {
      style: "currency",
      currency,
    }).resolvedOptions().maximumFractionDigits ?? 2;
  const match = /^(0|[1-9]\d*)(?:\.(\d+))?$/.exec(value);
  if (!match || (match[2]?.length ?? 0) > digits || !/[1-9]/.test(value))
    throw new Error(
      `Enter a positive amount with at most ${digits} decimal places for ${currency}.`,
    );
  return value; // Preserve decimal text; never do floating-point monetary arithmetic.
}
export const money = (value: string | number, currency: string) =>
  new Intl.NumberFormat("en-GB", { style: "currency", currency }).format(
    Number(value),
  );
export const date = (value: string) =>
  new Date(value).toLocaleString("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  });
