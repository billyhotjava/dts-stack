import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("platform session management source contract", () => {
	it("keeps browser idle timeout out of login guard and login page", () => {
		const guardSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/components/login-auth-guard.tsx"),
			"utf8",
		);
		const loginPageSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../pages/sys/login/index.tsx"),
			"utf8",
		);

		expect(guardSource.includes("function isSessionIdle")).toBe(false);
		expect(guardSource.includes("isSessionIdle()")).toBe(false);
		expect(loginPageSource.includes("function isSessionIdle")).toBe(false);
		expect(loginPageSource.includes("isSessionIdle()")).toBe(false);
	});

	it("lets the backend remain the single source of truth for portal session expiry", () => {
		const sessionManagerSource = fs.readFileSync(path.resolve(import.meta.dirname, "./session-manager.tsx"), "utf8");
		const guardSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../routes/components/login-auth-guard.tsx"),
			"utf8",
		);
		const loginPageSource = fs.readFileSync(
			path.resolve(import.meta.dirname, "../../pages/sys/login/index.tsx"),
			"utf8",
		);

		expect(sessionManagerSource.includes("logoutDueToIdle")).toBe(false);
		expect(sessionManagerSource.includes("window.setTimeout(logoutDueToIdle")).toBe(false);
		expect(sessionManagerSource.includes("backend remains")).toBe(true);
		expect(sessionManagerSource.includes("PORTAL_SESSION_STORAGE_KEYS")).toBe(true);
		expect(sessionManagerSource.includes("wasPortalLogoutBroadcastRecently")).toBe(true);
		expect(sessionManagerSource.includes("FOLLOWER_RECHECK_MS")).toBe(true);
		expect(sessionManagerSource.includes("refreshPortalSessionIfPossible")).toBe(true);
		expect(guardSource.includes("tokenExpiresAt - 10_000")).toBe(false);
		expect(guardSource.includes("backend session probe unavailable, preserving local session")).toBe(true);
		expect(guardSource.includes("forceLogout();\n\t\t\t\treturn;\n\t\t\t}\n\t\t\tsetSessionAuthenticated(true);")).toBe(false);
		expect(loginPageSource.includes("tokenExpiresAt - 10_000")).toBe(false);
		expect(guardSource.includes("getPortalSessionStatus(currentAccessToken)")).toBe(true);
	});
});
