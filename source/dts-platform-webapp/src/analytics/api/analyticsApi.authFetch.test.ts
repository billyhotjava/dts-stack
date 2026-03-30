import assert from "node:assert/strict";
import test from "node:test";
import { fetchWithPlatformAuth } from "./analyticsApi";

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

test("fetchWithPlatformAuth injects portal bearer token and credentials", async () => {
	const storage = new MemoryStorage();
	const originalLocalStorage = globalThis.localStorage;
	const originalFetch = globalThis.fetch;

	storage.setItem(
		"userStore",
		JSON.stringify({
			state: {
				userToken: {
					accessToken: "portal-access-token",
					refreshToken: "portal-refresh-token",
				},
			},
		}),
	);

	let capturedAuthorization: string | null = null;
	let capturedCredentials: RequestCredentials | undefined;

	Object.defineProperty(globalThis, "localStorage", {
		value: storage,
		configurable: true,
	});

	globalThis.fetch = (async (_input: RequestInfo | URL, init?: RequestInit) => {
		capturedAuthorization = new Headers(init?.headers ?? {}).get("authorization");
		capturedCredentials = init?.credentials;
		return new Response(JSON.stringify({ ok: true }), {
			status: 200,
			headers: { "content-type": "application/json" },
		});
	}) as typeof fetch;

	try {
		const response = await fetchWithPlatformAuth("/analytics/api/project-cockpit/screen/overview");
		assert.equal(response.status, 200);
		assert.equal(capturedAuthorization, "Bearer portal-access-token");
		assert.equal(capturedCredentials, "include");
	} finally {
		globalThis.fetch = originalFetch;
		Object.defineProperty(globalThis, "localStorage", {
			value: originalLocalStorage,
			configurable: true,
		});
	}
});
