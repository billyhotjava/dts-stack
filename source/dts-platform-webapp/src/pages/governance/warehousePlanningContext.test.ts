import { describe, expect, it } from "vitest";
import {
	WAREHOUSE_PLANNING_CONTEXT_VERSION,
	buildPlanningRoute,
	createWarehousePlanningContext,
	loadWarehousePlanningContext,
	resolveWarehousePlanningContext,
	resolveWarehousePlanningStatus,
	saveWarehousePlanningContext,
	type WarehousePlanningContext,
} from "./warehousePlanningContext";

const createStorage = (initial: Record<string, string> = {}) => {
	const values = new Map(Object.entries(initial));
	return {
		getItem: (key: string) => values.get(key) ?? null,
		setItem: (key: string, value: string) => values.set(key, value),
		removeItem: (key: string) => values.delete(key),
	};
};

const baseInput = {
	planningId: "plan-1",
	domainId: "domain-1",
	domainName: "客户域",
	warehouseLayer: "DWD" as const,
	modelingMode: "dimension" as const,
};

describe("warehouse planning context", () => {
	it("creates a versioned DWD dimension planning draft without persisting status", () => {
		const context = createWarehousePlanningContext(baseInput, () => "2026-07-11T01:00:00.000Z");

		expect(context).toMatchObject({
			version: WAREHOUSE_PLANNING_CONTEXT_VERSION,
			planningId: "plan-1",
			domainId: "domain-1",
			warehouseLayer: "DWD",
			modelingMode: "dimension",
			createdAt: "2026-07-11T01:00:00.000Z",
			updatedAt: "2026-07-11T01:00:00.000Z",
		});
		expect(context).not.toHaveProperty("status");
	});

	it("round-trips the session draft and discards a mismatched version", () => {
		const storage = createStorage();
		const context = createWarehousePlanningContext(baseInput);

		expect(saveWarehousePlanningContext(context, storage)).toBe(true);
		expect(loadWarehousePlanningContext(storage)).toEqual(context);

		storage.setItem(
			"dts.warehouse-planning.v1",
			JSON.stringify({ ...context, version: WAREHOUSE_PLANNING_CONTEXT_VERSION + 1 }),
		);
		expect(loadWarehousePlanningContext(storage)).toBeNull();
		expect(storage.getItem("dts.warehouse-planning.v1")).toBeNull();
	});

	it("builds a journey route with all planning parameters", () => {
		const context = createWarehousePlanningContext({
			...baseInput,
			sourceId: "source-1",
		});

		const route = buildPlanningRoute("/governance/standards/elements", context);
		const params = new URL(route, "http://dts.local").searchParams;

		expect(params.get("journey")).toBe("e2e-data-product");
		expect(params.get("planningId")).toBe("plan-1");
		expect(params.get("domainId")).toBe("domain-1");
		expect(params.get("warehouseLayer")).toBe("DWD");
		expect(params.get("modelingMode")).toBe("dimension");
		expect(params.get("sourceId")).toBe("source-1");
	});

	it("blocks a URL route when its planning draft is no longer in session storage", () => {
		const routeParams = new URLSearchParams(
			"?journey=e2e-data-product&planningId=plan-1&domainId=domain-1&warehouseLayer=DWD&modelingMode=dimension",
		);

		const result = resolveWarehousePlanningContext(routeParams, createStorage());

		expect(result.source).toBe("url-fallback");
		expect(result.status).toBe("blocked");
		expect(result.reason).toContain("规划草稿");
	});

	it("blocks URL and session disagreement instead of silently choosing either side", () => {
		const storage = createStorage();
		const context = createWarehousePlanningContext(baseInput);
		saveWarehousePlanningContext(context, storage);
		const routeParams = new URLSearchParams(
			"?journey=e2e-data-product&planningId=plan-1&domainId=other-domain&warehouseLayer=DWD&modelingMode=dimension",
		);

		const result = resolveWarehousePlanningContext(routeParams, storage);

		expect(result.context?.planningId).toBe("plan-1");
		expect(result.status).toBe("blocked");
		expect(result.reason).toContain("不一致");
	});

	it("keeps the session draft authoritative when the URL points to another planning id", () => {
		const storage = createStorage();
		const context = createWarehousePlanningContext(baseInput);
		saveWarehousePlanningContext(context, storage);
		const routeParams = new URLSearchParams(
			"?journey=e2e-data-product&planningId=plan-2&domainId=domain-2&warehouseLayer=DWD&modelingMode=dimension",
		);

		const result = resolveWarehousePlanningContext(routeParams, storage);

		expect(result.context?.planningId).toBe("plan-1");
		expect(result.source).toBe("session");
		expect(result.status).toBe("blocked");
		expect(result.reason).toContain("不一致");
	});

	it("degrades safely when session storage cannot be read or written", () => {
		const unavailableStorage = {
			getItem: () => {
				throw new Error("storage unavailable");
			},
			setItem: () => {
				throw new Error("storage unavailable");
			},
			removeItem: () => {
				throw new Error("storage unavailable");
			},
		};

		expect(loadWarehousePlanningContext(unavailableStorage)).toBeNull();
		expect(saveWarehousePlanningContext(createWarehousePlanningContext(baseInput), unavailableStorage)).toBe(false);
	});

	it("computes draft, blocked and ready without storing a status field", () => {
		const context = createWarehousePlanningContext(baseInput);

		expect(resolveWarehousePlanningStatus(context, { source: "session", standardFieldCount: 0 })).toMatchObject({
			status: "draft",
		});
		expect(resolveWarehousePlanningStatus(null, { source: "missing", standardFieldCount: 2 })).toMatchObject({
			status: "blocked",
		});
		expect(
			resolveWarehousePlanningStatus(
				{ ...context, standardDraftId: "draft-1" },
				{ source: "session", standardFieldCount: 2 },
			),
		).toMatchObject({ status: "ready" });
	});

	it("rejects a context with an unsupported modeling mode", () => {
		const invalid = {
			...createWarehousePlanningContext(baseInput),
			modelingMode: "fact",
		} as unknown as WarehousePlanningContext;

		const result = resolveWarehousePlanningStatus(invalid, { source: "session", standardFieldCount: 2 });

		expect(result.status).toBe("blocked");
		expect(result.reason).toContain("维度建模");
	});
});
