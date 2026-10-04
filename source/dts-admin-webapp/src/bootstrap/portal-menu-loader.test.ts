import { beforeEach, describe, expect, it, vi } from "vitest";

const getPortalMenusMock = vi.fn();
const setPortalMenusMock = vi.fn();

vi.mock("@/admin/api/adminApi", () => ({
	adminApi: {
		getPortalMenus: getPortalMenusMock,
	},
}));

vi.mock("@/store/portalMenuStore", () => ({
	setPortalMenus: setPortalMenusMock,
}));

describe("portal-menu-loader", () => {
	beforeEach(() => {
		getPortalMenusMock.mockReset();
		setPortalMenusMock.mockReset();
		vi.restoreAllMocks();
	});

	it("hydrates menu store from fetched portal menus", async () => {
		getPortalMenusMock.mockResolvedValue({
			menus: [{ id: 1 }],
			allMenus: [{ id: 1 }, { id: 2 }],
		});

		const { prefetchPortalMenus } = await import("./portal-menu-loader");

		await prefetchPortalMenus();

		expect(setPortalMenusMock).toHaveBeenCalledWith([{ id: 1 }], [{ id: 1 }, { id: 2 }]);
	});

	it("falls back to menus as full tree when allMenus is absent", async () => {
		getPortalMenusMock.mockResolvedValue({
			menus: [{ id: 1 }],
		});

		const { prefetchPortalMenus } = await import("./portal-menu-loader");

		await prefetchPortalMenus();

		expect(setPortalMenusMock).toHaveBeenCalledWith([{ id: 1 }], [{ id: 1 }]);
	});

	it("clears menu store on request failure", async () => {
		const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
		getPortalMenusMock.mockRejectedValue(new Error("401"));

		const { prefetchPortalMenus } = await import("./portal-menu-loader");

		await prefetchPortalMenus();

		expect(setPortalMenusMock).toHaveBeenCalledWith([]);
		expect(warnSpy).toHaveBeenCalled();
	});
});
