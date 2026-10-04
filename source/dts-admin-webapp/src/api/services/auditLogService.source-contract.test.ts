import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

describe("AuditLogService endpoint contract", () => {
	const source = () => fs.readFileSync(path.resolve(import.meta.dirname, "./auditLogService.ts"), "utf8");

	it("uses the current audit entries API route", () => {
		const auditServiceSource = source();

		expect(auditServiceSource.includes('BASE_URL = "/audit-entries"')).toBe(true);
		expect(auditServiceSource.includes('BASE_URL = "/audit-logs"')).toBe(false);
	});
});
