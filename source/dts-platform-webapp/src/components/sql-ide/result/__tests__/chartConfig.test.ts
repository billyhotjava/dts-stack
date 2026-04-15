import { describe, expect, it } from "vitest";
import { recommendChart, type ChartConfig } from "../chartConfig";

describe("recommendChart", () => {
  it("returns line chart for 1 time + 1 number column", () => {
    const cfg = recommendChart([
      { name: "ts", dataType: "TIMESTAMP", nullable: false },
      { name: "value", dataType: "DOUBLE", nullable: false },
    ]);
    expect(cfg.type).toBe("line");
    expect(cfg.xAxis).toBe("ts");
    expect(cfg.xAxisType).toBe("time");
    expect(cfg.yAxis).toEqual(["value"]);
  });

  it("returns bar chart for low-cardinality string + number", () => {
    const cfg = recommendChart([
      { name: "category", dataType: "VARCHAR", nullable: false },
      { name: "amount", dataType: "BIGINT", nullable: false },
    ]);
    expect(cfg.type).toBe("bar");
    expect(cfg.xAxis).toBe("category");
    expect(cfg.xAxisType).toBe("category");
    expect(cfg.yAxis).toEqual(["amount"]);
  });

  it("returns scatter for two numeric columns", () => {
    const cfg = recommendChart([
      { name: "x", dataType: "DOUBLE", nullable: false },
      { name: "y", dataType: "DOUBLE", nullable: false },
    ]);
    expect(cfg.type).toBe("scatter");
    expect(cfg.xAxisType).toBe("value");
  });

  it("falls back to bar for one column", () => {
    const cfg = recommendChart([
      { name: "x", dataType: "VARCHAR", nullable: false },
    ]);
    expect(cfg.type).toBe("bar");
  });

  it("falls back to bar for empty columns", () => {
    const cfg = recommendChart([]);
    expect(cfg.type).toBe("bar");
    expect(cfg.xAxis).toBe("");
    expect(cfg.yAxis).toEqual([]);
  });

  it("detects DECIMAL(10,2) as numeric", () => {
    const cfg = recommendChart([
      { name: "category", dataType: "VARCHAR(200)", nullable: false },
      { name: "amount", dataType: "DECIMAL(18,2)", nullable: false },
    ]);
    expect(cfg.type).toBe("bar");
    expect(cfg.xAxis).toBe("category");
  });

  it("detects TIMESTAMP WITH TIME ZONE as time", () => {
    const cfg = recommendChart([
      { name: "ts", dataType: "TIMESTAMP WITH TIME ZONE", nullable: false },
      { name: "value", dataType: "DOUBLE", nullable: false },
    ]);
    expect(cfg.type).toBe("line");
  });
});
