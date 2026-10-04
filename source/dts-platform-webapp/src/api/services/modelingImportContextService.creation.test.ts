// @vitest-environment jsdom
import { beforeEach, expect, it, vi } from "vitest";

vi.mock("../warehousePlanApi", () => ({
	listWarehousePlans: vi.fn(),
	createWarehousePlan: vi.fn(),
	getWarehousePlanCategories: vi.fn(),
	getWarehousePlanSources: vi.fn(),
}));
vi.mock("../modelSpecApi", () => ({ getModelSpecCreationContext: vi.fn() }));
vi.mock("../dataMartApi", () => ({ listDataMarts: vi.fn() }));
vi.mock("../platformApi", () => ({ getDomainTree: vi.fn() }));
vi.mock("../sprint64GovernanceApi", () => ({ listBusinessProcessesApi: vi.fn() }));
vi.mock("../subjectDomainApi", () => ({ listSubjectDomains: vi.fn() }));

import { getModelSpecCreationContext } from "../modelSpecApi";
import { createWarehousePlan, listWarehousePlans } from "../warehousePlanApi";
import { resolveDefaultModelingContextId } from "./modelingImportContextService";

beforeEach(() => {
	vi.resetAllMocks();
	vi.mocked(listWarehousePlans).mockResolvedValue([]);
	vi.mocked(getModelSpecCreationContext).mockResolvedValue({ planId: null });
});

it("keeps empty-deployment page loads read-only", async () => {
	expect(await resolveDefaultModelingContextId()).toBe("");
	expect(createWarehousePlan).not.toHaveBeenCalled();
});

it("uses the server default instead of the first readable legacy plan", async () => {
	vi.mocked(listWarehousePlans).mockResolvedValue([
		{ id: "published-legacy", lifecycleStatus: "PUBLISHED" },
		{ id: "ordinary-legacy", lifecycleStatus: "DRAFT" },
	] as Awaited<ReturnType<typeof listWarehousePlans>>);
	vi.mocked(getModelSpecCreationContext).mockResolvedValue({ planId: "server-default" });
	expect(await resolveDefaultModelingContextId()).toBe("server-default");
	expect(listWarehousePlans).not.toHaveBeenCalled();
	expect(createWarehousePlan).not.toHaveBeenCalled();
});

it("does not adopt an ordinary legacy plan when no default exists", async () => {
	vi.mocked(listWarehousePlans).mockResolvedValue([{ id: "legacy", lifecycleStatus: "DRAFT" }] as any);
	expect(await resolveDefaultModelingContextId()).toBe("");
	expect(listWarehousePlans).not.toHaveBeenCalled();
	expect(createWarehousePlan).not.toHaveBeenCalled();
});

it("propagates a default context read failure without selecting another plan", async () => {
	const forbidden = new Error("forbidden");
	vi.mocked(getModelSpecCreationContext).mockRejectedValue(forbidden);
	await expect(resolveDefaultModelingContextId()).rejects.toBe(forbidden);
	expect(createWarehousePlan).not.toHaveBeenCalled();
});
