// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ModelServingSyncStatus } from "./ModelServingSyncStatus";

const apiMocks = vi.hoisted(() => ({ getStatus: vi.fn(), retry: vi.fn() }));
const routerPush = vi.hoisted(() => vi.fn());

vi.mock("@/api/modelSpecApi", async (importOriginal) => ({
	...(await importOriginal<typeof import("@/api/modelSpecApi")>()),
	getModelServingSyncStatus: apiMocks.getStatus,
	retryModelServingSync: apiMocks.retry,
}));

vi.mock("@/routes/hooks", () => ({ useRouter: () => ({ push: routerPush }) }));

const failed = {
	modelSpecId: "model-1",
	catalogAssetKey: "semantic-model:model-1",
	syncStatus: "SYNC_FAILED",
	syncAttempts: 5,
	lastSyncError: "ANALYTICS_SEMANTIC_PUBLISH_UNAVAILABLE",
	nextSyncAt: "2026-08-17T07:00:00Z",
	updatedAt: "2026-08-17T06:00:00Z",
	version: 7,
	servingReady: true,
	latestPublishedRef: null,
	servingRef: null,
} as const;

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
	apiMocks.getStatus.mockReset();
	apiMocks.retry.mockReset();
	routerPush.mockReset();
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("model serving sync status", () => {
	it("shows failure evidence and performs a controlled retry", async () => {
		apiMocks.getStatus.mockResolvedValue(failed);
		apiMocks.retry.mockResolvedValue({
			status: {
				...failed,
				syncStatus: "SYNC_PENDING",
				syncAttempts: 0,
				lastSyncError: null,
				nextSyncAt: null,
				version: 8,
			},
			replayed: false,
			correlationId: "correlation-1",
		});

		await act(async () => root.render(<ModelServingSyncStatus canMaintain modelSpecId="model-1" />));
		await act(async () => Promise.resolve());

		expect(container.textContent).toContain("目录同步失败");
		expect(container.textContent).toContain("第 5 次");
		expect(container.textContent).toContain("ANALYTICS_SEMANTIC_PUBLISH_UNAVAILABLE");
		const retry = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === "重试同步");
		await act(async () => retry?.click());

		expect(apiMocks.retry).toHaveBeenCalledWith(failed);
		expect(container.textContent).toContain("目录同步中");
	});

	it("renders an explicit pre-publication state without claiming success", async () => {
		apiMocks.getStatus.mockResolvedValue({
			...failed,
			catalogAssetKey: null,
			syncStatus: "NOT_REGISTERED",
			syncAttempts: 0,
			lastSyncError: null,
			nextSyncAt: null,
			updatedAt: null,
			version: 0,
			servingReady: false,
		});

		await act(async () => root.render(<ModelServingSyncStatus canMaintain modelSpecId="model-1" />));
		await act(async () => Promise.resolve());

		expect(container.textContent).toContain("目录待登记");
		expect(container.textContent).not.toContain("同步成功");
	});

	it("deep-links a published physical asset to the existing catalog detail", async () => {
		apiMocks.getStatus.mockResolvedValue({
			...failed,
			syncStatus: "SYNCED",
			servingRef: { physicalAssetId: "33333333-3333-3333-3333-333333333333" },
		});

		await act(async () => root.render(<ModelServingSyncStatus canMaintain modelSpecId="model-1" />));
		await act(async () => Promise.resolve());
		const viewAsset = Array.from(container.querySelectorAll("button")).find((item) => item.textContent === "查看资产");
		await act(async () => viewAsset?.click());

		expect(routerPush).toHaveBeenCalledWith("/catalog/datasets/33333333-3333-3333-3333-333333333333");
	});
});
