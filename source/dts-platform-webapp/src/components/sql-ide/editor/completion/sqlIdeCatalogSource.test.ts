// @vitest-environment jsdom

import { describe, expect, it, vi } from "vitest";
import type { CatalogColumn, CatalogSchema, CatalogTable } from "../../api/sqlIdeCatalog";
import { createSqlIdeCatalogSource, normalizeCatalogEngine } from "./sqlIdeCatalogSource";

const schemas: CatalogSchema[] = [
  { name: "public", catalog: null },
  { name: "reporting", catalog: null },
];

const table = (name: string, comment: string | null = null): CatalogTable => ({
  name,
  type: "TABLE",
  comment,
  rowCountEstimate: null,
});

const column: CatalogColumn = {
  name: "id",
  dataType: "bigint",
  nullable: false,
  comment: "主键",
  ordinalPosition: 1,
};

describe("createSqlIdeCatalogSource", () => {
  it("loads qualified tables from every schema and caches the catalog", async () => {
    const api = {
      listSchemas: vi.fn(async () => schemas),
      listTables: vi.fn(async (_dsId: string, schema: string) =>
        schema === "public" ? [table("orders", "订单表")] : [table("daily_sales")],
      ),
      listColumns: vi.fn(async () => [column]),
    };
    const source = createSqlIdeCatalogSource("warehouse", api);

    await expect(source.listTables()).resolves.toEqual([
      { schema: "public", name: "orders", comment: "订单表" },
      { schema: "reporting", name: "daily_sales", comment: undefined },
    ]);
    await source.listTables();

    expect(api.listSchemas).toHaveBeenCalledTimes(1);
    expect(api.listTables).toHaveBeenCalledTimes(2);
  });

  it("completes tables after a schema dot and columns after a table name", async () => {
    const api = {
      listSchemas: vi.fn(async () => schemas),
      listTables: vi.fn(async (_dsId: string, schema: string) =>
        schema === "public" ? [table("orders", "订单表")] : [],
      ),
      listColumns: vi.fn(async () => [column]),
    };
    const source = createSqlIdeCatalogSource("warehouse", api);

    await expect(source.listColumns("public")).resolves.toEqual([
      { name: "orders", dataType: "表", nullable: false, comment: "订单表" },
    ]);
    await expect(source.listColumns("orders")).resolves.toEqual([
      { name: "id", dataType: "bigint", nullable: false, comment: "主键" },
    ]);
    expect(api.listColumns).toHaveBeenCalledWith("warehouse", "public.orders");
  });
});

describe("normalizeCatalogEngine", () => {
  it("keeps supported engines and safely falls back for other JDBC types", () => {
    expect(normalizeCatalogEngine("POSTGRESQL")).toBe("postgresql");
    expect(normalizeCatalogEngine("trino")).toBe("trino");
    expect(normalizeCatalogEngine("mysql")).toBe("generic");
  });
});
