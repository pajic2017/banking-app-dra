import { describe, expect, it } from "vitest";
import { decimalAmount, positiveId, staff, admin } from "../src/model";
describe("banking inputs", () => {
  it("preserves exact decimal text and supports ISO precision", () => {
    expect(decimalAmount("123456789.12", "BAM")).toBe("123456789.12");
    expect(decimalAmount("1.001", "KWD")).toBe("1.001");
    expect(decimalAmount("1", "JPY")).toBe("1");
  });
  it.each(["0", "0.00", "-1", "1e3", "NaN", ".1", "01.00", "1.001"])(
    "rejects invalid BAM amount %s",
    (v) => expect(() => decimalAmount(v, "BAM")).toThrow(),
  );
  it("rejects fractional yen", () =>
    expect(() => decimalAmount("1.1", "JPY")).toThrow());
  it.each(["0", "-1", "1.2", "1e2", "9007199254740992"])(
    "rejects invalid identifiers %s",
    (v) => expect(() => positiveId(v)).toThrow(),
  );
  it("accepts a positive identifier", () => expect(positiveId("42")).toBe(42));
});
describe("role navigation", () => {
  it("keeps customer and employee out of administrator screens", () => {
    for (const role of ["ROLE_CUSTOMER", "ROLE_BANK_EMPLOYEE"])
      expect(admin({ subject: "user", roles: [role] })).toBe(false);
    expect(staff({ subject: "user", roles: ["ROLE_CUSTOMER"] })).toBe(false);
  });
  it("allows staff controls for bank employee and admin", () => {
    for (const role of ["ROLE_BANK_EMPLOYEE", "ROLE_ADMIN"])
      expect(staff({ subject: "user", roles: [role] })).toBe(true);
    expect(admin({ subject: "user", roles: ["ROLE_ADMIN"] })).toBe(true);
  });
});
