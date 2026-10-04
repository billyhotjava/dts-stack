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

describe("formatSql additional", () => {
  it("is idempotent", async () => {
    const once = await formatSql("select 1 from t", "generic");
    const twice = await formatSql(once, "generic");
    expect(twice).toBe(once);
  });

  it("handles empty string without throwing", async () => {
    const out = await formatSql("", "generic");
    expect(out).toBe("");
  });
});
