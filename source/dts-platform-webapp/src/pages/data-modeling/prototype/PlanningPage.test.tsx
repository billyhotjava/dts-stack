// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DataModelingRoute } from "../types";

const mocks = vi.hoisted(() => ({
	loadPlanningProjection: vi.fn(),
}));

vi.mock("@/store/userStore", () => ({ useUserInfo: () => ({ id: "user-1" }) }));
vi.mock("./services/planningProjectionService", () => ({
	loadPlanningProjection: mocks.loadPlanningProjection,
	normalizeModelingRequestFailure: vi.fn(),
}));
vi.mock("./useDataModelingMenuGrant", () => ({ useDataModelingMenuGrant: () => false }));

import { PlanningPage } from "./PlanningPage";

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
	vi.clearAllMocks();
});

describe("PlanningPage", () => {
	it("renders an unavailable empty-state reason only once", async () => {
		const reason = "当前版本尚未提供可维护的规划参数";
		mocks.loadPlanningProjection.mockResolvedValue({ headers: [], rows: [], readOnlyReason: reason });
		const route: DataModelingRoute = {
			workspace: "planning",
			view: "system",
			title: "规划参数配置",
			description: "说明数仓规划参数的当前能力边界；当前版本暂不提供在线维护。",
		};

		await act(async () => root.render(<PlanningPage route={route} />));

		expect(container.textContent?.match(new RegExp(reason, "g"))).toHaveLength(1);
	});
});
