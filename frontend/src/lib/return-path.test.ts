import { describe, expect, it } from "vitest";
import { safeReturnPath, withReturnPath } from "./return-path";

describe("safeReturnPath", () => {
  it("accepts internal paths with query", () => {
    expect(safeReturnPath("/invite?code=abc123")).toBe("/invite?code=abc123");
  });

  it("rejects external or protocol-relative targets", () => {
    for (const value of ["https://evil.example", "//evil.example", "/\\evil.example", "invite", "", null, undefined]) {
      expect(safeReturnPath(value)).toBeNull();
    }
  });

  it("encodes the return path as a query parameter", () => {
    expect(withReturnPath("/login", "/invite?code=abc")).toBe("/login?next=%2Finvite%3Fcode%3Dabc");
  });
});
