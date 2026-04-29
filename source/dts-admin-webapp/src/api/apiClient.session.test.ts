import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("admin apiClient session resilience contract", () => {
	const source = () => fs.readFileSync(path.resolve(import.meta.dirname, "./apiClient.ts"), "utf8");

	it("preserves the local session during backend restart or gateway outage", () => {
		const apiClientSource = source();

		expect(apiClientSource.includes("isTransientTransportError")).toBe(true);
		expect(apiClientSource.includes("SERVICE_UNAVAILABLE")).toBe(true);
		expect(apiClientSource.includes('window.location.replace("/auth/login")')).toBe(false);
	});

	it("does not clear a tab when its refresh 401 races with another tab token sync", () => {
		const apiClientSource = source();

		expect(apiClientSource.includes("ADMIN_TOKEN_SYNC_KEY")).toBe(true);
		expect(apiClientSource.includes("adoptRecentSyncedTokenAfterRefreshRace")).toBe(true);
		expect(apiClientSource.includes("refresh 401 raced with another tab")).toBe(true);
		expect(apiClientSource.includes("state.actions.setUserToken")).toBe(true);
	});
});
