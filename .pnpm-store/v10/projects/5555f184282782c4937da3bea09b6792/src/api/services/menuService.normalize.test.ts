import { beforeAll, describe, expect, it, vi } from "vitest";
import type { MenuTree } from "#/entity";

vi.mock("../apiClient", () => ({
	default: {
		get: vi.fn(),
	},
}));

vi.mock("@/store/menuStore", () => ({
	useMenuStore: {
		getState: () => ({
			setMenus: vi.fn(),
		}),
	},
}));

describe("normalizeMenuTreePaths", () => {
	beforeAll(() => {
		vi.stubGlobal("localStorage", {
			getItem: () => null,
			setItem: () => undefined,
			removeItem: () => undefined,
			clear: () => undefined,
		});
		vi.stubGlobal("document", {
			documentElement: {
				lang: "zh-CN",
			},
		});
	});

	it("joins relative BI child paths with their parent path", async () => {
		const tree = [
			{
				id: "bi-root",
				parentId: "",
				name: "BI 分析",
				code: "bi",
				path: "bi",
				type: 0,
				children: [
					{
						id: "bi-home",
						parentId: "bi-root",
						name: "分析首页",
						code: "bi.home",
						path: "home",
						type: 1,
						component: "/analytics/pages/HomePage",
					},
					{
						id: "bi-screens",
						parentId: "bi-root",
						name: "数据大屏",
						code: "bi.screens",
						path: "screens",
						type: 1,
						component: "/analytics/pages/screens/ScreensPage",
					},
				],
			},
		] satisfies MenuTree[];

		const { normalizeMenuTreePaths } = await import("./menuService");
		const normalized = normalizeMenuTreePaths(tree);

		expect(normalized[0]?.path).toBe("/bi");
		expect(normalized[0]?.children?.[0]?.path).toBe("/bi/home");
		expect(normalized[0]?.children?.[1]?.path).toBe("/bi/screens");
	});

	it("preserves already-canonical nested BI paths", async () => {
		const tree = [
			{
				id: "bi-root",
				parentId: "",
				name: "BI 分析",
				code: "bi",
				path: "/bi",
				type: 0,
				children: [
					{
						id: "bi-dashboards",
						parentId: "bi-root",
						name: "仪表盘",
						code: "bi.dashboards",
						path: "/bi/dashboards",
						type: 1,
						component: "/analytics/pages/DashboardsPage",
					},
				],
			},
		] satisfies MenuTree[];

		const { normalizeMenuTreePaths } = await import("./menuService");
		const normalized = normalizeMenuTreePaths(tree);

		expect(normalized[0]?.children?.[0]?.path).toBe("/bi/dashboards");
	});
});
