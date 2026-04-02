// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const state = vi.hoisted(() => ({
	renderSpy: vi.fn(),
	createRootSpy: vi.fn(),
	registerLocalIcons: vi.fn(async () => {}),
	getMenuList: vi.fn(async () => []),
	getPortalMenus: vi.fn(),
	setPortalMenus: vi.fn(),
	createBrowserRouter: vi.fn(() => ({ router: true })),
}));

vi.mock("./polyfills/legacy-browser", () => ({}));
vi.mock("./global.css", () => ({}));
vi.mock("./theme/theme.css", () => ({}));
vi.mock("./locales/i18n", () => ({}));

vi.mock("react-dom/client", () => ({
	default: {
		createRoot: (...args: unknown[]) => state.createRootSpy(...args),
	},
}));

vi.mock("react-router", () => ({
	createBrowserRouter: (...args: unknown[]) => state.createBrowserRouter(...args),
	Outlet: () => null,
	RouterProvider: () => null,
}));

vi.mock("./App", () => ({
	default: ({ children }: { children?: React.ReactNode }) => children ?? null,
}));

vi.mock("./api/services/menuService", () => ({
	default: {
		getMenuList: (...args: unknown[]) => state.getMenuList(...args),
	},
}));

vi.mock("./admin/api/adminApi", () => ({
	adminApi: {
		getPortalMenus: (...args: unknown[]) => state.getPortalMenus(...args),
	},
}));

vi.mock("./store/portalMenuStore", () => ({
	setPortalMenus: (...args: unknown[]) => state.setPortalMenus(...args),
}));

vi.mock("./components/icon", () => ({
	registerLocalIcons: (...args: unknown[]) => state.registerLocalIcons(...args),
}));

vi.mock("./global-config", () => ({
	GLOBAL_CONFIG: {
		publicPath: "/",
		routerMode: "frontend",
	},
}));

vi.mock("./routes/components/error-boundary", () => ({
	default: () => null,
}));

vi.mock("./routes/sections", () => ({
	getRoutesSection: () => [],
}));

describe("admin main bootstrap", () => {
	beforeEach(() => {
		vi.resetModules();
		document.body.innerHTML = '<div id="root"></div>';
		state.renderSpy.mockReset();
		state.createRootSpy.mockReset();
		state.createRootSpy.mockReturnValue({ render: state.renderSpy });
		state.registerLocalIcons.mockClear();
		state.getMenuList.mockClear();
		state.setPortalMenus.mockReset();
		state.createBrowserRouter.mockClear();
	});

	afterEach(() => {
		document.body.innerHTML = "";
	});

	it("renders the app before portal menu prefetch completes", async () => {
		let resolvePortalMenus: ((value: unknown) => void) | null = null;
		state.getPortalMenus.mockImplementation(
			() =>
				new Promise((resolve) => {
					resolvePortalMenus = resolve;
				}),
		);

		const importPromise = import("./main");
		for (let attempt = 0; attempt < 40; attempt += 1) {
			if (state.createRootSpy.mock.calls.length > 0) {
				break;
			}
			await new Promise((resolve) => setTimeout(resolve, 5));
		}

		try {
			expect(state.createRootSpy).toHaveBeenCalledTimes(1);
			expect(state.renderSpy).toHaveBeenCalledTimes(1);
		} finally {
			resolvePortalMenus?.({ menus: [], allMenus: [] });
			void importPromise.catch(() => {});
		}
	});
});
