// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/routes/constants", () => ({
	resolveLoginHref: () => "/login",
}));

vi.mock("@/store/userStore", () => {
	const readStore = () => {
		try {
			const raw = globalThis.localStorage?.getItem("userStore");
			return raw ? JSON.parse(raw) : {};
		} catch {
			return {};
		}
	};

	const writeUserToken = (userToken: Record<string, unknown>) => {
		const store = readStore();
		globalThis.localStorage?.setItem("userStore", JSON.stringify({
			...store,
			state: {
				...(store?.state ?? {}),
				userToken,
			},
		}));
	};

	const actions = {
		setUserToken: (userToken: Record<string, unknown>) => {
			writeUserToken(userToken);
		},
		clearUserInfoAndToken: () => {
			writeUserToken({});
		},
	};

	return {
		default: {
			getState: () => ({
				userToken: readStore()?.state?.userToken ?? {},
				actions,
			}),
		},
	};
});

const { fetchWithPlatformAuth } = await import("./analyticsApi");

class MemoryStorage implements Storage {
	private readonly store = new Map<string, string>();

	get length(): number {
		return this.store.size;
	}

	clear(): void {
		this.store.clear();
	}

	getItem(key: string): string | null {
		return this.store.has(key) ? this.store.get(key) ?? null : null;
	}

	key(index: number): string | null {
		return Array.from(this.store.keys())[index] ?? null;
	}

	removeItem(key: string): void {
		this.store.delete(key);
	}

	setItem(key: string, value: string): void {
		this.store.set(key, String(value));
	}
}

function readPersistedUserToken(storage: Storage): Record<string, unknown> {
	const raw = storage.getItem("userStore");
	if (!raw) {
		return {};
	}
	const store = JSON.parse(raw);
	return store?.state?.userToken && typeof store.state.userToken === "object" ? store.state.userToken : {};
}

function seedUserStore(storage: Storage, token: Record<string, unknown>): void {
	storage.setItem(
		"userStore",
		JSON.stringify({
			state: {
				userToken: token,
			},
		}),
	);
}

describe("fetchWithPlatformAuth", () => {
	const originalLocalStorage = globalThis.localStorage;
	const originalFetch = globalThis.fetch;

	beforeEach(() => {
		const storage = new MemoryStorage();
		Object.defineProperty(globalThis, "localStorage", {
			value: storage,
			configurable: true,
		});
	});

	afterEach(() => {
		globalThis.fetch = originalFetch;
		Object.defineProperty(globalThis, "localStorage", {
			value: originalLocalStorage,
			configurable: true,
		});
	});

	it("injects portal bearer token and credentials", async () => {
		seedUserStore(globalThis.localStorage, {
			accessToken: "portal-access-token",
			refreshToken: "portal-refresh-token",
		});

		let capturedAuthorization: string | null = null;
		let capturedCredentials: RequestCredentials | undefined;

		globalThis.fetch = vi.fn(async (_input: RequestInfo | URL, init?: RequestInit) => {
			capturedAuthorization = new Headers(init?.headers ?? {}).get("authorization");
			capturedCredentials = init?.credentials;
			return new Response(JSON.stringify({ ok: true }), {
				status: 200,
				headers: { "content-type": "application/json" },
			});
		}) as typeof fetch;

		const response = await fetchWithPlatformAuth("/bi/api/project-cockpit/screen/overview");
		expect(response.status).toBe(200);
		expect(capturedAuthorization).toBe("Bearer portal-access-token");
		expect(capturedCredentials).toBe("include");
	});

	it("refreshes analytics token and persists the replacement refresh token", async () => {
		seedUserStore(globalThis.localStorage, {
			accessToken: "portal-access-token",
			refreshToken: "portal-refresh-token",
		});

		let refreshCalls = 0;
		let protectedCalls = 0;

		globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
			const url = String(input);
			if (url === "/api/keycloak/auth/refresh") {
				refreshCalls += 1;
				expect(JSON.parse(String(init?.body ?? "{}"))).toEqual({
					refreshToken: "portal-refresh-token",
				});
				return new Response(JSON.stringify({
					accessToken: "portal-access-token-2",
					refreshToken: "portal-refresh-token-2",
				}), {
					status: 200,
					headers: { "content-type": "application/json" },
				});
			}

			protectedCalls += 1;
			const authorization = new Headers(init?.headers ?? {}).get("authorization");
			if (protectedCalls === 1) {
				expect(authorization).toBe("Bearer portal-access-token");
				return new Response("unauthorized", { status: 401 });
			}
			expect(authorization).toBe("Bearer portal-access-token-2");
			return new Response(JSON.stringify({ ok: true }), {
				status: 200,
				headers: { "content-type": "application/json" },
			});
		}) as typeof fetch;

		const response = await fetchWithPlatformAuth("/bi/api/project-cockpit/screen/overview");
		expect(response.status).toBe(200);
		expect(refreshCalls).toBe(1);
		expect(readPersistedUserToken(globalThis.localStorage).accessToken).toBe("portal-access-token-2");
		expect(readPersistedUserToken(globalThis.localStorage).refreshToken).toBe("portal-refresh-token-2");
	});

	it("shares a single refresh request across concurrent 401 responses", async () => {
		seedUserStore(globalThis.localStorage, {
			accessToken: "portal-access-token",
			refreshToken: "portal-refresh-token",
		});

		let refreshCalls = 0;
		const protectedCalls = new Map<string, number>();

		globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
			const url = String(input);
			if (url === "/api/keycloak/auth/refresh") {
				refreshCalls += 1;
				await new Promise((resolve) => setTimeout(resolve, 10));
				return new Response(JSON.stringify({
					accessToken: "portal-access-token-2",
					refreshToken: "portal-refresh-token-2",
				}), {
					status: 200,
					headers: { "content-type": "application/json" },
				});
			}

			const nextCount = (protectedCalls.get(url) ?? 0) + 1;
			protectedCalls.set(url, nextCount);
			const authorization = new Headers(init?.headers ?? {}).get("authorization");
			if (nextCount === 1) {
				expect(authorization).toBe("Bearer portal-access-token");
				return new Response("unauthorized", { status: 401 });
			}
			expect(authorization).toBe("Bearer portal-access-token-2");
			return new Response(JSON.stringify({ ok: true, url }), {
				status: 200,
				headers: { "content-type": "application/json" },
			});
		}) as typeof fetch;

		const [overviewResponse, screensResponse] = await Promise.all([
			fetchWithPlatformAuth("/bi/api/project-cockpit/screen/overview"),
			fetchWithPlatformAuth("/bi/api/project-cockpit/screens"),
		]);

		expect(overviewResponse.status).toBe(200);
		expect(screensResponse.status).toBe(200);
		expect(refreshCalls).toBe(1);
		expect(readPersistedUserToken(globalThis.localStorage).accessToken).toBe("portal-access-token-2");
		expect(readPersistedUserToken(globalThis.localStorage).refreshToken).toBe("portal-refresh-token-2");
	});
});
