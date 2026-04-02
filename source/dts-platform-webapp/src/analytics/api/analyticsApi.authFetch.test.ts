// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/routes/constants", () => ({
	resolveLoginHref: () => "/login",
}));

vi.mock("@/auth/session-auth", async () => {
	const actual = await vi.importActual<typeof import("@/auth/session-auth")>("@/auth/session-auth");
	return {
		...actual,
		fetchCurrentSession: vi.fn(async () => ({ authenticated: false as const })),
	};
});

vi.mock("@/store/userStore", () => ({
	default: {
		getState: () => ({
			userInfo: {
				username: "alice",
				roles: ["ROLE_OP_ADMIN"],
			},
		}),
	},
}));

const { fetchWithPlatformAuth } = await import("./analyticsApi");
const { fetchCurrentSession, useRedirectIntentStore, resetLoginRedirectFlag } = await import("@/auth/session-auth");

describe("fetchWithPlatformAuth", () => {
	const originalFetch = globalThis.fetch;

	beforeEach(() => {
		resetLoginRedirectFlag();
		vi.mocked(fetchCurrentSession).mockClear();
	});

	afterEach(() => {
		globalThis.fetch = originalFetch;
		vi.restoreAllMocks();
	});

	it("uses cookies only and does not inject portal bearer headers", async () => {
		let capturedAuthorization: string | null = "unset";
		let capturedRoles: string | null = "unset";
		let capturedCredentials: RequestCredentials | undefined;

		globalThis.fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
			capturedAuthorization = new Headers(init?.headers ?? {}).get("authorization");
			capturedRoles = new Headers(init?.headers ?? {}).get("x-dts-roles");
			capturedCredentials = init?.credentials;
			return new Response(JSON.stringify({ ok: true }), {
				status: 200,
				headers: { "content-type": "application/json" },
			});
		}) as typeof fetch;

		const response = await fetchWithPlatformAuth("/bi/api/screens/66");

		expect(response.status).toBe(200);
		expect(capturedAuthorization).toBeNull();
		expect(capturedRoles).toBeNull();
		expect(capturedCredentials).toBe("include");
	});

	it("redirects once on protected 401 without attempting token refresh", async () => {
		const calls: string[] = [];

		globalThis.fetch = vi.fn(async (input: RequestInfo | URL) => {
			calls.push(String(input));
			return new Response("unauthorized", { status: 401 });
		}) as typeof fetch;

		const response = await fetchWithPlatformAuth("/bi/api/screens/66");

		expect(response.status).toBe(401);
		expect(calls).toEqual(["/bi/api/screens/66"]);
		expect(useRedirectIntentStore.getState().intent).toMatchObject({
			returnPath: expect.stringContaining("/"),
		});
	});

	it("does not redirect on public analytics endpoints", async () => {
		globalThis.fetch = vi.fn(async () => new Response("unauthorized", { status: 401 })) as typeof fetch;

		const response = await fetchWithPlatformAuth("/bi/api/public/screens/public-uuid", {}, true);

		expect(response.status).toBe(401);
		expect(useRedirectIntentStore.getState().intent).toBeNull();
	});

	it("does not redirect on protected 401 when the shared session probe is still authenticated", async () => {
		vi.mocked(fetchCurrentSession).mockResolvedValueOnce({
			authenticated: true,
			username: "alice",
			roles: ["ROLE_OP_ADMIN"],
			permissions: ["portal.view"],
		});
		globalThis.fetch = vi.fn(async () => new Response("unauthorized", { status: 401 })) as typeof fetch;

		const response = await fetchWithPlatformAuth("/bi/api/screens/66");

		expect(response.status).toBe(401);
		expect(fetchCurrentSession).toHaveBeenCalled();
		expect(useRedirectIntentStore.getState().intent).toBeNull();
	});
});
