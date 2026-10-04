// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { expect, it, vi } from "vitest";
import { useAssetLifecycleWorkspace } from "./useAssetLifecycleWorkspace";

const api = vi.hoisted(() => ({
	detail: vi.fn().mockResolvedValue({}),
	metrics: vi.fn().mockResolvedValue({}),
	issues: vi.fn().mockResolvedValue([]),
}));
vi.mock("@/api/platformApi", () => ({
	getCatalogAssetGovernanceWorkspace: api.detail,
	getCatalogLifecycleMetrics: api.metrics,
	getCatalogGovernanceIssues: api.issues,
}));

it("loads asset facts on open and only fetches global reports on their selected tabs", async () => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	const node = document.createElement("div");
	const root = createRoot(node);
	let state: ReturnType<typeof useAssetLifecycleWorkspace> | undefined;
	function Harness() {
		state = useAssetLifecycleWorkspace(true, "dataset-1", "subject-1");
		return null;
	}
	try {
		await act(async () => root.render(<Harness />));
		expect(api.detail).toHaveBeenCalledWith("dataset-1", "subject-1");
		expect(api.metrics).not.toHaveBeenCalled();
		expect(api.issues).not.toHaveBeenCalled();
		await act(async () => state?.setActiveTab("metrics"));
		expect(api.metrics).toHaveBeenCalledTimes(1);
		expect(api.issues).not.toHaveBeenCalled();
		await act(async () => state?.setActiveTab("issues"));
		expect(api.issues).toHaveBeenCalledTimes(1);
	} finally {
		await act(async () => root.unmount());
	}
});
