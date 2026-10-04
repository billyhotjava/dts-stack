import { describe, expect, it } from "vitest";
import { rowsToTSV, valueToCellString } from "../cellCopy";

describe("valueToCellString", () => {
  it("returns empty string for null/undefined", () => {
    expect(valueToCellString(null)).toBe("");
    expect(valueToCellString(undefined)).toBe("");
  });

  it("renders numbers and booleans", () => {
    expect(valueToCellString(42)).toBe("42");
    expect(valueToCellString(true)).toBe("true");
    expect(valueToCellString(false)).toBe("false");
  });

  it("escapes tab and newline by stripping", () => {
    expect(valueToCellString("a\tb\nc")).toBe("a b c");
  });

  it("stringifies objects as JSON", () => {
    expect(valueToCellString({ a: 1 })).toBe('{"a":1}');
  });
});

describe("rowsToTSV", () => {
  it("joins headers + rows with tabs and newlines", () => {
    const out = rowsToTSV([{ id: 1, name: "Alice" }, { id: 2, name: "Bob" }], ["id", "name"]);
    expect(out).toBe("id\tname\n1\tAlice\n2\tBob");
  });

  it("handles missing keys as empty cells", () => {
    const out = rowsToTSV([{ id: 1 }], ["id", "missing"]);
    expect(out).toBe("id\tmissing\n1\t");
  });
});
