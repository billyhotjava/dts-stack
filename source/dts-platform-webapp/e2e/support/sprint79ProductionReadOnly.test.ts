import { describe, expect, it } from "vitest";
import { isProtectedDtsApiWrite, shouldIgnoreReadOnlyAbort } from "./sprint79ProductionReadOnly";

describe("isProtectedDtsApiWrite", () => {
	it.each([
		["POST", "/api/modeling/plans", true],
		["PATCH", "/admin/api/menus/1", true],
		["DELETE", "/analytics/api/metrics/1", true],
		["PUT", "/bi/api/screens/1", true],
		["GET", "/api/modeling/plans", false],
		["HEAD", "/admin/api/menus/1", false],
		["POST", "/assets/telemetry", false],
	])("%s %s protected=%s", (method, url, protectedWrite) => {
		expect(isProtectedDtsApiWrite(method, url)).toBe(protectedWrite);
	});
});

describe("shouldIgnoreReadOnlyAbort", () => {
	it("ignores only requests that the read-only barrier itself blocked", () => {
		expect(shouldIgnoreReadOnlyAbort("net::ERR_BLOCKED_BY_CLIENT", true)).toBe(true);
		expect(shouldIgnoreReadOnlyAbort("net::ERR_BLOCKED_BY_CLIENT", false)).toBe(false);
		expect(shouldIgnoreReadOnlyAbort("net::ERR_ABORTED", false)).toBe(false);
	});
});
