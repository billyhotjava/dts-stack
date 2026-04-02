import { describe, expect, it } from "vitest";

describe("session-state helpers", () => {
	it("treats bootstrapping sessions as blocking and unauthenticated", async () => {
		const mod = await import("./session-state").catch(() => null);

		expect(mod).not.toBeNull();
		expect(mod?.createBootstrappingSessionState()).toEqual({
			initialized: false,
			checking: true,
			authenticated: false,
			reason: "bootstrapping",
		});
		expect(mod?.shouldBlockWhileSessionBootstraps(mod!.createBootstrappingSessionState())).toBe(true);
		expect(mod?.canAccessProtectedRoute(mod!.createBootstrappingSessionState())).toBe(false);
	});

	it("allows redirect from login only for authenticated sessions", async () => {
		const mod = await import("./session-state").catch(() => null);

		expect(mod).not.toBeNull();
		expect(mod?.shouldAutoRedirectFromLogin(mod!.createAnonymousSessionState())).toBe(false);
		expect(mod?.shouldAutoRedirectFromLogin(mod!.createAuthenticatedSessionState())).toBe(true);
	});
});
