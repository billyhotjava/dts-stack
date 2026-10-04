// @vitest-environment jsdom
import { describe, expect, it, vi } from "vitest";
import type { WarehousePlanSourceInventoryView } from "../warehousePlanApi";
import { collectCurrentWarehousePlanSources } from "./modelingImportContextService";

const page = (
	pageNumber: number,
	totalPages: number,
	bindings: WarehousePlanSourceInventoryView["bindings"],
): WarehousePlanSourceInventoryView => ({
	bindings,
	readiness: "READY",
	issues: [],
	version: 3,
	etag: '"sources:3"',
	checkedAt: "2026-08-10T00:00:00Z",
	page: pageNumber,
	size: 1,
	totalElements: 2,
	totalPages,
});

describe("Sprint 89 modeling import source pagination", () => {
	it("loads every page and keeps only confirmed current available sources", async () => {
		const current = {
			bindingId: "30000000-0000-0000-0000-000000000001",
			sourceType: "CATALOG_TABLE" as const,
			locator: { assetId: "40000000-0000-0000-0000-000000000001" },
			confirmationStatus: "CONFIRMED" as const,
			resolutionStatus: "AVAILABLE" as const,
			freshness: "CURRENT" as const,
			allowedActions: [],
		};
		const stale = {
			...current,
			bindingId: "30000000-0000-0000-0000-000000000002",
			freshness: "STALE" as const,
		};
		const loadPage = vi
			.fn()
			.mockResolvedValueOnce(page(0, 2, [current]))
			.mockResolvedValueOnce(page(1, 2, [stale]));

		await expect(collectCurrentWarehousePlanSources("plan-89", loadPage)).resolves.toEqual([current]);
		expect(loadPage).toHaveBeenNthCalledWith(1, "plan-89", 0, 200);
		expect(loadPage).toHaveBeenNthCalledWith(2, "plan-89", 1, 200);
	});
});
