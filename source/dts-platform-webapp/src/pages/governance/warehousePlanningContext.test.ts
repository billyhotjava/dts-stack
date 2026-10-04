import { describe, expect, it } from "vitest";
import {
	buildPlanningRoute,
	createWarehousePlanningContext,
	loadWarehousePlanningContext,
	resolveStandardDraftGate,
	resolveWarehousePlanningContext,
	resolveWarehousePlanningStatus,
	saveWarehousePlanningContext,
	WAREHOUSE_PLANNING_CONTEXT_VERSION,
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
	processId: "node-plan-loop",
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
			processId: "node-plan-loop",
			warehouseLayer: "DWD",
			layerSchemeId: "standard-lakehouse",
			layerSchemeVersion: 1,
			enabledLayers: ["ODS_RAW", "ODS_STANDARDIZED", "STG", "DWD", "DWS", "ADS"],
			outputLayers: ["DWD", "DWS", "ADS"],
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

	it("upgrades a legacy planning draft with the default layer scheme", () => {
		const storage = createStorage({
			"dts.warehouse-planning.v1": JSON.stringify({
				...baseInput,
				version: 1,
				createdAt: "2026-07-11T01:00:00.000Z",
				updatedAt: "2026-07-11T01:00:00.000Z",
			}),
		});

		const context = loadWarehousePlanningContext(storage);

		expect(context?.version).toBe(WAREHOUSE_PLANNING_CONTEXT_VERSION);
		expect(context?.enabledLayers).toContain("STG");
		expect(context?.outputLayers).toEqual(["DWD", "DWS", "ADS"]);
	});

	it("builds a journey route with canonical planning parameters only", () => {
		const context = createWarehousePlanningContext({
			...baseInput,
			sourceId: "source-1",
		});

		const route = buildPlanningRoute("/governance/standards/elements", context);
		const params = new URL(route, "http://dts.local").searchParams;

		expect(params.get("journey")).toBe("e2e-data-product");
		expect(params.get("planId")).toBe("plan-1");
		expect(params.get("domainId")).toBe("domain-1");
		for (const retired of [
			"planningId",
			"warehouseLayer",
			"modelingMode",
			"sourceId",
			"processId",
			"layerSchemeId",
			"layerSchemeVersion",
			"standardDraftId",
		]) {
			expect(params.has(retired)).toBe(false);
		}
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

	it("restores an authoritative session draft when route parameters agree", () => {
		const storage = createStorage();
		const context = createWarehousePlanningContext(baseInput);
		saveWarehousePlanningContext(context, storage);
		const routeParams = new URLSearchParams(
			"?journey=e2e-data-product&planningId=plan-1&domainId=domain-1&warehouseLayer=DWD&modelingMode=dimension",
		);

		expect(resolveWarehousePlanningContext(routeParams, storage)).toMatchObject({
			context,
			source: "session",
			status: "draft",
		});
	});

	it("distinguishes a missing route from an incomplete URL fallback", () => {
		const missing = resolveWarehousePlanningContext(new URLSearchParams(), createStorage());
		const incompleteFallback = resolveWarehousePlanningContext(
			new URLSearchParams("?planningId=plan-1&warehouseLayer=DWD&modelingMode=dimension"),
			createStorage(),
		);

		expect(missing).toMatchObject({ context: null, source: "missing", status: "blocked" });
		expect(incompleteFallback).toMatchObject({ context: null, source: "url-fallback", status: "blocked" });
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

	it("returns an executable blocker for each standard draft gap", () => {
		const context = createWarehousePlanningContext(baseInput);
		const missingPlanning = resolveStandardDraftGate({
			planningContext: null,
			planningSource: "missing",
			canManage: true,
			dataElementCount: 0,
		});
		const missingPermission = resolveStandardDraftGate({
			planningContext: context,
			planningSource: "session",
			canManage: false,
			dataElementCount: 2,
		});
		const missingElements = resolveStandardDraftGate({
			planningContext: context,
			planningSource: "session",
			canManage: true,
			dataElementCount: 0,
		});
		const missingBinding = resolveStandardDraftGate({
			planningContext: context,
			planningSource: "session",
			canManage: true,
			dataElementCount: 2,
		});

		expect(missingPlanning).toMatchObject({ blocker: "planning", canCreateDraft: false });
		expect(missingPlanning.reason).toContain("数仓规划");
		expect(missingPlanning.repairRoute).toBe("/governance/subjects");
		expect(missingPermission).toMatchObject({ blocker: "permission", canCreateDraft: false });
		expect(missingPermission.reason).toContain("治理维护权限");
		expect(missingPermission.repairRoute).toContain("/governance/subjects");
		expect(missingElements).toMatchObject({ blocker: "data-elements", canCreateDraft: false });
		expect(missingElements.reason).toContain("数据元");
		expect(missingElements.repairRoute).toContain("create=1");
		expect(missingBinding).toMatchObject({ blocker: "field-binding", canCreateDraft: true });
		expect(missingBinding.reason).toContain("字段落标草稿");
		expect(missingBinding.repairRoute).toContain("bindingDraft=1");
	});

	it("marks the standard draft ready only when planning, permission, elements and binding are present", () => {
		const context = createWarehousePlanningContext({ ...baseInput, standardDraftId: "draft-1" });

		expect(
			resolveStandardDraftGate({
				planningContext: context,
				planningSource: "session",
				canManage: true,
				dataElementCount: 2,
			}),
		).toMatchObject({ status: "ready", canCreateDraft: true });
	});

	it("propagates an untrusted planning reason into the standard draft repair action", () => {
		const context = createWarehousePlanningContext(baseInput);
		const result = resolveStandardDraftGate({
			planningContext: context,
			planningSource: "url-fallback",
			planningBlockedReason: "规划草稿已失效",
			canManage: true,
			dataElementCount: 2,
		});

		expect(result).toMatchObject({
			status: "blocked",
			blocker: "planning",
			reason: "规划草稿已失效",
			canCreateDraft: false,
		});
		expect(result.repairRoute).toContain("/governance/subjects");
	});
});
