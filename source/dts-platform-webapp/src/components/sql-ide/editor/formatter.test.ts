import { describe, expect, it } from "vitest";
import { formatSql } from "./formatter";

describe("formatSql", () => {
  it("upper-cases keywords", async () => {
    const result = await formatSql("select 1 from t", "generic");
    expect(result).toMatch(/^SELECT/);
  });

  it("supports trino dialect", async () => {
    const result = await formatSql("select 1", "trino");
    expect(typeof result).toBe("string");
  });
});
