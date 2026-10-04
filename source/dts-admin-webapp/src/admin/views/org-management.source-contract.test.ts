import fs from "node:fs";
import path from "node:path";
import { describe, expect, it } from "vitest";

const source = fs.readFileSync(path.join(import.meta.dirname, "org-management.tsx"), "utf8");

describe("org management member preview contract", () => {
	it("hides disabled accounts from the department member list", () => {
		expect(source).toMatch(/const isAccountActive = \(u\?: KeycloakUser\) => u\?\.enabled !== false;/);
		// 两条取数路径都要过滤：组成员列表与按组/部门编码扫描的兜底路径
		expect(source).toMatch(/\.filter\(\(x\) => x\.username && x\.active\)/);
		expect(source).toMatch(/if \(!isAccountActive\(u\)\) return false;/);
	});
});
