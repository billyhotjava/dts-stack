import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("admin session management source contract", () => {
	it("uses an admin-specific browser session namespace", () => {
		const sessionManagerSource = fs.readFileSync(path.resolve(import.meta.dirname, "./session-manager.tsx"), "utf8");

		expect(sessionManagerSource.includes('SESSION_ID: "dts.admin.session.id"')).toBe(true);
		expect(sessionManagerSource.includes('SESSION_USER: "dts.admin.session.user"')).toBe(true);
		expect(sessionManagerSource.includes('LOGOUT_TS: "dts.admin.session.logoutTs"')).toBe(true);
		expect(sessionManagerSource.includes('TOKEN_SYNC: "dts.admin.session.tokenSync"')).toBe(true);
	});

	it("does not implement browser-side idle logout timers", () => {
		const sessionManagerSource = fs.readFileSync(path.resolve(import.meta.dirname, "./session-manager.tsx"), "utf8");

		expect(sessionManagerSource.includes("IDLE_TIMEOUT")).toBe(false);
		expect(sessionManagerSource.includes("idleTimerRef")).toBe(false);
		expect(sessionManagerSource.includes("SESSION_TIMEOUT_MS")).toBe(false);
		expect(sessionManagerSource.includes("window.setTimeout")).toBe(false);
	});
});
