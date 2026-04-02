// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/global-config", () => ({
	GLOBAL_CONFIG: { apiBaseUrl: "/api", routerHistory: "hash" },
}));

vi.mock("@/routes/constants", () => ({
	resolveLoginHref: () => "/#/auth/login",
}));

const {
	fetchCurrentSession,
	redirectToLoginWithReturn,
	resetLoginRedirectFlag,
	useRedirectIntentStore,
} = await import("./session-auth");

let fetchCalls: Array<{ url: string; credentials: RequestCredentials | undefined }>;
let fetchDelay = 0;
let responseBody: unknown = {
	authenticated: true,
	username: "alice",
	displayName: "Alice",
	browserId: "browser-1",
	expiresAt: "2026-04-02T01:23:45Z",
	roles: ["ROLE_OP_ADMIN"],
	permissions: ["portal.view"],
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
		username: "alice",
		displayName: "Alice",
		browserId: "browser-1",
		expiresAt: "2026-04-02T01:23:45Z",
		roles: ["ROLE_OP_ADMIN"],
		permissions: ["portal.view"],
	};
	resetLoginRedirectFlag();
});

afterEach(() => {
	vi.restoreAllMocks();
});

describe("fetchCurrentSession", () => {
	it("shares one in-flight /session/current probe across concurrent callers", async () => {
		fetchDelay = 20;

		const [r1, r2, r3] = await Promise.all([
			fetchCurrentSession(),
			fetchCurrentSession(),
			fetchCurrentSession(),
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
			username: "alice",
			browserId: "browser-1",
		});
	});

	it("normalizes unauthenticated probe responses without throwing", async () => {
		responseBody = { authenticated: false };

		const result = await fetchCurrentSession();

		expect(result).toEqual({ authenticated: false });
		expect(fetchCalls).toHaveLength(1);
	});
});

describe("redirectToLoginWithReturn", () => {
	beforeEach(() => {
		Object.defineProperty(window, "location", {
			value: {
				...window.location,
				hash: "#/bi/screens/66/preview",
				pathname: "/",
				search: "",
			},
			writable: true,
			configurable: true,
		});
		resetLoginRedirectFlag();
	});

	it("writes redirect intent with the current route", () => {
		redirectToLoginWithReturn();

		expect(useRedirectIntentStore.getState().intent).toMatchObject({
			returnPath: "/bi/screens/66/preview",
		});
	});

	it("skips when already on the login page", () => {
		Object.defineProperty(window, "location", {
			value: {
				...window.location,
				hash: "#/auth/login?redirect=%2Fworkbench",
				pathname: "/",
				search: "",
			},
			writable: true,
			configurable: true,
		});

		redirectToLoginWithReturn();

		expect(useRedirectIntentStore.getState().intent).toBeNull();
	});
});
