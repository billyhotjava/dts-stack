import { describe, expect, it } from "vitest";
import { parseContext } from "./contextParser";

describe("parseContext", () => {
  it("detects dot-access with alias", () => {
    // text-before-cursor ends immediately after the dot (cursor is where column name would go)
    expect(parseContext("SELECT u.")).toEqual({ kind: "afterDot", alias: "u" });
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

  it("extracts alias at cursor position in a multi-alias statement", () => {
    // text-before-cursor ends at the dot where the user is typing a column name
    const ctx = parseContext("SELECT u. FROM users u JOIN orders o ON u.id = o.user_id");
    // cursor is at end of string — no trailing dot, so this is default context
    // (to test alias extraction at a dot, pass text truncated at the dot)
    expect(parseContext("SELECT u. FROM users u JOIN orders o ON u.id = o.")).toMatchObject({
      kind: "afterDot",
      alias: "o",
    });
    // original input (cursor past end of full statement, no trailing dot) → default
    expect(ctx).toEqual({ kind: "default" });
  });

  it("falls through to default context", () => {
    expect(parseContext("SELECT 1")).toEqual({ kind: "default" });
  });

  it("ignores dots inside string literals", () => {
    expect(parseContext("SELECT 'foo.bar' FROM ")).toEqual({ kind: "afterFrom" });
  });

  it("returns default for empty input", () => {
    expect(parseContext("")).toEqual({ kind: "default" });
  });

  it("does not treat numeric dots as afterDot", () => {
    expect(parseContext("SELECT 3.14 FROM t WHERE")).toEqual({ kind: "default" });
  });
});

describe("parseContext multi-dot handling", () => {
  it("returns the alias at cursor position, not the first dot", () => {
    // Text-before-cursor ending at the last dot
    expect(parseContext("SELECT u., o.")).toEqual({ kind: "afterDot", alias: "o" });
  });

  it("handles dangling dot after comma list", () => {
    expect(parseContext("SELECT a, b, table.")).toEqual({ kind: "afterDot", alias: "table" });
  });

  it("handles three-part names cursor after last dot", () => {
    expect(parseContext("SELECT * FROM db.public.")).toEqual({ kind: "afterDot", alias: "public" });
  });
});

describe("parseContext unicode identifiers", () => {
  it("handles CJK table name before dot", () => {
    expect(parseContext("SELECT 表名.")).toEqual({ kind: "afterDot", alias: "表名" });
  });

  it("handles CJK in FROM context", () => {
    expect(parseContext("SELECT 姓名 FROM 用户表 用户表别名.")).toEqual({
      kind: "afterDot",
      alias: "用户表别名",
    });
  });

  it("handles mixed latin and CJK identifiers", () => {
    expect(parseContext("SELECT * FROM schema_中文.")).toEqual({
      kind: "afterDot",
      alias: "schema_中文",
    });
  });
});
