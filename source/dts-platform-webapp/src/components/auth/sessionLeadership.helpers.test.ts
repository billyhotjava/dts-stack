import { describe, expect, it } from "vitest";
import {
	buildSessionLeaderLease,
	isSessionLeaderActive,
	parseSessionLeaderLease,
	shouldAcquireSessionLeadership,
} from "./sessionLeadership.helpers";

describe("sessionLeadership.helpers", () => {
	it("parses valid leases", () => {
		expect(parseSessionLeaderLease('{"tabId":"tab-1","expiresAt":12345}')).toEqual({
			tabId: "tab-1",
			expiresAt: 12345,
		});
	});

	it("ignores invalid leases", () => {
		expect(parseSessionLeaderLease("")).toBeNull();
		expect(parseSessionLeaderLease("{bad json")).toBeNull();
		expect(parseSessionLeaderLease('{"tabId":"","expiresAt":"x"}')).toBeNull();
	});

	it("acquires leadership when lease is missing, expired, or already owned", () => {
		expect(shouldAcquireSessionLeadership(null, "tab-1", 1000)).toBe(true);
		expect(shouldAcquireSessionLeadership({ tabId: "tab-2", expiresAt: 999 }, "tab-1", 1000)).toBe(true);
		expect(shouldAcquireSessionLeadership({ tabId: "tab-1", expiresAt: 5000 }, "tab-1", 1000)).toBe(true);
		expect(shouldAcquireSessionLeadership({ tabId: "tab-2", expiresAt: 5000 }, "tab-1", 1000)).toBe(false);
	});

	it("builds active leases with finite expiry", () => {
		const lease = buildSessionLeaderLease("tab-1", 1000, 45000);
		expect(lease).toEqual({ tabId: "tab-1", expiresAt: 46000 });
		expect(isSessionLeaderActive(lease, 1001)).toBe(true);
		expect(isSessionLeaderActive(lease, 46000)).toBe(false);
	});
});
