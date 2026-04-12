import { describe, expect, it } from "vitest";
import { findStatementAt, splitStatements } from "./statementSplitter";

describe("splitStatements", () => {
  it("splits simple statements by semicolon", () => {
    expect(splitStatements("SELECT 1; SELECT 2;")).toEqual([
      { start: 0, end: 9, text: "SELECT 1;" },
      { start: 10, end: 19, text: "SELECT 2;" },
    ]);
  });

  it("keeps semicolons inside strings", () => {
    const sql = "SELECT ';' FROM t; SELECT 2;";
    const parts = splitStatements(sql);
    expect(parts).toHaveLength(2);
    expect(parts[0].text).toBe("SELECT ';' FROM t;");
  });

  it("handles trailing statement without semicolon", () => {
    const parts = splitStatements("SELECT 1; SELECT 2");
    expect(parts).toHaveLength(2);
    expect(parts[1].text).toBe("SELECT 2");
  });
});

describe("findStatementAt", () => {
  it("returns statement containing offset", () => {
    const sql = "SELECT 1; SELECT 2; SELECT 3;";
    const stmt = findStatementAt(sql, 12); // inside "SELECT 2"
    expect(stmt?.text).toBe("SELECT 2;");
  });

  it("returns undefined for empty input", () => {
    expect(findStatementAt("", 0)).toBeUndefined();
  });
});
