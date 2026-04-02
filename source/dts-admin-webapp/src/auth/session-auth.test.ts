// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/global-config", () => ({
	GLOBAL_CONFIG: { apiBaseUrl: "/api", publicPath: "/" },
}));

let fetchCalls: Array<{ url: string; credentials: RequestCredentials | undefined }>;
let fetchDelay = 0;
let responseBody: unknown = {
	authenticated: true,
	username: "sysadmin",
	displayName: "系统管理员",
	browserId: "browser-admin-1",
	roles: ["ROLE_SYS_ADMIN"],
	permissions: ["admin.portal"],
};

function installFetchMock() {
	fetchCalls = [];
	globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
		fetchCalls.push({
			url: String(input),
			credentials: init?.credentials,
		});
		if (fetchDelay > 0) {
			await new Promise((resolve) => setTimeout(resolve, fetchDelay));
		}
		return new Response(JSON.stringify({ data: responseBody }), {
			status: 200,
			headers: { "content-type": "application/json" },
		});
	}) as typeof fetch;
}

beforeEach(() => {
	installFetchMock();
	fetchDelay = 0;
	responseBody = {
		authenticated: true,
		username: "sysadmin",
		displayName: "系统管理员",
		browserId: "browser-admin-1",
		roles: ["ROLE_SYS_ADMIN"],
		permissions: ["admin.portal"],
	};
});

afterEach(() => {
	vi.restoreAllMocks();
});

describe("fetchCurrentSession", () => {
	it("probes /session/current once for concurrent callers", async () => {
		const sessionAuth = (await import("./session-auth")) as Record<string, unknown>;
		const fetchCurrentSession = sessionAuth.fetchCurrentSession as (() => Promise<Record<string, unknown>>) | undefined;

		expect(typeof fetchCurrentSession).toBe("function");

		fetchDelay = 20;
		const [r1, r2, r3] = await Promise.all([
			fetchCurrentSession?.(),
			fetchCurrentSession?.(),
			fetchCurrentSession?.(),
		]);

		expect(fetchCalls).toHaveLength(1);
		expect(fetchCalls[0]).toEqual({
			url: "/api/session/current",
			credentials: "include",
		});
		expect(r1).toEqual(r2);
		expect(r2).toEqual(r3);
		expect(r1).toMatchObject({
			authenticated: true,
			username: "sysadmin",
			browserId: "browser-admin-1",
		});
	});
});

describe("redirectToLoginWithReturn", () => {
	it("records redirect intent instead of forcing a hard navigation", async () => {
		const sessionAuth = (await import("./session-auth")) as Record<string, unknown>;
		const redirectToLoginWithReturn = sessionAuth.redirectToLoginWithReturn as (() => void) | undefined;
		const useRedirectIntentStore = sessionAuth.useRedirectIntentStore as
			| { getState: () => { intent: { returnPath: string } | null } }
			| undefined;

		expect(typeof redirectToLoginWithReturn).toBe("function");
		expect(useRedirectIntentStore).toBeDefined();

		Object.defineProperty(window, "location", {
			value: {
				...window.location,
				hash: "",
				pathname: "/admin/users",
				search: "",
				replace: vi.fn(),
			},
			writable: true,
			configurable: true,
		});

		redirectToLoginWithReturn?.();

		expect(useRedirectIntentStore?.getState().intent).toMatchObject({
			returnPath: "/admin/users",
		});
		expect(window.location.replace).not.toHaveBeenCalled();
	});
});
