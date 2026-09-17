import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const source = fs.readFileSync(path.join(import.meta.dirname, "user-management.tsx"), "utf8");

describe("user management list contract", () => {
	it("does not expose email as a top-level table column", () => {
		expect(source).not.toMatch(/title:\s*"邮箱"[\s\S]{0,160}dataIndex:\s*"email"/);
	});

	it("offers account-status and MDM-status filters wired into the list query", () => {
		expect(source).toContain('aria-label="按账号状态过滤"');
		expect(source).toContain('aria-label="按院级状态过滤"');
		expect(source).toMatch(/enabled:\s*toAccountEnabledParam\(accountStatus\)/);
		expect(source).toMatch(/status:\s*toMdmStatusParam\(mdmStatus\)/);
		expect(source).toMatch(/usersQueryKey = \[[^\]]*accountStatus[^\]]*mdmStatus[^\]]*\]/);
	});

	it("returns to the first page whenever a status filter changes", () => {
		for (const setter of ["setAccountStatus", "setMdmStatus"]) {
			const pattern = new RegExp(`${setter}\\(value\\);\\s*setPagination\\(\\(prev\\) => \\(\\{ \\.\\.\\.prev, current: 1 \\}\\)\\)`);
			expect(source).toMatch(pattern);
		}
	});
});
