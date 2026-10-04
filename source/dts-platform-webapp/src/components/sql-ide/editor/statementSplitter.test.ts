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

describe("splitStatements comments", () => {
  it("ignores semicolons in block comments", () => {
    const parts = splitStatements("SELECT 1 /* a;b */; SELECT 2;");
    expect(parts).toHaveLength(2);
    expect(parts[0].text).toBe("SELECT 1 /* a;b */;");
    expect(parts[1].text).toBe("SELECT 2;");
  });

  it("handles multiline block comments", () => {
    const parts = splitStatements("SELECT 1 /* line1;\nline2; */ FROM t;");
    expect(parts).toHaveLength(1);
  });

  it("ignores semicolons in line comments", () => {
    const parts = splitStatements("SELECT 1; -- a;b\nSELECT 2;");
    expect(parts).toHaveLength(2);
    expect(parts[1].text).toBe("SELECT 2;");
  });

  it("line comment terminates at newline", () => {
    const parts = splitStatements("SELECT 1; -- x\n; SELECT 2;");
    // The standalone `;` on line 2 should split statement 2 off
    expect(parts.length).toBeGreaterThanOrEqual(2);
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
