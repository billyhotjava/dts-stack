// @vitest-environment jsdom
/**
 * Tests for session-auth.ts — the unified refresh lock and redirect guard.
 *
 * These tests exercise the actual logic this module owns:
 *  1. Single-flight refresh: concurrent callers share one in-flight request
 *  2. Lock release: completed refresh does NOT leak into subsequent calls
 *  3. Redirect guard: only one redirect fires, reset allows the next one
 */
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// ── Mocks ────────────────────────────────────────────────────────────────────

vi.mock("@/global-config", () => ({
	GLOBAL_CONFIG: { apiBaseUrl: "/api", routerHistory: "hash" },
}));

vi.mock("@/routes/constants", () => ({
	resolveLoginHref: () => "/#/auth/login",
}));

let storedToken: Record<string, unknown> = {};

vi.mock("@/store/userStore", () => ({
	default: {
		getState: () => ({
			userToken: storedToken,
			actions: {
				setUserToken: (t: Record<string, unknown>) => {
					storedToken = t;
				},
			},
		}),
	},
}));

const { refreshAccessToken, redirectToLoginWithReturn, resetLoginRedirectFlag, useRedirectIntentStore } =
	await import("./session-auth");

// ── Helpers ──────────────────────────────────────────────────────────────────

let fetchCalls: Array<{ url: string; body: unknown }>;
let fetchDelay: number;
let fetchOk: boolean;

function installFetchMock() {
	fetchCalls = [];
	fetchDelay = 0;
	fetchOk = true;

	globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
		const url = String(input);
		const body = init?.body ? JSON.parse(String(init.body)) : undefined;
		fetchCalls.push({ url, body });
		if (fetchDelay > 0) await new Promise((r) => setTimeout(r, fetchDelay));
		if (!fetchOk) return new Response("error", { status: 401 });
		return new Response(
			JSON.stringify({
				accessToken: `new-access-${fetchCalls.length}`,
				refreshToken: `new-refresh-${fetchCalls.length}`,
			}),
			{ status: 200, headers: { "content-type": "application/json" } },
		);
	}) as typeof fetch;
}

// ── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
	storedToken = { accessToken: "old-access", refreshToken: "old-refresh" };
	installFetchMock();
	resetLoginRedirectFlag();
});

afterEach(() => {
	vi.restoreAllMocks();
});

// ── Tests ────────────────────────────────────────────────────────────────────

describe("refreshAccessToken: single-flight lock", () => {
	it("concurrent callers share one in-flight refresh request", async () => {
		fetchDelay = 20; // simulate network latency
		const [r1, r2, r3] = await Promise.all([
			refreshAccessToken(),
			refreshAccessToken(),
			refreshAccessToken(),
		]);

		// All three get the same result
		expect(r1).not.toBeNull();
		expect(r1).toBe(r2);
		expect(r2).toBe(r3);

		// Only one fetch was made
		expect(fetchCalls).toHaveLength(1);
		expect(fetchCalls[0].url).toBe("/api/keycloak/auth/refresh");
	});

	it("releases lock after completion so the next call starts a fresh refresh", async () => {
		// First refresh
		const r1 = await refreshAccessToken();
		expect(r1).not.toBeNull();
		expect(fetchCalls).toHaveLength(1);

		// Second refresh — must NOT reuse the first result
		storedToken = { accessToken: "old-access-2", refreshToken: "old-refresh-2" };
		const r2 = await refreshAccessToken();
		expect(r2).not.toBeNull();
		expect(fetchCalls).toHaveLength(2);

		// Results are different (different fetch call ids)
		expect(r1!.accessToken).toBe("new-access-1");
		expect(r2!.accessToken).toBe("new-access-2");
	});

	it("releases lock even when refresh fails, after cooldown expires", async () => {
		fetchOk = false;
		const r1 = await refreshAccessToken();
		expect(r1).toBeNull();

		// The refresh has a 5s cooldown after failure. Advance Date.now() past it.
		const realDateNow = Date.now;
		const failedAt = realDateNow();
		Date.now = () => failedAt + 6_000;

		// Next call should start a fresh attempt
		fetchOk = true;
		const r2 = await refreshAccessToken();
		expect(r2).not.toBeNull();

		Date.now = realDateNow; // restore
	});

	it("returns null when no refresh token in store", async () => {
		storedToken = { accessToken: "access" };
		const result = await refreshAccessToken();
		expect(result).toBeNull();
		expect(fetchCalls).toHaveLength(0);
	});

	it("writes new tokens to userStore on success", async () => {
		const result = await refreshAccessToken();
		expect(result).not.toBeNull();
		expect(storedToken.accessToken).toBe("new-access-1");
		expect(storedToken.refreshToken).toBe("new-refresh-1");
	});
});

describe("redirectToLoginWithReturn: Zustand intent pattern", () => {
	beforeEach(() => {
		Object.defineProperty(window, "location", {
			value: {
				...window.location,
				hash: "#/bi/gpmc/drill/execution",
				pathname: "/",
				search: "",
			},
			writable: true,
			configurable: true,
		});
		resetLoginRedirectFlag();
	});

	it("writes redirect intent with current route to Zustand store", () => {
		redirectToLoginWithReturn();
		const intent = useRedirectIntentStore.getState().intent;
		expect(intent).not.toBeNull();
		expect(intent!.returnPath).toBe("/bi/gpmc/drill/execution");
	});

	it("deduplicates identical redirect paths", () => {
		redirectToLoginWithReturn();
		const seq1 = useRedirectIntentStore.getState().intent!.seq;
		redirectToLoginWithReturn();
		const seq2 = useRedirectIntentStore.getState().intent!.seq;
		expect(seq1).toBe(seq2); // same seq = not bumped
	});

	it("clears intent on resetLoginRedirectFlag", () => {
		redirectToLoginWithReturn();
		expect(useRedirectIntentStore.getState().intent).not.toBeNull();
		resetLoginRedirectFlag();
		expect(useRedirectIntentStore.getState().intent).toBeNull();
	});

	it("skips redirect when already on login page", () => {
		Object.defineProperty(window, "location", {
			value: { ...window.location, hash: "#/auth/login?redirect=%2Fdashboard", pathname: "/" },
			writable: true,
			configurable: true,
		});
		redirectToLoginWithReturn();
		expect(useRedirectIntentStore.getState().intent).toBeNull();
	});
});
