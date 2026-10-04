import { beforeEach, expect, it, vi } from "vitest";

const get = vi.hoisted(() => vi.fn());

vi.mock("@/api/apiClient", () => ({ default: { get } }));

import { listColumns, listTables } from "./sql-workbench";

beforeEach(() => get.mockReset());

it("passes bounded metadata search options without sending a SQL query", async () => {
	get.mockResolvedValue([]);

	await listTables("target-1", { keyword: "project_no", limit: 20 });

	expect(get).toHaveBeenCalledWith({
		url: "/sql/tables/target-1",
		params: { keyword: "project_no", limit: 20 },
	});
});

it("requests one selected table's column metadata", async () => {
	get.mockResolvedValue([]);

	await listColumns("target-1", "public", "orders");

	expect(get).toHaveBeenCalledWith({
		url: "/sql/columns/target-1",
		params: { schema: "public", table: "orders" },
	});
});
