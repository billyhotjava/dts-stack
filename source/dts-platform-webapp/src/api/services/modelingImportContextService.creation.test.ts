// @vitest-environment jsdom
import { beforeEach, expect, it, vi } from "vitest";

vi.mock("../warehousePlanApi", () => ({
	listWarehousePlans: vi.fn(),
	createWarehousePlan: vi.fn(),
	getWarehousePlanCategories: vi.fn(),
	getWarehousePlanSources: vi.fn(),
}));
vi.mock("../dataMartApi", () => ({ listDataMarts: vi.fn() }));
vi.mock("../platformApi", () => ({ getDomainTree: vi.fn() }));
vi.mock("../sprint64GovernanceApi", () => ({ listBusinessProcessesApi: vi.fn() }));
vi.mock("../subjectDomainApi", () => ({ listSubjectDomains: vi.fn() }));

import { createWarehousePlan, listWarehousePlans } from "../warehousePlanApi";
import { resolveDefaultModelingContextId } from "./modelingImportContextService";

beforeEach(() => {
	vi.resetAllMocks();
	vi.mocked(listWarehousePlans).mockResolvedValue([]);
});

it("keeps empty-deployment page loads read-only", async () => {
	expect(await resolveDefaultModelingContextId()).toBe("");
	expect(createWarehousePlan).not.toHaveBeenCalled();
});

it("reuses an accessible active context without creating another", async () => {
	vi.mocked(listWarehousePlans).mockResolvedValue([
		{ id: "archived", lifecycleStatus: "ARCHIVED" },
		{ id: "active", lifecycleStatus: "DRAFT" },
	] as Awaited<ReturnType<typeof listWarehousePlans>>);
	expect(await resolveDefaultModelingContextId("save-1")).toBe("active");
	expect(createWarehousePlan).not.toHaveBeenCalled();
});

it("initializes on explicit save with a stable key and server-owned identity", async () => {
	vi.mocked(createWarehousePlan).mockResolvedValue({ planId: "created" } as Awaited<
		ReturnType<typeof createWarehousePlan>
	>);
	expect(await resolveDefaultModelingContextId("save-1")).toBe("created");
	expect(await resolveDefaultModelingContextId("save-1")).toBe("created");
	for (const [command] of vi.mocked(createWarehousePlan).mock.calls) {
		expect(command).toEqual({
			name: "数据建模",
			objective: "维护数据模型设计与实现",
			onboardingMode: "BUSINESS_FIRST",
			idempotencyKey: "modeling-context:save-1",
		});
	}
});

it("propagates a list failure without attempting initialization", async () => {
	const forbidden = new Error("forbidden");
	vi.mocked(listWarehousePlans).mockRejectedValue(forbidden);
	await expect(resolveDefaultModelingContextId("save-1")).rejects.toBe(forbidden);
	expect(createWarehousePlan).not.toHaveBeenCalled();
});

it("propagates rejected initialization without inventing a context", async () => {
	const forbidden = new Error("forbidden");
	vi.mocked(createWarehousePlan).mockRejectedValue(forbidden);
	await expect(resolveDefaultModelingContextId("save-1")).rejects.toBe(forbidden);
});
