import { test, expect, type Page as BrowserPage } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { randomUUID } from "node:crypto";
import type { Account, Customer, Transaction } from "../src/model";
function bridge<T>(args: object): T {
  return JSON.parse(
    execFileSync("python3", ["tests/local-bank.py"], {
      input: JSON.stringify(args),
      encoding: "utf8",
      stdio: ["pipe", "pipe", "pipe"],
    }),
  );
}
function password(role: string) {
  return execFileSync("python3", ["tests/local-bank.py"], {
    input: JSON.stringify({ action: "password", role }),
    encoding: "utf8",
    stdio: ["pipe", "pipe", "pipe"],
  });
}
async function signIn(page: BrowserPage, role: string) {
  await page.goto("/");
  await page.getByRole("button", { name: "Sign in securely" }).click();
  await page.locator("#username").fill("phase6-" + role);
  await page.locator("#password").fill(password(role));
  await page.locator("#kc-login").click();
  await expect(
    page.getByRole("heading", { name: "Banking, at a glance." }),
  ).toBeVisible();
}
async function post(
  page: BrowserPage,
  path: string,
  action: () => Promise<void>,
) {
  const response = page.waitForResponse(
    (r) => r.url().endsWith("/api" + path) && r.request().method() === "POST",
  );
  await action();
  return (await response).json();
}
async function payment(
  page: BrowserPage,
  kind: string,
  accountId: number,
  amount: string,
  target?: number,
) {
  await page
    .getByRole("navigation")
    .getByRole("button", { name: "Move money" })
    .click();
  if (await page.getByRole("button", { name: "New payment" }).isVisible())
    await page.getByRole("button", { name: "New payment" }).click();
  await page.getByLabel("Operation").selectOption(kind);
  await page
    .getByLabel(kind === "deposit" ? "Account to fund" : "Source account")
    .selectOption(String(accountId));
  if (target)
    await page.getByLabel("Destination account ID").fill(String(target));
  await page.getByLabel(/^Amount/).fill(amount);
  await page.getByRole("button", { name: "Submit payment" }).click();
  await expect(
    page.getByText(
      "Payment completed. Balances and history have been refreshed.",
    ),
  ).toBeVisible({ timeout: 30000 });
}
test("real PKCE banking lifecycle, uncertain retry, customer ownership and responsive UI", async ({
  page,
  browser,
}) => {
  if (process.env.BANK_E2E_LOCAL !== "1")
    throw new Error(
      "This test writes synthetic local banking data. Set BANK_E2E_LOCAL=1 only for the disposable local stack.",
    );
  test.setTimeout(180000);
  let customer: Customer | undefined;
  const accountIds: number[] = [];
  let previous: string[] | null | undefined;
  page.on("dialog", (dialog) => void dialog.accept());
  const errors: string[] = [];
  page.on("pageerror", (e) => errors.push(e.message));
  try {
    console.log("Browser check: administrator PKCE sign-in");
    await signIn(page, "admin");
    console.log("Browser check: customer and account creation");
    await page
      .getByRole("navigation")
      .getByRole("button", { name: "Customers", exact: true })
      .click();
    await page.getByRole("button", { name: "Add customer" }).click();
    await page.getByLabel("First name").fill("Browser");
    await page.getByLabel("Last name").fill("Phase8");
    await page
      .getByLabel("Email", { exact: true })
      .fill("phase8-" + randomUUID() + "@example.test");
    await page.getByLabel("Phone (E.164)").fill("+38761123456");
    customer = await post(page, "/customers", () =>
      page.getByRole("button", { name: "Save customer" }).click(),
    );
    await page
      .getByRole("navigation")
      .getByRole("button", { name: "Accounts", exact: true })
      .click();
    await page.getByLabel("Customer workspace").fill(String(customer!.id));
    await page.getByRole("button", { name: "Load accounts" }).click();
    for (let i = 0; i < 2; i++) {
      await page.getByRole("button", { name: "Open account" }).click();
      const a: Account = await post(page, "/accounts", () =>
        page
          .getByRole("button", { name: "Create account", exact: true })
          .click(),
      );
      accountIds.push(a.id);
      await expect(
        page.getByRole("button", { name: "Open account" }),
      ).toBeVisible();
    }
    // The server accepts a deposit but the browser loses its response. Reload and
    // retry the recovered key: one durable transaction, one monetary posting.
    await page
      .getByRole("navigation")
      .getByRole("button", { name: "Move money" })
      .click();
    await page.getByLabel("Operation").selectOption("deposit");
    await page
      .getByLabel("Account to fund")
      .selectOption(String(accountIds[0]));
    await page.getByLabel(/^Amount/).fill("25.00");
    console.log("Browser check: lost-response payment recovery");
    let originalKey = "";
    let acceptedId = "";
    await page.route(
      "**/api/transactions/deposit",
      async (route) => {
        originalKey = route.request().headers()["idempotency-key"];
        const response = await route.fetch();
        acceptedId = (await response.json()).id;
        await route.abort("failed");
      },
      { times: 1 },
    );
    await page.getByRole("button", { name: "Submit payment" }).click();
    await expect(
      page.getByRole("button", { name: "Retry same request" }),
    ).toBeVisible();
    await page.reload();
    await page
      .getByRole("navigation")
      .getByRole("button", { name: "Move money" })
      .click();
    // A denied retry does not prove the already-accepted original request failed.
    await page.route(
      "**/api/transactions/deposit",
      (route) =>
        route.fulfill({
          status: 403,
          contentType: "application/json",
          body: JSON.stringify({
            detail: "Temporary permission denial on retry.",
          }),
        }),
      { times: 1 },
    );
    await page.getByRole("button", { name: "Retry same request" }).click();
    await expect(
      page.getByText("Temporary permission denial on retry."),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Retry same request" }),
    ).toBeVisible();
    const retry = page.waitForResponse(
      (r) =>
        r.url().endsWith("/api/transactions/deposit") &&
        r.request().method() === "POST",
    );
    await page.getByRole("button", { name: "Retry same request" }).click();
    const response = await retry;
    expect(response.request().headers()["idempotency-key"]).toBe(originalKey);
    expect((await response.json()).id).toBe(acceptedId);
    await expect(
      page.getByText(
        "Payment completed. Balances and history have been refreshed.",
      ),
    ).toBeVisible({ timeout: 30000 });
    expect(
      Number(
        bridge<Account>({
          action: "request",
          path: "/accounts/" + accountIds[0],
        }).balance,
      ),
    ).toBe(25);
    // Restore the selected workspace after reload; refresh displays actual balances.
    await page.getByLabel("Customer workspace").fill(String(customer!.id));
    await page.getByRole("button", { name: "Load accounts" }).click();
    await payment(page, "transfer", accountIds[0], "5.00", accountIds[1]);
    await page
      .getByRole("navigation")
      .getByRole("button", { name: "Event operations" })
      .click();
    await expect(
      page.getByRole("heading", { name: "Event operations", level: 1 }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Dead letters", exact: true })
      .click();
    await expect(
      page.getByRole("button", { name: "Events", exact: true }),
    ).toBeVisible();
    console.log("Browser check: customer ownership and mobile workflow");
    previous = bridge<string[] | null>({
      action: "bind",
      role: "alice",
      customerId: customer!.id,
    });
    const context = await browser.newContext({
      baseURL: "http://localhost:5173",
      viewport: { width: 390, height: 844 },
    });
    const customerPage = await context.newPage();
    await signIn(customerPage, "alice");
    await expect(
      customerPage.getByRole("button", { name: "Event operations" }),
    ).toHaveCount(0);
    await expect(
      customerPage.getByRole("button", { name: "Open account" }),
    ).toHaveCount(0);
    await payment(customerPage, "withdraw", accountIds[0], "2.00");
    await expect(
      customerPage.getByLabel("Operation").locator("option[value=deposit]"),
    ).toHaveCount(0);
    await customerPage
      .getByRole("navigation")
      .getByRole("button", { name: "Accounts", exact: true })
      .click();
    await customerPage
      .getByRole("button", { name: "View activity" })
      .first()
      .click();
    await expect(
      customerPage.getByRole("heading", {
        name: `Account #${accountIds[0]} activity`,
      }),
    ).toBeVisible();
    await expect(
      customerPage.getByRole("table").getByText("WITHDRAWAL", { exact: true }),
    ).toBeVisible();
    expect(
      await customerPage.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await customerPage.getByRole("button", { name: "Sign out" }).click();
    await expect(
      customerPage.getByRole("button", { name: "Sign in securely" }),
    ).toBeVisible();
    await context.close();
    expect(errors).toEqual([]);
  } finally {
    console.log(
      "Browser check: restoring fixture bindings and retaining financial audit",
    );
    if (previous !== undefined)
      bridge({
        action: "bind",
        role: "alice",
        customerId: previous?.[0] ?? null,
      });
    for (const id of accountIds) {
      const account = bridge<Account>({
        action: "request",
        path: "/accounts/" + id,
      });
      if (Number(account.balance) > 0) {
        let transaction = bridge<Transaction>({
          action: "request",
          method: "POST",
          path: "/transactions/withdraw",
          key: randomUUID(),
          body: {
            accountId: id,
            amount: String(account.balance),
            currency: account.currency,
            description: "Phase8 browser cleanup",
          },
        });
        for (let i = 0; i < 30 && transaction.status === "PENDING"; i++) {
          await new Promise((resolve) => setTimeout(resolve, 1000));
          transaction = bridge({
            action: "request",
            path: "/transactions/" + transaction.id,
          });
        }
        expect(transaction.status).toBe("COMPLETED");
      }
      bridge({
        action: "request",
        method: "PATCH",
        path: "/accounts/" + id + "/status",
        body: { status: "CLOSED" },
      });
    }
    if (customer)
      bridge({
        action: "request",
        method: "DELETE",
        path: "/customers/" + customer.id,
      });
  }
});
