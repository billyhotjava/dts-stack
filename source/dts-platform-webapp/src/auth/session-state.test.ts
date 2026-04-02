import { describe, expect, it } from "vitest";
import {
	canAccessProtectedRoute,
	createAnonymousSessionState,
	createAuthenticatedSessionState,
	shouldAutoRedirectFromLogin,
	shouldBlockWhileSessionBootstraps,
	type PortalSessionState,
} from "./session-state";

describe("session-state", () => {
	it("blocks protected content until the server-side session has been initialized", () => {
		const session: PortalSessionState = {
			initialized: false,
			checking: true,
			authenticated: false,
			reason: "bootstrapping",
		};

		expect(shouldBlockWhileSessionBootstraps(session)).toBe(true);
		expect(canAccessProtectedRoute(session)).toBe(false);
	});

	it("allows protected content only for authenticated session snapshots", () => {
		expect(canAccessProtectedRoute(createAuthenticatedSessionState())).toBe(true);
		expect(canAccessProtectedRoute(createAnonymousSessionState("expired"))).toBe(false);
	});

	it("auto-redirects from the login page only after an authenticated probe", () => {
		expect(shouldAutoRedirectFromLogin(createAuthenticatedSessionState())).toBe(true);
		expect(shouldAutoRedirectFromLogin(createAnonymousSessionState("anonymous"))).toBe(false);
		expect(shouldAutoRedirectFromLogin({
			initialized: false,
			checking: true,
			authenticated: false,
			reason: "bootstrapping",
		})).toBe(false);
	});
});
