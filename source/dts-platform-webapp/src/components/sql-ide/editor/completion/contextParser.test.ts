import { describe, expect, it } from "vitest";
import { parseContext } from "./contextParser";

describe("parseContext", () => {
  it("detects dot-access with alias", () => {
    expect(parseContext("SELECT u. FROM users u")).toEqual({ kind: "afterDot", alias: "u" });
  });

  it("detects dot-access with full table name", () => {
    expect(parseContext("SELECT * FROM public.")).toEqual({ kind: "afterDot", alias: "public" });
  });

  it("detects FROM context", () => {
    expect(parseContext("SELECT * FROM ")).toEqual({ kind: "afterFrom" });
  });

  it("detects JOIN context", () => {
    expect(parseContext("SELECT * FROM a JOIN ")).toEqual({ kind: "afterFrom" });
  });

  it("extracts aliases from the statement", () => {
    const ctx = parseContext("SELECT u. FROM users u JOIN orders o ON u.id = o.user_id");
    expect(ctx).toMatchObject({ kind: "afterDot", alias: "u" });
  });

  it("falls through to default context", () => {
    expect(parseContext("SELECT 1")).toEqual({ kind: "default" });
  });

  it("ignores dots inside string literals", () => {
    expect(parseContext("SELECT 'foo.bar' FROM ")).toEqual({ kind: "afterFrom" });
  });
});
