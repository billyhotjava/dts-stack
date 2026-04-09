import { describe, expect, it } from "vitest";
import {
	buildSessionLeaderLease,
	isSessionLeaderActive,
	parseSessionLeaderLease,
	shouldAcquireSessionLeadership,
} from "./sessionLeadership.helpers";

describe("sessionLeadership.helpers", () => {
	it("parses valid leases", () => {
		expect(parseSessionLeaderLease('{"tabId":"tab-1","effectiveAt":12000,"expiresAt":12345}')).toEqual({
			tabId: "tab-1",
			effectiveAt: 12000,
			expiresAt: 12345,
		});
	});

	it("ignores invalid leases", () => {
		expect(parseSessionLeaderLease("")).toBeNull();
		expect(parseSessionLeaderLease("{bad json")).toBeNull();
		expect(parseSessionLeaderLease('{"tabId":"","effectiveAt":0,"expiresAt":"x"}')).toBeNull();
	});

	it("acquires leadership when lease is missing, expired, or already owned", () => {
		expect(shouldAcquireSessionLeadership(null, "tab-1", 1000)).toBe(true);
		expect(shouldAcquireSessionLeadership({ tabId: "tab-2", effectiveAt: 900, expiresAt: 999 }, "tab-1", 1000)).toBe(true);
		expect(shouldAcquireSessionLeadership({ tabId: "tab-1", effectiveAt: 1100, expiresAt: 5000 }, "tab-1", 1000)).toBe(true);
		expect(shouldAcquireSessionLeadership({ tabId: "tab-2", effectiveAt: 1100, expiresAt: 5000 }, "tab-1", 1000)).toBe(false);
	});

	it("builds leases with a confirmation window before they become active", () => {
		const lease = buildSessionLeaderLease("tab-1", 1000, 45000, 250);
		expect(lease).toEqual({ tabId: "tab-1", effectiveAt: 1250, expiresAt: 46000 });
		expect(isSessionLeaderActive(lease, 1100)).toBe(false);
		expect(isSessionLeaderActive(lease, 1250)).toBe(true);
		expect(isSessionLeaderActive(lease, 46000)).toBe(false);
	});
});
